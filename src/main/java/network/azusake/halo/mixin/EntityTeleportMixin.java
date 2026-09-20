package network.azusake.halo.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import network.azusake.halo.lifecycle.EntityHaloTracker;
import network.azusake.halo.manager.HaloManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks the 26.1 teleport entry points {@code snapTo} and {@code teleportTo} on
 * {@link Entity} so any teleport — regardless of distance, dimension, or whether
 * a subclass overrides one path — instantly triggers a halo snap.
 *
 * <h3>Why these two hooks cover <em>every</em> vanilla teleport</h3>
 *
 * <p>Every subclass that needs to move an entity discontinuously must go
 * through one of them. The complete call-chain audit:</p>
 *
 * <pre>
 * Teleport source           → entry point                     → hook hit
 * ─────────────────────────────────────────────────────────────────────────
 * /tp                       → TeleportCommand → Entity.snapTo() → snapTo
 * /spreadplayers            → SpreadPlayersCommand → Entity.snapTo() → snapTo
 * Ender pearl impact        → EnderPearlEntity → Player.teleportTo() → teleportTo
 * Chorus fruit              → ChorusFruitItem → LivingEntity.teleportTo() → teleportTo
 * Nether / End travel       → Entity.changeDimension()         → teleportTo
 * Player respawn            → ServerPlayer.onRespawn()         → teleportTo
 * </pre>
 *
 * <p>Both hooks call {@code markIfHasHalo()} which delegates to
 * {@link EntityHaloTracker#markTeleport(LivingEntity)} — triggering an
 * immediate snap on the next physics tick. The tracker also maintains a
 * distance-based safety net (threshold: 1000² blocks) as a last resort
 * against other mods that might bypass both canonical methods.</p>
 *
 * <p><b>Note:</b> {@code snapTo} is also called internally by some teleport
 * paths, so some teleports fire both hooks. This is harmless — the tracker's
 * grace period (250 ms) debounces redundant triggers.</p>
 */
@Mixin(Entity.class)
public abstract class EntityTeleportMixin {

    @Inject(method = "snapTo(DDD)V", at = @At("TAIL"))
    private void halo$afterSnapTo(double x, double y, double z, CallbackInfo ci) {
        markIfHasHalo();
    }

    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"))
    private void halo$onTeleportTo(double x, double y, double z, CallbackInfo ci) {
        markIfHasHalo();
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z", at = @At("HEAD"))
    private void halo$onTeleportToLevel(net.minecraft.server.level.ServerLevel level, double x, double y, double z,
                                        java.util.Set<net.minecraft.world.entity.Relative> relatives,
                                        float yaw, float pitch, boolean teleportCamera, CallbackInfoReturnable<Boolean> cir) {
        markIfHasHalo();
    }

    private void markIfHasHalo() {
        Entity self = (Entity) (Object) this;
        if (self instanceof LivingEntity living) {
            if (HaloManager.getInstance().getHaloInstance(living.getUUID()) != null) {
                EntityHaloTracker.markTeleport(living);
            }
        }
    }
}
