package network.azusake.halo.lifecycle;

import network.azusake.halo.HaloMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.manager.HaloManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World-level persistent state that records halo assignments so they survive
 * death, world reload, and even a player respawn (which creates a new entity
 * object that does not inherit the old one's NBT).
 *
 * <p>This is the <em>single authoritative</em> record of who owns which halo.
 * Only {@code /halo show} and {@code /halo hide} (via
 * {@link HaloManager#showHaloOn}/{@link HaloManager#hideHaloOn}) may modify it.
 * Death, respawn, dimension travel, unload and disconnect only ever <em>read</em>
 * from it — they never grant or revoke ownership. A dead non-player entity's
 * entry is auto-pruned on cleanup; a dead player's entry is kept so the halo can
 * be restored on respawn.</p>
 *
 * <p>Entity-level NBT (see {@link HaloEntityData}) is <em>not</em> used for
 * restoration — it is kept as a diagnostic mirror only.</p>
 */
public class HaloWorldSaveData extends SavedData {

    private static final String HALOS_KEY = "Halos";
    private static final String UUID_KEY = "UUID";
    private static final String DEF_KEY = "Definition";

    /** Halo assignments persisted to / loaded from world NBT. */
    private final List<HaloEntry> entries = new ArrayList<>();

    /** Encode the adapter's identifier type using the stable string payload. */
    private static final Codec<Identifier> DEFINITION_CODEC = Codec.STRING.comapFlatMap(
        value -> {
            try {
                return DataResult.success(new Identifier(value));
            } catch (RuntimeException exception) {
                return DataResult.error(() -> "Invalid halo definition identifier '" + value
                    + "': " + exception.getMessage());
            }
        },
        Identifier::toString
    );

    /** Codec for a single halo ownership entry. */
    private static final Codec<HaloEntry> HALO_ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.LENIENT_CODEC.fieldOf(UUID_KEY).forGetter(HaloEntry::entityUuid),
        DEFINITION_CODEC.fieldOf(DEF_KEY).forGetter(HaloEntry::definitionId)
    ).apply(instance, HaloEntry::new));

    /**
     * Codec for the durable halo-ownership record.
     *
     * <p>{@link UUIDUtil#LENIENT_CODEC} reads both the flash format's UUID
     * integer array and the string form emitted by the affected Fabric
     * branches. Its primary encoder remains {@link UUIDUtil#CODEC}, so every
     * subsequent save is readable by flash.</p>
     */
    private static final Codec<HaloWorldSaveData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        HALO_ENTRY_CODEC.listOf().fieldOf(HALOS_KEY).orElse(List.of())
            .forGetter(data -> List.copyOf(data.entries))
    ).apply(instance, HaloWorldSaveData::new));

    /** SavedData descriptor for data/halo/world_data.dat. */
    public static final SavedDataType<HaloWorldSaveData> TYPE = new SavedDataType<>(
        net.minecraft.resources.Identifier.fromNamespaceAndPath("halo", "world_data"),
        HaloWorldSaveData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_MAP_DATA
    );

    // ------------------------------------------------------------------
    // SavedData contract
    // ------------------------------------------------------------------

    private HaloWorldSaveData() {
        // Used by SavedDataStorage when no file exists.
    }

    private HaloWorldSaveData(List<HaloEntry> halos) {
        entries.addAll(halos);
    }

    // ------------------------------------------------------------------
    // Access
    // ------------------------------------------------------------------

    /**
     * Get (or create) the persistent state for the given world.
     *
     * @param world the server world (typically {@code server.overworld()})
     * @return the persistent state instance, never {@code null}
     */
    public static HaloWorldSaveData get(ServerLevel world) {
        var storage = world.getDataStorage();
        var current = storage.get(TYPE);
        if (current != null) return current;
        var directory = net.minecraft.world.level.dimension.DimensionType.getStorageFolder(world.dimension(),
            world.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("data");
        var restored = readLegacyDirectory(directory);
        storage.set(TYPE, restored);
        return restored;
    }

    /** Import a pre-namespaced save once; never overwrite a current save or remove the old file. */
    static HaloWorldSaveData readLegacyDirectory(java.nio.file.Path directory) {
        // The newer storage location wins even when empty or unreadable: stale legacy entries must not revive.
        if (java.nio.file.Files.exists(directory.resolve("halo/world_data.dat"))) return new HaloWorldSaveData();
        for (String relative : new String[]{"minecraft/halo_world_data.dat", "halo_world_data.dat"}) {
            var file = directory.resolve(relative);
            if (!java.nio.file.Files.isRegularFile(file)) continue;
            try {
                var tag = net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.defaultQuota());
                var restored = TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,
                    tag.getCompoundOrEmpty("data")).getOrThrow();
                restored.setDirty();
                HaloMod.LOGGER.info("Imported {} legacy Halo ownership entries from {}", restored.entries.size(), file);
                return restored;
            } catch (java.io.IOException | RuntimeException error) {
                throw new IllegalStateException("Cannot import legacy Halo ownership from " + file, error);
            }
        }
        return new HaloWorldSaveData();
    }

    // ------------------------------------------------------------------
    // Mutation — the ONLY entry points for ownership changes
    // ------------------------------------------------------------------

    /**
     * Record that the entity with the given UUID owns the halo definition.
     * Idempotent upsert — assigning a new definition to the same entity
     * replaces the old entry (no duplicates). Marks the state dirty so it
     * is written to disk on the next world save.
     *
     * @param entityUuid the owning entity
     * @param defId      the halo definition identifier
     */
    public void set(UUID entityUuid, Identifier defId) {
        entries.removeIf(e -> e.entityUuid().equals(entityUuid));
        entries.add(new HaloEntry(entityUuid, defId));
        setDirty();
    }

    /**
     * Remove the halo ownership record for the given entity. Idempotent —
     * no-op if the entity has no entry.
     *
     * @param entityUuid the entity whose halo ownership is revoked
     */
    public void remove(UUID entityUuid) {
        boolean removed = entries.removeIf(e -> e.entityUuid().equals(entityUuid));
        if (removed) {
            setDirty();
        }
    }

    // ------------------------------------------------------------------
    // Query — read-only
    // ------------------------------------------------------------------

    /**
     * Look up the halo definition owned by the given entity, or {@code null}
     * if none is recorded.
     */
    public Identifier get(UUID entityUuid) {
        for (HaloEntry entry : entries) {
            if (entry.entityUuid().equals(entityUuid)) {
                return entry.definitionId();
            }
        }
        return null;
    }

    /** Check whether the given entity has a recorded halo ownership. */
    public boolean contains(UUID entityUuid) {
        return get(entityUuid) != null;
    }

    /** Return an immutable snapshot of all recorded halo ownerships. */
    public List<HaloEntry> getAll() {
        return List.copyOf(entries);
    }

    // ------------------------------------------------------------------
    // Entry record
    // ------------------------------------------------------------------

    /** A single halo assignment entry in world persistent state. */
    public record HaloEntry(UUID entityUuid, Identifier definitionId) {}
}
