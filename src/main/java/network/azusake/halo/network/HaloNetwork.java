package network.azusake.halo.network;

import network.azusake.halo.HaloMod;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;
import network.azusake.halo.item.HaloScepterService;
import net.minecraft.entity.LivingEntity;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import network.azusake.halo.core.Identifier;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import static network.azusake.halo.platform.PlatformTypes.*;

/**
 * Server-side networking hub for halo state synchronisation.
 *
 * <p>Two S2C channels:
 * <ul>
 *   <li>{@code halo:sync} — full state snapshot sent when a player joins</li>
 *   <li>{@code halo:update} — incremental attach / remove broadcast to all players</li>
 * </ul>
 *
 * <p>For Fabric API 0.92.0+1.20.1, S2C channels do not require explicit server-side
 * registration — only the client must register a global receiver.  The
 * {@link #register()} method exists as a documentation hook and for future C2S
 * extension.</p>
 */
public final class HaloNetwork {

    /** Full-state snapshot — sent to a player on join. */
    public static final net.minecraft.util.Identifier CHANNEL_SYNC = new net.minecraft.util.Identifier("halo", "sync");

    /** Incremental attach / remove — broadcast to all players. */
    public static final net.minecraft.util.Identifier CHANNEL_UPDATE = new net.minecraft.util.Identifier("halo", "update");

    /** C2S — client reports its locally-available definition IDs. */
    public static final net.minecraft.util.Identifier CHANNEL_DEFS_REPORT = new net.minecraft.util.Identifier("halo", "defs_report");

    /** S2C — handshake, sent on player join to signal "server has the mod installed". */
    public static final net.minecraft.util.Identifier CHANNEL_HELLO = new net.minecraft.util.Identifier("halo", "hello");

    /** S2C — open the selector for a server-locked target. */
    public static final net.minecraft.util.Identifier CHANNEL_SCEPTER_OPEN = new net.minecraft.util.Identifier("halo", "scepter_open");

    /** S2C — close a selector whose server session became invalid. */
    public static final net.minecraft.util.Identifier CHANNEL_SCEPTER_CLOSE_SCREEN = new net.minecraft.util.Identifier("halo", "scepter_close_screen");

    /** C2S — apply a definition to the current locked target. */
    public static final net.minecraft.util.Identifier CHANNEL_SCEPTER_SELECT = new net.minecraft.util.Identifier("halo", "scepter_select");

    /** C2S — release the current target lock. */
    public static final net.minecraft.util.Identifier CHANNEL_SCEPTER_CLOSE = new net.minecraft.util.Identifier("halo", "scepter_close");

    /** C2S — crouching left-click on air/block removes the player's own halo. */
    public static final net.minecraft.util.Identifier CHANNEL_SCEPTER_REMOVE_SELF = new net.minecraft.util.Identifier("halo", "scepter_remove_self");

    private HaloNetwork() {
        // utility class
    }

    /**
     * Initialise the network layer (currently a no-op for S2C-only channels).
     */
    public static void register() {
        HaloMod.LOGGER.info("HaloNetwork: S2C channels registered (sync={}, update={}, defs_report={}, hello={})",
            CHANNEL_SYNC, CHANNEL_UPDATE, CHANNEL_DEFS_REPORT, CHANNEL_HELLO);
        registerServerReceivers();
    }

    /**
     * Register C2S packet handlers on the server side.
     */
    public static void registerServerReceivers() {
        // ---- Client definition report ----
        ServerPlayNetworking.registerGlobalReceiver(
            CHANNEL_DEFS_REPORT,
            (server, player, handler, buf, responseSender) -> {
                int count = buf.readInt();
                Set<Identifier> ids = new LinkedHashSet<>(count);
                for (int i = 0; i < count; i++) {
                    ids.add(core(buf.readIdentifier()));
                }
                server.execute(() ->
                    HaloJsonLoader.putClientReportedDefs(player.getUuid(), ids)
                );
            }
        );

        ServerPlayNetworking.registerGlobalReceiver(
            CHANNEL_SCEPTER_SELECT,
            (server, player, handler, buf, responseSender) -> {
                Identifier definitionId = core(buf.readIdentifier());
                server.execute(() -> HaloScepterService.select(player, definitionId));
            }
        );

        ServerPlayNetworking.registerGlobalReceiver(
            CHANNEL_SCEPTER_CLOSE,
            (server, player, handler, buf, responseSender) ->
                server.execute(() -> HaloScepterService.close(player.getUuid()))
        );

        ServerPlayNetworking.registerGlobalReceiver(
            CHANNEL_SCEPTER_REMOVE_SELF,
            (server, player, handler, buf, responseSender) ->
                server.execute(() -> HaloScepterService.remove(player, player, true))
        );
        HaloMod.LOGGER.info("HaloNetwork: C2S receivers registered");
    }

