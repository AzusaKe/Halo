package network.azusake.halo.network;

import network.azusake.halo.HaloMod;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

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
    public static final Identifier CHANNEL_SYNC = new Identifier("halo", "sync");

    /** Incremental attach / remove — broadcast to all players. */
    public static final Identifier CHANNEL_UPDATE = new Identifier("halo", "update");

    /** C2S — client reports its locally-available definition IDs. */
    public static final Identifier CHANNEL_DEFS_REPORT = new Identifier("halo", "defs_report");

    /** S2C — handshake, sent on player join to signal "server has the mod installed". */
    public static final Identifier CHANNEL_HELLO = new Identifier("halo", "hello");

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
                    ids.add(buf.readIdentifier());
                }
                server.execute(() ->
                    HaloJsonLoader.putClientReportedDefs(player.getUuid(), ids)
                );
            }
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
        var instances = HaloManager.getInstance().getAllInstances();
        // Count only active instances
        int count = 0;
        for (HaloInstance inst : instances) {
            if (inst.isActive()) count++;
        }

        var buf = PacketByteBufs.create();
        buf.writeInt(count);
        for (HaloInstance inst : instances) {
            if (!inst.isActive()) continue;
            writeUuid(buf, inst.getEntityUuid());
            buf.writeIdentifier(inst.getDefinitionId());
        }

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
        var buf = PacketByteBufs.create();
        writeUuid(buf, entityUuid);
        buf.writeBoolean(true); // isAttach
        buf.writeIdentifier(defId);

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
    public static void sendHaloRemove(MinecraftServer server, UUID entityUuid) {
        var buf = PacketByteBufs.create();
        writeUuid(buf, entityUuid);
        buf.writeBoolean(false); // isAttach = false → removal

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
