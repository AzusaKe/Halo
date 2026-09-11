package network.azusake.halo.physics;

import network.azusake.halo.data.EntityAnchorProfile;
import network.azusake.halo.data.PoseAnchor;
import network.azusake.halo.json.EntityAnchorLoader;
import network.azusake.halo.anchor.AnchorPoseMath;
import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorVec3;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Player-specific fallback that resolves the world-space head
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
 *       head yaw, pitch and roll via {@link HeadFrameMath}.</li>
 *   <li>Project the {@code headCenterVector} through this basis to get the
 *       world-space head center:
 *       {@code headCenter = pivotWorld + right * hcv.x + headUp * hcv.y + forward * hcv.z}</li>
 * </ol>
 *
 * <p>Fallback: if the player profile or pose entry is missing, the provider
 * falls back to the standard {@code standing} eye-height behaviour, which is
 * equivalent to the internal Vanilla fallback for non-player entities.</p>
 */
public final class PlayerAnchorProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerAnchorProvider.class);

    private static final Identifier PLAYER_ID = Identifier.of("minecraft", "player");

    private static final PlayerAnchorProvider INSTANCE = new PlayerAnchorProvider();

    private PlayerAnchorProvider() { /* singleton */ }

    public static PlayerAnchorProvider getInstance() {
        return INSTANCE;
    }

    public AnchorPose resolve(LivingEntity entity, float tickDelta) {
        // 1. Interpolated foot position & head yaw/pitch
        double x = entity.prevX + (entity.getX() - entity.prevX) * tickDelta;
        double y = entity.prevY + (entity.getY() - entity.prevY) * tickDelta;
        double z = entity.prevZ + (entity.getZ() - entity.prevZ) * tickDelta;
        Vec3d footPos = new Vec3d(x, y, z);

        float yaw = getInterpolatedHeadYaw(entity, tickDelta);
        float pitch = entity.prevPitch + (entity.getPitch() - entity.prevPitch) * tickDelta;
        Camera firstPersonCamera = getLocalFirstPersonCamera(entity);
        if (firstPersonCamera != null) {
            yaw = firstPersonCamera.getYaw();
            pitch = firstPersonCamera.getPitch();
        }
        float roll = getHeadRoll(entity);

        if (firstPersonCamera != null) {
            // The local first-person camera is the rendered head.  Entity
            // interpolation omits camera bob and can lag independently,
            // causing shader-pass captures to orbit or jump around the player.
            return pose(firstPersonCamera.getPos(), yaw, pitch, roll);
        }

        // 2. Pose key → PoseAnchor
        String poseKey = resolvePoseKey(entity);
        PoseAnchor pose = getPoseAnchor(poseKey);

        // 3. World-space pivot
        Vec3d pivotWorld = footPos.add(pose.pivot());

        // 4. Head orientation basis (shared with AnchorFrameCalculator)
        HeadFrameMath.HeadFrame frame = HeadFrameMath.of(yaw, pitch, roll);

        // 5. Project head_center_vector through basis → world-space head center
        Vec3d hcv = pose.headCenterVector();
        Vec3d offset = frame.right().multiply(hcv.x)
            .add(frame.headUp().multiply(hcv.y))
            .add(frame.forward().multiply(hcv.z));
        Vec3d headCenter = pivotWorld.add(offset);

        return pose(headCenter, yaw, pitch, roll);
    }

    private static AnchorPose pose(Vec3d position, float yaw, float pitch, float roll) {
        return new AnchorPose(
            new AnchorVec3(position.x, position.y, position.z),
            AnchorPoseMath.fromMinecraftYawPitchRoll(yaw, pitch, roll)
        );
    }

    /**
     * Head roll for this entity this frame.  Vanilla entity heads never roll,
     * but the local player's head follows the camera, so its roll is inherited
     * from the actual camera rotation.  The 1.21.1 {@link Camera} exposes no
     * {@code getRoll()}; a roll (when present, e.g. from a camera mod) is folded
     * into {@link Camera#getRotation()}, so it is recovered by stripping the
     * camera's own yaw/pitch component.  Other entities (including remote
     * players) always get 0.
     */
    private static float getHeadRoll(LivingEntity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null || entity != client.player) {
            return 0f;
        }
        Camera camera = client.gameRenderer.getCamera();
        if (camera == null) {
            return 0f;
        }
        float roll = HeadFrameMath.recoverRollDeg(camera.getYaw(), camera.getPitch(), camera.getRotation());
        if (Math.abs(roll) > 0.001f) {
            LOGGER.debug("Local player head roll recovered from camera: {} deg (camera yaw={}, pitch={})",
                roll, camera.getYaw(), camera.getPitch());
        }
        return roll;
    }

    /** Local first-person camera when it is actually attached to this entity. */
    private static Camera getLocalFirstPersonCamera(LivingEntity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null || entity != client.player
            || !client.options.getPerspective().isFirstPerson()) {
            return null;
        }
        Camera camera = client.gameRenderer.getCamera();
        return camera != null && camera.getFocusedEntity() == entity ? camera : null;
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
     *   <li>{@code sneaking}  — crouching on the ground (shift key). Airborne
     *       sneaking keeps {@code standing}: on 1.20.1 the pose may already be
     *       {@code CROUCHING} midair, so the on-ground check is the actual gate.</li>
     *   <li>{@code standing}  — default</li>
     * </ol>
     */
    static String resolvePoseKey(LivingEntity entity) {
        return resolvePoseKey(
            entity.isSleeping(),
            entity.isFallFlying(),
            entity.isSwimming(),
            entity.getPose(),
            entity.isSneaking(),
            entity.isOnGround()
        );
    }

    /**
     * Pure pose-key decision, split out so the mapping can be unit-tested
     * without a Minecraft instance. Same priority order as
     * {@link #resolvePoseKey(LivingEntity)}.
     */
    static String resolvePoseKey(boolean sleeping, boolean fallFlying, boolean swimming,
                                 EntityPose pose, boolean sneaking, boolean onGround) {
        if (sleeping) {
            return "sleeping";
        }
        if (fallFlying) {
            return "fall_flying";
        }
        if (swimming) {
            return "swimming";
        }
        // EntityPose.SWIMMING without the swimming flag means crawling under a block
        if (pose == EntityPose.SWIMMING) {
            return "crawling";
        }
        // Crouch anchor only applies on the ground; airborne sneaking keeps standing.
        if (onGround && (pose == EntityPose.CROUCHING || sneaking)) {
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