    // ------------------------------------------------------------------
    // Sending helpers
    // ------------------------------------------------------------------

    /**
     * Send the full active-halo snapshot to a single player (typically on join).
     *
     * @param player the player who just joined
     */
    public static void sendFullSync(ServerPlayerEntity player) {
        var snapshot = HaloManager.getInstance().ownershipSnapshot();
        var buf = HaloPacketCodec.encodeSnapshot(snapshot);

        ServerPlayNetworking.send(player, CHANNEL_SYNC, buf);
    }

    /**
     * Broadcast a halo-attach event to every online player.
     *
     * @param server     the current Minecraft server
     * @param entityUuid the entity that gained a halo
     * @param defId      the halo definition identifier
     */
    public static void sendHaloAttach(MinecraftServer server, UUID entityUuid, Identifier defId) {
        var buf = HaloPacketCodec.encodeUpdate(entityUuid, true, defId);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, CHANNEL_UPDATE, buf);
        }
    }

    /**
     * Broadcast a halo-remove event to every online player.
     *
     * @param server     the current Minecraft server
     * @param entityUuid the entity whose halo was removed
     */
    /**
     * Broadcast a halo removal to all online players, including the definition ID
     * so clients can play the shutdown animation even if the instance was already
     * removed from the shared map (integrated server mode).
     *
     * @param server     the current Minecraft server
     * @param entityUuid the entity whose halo was removed
     * @param defId      the halo definition identifier (for client-side shutdown animation)
     */
    public static void sendHaloRemove(MinecraftServer server, UUID entityUuid, Identifier defId) {
        var buf = HaloPacketCodec.encodeUpdate(entityUuid, false, defId);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, CHANNEL_UPDATE, buf);
        }
    }

    /**
     * Send the handshake hello packet to a single player, signalling that the
     * server has the Halo mod installed.  The empty payload is intentional —
     * the channel ID itself is the signal.  Future protocol version negotiation
     * can extend the payload if needed.
     *
     * @param player the player to notify
     */
    public static void sendHello(ServerPlayerEntity player) {
        var buf = PacketByteBufs.create();
        ServerPlayNetworking.send(player, CHANNEL_HELLO, buf);
    }

    /** Open the halo-scepter client screen for the locked target. */
    public static void sendScepterOpen(ServerPlayerEntity player, LivingEntity target) {
        var buf = PacketByteBufs.create();
        buf.writeInt(target.getId());
        writeUuid(buf, target.getUuid());
        buf.writeString(target.getDisplayName().getString(), 128);
        ServerPlayNetworking.send(player, CHANNEL_SCEPTER_OPEN, buf);
    }

    /** Close an open halo-scepter screen after invalidating its session. */
    public static void sendScepterClose(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, CHANNEL_SCEPTER_CLOSE_SCREEN, PacketByteBufs.empty());
    }

    // ------------------------------------------------------------------
    // Packet format helpers
    // ------------------------------------------------------------------

    /**
     * Write a UUID as two longs (most / least significant bits).
     *
     * <p>Minecraft 1.20.1's {@code PacketByteBuf} does not expose
     * {@code writeUuid} — we serialise manually.</p>
     */
    public static void writeUuid(net.minecraft.network.PacketByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    /**
     * Read a UUID from two longs (most / least significant bits).
     *
     * <p>This is the inverse of {@link #writeUuid} and is used by the client
     * receiver in {@link HaloNetworkClient}.</p>
     */
    public static UUID readUuid(net.minecraft.network.PacketByteBuf buf) {
        long most = buf.readLong();
        long least = buf.readLong();
        return new UUID(most, least);
    }
}
