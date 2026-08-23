package network.azusake.halo.physics;

import network.azusake.halo.data.EntityAnchorProfile;
import network.azusake.halo.data.PoseAnchor;
import network.azusake.halo.json.EntityAnchorLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import network.azusake.halo.api.EntityAnchorProvider;
import network.azusake.halo.api.HeadAnchor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *       head yaw, pitch and roll via {@link HeadFrameMath}.</li>
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

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerAnchorProvider.class);

    private static final Identifier PLAYER_ID = Identifier.fromNamespaceAndPath("minecraft", "player");

    private static final PlayerAnchorProvider INSTANCE = new PlayerAnchorProvider();

    private PlayerAnchorProvider() { /* singleton */ }

    public static PlayerAnchorProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public HeadAnchor resolve(LivingEntity entity, float tickDelta) {
        // 1. Interpolated foot position & head yaw/pitch
        double x = entity.xo + (entity.getX() - entity.xo) * tickDelta;
        double y = entity.yo + (entity.getY() - entity.yo) * tickDelta;
        double z = entity.zo + (entity.getZ() - entity.zo) * tickDelta;
        Vec3 footPos = new Vec3(x, y, z);

        float yaw = getInterpolatedHeadYaw(entity, tickDelta);
        float pitch = entity.xRotO + (entity.getXRot() - entity.xRotO) * tickDelta;
        Camera firstPersonCamera = getLocalFirstPersonCamera(entity);
        if (firstPersonCamera != null) {
            // Vanilla does not render the local player's body in first person,
            // so RenderHeadAnchorProvider necessarily reaches this fallback.
            // The camera is the rendered head in that view. Entity position and
            // head interpolation can lag, clamp, or omit camera bob independently
            // and make the Halo orbit around the player as the view turns.
            yaw = firstPersonCamera.yRot();
            pitch = firstPersonCamera.xRot();
        }
        float roll = getHeadRoll(entity);

        if (firstPersonCamera != null) {
            return new HeadAnchor(firstPersonCamera.position(), yaw, pitch, roll);
        }

        // 2. Pose key → PoseAnchor
        String poseKey = resolvePoseKey(entity);
        PoseAnchor pose = getPoseAnchor(poseKey);

        // 3. World-space pivot
        Vec3 pivotWorld = footPos.add(pose.pivot());

        // 4. Head orientation basis (shared with AnchorFrameCalculator)
        HeadFrameMath.HeadFrame frame = HeadFrameMath.of(yaw, pitch, roll);

        // 5. Project head_center_vector through basis → world-space head center
        Vec3 hcv = pose.headCenterVector();
        Vec3 offset = frame.right().scale(hcv.x)
            .add(frame.headUp().scale(hcv.y))
            .add(frame.forward().scale(hcv.z));
        Vec3 headCenter = pivotWorld.add(offset);

        return new HeadAnchor(headCenter, yaw, pitch, roll);
    }

    /**
     * Head roll for this entity this frame.  Vanilla entity heads never roll,
     * but the local player's head follows the camera, so its roll is inherited
     * from the actual camera rotation.  The 1.20.1 {@link Camera} exposes no
     * {@code getRoll()}; a roll (when present, e.g. from a camera mod) is folded
     * into {@link Camera#rotation()}, so it is recovered by stripping the
     * camera's own yaw/pitch component.  Other entities (including remote
     * players) always get 0.
     */
    private static float getHeadRoll(LivingEntity entity) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gameRenderer == null || entity != client.player) {
            return 0f;
        }
        Camera camera = client.gameRenderer.mainCamera();
        if (camera == null) {
            return 0f;
        }
        float roll = HeadFrameMath.recoverRollDeg(camera.yRot(), camera.xRot(), camera.rotation());
        if (Math.abs(roll) > 0.001f) {
            LOGGER.debug("Local player head roll recovered from camera: {} deg (camera yaw={}, pitch={})",
                roll, camera.yRot(), camera.xRot());
        }
        return roll;
    }

    /** Local first-person camera when it is actually attached to this entity. */
    private static Camera getLocalFirstPersonCamera(LivingEntity entity) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gameRenderer == null || entity != client.player
                || !client.options.getCameraType().isFirstPerson()) {
            return null;
        }
        Camera camera = client.gameRenderer.mainCamera();
        return camera != null && camera.entity() == entity ? camera : null;
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
            entity.isShiftKeyDown(),
            entity.onGround()
        );
    }

    /**
     * Pure pose-key decision, split out so the mapping can be unit-tested
     * without a Minecraft instance. Same priority order as
     * {@link #resolvePoseKey(LivingEntity)}.
     */
    static String resolvePoseKey(boolean sleeping, boolean fallFlying, boolean swimming,
                                 Pose pose, boolean sneaking, boolean onGround) {
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
        if (pose == Pose.SWIMMING) {
            return "crawling";
        }
        // Crouch anchor only applies on the ground; airborne sneaking keeps standing.
        if (onGround && (pose == Pose.CROUCHING || sneaking)) {
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
        new Vec3(0.0, 1.62, 0.0),    // pivot = eye height itself
        new Vec3(0.0, 0.0, 0.0)       // zero head_center_vector → head center IS the eye position
    );

    // ------------------------------------------------------------------
    // Interpolation helper
    // ------------------------------------------------------------------

    private static float getInterpolatedHeadYaw(LivingEntity entity, float tickDelta) {
        float prev = entity.yHeadRotO;
        float curr = entity.yHeadRot;
        float diff = curr - prev;
        if (diff > 180f) diff -= 360f;
        if (diff < -180f) diff += 360f;
        return prev + diff * tickDelta;
    }
}
