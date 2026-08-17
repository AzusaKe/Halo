package network.azusake.halo.network;

import io.netty.buffer.Unpooled;
import network.azusake.halo.data.HaloTransitionState;
import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;
import network.azusake.halo.render.HaloRenderer;
import network.azusake.halo.render.IdlePhaseTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side network receiver for halo state synchronisation.
 *
 * <p>Handles {@link HaloNetwork#CHANNEL_SYNC} (full snapshot), {@code
 * HaloNetwork#CHANNEL_UPDATE} (incremental attach/remove) and {@code
 * HaloNetwork#CHANNEL_HELLO} (handshake).  The handler methods are referenced
 * from the common payload registration in {@link HaloNetwork}; they only ever
 * run on the physical client.
 *
 * <p>Received state is written directly into the client's {@link HaloManager}
 * singleton, so the existing single-player rendering pipeline works unchanged
 * on dedicated-server clients.</p>
 *
 * <p><b>Thread safety:</b> the payload handlers run on the main client thread
 * (NeoForge wraps them), and every state mutation is additionally dispatched
 * via {@code enqueueWork}.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class HaloNetworkClient {

    private HaloNetworkClient() {
        // utility class
    }

    /**
     * Full-state snapshot handler (sent on player join).
     */
    public static void handleSync(HaloPayloads.Sync payload, IPayloadContext context) {
        var buf = payload.buf();
        int count = buf.readInt();
        Map<UUID, Identifier> incoming = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            UUID uuid = HaloNetwork.readUuid(buf);
            Identifier defId = buf.readIdentifier();
            incoming.put(uuid, defId);
        }
        context.enqueueWork(() -> {
            HaloManager.getInstance().replaceAllClientHalos(incoming);
            // New authoritative snapshot — drop any phase records from the
            // previous world/connection.
            HaloRenderer.getInstance().clearIdlePhases();
        });
    }

    /**
     * Incremental attach / remove handler (broadcast to all players).
     */
    public static void handleUpdate(HaloPayloads.Update payload, IPayloadContext context) {
        var buf = payload.buf();
        UUID uuid = HaloNetwork.readUuid(buf);
        boolean isAttach = buf.readBoolean();
        if (isAttach) {
            Identifier defId = buf.readIdentifier();
            context.enqueueWork(() ->
                HaloManager.getInstance().putClientHalo(uuid, defId, HaloTransitionState.STARTING)
            );
        } else {
            Identifier defId = buf.readIdentifier();
            boolean hasDefId = !defId.getPath().isEmpty();
            context.enqueueWork(() -> {
                // Set ENDING state — renderer will play shutdown animation
                HaloInstance inst = HaloManager.getInstance().getInstance(uuid);
                // Read the renderer-owned render state (idle phase + whether
                // the last frame was inside a transition + the per-group values
                // actually drawn) so a hide that lands mid-transition starts the
                // fade-out from the exact on-screen state.  Rendering state stays
                // client-owned.
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

    /**
     * Handshake hello handler (server has mod → transition to MULTIPLAYER).
     */
    public static void handleHello(HaloPayloads.Hello payload, IPayloadContext context) {
        context.enqueueWork(() ->
            HaloPhaseTracker.getInstance().transitionToMultiplayer()
        );
    }

    /**
     * Send the client's locally-available halo definition IDs to the server
     * so they appear in {@code /halo list} and tab-completion.
     * Safe to call at any time — silently no-ops when not connected to a world.
     */
    public static void sendDefsReport() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return; // not connected to a server — silently skip
        }
        // Only report to servers that negotiated the halo channels (i.e. have
        // the Halo mod installed).  Mirrors the Fabric build's
        // ClientPlayNetworking.canSend guard — on servers without the mod the
        // client stays in local mode and never sends this.
        if (!NetworkRegistry.hasChannel(connection, HaloNetwork.CHANNEL_DEFS_REPORT)) {
            return;
        }
        var defs = HaloJsonLoader.getDefinitions();
        if (defs.isEmpty()) return;

        var buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(defs.size());
        for (Identifier id : defs.keySet()) {
            buf.writeIdentifier(id);
        }
        ClientPacketDistributor.sendToServer(new HaloPayloads.DefsReport(buf));
    }
}
