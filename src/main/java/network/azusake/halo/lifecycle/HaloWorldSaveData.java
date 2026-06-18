package network.azusake.halo.lifecycle;

import network.azusake.halo.HaloMod;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.manager.HaloManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * World-level persistent state that records active halo assignments
 * so they survive {@code /save-all} and world reload.
 *
 * <p>Each entry is a simple (UUID, definition) pair.  When the world loads,
 * entities restore their own halos from entity-level persistent NBT
 * (see {@link HaloEntityData}).  This world-level index exists so
 * administrative tooling can inspect halo state without iterating every
 * loaded entity.</p>
 *
 * <p>Access pattern:</p>
 * <pre>{@code
 *   HaloWorldSaveData data = HaloWorldSaveData.get(world);
 *   data.syncFromManager();       // write current state
 * }</pre>
 *
 * <p>On world save, Minecraft automatically calls {@link #writeNbt(NbtCompound)}
 * when this state is dirty.</p>
 */
public class HaloWorldSaveData extends PersistentState {

    private static final String NAME = "halo_world_data";
    private static final String HALOS_KEY = "Halos";
    private static final String UUID_KEY = "UUID";
    private static final String DEF_KEY = "Definition";

    /** Snapshot of halo assignments persisted to / loaded from world NBT. */
    private final List<HaloEntry> entries = new ArrayList<>();

    // ------------------------------------------------------------------
    // PersistentState contract
    // ------------------------------------------------------------------

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        // Sync latest state from the manager before writing
        syncFromManager();

        NbtList haloList = new NbtList();

        for (HaloEntry entry : entries) {
            NbtCompound haloTag = new NbtCompound();
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
    public static HaloWorldSaveData fromNbt(NbtCompound nbt) {
        HaloWorldSaveData data = new HaloWorldSaveData();

        if (!nbt.contains(HALOS_KEY)) {
            return data;
        }

        NbtList haloList = nbt.getList(HALOS_KEY, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < haloList.size(); i++) {
            NbtCompound haloTag = haloList.getCompound(i);
            try {
                UUID uuid = UUID.fromString(haloTag.getString(UUID_KEY));
                Identifier defId = new Identifier(haloTag.getString(DEF_KEY));
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
    public static HaloWorldSaveData get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
            HaloWorldSaveData::fromNbt,
            HaloWorldSaveData::new,
            NAME
        );
    }

    /**
     * Read the current halo assignments from {@link HaloManager} and
     * overwrite the in-memory entry list.  Marks this state dirty so
     * Minecraft writes it on the next world save.
     */
    public void syncFromManager() {
        entries.clear();

        HaloManager.getInstance().getActiveHalos().forEach((uuid, instance) -> {
            entries.add(new HaloEntry(uuid, instance.getDefinitionId()));
        });

        if (!entries.isEmpty()) {
            markDirty();
        }
    }

    /**
     * Return an immutable snapshot of the persisted halo entries.
     */
    public List<HaloEntry> getEntries() {
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
    public record HaloEntry(UUID entityUuid, Identifier definitionId) {}
}
