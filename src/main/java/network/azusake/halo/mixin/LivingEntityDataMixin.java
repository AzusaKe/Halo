package network.azusake.halo.mixin;

import network.azusake.halo.HaloMod;
import network.azusake.halo.data.HaloEntityData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that hooks {@link LivingEntity#writeCustomDataToNbt(NbtCompound)}
 * and {@link LivingEntity#readCustomDataFromNbt(NbtCompound)} so halo NBT
 * is serialised / deserialised as part of the entity's own NBT.
 *
 * <p>At runtime, {@link HaloEntityData} stores data in a static
 * {@code ConcurrentHashMap} for fast lock-free access.  This mixin bridges
 * that map to the entity's on-disk NBT representation.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDataMixin {

    /**
     * Write halo NBT into the entity's custom data compound before the
     * entity is saved to disk.
     */
    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void halo$writeCustomData(NbtCompound nbt, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        NbtCompound haloTag = HaloEntityData.getOrCreateTag(self);
        if (!haloTag.isEmpty()) {
            nbt.put(HaloEntityData.KEY, haloTag);
            HaloMod.LOGGER.debug("Mixin: wrote halo NBT for entity {}", self.getUuid());
        }
    }

    /**
     * Read halo NBT from the entity's custom data compound when the
     * entity is loaded from disk.
     */
    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void halo$readCustomData(NbtCompound nbt, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (nbt.contains(HaloEntityData.KEY)) {
            HaloEntityData.loadFromTag(self, nbt.getCompound(HaloEntityData.KEY));
            HaloMod.LOGGER.debug("Mixin: read halo NBT for entity {}", self.getUuid());
        }
    }
}
