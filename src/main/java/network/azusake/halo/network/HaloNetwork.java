package network.azusake.halo.network;

import network.azusake.halo.HaloMod;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;
import network.azusake.halo.item.HaloScepterService;
import net.minecraft.entity.LivingEntity;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
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
 * <p>Fabric API 1.20.5+ removed the legacy {@code Identifier}-based networking
 * API, so all channels now use {@code CustomPayload} records registered via
 * {@link PayloadTypeRegistry}.  The payloads wrap the exact same byte layout
 * as the 1.20.x protocol (see {@link HaloPayloads}).</p>
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

    /** S2C — open the selector for a server-locked target. */
    public static final Identifier CHANNEL_SCEPTER_OPEN = HaloPayloads.ScepterOpen.ID.id();

    /** S2C — close a selector whose server session became invalid. */
    public static final Identifier CHANNEL_SCEPTER_CLOSE_SCREEN = HaloPayloads.ScepterCloseScreen.ID.id();

    /** C2S — apply a definition to the current locked target. */
    public static final Identifier CHANNEL_SCEPTER_SELECT = HaloPayloads.ScepterSelect.ID.id();

    /** C2S — release the current target lock. */
    public static final Identifier CHANNEL_SCEPTER_CLOSE = HaloPayloads.ScepterClose.ID.id();

    /** C2S — crouching left-click on air/block removes the player's own halo. */
    public static final Identifier CHANNEL_SCEPTER_REMOVE_SELF = HaloPayloads.ScepterRemoveSelf.ID.id();

    private HaloNetwork() {
        // utility class
    }

    /**
     * Initialise the network layer: register payload types (codecs + IDs) and
     * the C2S receiver.  Runs in common init so both the dedicated server and
     * the integrated server can send S2C payloads and receive C2S payloads.
     */
    public static void register() {
        PayloadTypeRegistry.playS2C().register(HaloPayloads.Sync.ID, HaloPayloads.Sync.CODEC);
        PayloadTypeRegistry.playS2C().register(HaloPayloads.Update.ID, HaloPayloads.Update.CODEC);
        PayloadTypeRegistry.playS2C().register(HaloPayloads.Hello.ID, HaloPayloads.Hello.CODEC);
        PayloadTypeRegistry.playS2C().register(HaloPayloads.ScepterOpen.ID, HaloPayloads.ScepterOpen.CODEC);
        PayloadTypeRegistry.playS2C().register(HaloPayloads.ScepterCloseScreen.ID, HaloPayloads.ScepterCloseScreen.CODEC);
        PayloadTypeRegistry.playC2S().register(HaloPayloads.DefsReport.ID, HaloPayloads.DefsReport.CODEC);
        PayloadTypeRegistry.playC2S().register(HaloPayloads.ScepterSelect.ID, HaloPayloads.ScepterSelect.CODEC);
        PayloadTypeRegistry.playC2S().register(HaloPayloads.ScepterClose.ID, HaloPayloads.ScepterClose.CODEC);
        PayloadTypeRegistry.playC2S().register(HaloPayloads.ScepterRemoveSelf.ID, HaloPayloads.ScepterRemoveSelf.CODEC);

        HaloMod.LOGGER.info("HaloNetwork: S2C channels registered (sync={}, update={}, defs_report={}, hello={})",
            CHANNEL_SYNC, CHANNEL_UPDATE, CHANNEL_DEFS_REPORT, CHANNEL_HELLO);
        registerServerReceivers();
    }

    /**
     * Register C2S packet handlers on the server side.
     */
    public static void registerServerReceivers() {
        // ---- Client definition report (C2S) ----
        ServerPlayNetworking.registerGlobalReceiver(
            HaloPayloads.DefsReport.ID,
            (payload, context) -> {
                var buf = payload.buf();
                int count = buf.readInt();
                Set<Identifier> ids = new LinkedHashSet<>(count);
                for (int i = 0; i < count; i++) {
                    ids.add(buf.readIdentifier());
                }
                context.server().execute(() ->
                    HaloJsonLoader.putClientReportedDefs(context.player().getUuid(), ids)
                );
            }
        );

        ServerPlayNetworking.registerGlobalReceiver(
            HaloPayloads.ScepterSelect.ID,
            (payload, context) -> {
                var buf = payload.buf();
                Identifier definitionId = buf.readIdentifier();
                context.server().execute(() -> HaloScepterService.select(context.player(), definitionId));
            }
        );

        ServerPlayNetworking.registerGlobalReceiver(
            HaloPayloads.ScepterClose.ID,
            (payload, context) ->
                context.server().execute(() -> HaloScepterService.close(context.player().getUuid()))
        );

        ServerPlayNetworking.registerGlobalReceiver(
            HaloPayloads.ScepterRemoveSelf.ID,
            (payload, context) ->
                context.server().execute(() -> HaloScepterService.remove(context.player(), context.player(), true))
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

        ServerPlayNetworking.send(player, new HaloPayloads.Sync(buf));
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

        HaloPayloads.Update payload = new HaloPayloads.Update(buf);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
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
        var buf = PacketByteBufs.create();
        writeUuid(buf, entityUuid);
        buf.writeBoolean(false); // isAttach = false → removal
        buf.writeIdentifier(defId);

        HaloPayloads.Update payload = new HaloPayloads.Update(buf);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
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
        ServerPlayNetworking.send(player, new HaloPayloads.Hello(PacketByteBufs.create()));
    }

    /** Open the halo-scepter client screen for the locked target. */
    public static void sendScepterOpen(ServerPlayerEntity player, LivingEntity target) {
        var buf = PacketByteBufs.create();
        buf.writeInt(target.getId());
        writeUuid(buf, target.getUuid());
        buf.writeString(target.getDisplayName().getString(), 128);
        ServerPlayNetworking.send(player, new HaloPayloads.ScepterOpen(buf));
    }

    /** Close an open halo-scepter screen after invalidating its session. */
    public static void sendScepterClose(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new HaloPayloads.ScepterCloseScreen(PacketByteBufs.create()));
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
