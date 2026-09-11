package network.azusake.halo.network;

import network.azusake.halo.data.HaloTransitionState;
import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.client.HaloScepterScreen;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;
import network.azusake.halo.render.HaloRenderer;
import network.azusake.halo.render.IdlePhaseTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.resources.Identifier;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side network receiver for halo state synchronisation.
 *
 * <p>Registers handlers for {@link HaloNetwork#CHANNEL_SYNC} (full snapshot)
 * and {@link HaloNetwork#CHANNEL_UPDATE} (incremental attach/remove).
 *
 * <p>Received state is written directly into the client's {@link HaloManager}
 * singleton, so the existing single-player rendering pipeline works unchanged
 * on dedicated-server clients.</p>
 *
 * <p><b>Thread safety:</b> the Fabric networking callback runs on the netty
 * I/O thread.  All state mutations are dispatched to the main client thread
 * via {@code client.execute()}.</p>
 */
@Environment(EnvType.CLIENT)
public final class HaloNetworkClient {

    private HaloNetworkClient() {
        // utility class
    }

    /**
     * Register all S2C packet receivers.  Called from
     * {@code HaloModClient.onInitializeClient()}.
     */
    public static void registerReceivers() {
        // ---- Full snapshot (sent on player join) ----
        ClientPlayNetworking.registerGlobalReceiver(
            HaloPayloads.Sync.ID,
            (payload, context) -> {
                var buf = payload.buf();
                int count = buf.readInt();
                Map<UUID, Identifier> incoming = new HashMap<>(count);
                for (int i = 0; i < count; i++) {
                    UUID uuid = HaloNetwork.readUuid(buf);
                    Identifier defId = buf.readIdentifier();
                    incoming.put(uuid, defId);
                }
                context.client().execute(() -> {
                    HaloManager.getInstance().replaceAllClientHalos(incoming);
                    // New authoritative snapshot — drop any phase records from the
                    // previous world/connection.
                    HaloRenderer.getInstance().clearIdlePhases();
                });
            }
        );

        // ---- Incremental attach / remove ----
        ClientPlayNetworking.registerGlobalReceiver(
            HaloPayloads.Update.ID,
            (payload, context) -> {
                var buf = payload.buf();
                UUID uuid = HaloNetwork.readUuid(buf);
                boolean isAttach = buf.readBoolean();
                if (isAttach) {
                    Identifier defId = buf.readIdentifier();
                    context.client().execute(() ->
                        HaloManager.getInstance().putClientHalo(uuid, defId, HaloTransitionState.STARTING)
                    );
                } else {
                    Identifier defId = buf.readIdentifier();
                    boolean hasDefId = !defId.getPath().isEmpty();
                    context.client().execute(() -> {
                        // Set ENDING state — renderer will play shutdown animation
                        HaloInstance inst = HaloManager.getInstance().getInstance(uuid);
                        // Read the renderer-owned render state (idle phase +
                        // whether the last frame was inside a transition + the
                        // per-group values actually drawn) so a hide that lands
                        // mid-transition starts the fade-out from the exact
                        // on-screen state.  Rendering state stays client-owned.
                        IdlePhaseTracker.RenderState renderState =
                            HaloRenderer.getInstance().readLastRenderState(uuid);
                        double freeze;
                        if (inst == null && hasDefId) {
                            // In integrated server mode, the server already removed the
                            // instance from the shared activeHalos.  Create a fresh one
                            // with ENDING so the shutdown animation can play, and align
                            // its head to the last idle phase the renderer actually drew
                            // (owned by the renderer — no server involvement).  Without
                            // this the fresh instance would freeze at ~0 and the head
                            // would align to idle(0) instead of the last rendered frame.
                            HaloManager.getInstance().putClientHalo(uuid, defId);
                            inst = HaloManager.getInstance().getInstance(uuid);
                            freeze = renderState != null ? renderState.phase() : 0.0;
                        } else if (inst != null) {
                            var def = HaloJsonLoader.getDefinition(inst.getDefinitionId()).orElse(null);
                            freeze = inst.currentAnimTime(
                                def != null ? def.startupAnimation().orElse(null) : null);
                        } else {
                            return; // no definition id and no instance — nothing to animate
                        }
                        inst.setHiddenByState(false);
                        inst.setTransitionState(HaloTransitionState.ENDING);
                        inst.startTransition(freeze);
                        if (renderState != null && renderState.transitionActive()
                                && !renderState.groups().isEmpty()) {
                            // Hide landed mid-transition — head-patch the
                            // shutdown queues to the exact on-screen values.
                            inst.setHideVisuals(renderState.groups());
                        }
                    });
                }
            }
        );

        // ---- Handshake hello (server has mod → transition to MULTIPLAYER) ----
        ClientPlayNetworking.registerGlobalReceiver(
            HaloPayloads.Hello.ID,
            (payload, context) -> {
                context.client().execute(() ->
                    HaloPhaseTracker.getInstance().transitionToMultiplayer()
                );
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            HaloPayloads.ScepterOpen.ID,
            (payload, context) -> {
                var buf = payload.buf();
                int targetEntityId = buf.readInt();
                UUID targetUuid = HaloNetwork.readUuid(buf);
                String targetName = buf.readUtf(128);
                context.client().execute(() -> context.client().setScreen(
                    new HaloScepterScreen(targetEntityId, targetUuid, targetName)
                ));
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            HaloPayloads.ScepterCloseScreen.ID,
            (payload, context) -> context.client().execute(() -> {
                if (context.client().screen instanceof HaloScepterScreen) {
                    context.client().setScreen(null);
                }
            })
        );
    }

    public static void sendScepterSelection(Identifier definitionId) {
        if (!ClientPlayNetworking.canSend(HaloPayloads.ScepterSelect.ID)) {
            return;
        }
        var buf = FriendlyByteBufs.create();
        buf.writeIdentifier(definitionId);
        ClientPlayNetworking.send(new HaloPayloads.ScepterSelect(buf));
    }

    public static void sendScepterClose() {
        if (ClientPlayNetworking.canSend(HaloPayloads.ScepterClose.ID)) {
            ClientPlayNetworking.send(new HaloPayloads.ScepterClose(FriendlyByteBufs.create()));
        }
    }

    public static void sendScepterRemoveSelf() {
        if (ClientPlayNetworking.canSend(HaloPayloads.ScepterRemoveSelf.ID)) {
            ClientPlayNetworking.send(new HaloPayloads.ScepterRemoveSelf(FriendlyByteBufs.create()));
        }
    }

    /**
     * Send the client's locally-available halo definition IDs to the server
     * so they appear in {@code /halo list} and tab-completion.
     * Safe to call at any time — silently no-ops when not connected to a world.
     */
    public static void sendDefsReport() {
        if (!ClientPlayNetworking.canSend(HaloPayloads.DefsReport.ID)) {
            return; // not connected to a server — silently skip
        }
        var defs = HaloJsonLoader.getDefinitions();
        if (defs.isEmpty()) return;

        var buf = FriendlyByteBufs.create();
        buf.writeInt(defs.size());
        for (Identifier id : defs.keySet()) {
            buf.writeIdentifier(id);
        }
        ClientPlayNetworking.send(new HaloPayloads.DefsReport(buf));
    }
}
