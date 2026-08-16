package network.azusake.halo.lifecycle;

import network.azusake.halo.HaloMod;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.manager.HaloManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

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

    private static final String NAME = "halo_world_data";
    private static final String HALOS_KEY = "Halos";
    private static final String UUID_KEY = "UUID";
    private static final String DEF_KEY = "Definition";

    /** Halo assignments persisted to / loaded from world NBT. */
    private final List<HaloEntry> entries = new ArrayList<>();

    /** PersistentState type descriptor used by {@link #get(ServerLevel)}. */
    public static final SavedData.Factory<HaloWorldSaveData> TYPE = new SavedData.Factory<>(
        HaloWorldSaveData::new,
        HaloWorldSaveData::fromNbt,
        DataFixTypes.SAVED_DATA_MAP_DATA
    );

    // ------------------------------------------------------------------
    // PersistentState contract
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        // Serialise the stored entries directly.  Deliberately NOT synced from
        // HaloManager's activeHalos — that map is transient (cleared on death)
        // and must not overwrite the durable ownership record.
        ListTag haloList = new ListTag();

        for (HaloEntry entry : entries) {
            CompoundTag haloTag = new CompoundTag();
            haloTag.putString(UUID_KEY, entry.entityUuid().toString());
            haloTag.putString(DEF_KEY, entry.definitionId().toString());
            haloList.add(haloTag);
        }

        nbt.put(HALOS_KEY, haloList);
        return nbt;
    }

    /**
     * Factory: reconstruct from saved NBT.
     */
    public static HaloWorldSaveData fromNbt(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        HaloWorldSaveData data = new HaloWorldSaveData();

        if (!nbt.contains(HALOS_KEY)) {
            return data;
        }

        ListTag haloList = nbt.getList(HALOS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < haloList.size(); i++) {
            CompoundTag haloTag = haloList.getCompound(i);
            try {
                UUID uuid = UUID.fromString(haloTag.getString(UUID_KEY));
                ResourceLocation defId = ResourceLocation.parse(haloTag.getString(DEF_KEY));
                data.entries.add(new HaloEntry(uuid, defId));
            } catch (Exception e) {
                HaloMod.LOGGER.warn("HaloWorldSaveData: skipping malformed halo entry at index {}: {}",
                    i, e.getMessage());
            }
        }

        HaloMod.LOGGER.debug("HaloWorldSaveData: loaded {} halo entries from world NBT", data.entries.size());
        return data;
    }

    // ------------------------------------------------------------------
    // Access
    // ------------------------------------------------------------------

    /**
     * Get (or create) the persistent state for the given world.
     *
     * @param world the server world (typically {@code server.getOverworld()})
     * @return the persistent state instance, never {@code null}
     */
    public static HaloWorldSaveData get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(TYPE, NAME);
    }

    // ------------------------------------------------------------------
    // Mutation — the ONLY entry points for ownership changes
    // ------------------------------------------------------------------

    /**
     * Record that the entity with the given UUID owns the halo definition.
     * Idempotent upsert — assigning a new definition to the same entity
     * replaces the old entry (no duplicates).  Marks the state dirty so it
     * is written to disk on the next world save.
     *
     * <p>Callers: {@link HaloManager#showHaloOn} (from {@code /halo show}) and
     * the respawn / entity-load restoration paths, which must re-assert the
     * ownership the world save already records.</p>
     *
     * @param entityUuid the owning entity
     * @param defId      the halo definition identifier
     */
    public void set(UUID entityUuid, ResourceLocation defId) {
        entries.removeIf(e -> e.entityUuid().equals(entityUuid));
        entries.add(new HaloEntry(entityUuid, defId));
        setDirty();
    }

    /**
     * Remove the halo ownership record for the given entity.  Idempotent —
     * no-op if the entity has no entry.
     *
     * <p>Callers: {@link HaloManager#hideHaloOn} (from {@code /halo hide}),
     * and cleanup for non-player entities that died permanently.</p>
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
     *
     * @param entityUuid the owning entity UUID
     * @return the definition {@link ResourceLocation}, or {@code null}
     */
    public ResourceLocation get(UUID entityUuid) {
        for (HaloEntry entry : entries) {
            if (entry.entityUuid().equals(entityUuid)) {
                return entry.definitionId();
            }
        }
        return null;
    }

    /**
     * Check whether the given entity has a recorded halo ownership.
     *
     * @param entityUuid the owning entity UUID
     * @return {@code true} if the entity owns a halo
     */
    public boolean contains(UUID entityUuid) {
        return get(entityUuid) != null;
    }

    /**
     * Return an immutable snapshot of all recorded halo ownerships.
     */
    public List<HaloEntry> getAll() {
        return List.copyOf(entries);
    }

    // ------------------------------------------------------------------
    // Entry record
    // ------------------------------------------------------------------

    /**
     * A single halo assignment entry in world persistent state.
     *
     * @param entityUuid   the entity that bears the halo
     * @param definitionId the halo definition identifier
     */
    public record HaloEntry(UUID entityUuid, ResourceLocation definitionId) {}
}
