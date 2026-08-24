package network.azusake.halo.lifecycle;

import network.azusake.halo.HaloMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Durable world-level halo ownership data. */
public final class HaloWorldSaveData extends SavedData {
    private static final String NAME = "halo_world_data";
    private static final String HALOS_KEY = "Halos";
    private static final String UUID_KEY = "UUID";
    private static final String DEF_KEY = "Definition";

    public static final SavedData.Factory<HaloWorldSaveData> TYPE = new SavedData.Factory<>(
        HaloWorldSaveData::new, HaloWorldSaveData::load, null);

    private final List<HaloEntry> entries = new ArrayList<>();

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag halos = new ListTag();
        for (HaloEntry entry : entries) {
            CompoundTag halo = new CompoundTag();
            halo.putString(UUID_KEY, entry.entityUuid().toString());
            halo.putString(DEF_KEY, entry.definitionId().toString());
            halos.add(halo);
        }
        tag.put(HALOS_KEY, halos);
        return tag;
    }

    public static HaloWorldSaveData load(CompoundTag tag, HolderLookup.Provider registries) {
        HaloWorldSaveData data = new HaloWorldSaveData();
        ListTag halos = tag.getList(HALOS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < halos.size(); i++) {
            CompoundTag halo = halos.getCompound(i);
            try {
                data.entries.add(new HaloEntry(UUID.fromString(halo.getString(UUID_KEY)),
                    ResourceLocation.parse(halo.getString(DEF_KEY))));
            } catch (RuntimeException exception) {
                HaloMod.LOGGER.warn("HaloWorldSaveData: skipping malformed halo entry at index {}: {}",
                    i, exception.getMessage());
            }
        }
        HaloMod.LOGGER.debug("HaloWorldSaveData: loaded {} halo entries from world NBT", data.entries.size());
        return data;
    }

    public static HaloWorldSaveData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE, NAME);
    }

    public void set(UUID entityUuid, ResourceLocation definitionId) {
        entries.removeIf(entry -> entry.entityUuid().equals(entityUuid));
        entries.add(new HaloEntry(entityUuid, definitionId));
        setDirty();
    }

    public void remove(UUID entityUuid) {
        if (entries.removeIf(entry -> entry.entityUuid().equals(entityUuid))) setDirty();
    }

    public ResourceLocation get(UUID entityUuid) {
        for (HaloEntry entry : entries) {
            if (entry.entityUuid().equals(entityUuid)) return entry.definitionId();
        }
        return null;
    }

    public boolean contains(UUID entityUuid) { return get(entityUuid) != null; }
    public List<HaloEntry> getAll() { return List.copyOf(entries); }
    public record HaloEntry(UUID entityUuid, ResourceLocation definitionId) {}
}
