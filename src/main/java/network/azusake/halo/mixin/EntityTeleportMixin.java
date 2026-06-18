package network.azusake.halo.mixin;

import network.azusake.halo.lifecycle.EntityHaloTracker;
import network.azusake.halo.manager.HaloManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks both {@code requestTeleport} and {@code refreshPositionAfterTeleport} on
 * {@link Entity} so any teleport — regardless of distance, dimension, or whether
 * a subclass overrides one path — instantly triggers a halo snap.
 *
 * <h3>Why these two hooks cover <em>every</em> vanilla teleport</h3>
 *
 * <p>Both methods are {@code final} on {@code Entity.class}. Every subclass
 * that needs to move an entity discontinuously must go through one of them.
 * The complete call-chain audit:</p>
 *
 * <pre>
 * Teleport source           → entry point                                  → hook hit
 * ─────────────────────────────────────────────────────────────────────────────────────
 * /tp                       → TeleportCommand → Entity.requestTeleport()   → requestTeleport
 * /teleport (alias)         → same as /tp                                  → requestTeleport
 * /spreadplayers            → SpreadPlayersCommand → Entity.requestTeleport() → requestTeleport
 * /spectate                 → SpectatorCommand → Entity.teleport()         → requestTeleport
 * Ender pearl impact        → EnderPearlEntity → Player.requestTeleport()  → requestTeleport
 * Chorus fruit              → ChorusFruitItem → LivingEntity.teleport()    → requestTeleport
 * Boat / minecart dismount  → BoatEntity etc. → passenger.requestTeleport()→ requestTeleport
 * Nether portal travel      → Entity.changeDimension()                     → refreshPositionAfterTeleport
 * End portal travel         → Entity.changeDimension()                     → refreshPositionAfterTeleport
 * End gateway               → Entity.changeDimension()                     → refreshPositionAfterTeleport
 * /execute in <dim>         → ServerPlayerEntity.changeDimension()         → refreshPositionAfterTeleport
 * Player respawn            → ServerPlayerEntity.onRespawn()               → refreshPositionAfterTeleport
 * End return portal         → Entity.changeDimension()                     → refreshPositionAfterTeleport
 * </pre>
 *
 * <p>Both hooks call {@code markIfHasHalo()} which delegates to
 * {@link EntityHaloTracker#markTeleport(LivingEntity)} — triggering an
 * immediate snap on the next physics tick. The tracker also maintains a
 * distance-based safety net (threshold: 1000² blocks) as a last resort
 * against other mods that might bypass both canonical methods.</p>
 *
 * <p><b>Note:</b> {@code refreshPositionAfterTeleport} is also called by
 * {@code requestTeleport} internally, so some teleport paths fire both hooks.
 * This is harmless — the tracker's grace period (250 ms) debounces redundant
 * triggers.</p>
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
