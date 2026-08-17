package network.azusake.halo.network;

import io.netty.buffer.Unpooled;
import network.azusake.halo.HaloMod;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

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
 * <p>The channels use {@code CustomPayload} records registered via
 * {@link PayloadRegistrar} on the mod event bus.  The payloads wrap the exact
 * same byte layout as the 1.20.x protocol (see {@link HaloPayloads}).</p>
 */
public final class HaloNetwork {

    /** Full-state snapshot — sent to a player on join. */
    public static final Identifier CHANNEL_SYNC = HaloPayloads.Sync.ID.id();

    /** Incremental attach / remove — broadcast to all players. */
    public static final Identifier CHANNEL_UPDATE = HaloPayloads.Update.ID.id();

    /** C2S — client reports its locally-available definition IDs. */
    public static final Identifier CHANNEL_DEFS_REPORT = HaloPayloads.DefsReport.ID.id();

    /** S2C — handshake, sent on player join to signal "server has the mod installed". */
    public static final Identifier CHANNEL_HELLO = HaloPayloads.Hello.ID.id();

    private HaloNetwork() {
        // utility class
    }

    /**
     * Initialise the network layer: register payload types (codecs + IDs) and
     * the handlers.  Runs on the mod event bus during {@code
     * RegisterPayloadHandlersEvent} so both the dedicated server and the
     * integrated server can send S2C payloads and receive C2S payloads.
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        // Mark all channels optional so the client can join servers that do not
        // have the Halo mod installed (NeoForge drops missing optional channels
        // during negotiation instead of rejecting the connection).  The client
        // then stays in LOCAL mode until a halo:hello arrives — same behaviour
        // as the Fabric build.
        PayloadRegistrar registrar = event.registrar("1").optional();

        // S2C — the handler methods live on the client side; the type + codec
        // must be registered on both sides so the server can send.
        registrar.playToClient(HaloPayloads.Sync.ID, HaloPayloads.Sync.CODEC, HaloNetworkClient::handleSync);
        registrar.playToClient(HaloPayloads.Update.ID, HaloPayloads.Update.CODEC, HaloNetworkClient::handleUpdate);
        registrar.playToClient(HaloPayloads.Hello.ID, HaloPayloads.Hello.CODEC, HaloNetworkClient::handleHello);

        // C2S — client definition report
        registrar.playToServer(HaloPayloads.DefsReport.ID, HaloPayloads.DefsReport.CODEC, HaloNetwork::handleDefsReport);

        HaloMod.LOGGER.info("HaloNetwork: S2C channels registered (sync={}, update={}, defs_report={}, hello={})",
            CHANNEL_SYNC, CHANNEL_UPDATE, CHANNEL_DEFS_REPORT, CHANNEL_HELLO);
    }

    /**
     * C2S handler — client reports its locally-available definition IDs.
     */
    private static void handleDefsReport(HaloPayloads.DefsReport payload, IPayloadContext context) {
        var buf = payload.buf();
        int count = buf.readInt();
        Set<Identifier> ids = new LinkedHashSet<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(buf.readIdentifier());
        }
        UUID playerUuid = context.player().getUUID();
        context.enqueueWork(() ->
            HaloJsonLoader.putClientReportedDefs(playerUuid, ids)
        );
    }

    // ------------------------------------------------------------------
    // Sending helpers
    // ------------------------------------------------------------------

    /**
     * Send the full active-halo snapshot to a single player (typically on join).
     *
     * @param player the player who just joined
     */
    public static void sendFullSync(ServerPlayer player) {
        var instances = HaloManager.getInstance().getAllInstances();
        // Count only active instances
        int count = 0;
        for (HaloInstance inst : instances) {
            if (inst.isActive()) count++;
        }

        var buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(count);
        for (HaloInstance inst : instances) {
            if (!inst.isActive()) continue;
            writeUuid(buf, inst.getEntityUuid());
            buf.writeIdentifier(inst.getDefinitionId());
        }

        PacketDistributor.sendToPlayer(player, new HaloPayloads.Sync(buf));
    }

    /**
     * Broadcast a halo-attach event to every online player.
     *
     * @param server     the current Minecraft server
     * @param entityUuid the entity that gained a halo
     * @param defId      the halo definition identifier
     */
    public static void sendHaloAttach(MinecraftServer server, UUID entityUuid, Identifier defId) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        writeUuid(buf, entityUuid);
        buf.writeBoolean(true); // isAttach
        buf.writeIdentifier(defId);

        HaloPayloads.Update payload = new HaloPayloads.Update(buf);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

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
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        writeUuid(buf, entityUuid);
        buf.writeBoolean(false); // isAttach = false → removal
        buf.writeIdentifier(defId);

        HaloPayloads.Update payload = new HaloPayloads.Update(buf);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
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
    public static void sendHello(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new HaloPayloads.Hello(new FriendlyByteBuf(Unpooled.buffer())));
    }

    // ------------------------------------------------------------------
    // Packet format helpers
    // ------------------------------------------------------------------

    /**
     * Write a UUID as two longs (most / least significant bits).
     *
     * <p>Kept as explicit two-long serialisation to match the legacy 1.20.x
     * wire format byte-for-byte.</p>
     */
    public static void writeUuid(FriendlyByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    /**
     * Read a UUID from two longs (most / least significant bits).
     *
     * <p>This is the inverse of {@link #writeUuid} and is used by the client
     * receiver in {@link HaloNetworkClient}.</p>
     */
    public static UUID readUuid(FriendlyByteBuf buf) {
        long most = buf.readLong();
        long least = buf.readLong();
        return new UUID(most, least);
    }
}
