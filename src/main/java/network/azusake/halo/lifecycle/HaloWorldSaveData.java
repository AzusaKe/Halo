package network.azusake.halo.lifecycle;

import network.azusake.halo.HaloMod;
import network.azusake.halo.manager.HaloManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Durable halo ownership map, kept compatible with the Fabric NBT layout. */
public class HaloWorldSaveData extends SavedData {
    public static final String NAME = "halo_world_data";
    private static final String HALOS_KEY = "Halos";
    private static final String UUID_KEY = "UUID";
    private static final String DEF_KEY = "Definition";
    private final List<HaloEntry> entries = new ArrayList<>();
    public HaloWorldSaveData() {}
    public static HaloWorldSaveData load(CompoundTag nbt) {
        HaloWorldSaveData data = new HaloWorldSaveData();
        ListTag list = nbt.getList(HALOS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            try { data.entries.add(new HaloEntry(UUID.fromString(tag.getString(UUID_KEY)), new ResourceLocation(tag.getString(DEF_KEY)))); }
            catch (Exception e) { HaloMod.LOGGER.warn("Skipping malformed halo entry {}: {}", i, e.getMessage()); }
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag nbt) {
        ListTag list = new ListTag();
        for (HaloEntry entry : entries) {
            CompoundTag tag = new CompoundTag();
            tag.putString(UUID_KEY, entry.entityUuid().toString());
            tag.putString(DEF_KEY, entry.definitionId().toString());
            list.add(tag);
        }
        nbt.put(HALOS_KEY, list);
        return nbt;
    }
    public static HaloWorldSaveData get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(HaloWorldSaveData::load, HaloWorldSaveData::new, NAME);
    }
    public void set(UUID uuid, ResourceLocation defId) {
        entries.removeIf(e -> e.entityUuid().equals(uuid));
        entries.add(new HaloEntry(uuid, defId));
        setDirty();
    }
    public void remove(UUID uuid) { if (entries.removeIf(e -> e.entityUuid().equals(uuid))) setDirty(); }
    public ResourceLocation get(UUID uuid) {
        for (HaloEntry e : entries) if (e.entityUuid().equals(uuid)) return e.definitionId();
        return null;
    }
    public boolean contains(UUID uuid) { return get(uuid) != null; }
    public List<HaloEntry> getAll() { return List.copyOf(entries); }
    public record HaloEntry(UUID entityUuid, ResourceLocation definitionId) {}
}
