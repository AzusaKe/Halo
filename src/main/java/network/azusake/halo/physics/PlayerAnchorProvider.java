package network.azusake.halo.physics;

import network.azusake.halo.data.EntityAnchorProfile;
import network.azusake.halo.data.PoseAnchor;
import network.azusake.halo.json.EntityAnchorLoader;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Player-specific {@link EntityAnchorProvider} that resolves the world-space head
 * center using pose-aware pivot and head-center-vector data loaded from
 * {@code data/halo/entity_anchors/player.json}.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Determine the player's current pose key (standing, sneaking, swimming, etc.).</li>
 *   <li>Look up the {@link PoseAnchor} for that key from the player profile.</li>
 *   <li>Compute world-space pivot:
 *       {@code pivotWorld = interpolatedFootPos + pose.pivot()}</li>
 *   <li>Build a head orientation basis (right, headUp, forward) from interpolated
 *       head yaw and pitch.</li>
 *   <li>Project the {@code headCenterVector} through this basis to get the
 *       world-space head center:
 *       {@code headCenter = pivotWorld + right * hcv.x + headUp * hcv.y + forward * hcv.z}</li>
 * </ol>
 *
 * <p>Fallback: if the player profile or pose entry is missing, the provider
 * falls back to the standard {@code standing} eye-height behaviour, which is
 * equivalent to what {@link FallbackAnchorProvider} does for non-player entities.</p>
 */
public final class PlayerAnchorProvider implements EntityAnchorProvider {

    private static final Identifier PLAYER_ID = new Identifier("minecraft", "player");

    private static final PlayerAnchorProvider INSTANCE = new PlayerAnchorProvider();

    private PlayerAnchorProvider() { /* singleton */ }

    public static PlayerAnchorProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public HeadAnchor resolve(LivingEntity entity, float tickDelta) {
        // 1. Interpolated foot position & head yaw/pitch
        double x = entity.prevX + (entity.getX() - entity.prevX) * tickDelta;
        double y = entity.prevY + (entity.getY() - entity.prevY) * tickDelta;
        double z = entity.prevZ + (entity.getZ() - entity.prevZ) * tickDelta;
        Vec3d footPos = new Vec3d(x, y, z);

        float yaw = getInterpolatedHeadYaw(entity, tickDelta);
        float pitch = entity.prevPitch + (entity.getPitch() - entity.prevPitch) * tickDelta;

        // 2. Pose key → PoseAnchor
        String poseKey = resolvePoseKey(entity);
        PoseAnchor pose = getPoseAnchor(poseKey);

        // 3. World-space pivot
        Vec3d pivotWorld = footPos.add(pose.pivot());

        // 4. Build head orientation basis (same convention as computeHeadRelativeOffset)
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);

        Vec3d forward = new Vec3d(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        ).normalize();

        Vec3d worldUp = new Vec3d(0, 1, 0);
        Vec3d right;
        if (Math.abs(forward.dotProduct(worldUp)) > 0.999) {
            right = new Vec3d(-Math.cos(yawRad), 0, -Math.sin(yawRad));
        } else {
            right = forward.crossProduct(worldUp).normalize();
        }
        Vec3d headUp = right.crossProduct(forward).normalize();

        // 5. Project head_center_vector through basis → world-space head center
        Vec3d hcv = pose.headCenterVector();
        Vec3d offset = right.multiply(hcv.x)
            .add(headUp.multiply(hcv.y))
            .add(forward.multiply(hcv.z));
        Vec3d headCenter = pivotWorld.add(offset);

        return new HeadAnchor(headCenter, yaw, pitch);
    }

    // ------------------------------------------------------------------
    // Pose resolution
    // ------------------------------------------------------------------

    /**
     * Map the entity's current state to a pose key string.
     *
     * <p>Priority order (first match wins):
     * <ol>
     *   <li>{@code sleeping}  — entity is in a bed</li>
     *   <li>{@code fall_flying} — gliding with elytra</li>
     *   <li>{@code swimming}  — in water (swimming upwards)</li>
     *   <li>{@code crawling}  — SWIMMING pose but not in water (stuck under block)</li>
     *   <li>{@code sneaking}  — crouching (shift key)</li>
     *   <li>{@code standing}  — default</li>
     * </ol>
     */
    static String resolvePoseKey(LivingEntity entity) {
        if (entity.isSleeping()) {
            return "sleeping";
        }
        if (entity.isFallFlying()) {
            return "fall_flying";
        }
        if (entity.isSwimming()) {
            return "swimming";
        }
        // EntityPose.SWIMMING without isSwimming() means crawling under block
        if (entity.getPose() == EntityPose.SWIMMING) {
            return "crawling";
        }
        if (entity.isSneaking() || entity.getPose() == EntityPose.CROUCHING) {
            return "sneaking";
        }
        return "standing";
    }

    // ------------------------------------------------------------------
    // Profile lookup
    // ------------------------------------------------------------------

    /**
     * Load the PoseAnchor for a pose key from the player profile.
     * Falls back to the profile's default pose, then to a hardcoded standing
     * equivalent (the old behaviour) if the profile itself is missing.
     */
    private static PoseAnchor getPoseAnchor(String poseKey) {
        // Try the player profile
        var profile = EntityAnchorLoader.getProfile(PLAYER_ID);
        if (profile.isPresent()) {
            var resolved = profile.get().resolve(poseKey);
            if (resolved.isPresent()) {
                return resolved.get();
            }
        }

        // Hard fallback: standing-equivalent (matches old getStandingEyeHeight behaviour)
        return FALLBACK_STANDING;
    }

    /** Hard fallback matching old {@code getStandingEyeHeight}-based behaviour. */
    private static final PoseAnchor FALLBACK_STANDING = new PoseAnchor(
        new Vec3d(0.0, 1.62, 0.0),    // pivot = eye height itself
        new Vec3d(0.0, 0.0, 0.0)       // zero head_center_vector → head center IS the eye position
    );

    // ------------------------------------------------------------------
    // Interpolation helper
    // ------------------------------------------------------------------

    private static float getInterpolatedHeadYaw(LivingEntity entity, float tickDelta) {
        float prev = entity.prevHeadYaw;
        float curr = entity.headYaw;
        float diff = curr - prev;
        if (diff > 180f) diff -= 360f;
        if (diff < -180f) diff += 360f;
        return prev + diff * tickDelta;
    }
}
