package network.azusake.halo.data;

import network.azusake.halo.HaloMod;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * NBT persistence wrapper for attaching halo metadata to entities.
 *
 * <p>At runtime halo data lives in a static {@link ConcurrentHashMap} keyed by
 * entity UUID for fast, lock-free access.  The {@code LivingEntityDataMixin}
 * bridges this map to the entity's on-disk NBT via
 * {@code writeCustomDataToNbt} / {@code readCustomDataFromNbt}.</p>
 *
 * <p>NBT layout:</p>
 * <pre>{@code
 *   HaloInstance: {
 *     Definition: "<definition-identifier>",
 *     Scale: 1.0,
 *     Offset: [x, y, z]
 *   }
 * }</pre>
 */
public final class HaloEntityData {

    /** Top-level NBT key shared between this class and the mixin. */
    public static final String KEY = "HaloInstance";

    private static final String DEF_KEY = "Definition";
    private static final String SCALE_KEY = "Scale";
    private static final String OFFSET_KEY = "Offset";

    /** Per-entity halo NBT.  Written at attach / remove time; read from the mixin on entity load. */
    private static final Map<UUID, CompoundTag> DATA = new ConcurrentHashMap<>();

    private HaloEntityData() {
        // utility class — no instances
    }

    // -----------------------------------------------------------------------
    // Attach / detach
    // -----------------------------------------------------------------------

    /**
     * Write halo metadata to the in-memory store so the mixin will persist it.
     *
     * @param entity       the target living entity
     * @param definitionId the halo definition identifier
     * @param scale        uniform scale multiplier
     * @param offsetX      X offset from entity anchor
     * @param offsetY      Y offset from entity anchor
     * @param offsetZ      Z offset from entity anchor
     */
    public static void attachHalo(LivingEntity entity, ResourceLocation definitionId,
                                  double scale, double offsetX, double offsetY, double offsetZ) {
        CompoundTag haloTag = new CompoundTag();
        haloTag.putString(DEF_KEY, definitionId.toString());
        haloTag.putDouble(SCALE_KEY, scale);

        ListTag offsetList = new ListTag();
        offsetList.add(net.minecraft.nbt.DoubleTag.valueOf(offsetX));
        offsetList.add(net.minecraft.nbt.DoubleTag.valueOf(offsetY));
        offsetList.add(net.minecraft.nbt.DoubleTag.valueOf(offsetZ));
        haloTag.put(OFFSET_KEY, offsetList);

        DATA.put(entity.getUUID(), haloTag);
    }

    /**
     * Convenience overload that attaches a halo with default scale (1.0)
     * and default offset (0, 0.3, 0).
     *
     * @param entity       the target living entity
     * @param definitionId the halo definition identifier
     */
    public static void attachHalo(LivingEntity entity, ResourceLocation definitionId) {
        attachHalo(entity, definitionId, 1.0, 0.0, 0.3, 0.0);
    }

    /**
     * Remove the halo data from the in-memory store (no-op if absent).
     *
     * @param entity the target living entity
     */
    public static void removeHalo(LivingEntity entity) {
        DATA.remove(entity.getUUID());
    }

    // -----------------------------------------------------------------------
    // Mixin bridge (called from LivingEntityDataMixin)
    // -----------------------------------------------------------------------

    /**
     * Return a copy of the current halo NBT tag for the given entity,
     * or an empty compound if no halo is attached.  Called by the mixin
     * during entity serialisation.
     *
     * @param entity the living entity being serialised
     * @return a copy of the halo NBT tag (never {@code null})
     */
    public static CompoundTag getOrCreateTag(LivingEntity entity) {
        CompoundTag tag = DATA.get(entity.getUUID());
        if (tag == null) {
            return new CompoundTag();
        }
        // Return a copy so the mixin can mutate it without affecting the stored version
        CompoundTag copy = new CompoundTag();
        copy.putString(DEF_KEY, tag.getString(DEF_KEY));
        if (tag.contains(SCALE_KEY)) {
            copy.putDouble(SCALE_KEY, tag.getDouble(SCALE_KEY));
        }
        if (tag.contains(OFFSET_KEY)) {
            copy.put(OFFSET_KEY, tag.getList(OFFSET_KEY, Tag.TAG_DOUBLE));
        }
        return copy;
    }

    /**
     * Populate the in-memory store from a deserialised NBT tag.
     * Called by the mixin when an entity is loaded from disk.
     *
     * @param entity the living entity being deserialised
     * @param tag    the halo NBT compound read from disk
     */
    public static void loadFromTag(LivingEntity entity, CompoundTag tag) {
        CompoundTag stored = new CompoundTag();
        if (tag.contains(DEF_KEY)) {
            stored.putString(DEF_KEY, tag.getString(DEF_KEY));
        }
        if (tag.contains(SCALE_KEY)) {
            stored.putDouble(SCALE_KEY, tag.getDouble(SCALE_KEY));
        }
        if (tag.contains(OFFSET_KEY)) {
            stored.put(OFFSET_KEY, tag.getList(OFFSET_KEY, Tag.TAG_DOUBLE));
        }
        DATA.put(entity.getUUID(), stored);
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    /**
     * Check whether the entity has a persisted halo tag.
     *
     * @param entity the target living entity
     * @return {@code true} if halo NBT data is present in the store
     */
    public static boolean hasHalo(LivingEntity entity) {
        return DATA.containsKey(entity.getUUID());
    }

    /**
     * Check whether a halo tag exists for the given entity UUID.
     *
     * @param entityUuid the entity UUID
     * @return {@code true} if halo NBT data is present in the store
     */
    public static boolean hasHalo(UUID entityUuid) {
        return DATA.containsKey(entityUuid);
    }

    /**
     * Read the halo definition identifier from the store.
     *
     * @param entity the target living entity
     * @return the definition {@link ResourceLocation}, or {@code null} if absent or malformed
     */
    public static ResourceLocation getHaloDefinition(LivingEntity entity) {
        CompoundTag tag = DATA.get(entity.getUUID());
        if (tag == null || !tag.contains(DEF_KEY)) {
            return null;
        }

        try {
            return ResourceLocation.parse(tag.getString(DEF_KEY));
        } catch (Exception e) {
            HaloMod.LOGGER.warn("HaloEntityData: malformed Definition in NBT for entity {}",
                entity.getUUID());
            return null;
        }
    }

    /**
     * Read the halo scale from the store.
     *
     * @param entity the target living entity
     * @return the scale value, or 1.0 if absent
     */
    public static double getScale(LivingEntity entity) {
        CompoundTag tag = DATA.get(entity.getUUID());
        if (tag == null || !tag.contains(SCALE_KEY)) {
            return 1.0;
        }
        return tag.getDouble(SCALE_KEY);
    }

    /**
     * Read the halo position offset from the store.
     *
     * @param entity the target living entity
     * @return a {@code double[3]} array of [x, y, z], or {@code null} if absent
     */
    public static double[] getOffset(LivingEntity entity) {
        CompoundTag tag = DATA.get(entity.getUUID());
        if (tag == null || !tag.contains(OFFSET_KEY)) {
            return null;
        }

        ListTag list = tag.getList(OFFSET_KEY, Tag.TAG_DOUBLE);
        if (list.size() < 3) {
            return null;
        }
        return new double[]{
            list.getDouble(0),
            list.getDouble(1),
            list.getDouble(2)
        };
    }
}
