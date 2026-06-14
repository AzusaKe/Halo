package com.example.halo.mixin;

import com.example.halo.lifecycle.EntityHaloTracker;
import com.example.halo.manager.HaloManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks both {@code requestTeleport} and {@code refreshPositionAfterTeleport}
 * so any teleport — regardless of distance or whether the subclass overrides
 * one path — instantly triggers a halo snap.
 *
 * <p>Coverage:</p>
 * <ul>
 *   <li>{@code refreshPositionAfterTeleport} — always called after any teleport completes
 *       (dimension change, /tp, ender pearl, portal, gateway).</li>
 *   <li>{@code requestTeleport} — called by non-player entities and some codepaths
 *       that bypass the refresh hook.</li>
 * </ul>
 */
@Mixin(Entity.class)
public abstract class EntityTeleportMixin {

    @Inject(method = "refreshPositionAfterTeleport(DDD)V", at = @At("TAIL"))
    private void halo$afterTeleport(double x, double y, double z, CallbackInfo ci) {
        markIfHasHalo();
    }

    @Inject(method = "requestTeleport(DDD)V", at = @At("HEAD"))
    private void halo$onRequestTeleport(double x, double y, double z, CallbackInfo ci) {
        markIfHasHalo();
    }

    private void markIfHasHalo() {
        Entity self = (Entity) (Object) this;
        if (self instanceof LivingEntity living) {
            if (HaloManager.getInstance().getHaloInstance(living.getUuid()) != null) {
                EntityHaloTracker.markTeleport(living);
            }
        }
    }
}
