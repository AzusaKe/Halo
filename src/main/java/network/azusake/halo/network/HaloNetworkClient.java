package network.azusake.halo.network;

import network.azusake.halo.data.HaloTransitionState;
import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;
import network.azusake.halo.render.HaloRenderer;
import network.azusake.halo.render.IdlePhaseTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.resources.ResourceLocation;
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
                Map<UUID, ResourceLocation> incoming = new HashMap<>(count);
                for (int i = 0; i < count; i++) {
                    UUID uuid = HaloNetwork.readUuid(buf);
                    ResourceLocation defId = buf.readResourceLocation();
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
                    ResourceLocation defId = buf.readResourceLocation();
                    context.client().execute(() ->
                        HaloManager.getInstance().putClientHalo(uuid, defId, HaloTransitionState.STARTING)
                    );
                } else {
                    ResourceLocation defId = buf.readResourceLocation();
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

        var buf = PacketByteBufs.create();
        buf.writeInt(defs.size());
        for (ResourceLocation id : defs.keySet()) {
            buf.writeResourceLocation(id);
        }
        ClientPlayNetworking.send(new HaloPayloads.DefsReport(buf));
    }
}
