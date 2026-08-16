package network.azusake.halo.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.entity.LivingEntity;
import network.azusake.halo.HaloMod;
import network.azusake.halo.data.HaloEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that hooks {@link LivingEntity#addAdditionalSaveData(ValueOutput)}
 * and {@link LivingEntity#readAdditionalSaveData(ValueInput)} so halo NBT
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
    @Inject(method = "addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V", at = @At("TAIL"))
    private void halo$writeCustomData(ValueOutput output, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        CompoundTag haloTag = HaloEntityData.getOrCreateTag(self);
        if (!haloTag.isEmpty()) {
            output.store(HaloEntityData.KEY, CompoundTag.CODEC, haloTag);
            HaloMod.LOGGER.debug("Mixin: wrote halo NBT for entity {}", self.getUUID());
        }
    }

    /**
     * Read halo NBT from the entity's custom data compound when the
     * entity is loaded from disk.
     */
    @Inject(method = "readAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueInput;)V", at = @At("TAIL"))
    private void halo$readCustomData(ValueInput input, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        input.read(HaloEntityData.KEY, CompoundTag.CODEC).ifPresent(tag -> {
            HaloEntityData.loadFromTag(self, tag);
            HaloMod.LOGGER.debug("Mixin: read halo NBT for entity {}", self.getUUID());
        });
    }
}
