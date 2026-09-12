package network.azusake.halo.network;

import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.json.HaloJsonLoader;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.client.HaloScepterScreen;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static network.azusake.halo.platform.PlatformTypes.*;

/**
 * Client-side network receiver for halo state synchronisation.
 *
 * <p>Registers handlers for {@link HaloNetwork#CHANNEL_SYNC} (full snapshot)
 * and {@link HaloNetwork#CHANNEL_UPDATE} (incremental attach/remove).
 *
 * <p>Decoded messages update the client core replica. Animation state is owned exclusively by core.</p>
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
                var incoming = HaloPacketCodec.decodeSnapshot(buf);
                client.execute(() -> {
                    network.azusake.halo.platform.HaloClientState.get().replaceAllClientHalos(incoming);
                    // New authoritative snapshot — drop any phase records from the
                    // previous world/connection.

                });
            }
        );

        // ---- Incremental attach / remove ----
        ClientPlayNetworking.registerGlobalReceiver(
            HaloNetwork.CHANNEL_UPDATE,
            (client, handler, buf, responseSender) -> {
                var update = HaloPacketCodec.decodeUpdate(buf);
                client.execute(() -> {
                    var runtime = network.azusake.halo.platform.HaloClientState.get();
                    if (update.attach()) runtime.attach(update.entity(), update.definition(), true);
                    else runtime.hide(update.entity(), update.definition());
                });
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
        buf.writeIdentifier(game(definitionId));
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

        var buf = PacketByteBufs.create();
        buf.writeInt(defs.size());
        for (Identifier id : defs.keySet()) {
            buf.writeIdentifier(game(id));
        }
        ClientPlayNetworking.send(HaloNetwork.CHANNEL_DEFS_REPORT, buf);
    }
}
