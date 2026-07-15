package network.azusake.halo.network;

import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.util.Identifier;

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
                client.execute(() ->
                    HaloManager.getInstance().replaceAllClientHalos(incoming)
                );
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
                        HaloManager.getInstance().putClientHalo(uuid, defId)
                    );
                } else {
                    client.execute(() ->
                        HaloManager.getInstance().removeClientHalo(uuid)
                    );
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
