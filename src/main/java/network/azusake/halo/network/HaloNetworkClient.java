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
import net.minecraft.util.Identifier;
import network.azusake.halo.client.HaloScepterScreen;

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
            HaloNetwork.CHANNEL_SYNC,
            (client, handler, buf, responseSender) -> {
                int count = buf.readInt();
                Map<UUID, Identifier> incoming = new HashMap<>(count);
                for (int i = 0; i < count; i++) {
                    UUID uuid = HaloNetwork.readUuid(buf);
                    Identifier defId = buf.readIdentifier();
                    incoming.put(uuid, defId);
                }
                client.execute(() -> {
                    HaloManager.getInstance().replaceAllClientHalos(incoming);
                    // New authoritative snapshot — drop any phase records from the
                    // previous world/connection.
                    HaloRenderer.getInstance().clearIdlePhases();
                });
            }
        );

        // ---- Incremental attach / remove ----
        ClientPlayNetworking.registerGlobalReceiver(
            HaloNetwork.CHANNEL_UPDATE,
            (client, handler, buf, responseSender) -> {
                UUID uuid = HaloNetwork.readUuid(buf);
                boolean isAttach = buf.readBoolean();
                if (isAttach) {
                    Identifier defId = buf.readIdentifier();
                    client.execute(() ->
                        HaloManager.getInstance().putClientHalo(uuid, defId, HaloTransitionState.STARTING)
                    );
                } else {
                    Identifier defId = buf.readIdentifier();
                    boolean hasDefId = !defId.getPath().isEmpty();
                    client.execute(() -> {
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
            HaloNetwork.CHANNEL_HELLO,
            (client, handler, buf, responseSender) -> {
                client.execute(() ->
                    HaloPhaseTracker.getInstance().transitionToMultiplayer()
                );
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            HaloNetwork.CHANNEL_SCEPTER_OPEN,
            (client, handler, buf, responseSender) -> {
                int targetEntityId = buf.readInt();
                UUID targetUuid = HaloNetwork.readUuid(buf);
                String targetName = buf.readString(128);
                client.execute(() -> client.setScreen(
                    new HaloScepterScreen(targetEntityId, targetUuid, targetName)
                ));
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            HaloNetwork.CHANNEL_SCEPTER_CLOSE_SCREEN,
            (client, handler, buf, responseSender) -> client.execute(() -> {
                if (client.currentScreen instanceof HaloScepterScreen) {
                    client.setScreen(null);
                }
            })
        );
    }

    public static void sendScepterSelection(Identifier definitionId) {
        if (!ClientPlayNetworking.canSend(HaloNetwork.CHANNEL_SCEPTER_SELECT)) {
            return;
        }
        var buf = PacketByteBufs.create();
        buf.writeIdentifier(definitionId);
        ClientPlayNetworking.send(HaloNetwork.CHANNEL_SCEPTER_SELECT, buf);
    }

    public static void sendScepterClose() {
        if (ClientPlayNetworking.canSend(HaloNetwork.CHANNEL_SCEPTER_CLOSE)) {
            ClientPlayNetworking.send(HaloNetwork.CHANNEL_SCEPTER_CLOSE, PacketByteBufs.empty());
        }
    }

    public static void sendScepterRemoveSelf() {
        if (ClientPlayNetworking.canSend(HaloNetwork.CHANNEL_SCEPTER_REMOVE_SELF)) {
            ClientPlayNetworking.send(HaloNetwork.CHANNEL_SCEPTER_REMOVE_SELF, PacketByteBufs.empty());
        }
    }

    /**
     * Send the client's locally-available halo definition IDs to the server
     * so they appear in {@code /halo list} and tab-completion.
     * Safe to call at any time — silently no-ops when not connected to a world.
     */
    public static void sendDefsReport() {
        if (!ClientPlayNetworking.canSend(HaloNetwork.CHANNEL_DEFS_REPORT)) {
            return; // not connected to a server — silently skip
        }
        var defs = HaloJsonLoader.getDefinitions();
        if (defs.isEmpty()) return;

        var buf = PacketByteBufs.create();
        buf.writeInt(defs.size());
        for (Identifier id : defs.keySet()) {
            buf.writeIdentifier(id);
        }
        ClientPlayNetworking.send(HaloNetwork.CHANNEL_DEFS_REPORT, buf);
    }
}
