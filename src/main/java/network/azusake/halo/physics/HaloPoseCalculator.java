package network.azusake.halo.physics;

import network.azusake.halo.config.HaloConfig;
import network.azusake.halo.data.HaloDampingConfig;
import network.azusake.halo.data.HaloDefinition;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.manager.HaloManager;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Computes the world-space {@link HaloPose} for a halo instance each render
 * frame, using frame-rate-independent exponential damping.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Interpolate entity head position / orientation from tick state.</li>
 *   <li>Compute the halo target position from the definition's
 *       {@link network.azusake.halo.data.HaloPositioning#offset()} and the
 *       entity's current yaw/pitch (head-relative coordinate frame).</li>
 *   <li>Apply frame-rate-independent exponential damping so the halo
 *       converges to its target at a consistent real-time speed regardless
 *       of FPS.</li>
 *   <li>Compute the {@code toHead} direction vector used for billboard
 *       face-toward-camera rendering.</li>
 *   <li>Merge runtime {@link HaloConfig} overrides with definition defaults
 *       for both damping parameters and uniform scale.</li>
 * </ul>
 *
 * <h3>Threading</h3>
 * <p>This class runs exclusively on the render thread.  Per-instance state
 * ({@code prevFramePos}, {@code rotationStates}) is not synchronised because
 * no other thread accesses it.</p>
 *
 * <h3>Relationship to {@link DampingPhysics}</h3>
 * <p>Position damping uses the same exponential-decay formula as
 * {@link DampingPhysics} but works with absolute world-space coordinates
 * for simplicity (matching the legacy {@code HaloRenderer} behaviour).
 * Rotation damping delegates directly to
 * {@link DampingPhysics#computeDampedRotation} for quaternion slerp and
 * angular clamping.</p>
 */
public final class HaloPoseCalculator {

    private static final HaloPoseCalculator INSTANCE = new HaloPoseCalculator();

    /** Reference tick duration in seconds (50 ms = 20 TPS). */
    private static final double REFERENCE_TICK = 0.05;

    // ---- per-frame position state ----
    /** Previous frame halo world position, keyed by entity UUID. */
    private final Map<UUID, Vec3d> prevFramePos = new HashMap<>();

    // ---- per-instance rotation damping state ----
    private final Map<UUID, HaloDampingState> rotationStates = new HashMap<>();

    private HaloPoseCalculator() { /* singleton */ }

    public static HaloPoseCalculator getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Compute the world-space pose for one halo instance this frame.
     *
     * @param instance   the halo instance (provides UUID, snap flag)
     * @param entity     the living entity this halo is attached to
     * @param definition the parsed halo definition (offset, damping, scale)
     * @param camera     the player camera (for camera-relative coordinates)
     * @param tickDelta  partial-tick progress (0.0–1.0) for entity interpolation
     * @param frameDt    seconds since the previous render frame
     * @return a fully computed {@link HaloPose} ready for the renderer
     */
    public HaloPose calculate(
        HaloInstance instance,
        LivingEntity entity,
        HaloDefinition definition,
        Camera camera,
        float tickDelta,
        double frameDt
    ) {
        UUID uuid = instance.getEntityUuid();

        // 1. Interpolated entity head state
        Vec3d headAnchor = getInterpolatedHeadAnchor(entity, tickDelta);
        float yaw = getInterpolatedHeadYaw(entity, tickDelta);
        float pitch = entity.prevPitch + (entity.getPitch() - entity.prevPitch) * tickDelta;

        // 2. Head-relative offset → world-space target position
        Vec3d offset = getEffectiveOffset(definition);
        Vec3d headRelOffset = computeHeadRelativeOffset(yaw, pitch, offset);
        Vec3d targetPos = headAnchor.add(headRelOffset);

        // 3. Merge damping config (definition defaults vs runtime overrides)
        HaloDampingConfig damping = mergeDampingConfig(definition);

        // 4. Frame-rate-independent position damping
        Vec3d prevPos = prevFramePos.get(uuid);
        boolean needsSnap = instance.isNeedsSnap();
        if (prevPos == null || needsSnap) {
            prevPos = targetPos;
            if (needsSnap) {
                instance.setNeedsSnap(false);
            }
        }

        double k = Math.max(0.001, Math.min(damping.linearFactor(), 0.999));
        double exp = frameDt / REFERENCE_TICK;
        if (exp <= 0.0) exp = 1.0;
        if (exp > 10.0) exp = 10.0;
        double kF = 1.0 - Math.pow(1.0 - k, exp);
        kF = Math.max(0.0, Math.min(1.0, kF));

        Vec3d haloWorldPos = prevPos.add(targetPos.subtract(prevPos).multiply(kF));

        // Clamp to maxLinearDistance
        double dist = haloWorldPos.distanceTo(targetPos);
        double maxDist = damping.maxLinearDistance();
        if (dist > maxDist && dist > 1e-9) {
            Vec3d toTarget = targetPos.subtract(haloWorldPos).normalize();
            haloWorldPos = targetPos.subtract(toTarget.multiply(maxDist));
        }

        prevFramePos.put(uuid, haloWorldPos);

        // 5. toHead direction: from halo centre toward entity head
        Vec3d toHead = headAnchor.subtract(haloWorldPos).normalize();

        // 6. Rotation damping (converges toward identity)
        HaloDampingState rotState = rotationStates.computeIfAbsent(uuid,
            k2 -> new HaloDampingState());
        if (needsSnap) {
            rotState.markTeleport();
        }
        Quaternionf orientation = DampingPhysics.computeDampedRotation(
            rotState.prevRelativeRotation,
            new Quaternionf(),          // target = identity
            damping,
            rotState,
            frameDt
        );

        // 7. Scale (definition default vs runtime override)
        float scale = getRuntimeScaleOverride(definition);

        // 8. Camera-relative position
        Vec3d camPos = camera.getPos();
        Vec3d camRelPos = new Vec3d(
            haloWorldPos.x - camPos.x,
            haloWorldPos.y - camPos.y,
            haloWorldPos.z - camPos.z
        );

        return new HaloPose(haloWorldPos, camRelPos, orientation, scale, toHead);
    }

    /**
     * Remove state entries for UUIDs that are no longer visible, preventing
     * unbounded growth of the internal maps over long sessions.
     *
     * @param activeUuids the set of entity UUIDs that are currently visible
     */
    public void retainOnly(Set<UUID> activeUuids) {
        prevFramePos.keySet().retainAll(activeUuids);
        rotationStates.keySet().retainAll(activeUuids);
    }

    // ------------------------------------------------------------------
    // Entity interpolation helpers
    // ------------------------------------------------------------------

    /**
     * Interpolated head anchor position (frame-accurate, not tick-accurate).
     */
    private static Vec3d getInterpolatedHeadAnchor(LivingEntity entity, float tickDelta) {
        double x = entity.prevX + (entity.getX() - entity.prevX) * tickDelta;
        double y = entity.prevY + (entity.getY() - entity.prevY) * tickDelta;
        double z = entity.prevZ + (entity.getZ() - entity.prevZ) * tickDelta;
        if (entity instanceof PlayerEntity player) {
            return new Vec3d(x, y + player.getStandingEyeHeight(), z);
        }
        return new Vec3d(x, y + entity.getHeight() * 0.85, z);
    }

    /**
     * Interpolated head yaw (frame-accurate), with angle-wrapping correction.
     */
    private static float getInterpolatedHeadYaw(LivingEntity entity, float tickDelta) {
        float prev = entity.prevHeadYaw;
        float curr = entity.headYaw;
        float diff = curr - prev;
        if (diff > 180f) diff -= 360f;
        if (diff < -180f) diff += 360f;
        return prev + diff * tickDelta;
    }

    // ------------------------------------------------------------------
    // Head-relative offset
    // ------------------------------------------------------------------

    /**
     * Resolve the effective head-relative offset, preferring the runtime
     * config override ({@code /halo config offset}) if the user has changed
     * it, otherwise using the definition's {@code positioning.offset}.
     */
    private static Vec3d getEffectiveOffset(HaloDefinition definition) {
        HaloConfig runtime = HaloManager.getInstance().getConfig();
        Vec3d rtOffset = runtime.getPositionOffset();
        // Default runtime offset is (0, 0.2, 0) — if unchanged, use definition
        if (Math.abs(rtOffset.x) < 1e-9
            && Math.abs(rtOffset.y - 0.2) < 1e-9
            && Math.abs(rtOffset.z) < 1e-9) {
            return definition.positioning().offset();
        }
        return rtOffset;
    }

    /**
     * Convert a head-relative offset to a world-space direction vector.
     *
     * <p>Coordinate convention (right-handed, Minecraft entity frame):</p>
     * <ul>
     *   <li>{@code offset.x} → right of the entity</li>
     *   <li>{@code offset.y} → above the entity's head</li>
     *   <li>{@code offset.z} → behind the entity (positive = behind)</li>
     * </ul>
     *
     * @param yawDeg   interpolated head yaw in degrees
     * @param pitchDeg interpolated head pitch in degrees
     * @param offset   the head-relative offset vector [right, up, behind]
     * @return world-space direction vector to add to the head anchor
     */
    static Vec3d computeHeadRelativeOffset(float yawDeg, float pitchDeg, Vec3d offset) {
        float yawRad = (float) Math.toRadians(yawDeg);
        float pitchRad = (float) Math.toRadians(pitchDeg);

        Vec3d forward = new Vec3d(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        ).normalize();

        Vec3d worldUp = new Vec3d(0, 1, 0);
        Vec3d right;
        if (Math.abs(forward.dotProduct(worldUp)) > 0.999) {
            right = new Vec3d(Math.cos(yawRad), 0, Math.sin(yawRad));
        } else {
            right = worldUp.crossProduct(forward).normalize();
        }
        Vec3d headUp = forward.crossProduct(right).normalize();
        Vec3d behind = forward.multiply(-1);

        return right.multiply(offset.x)
            .add(headUp.multiply(offset.y))
            .add(behind.multiply(offset.z));
    }

    // ------------------------------------------------------------------
    // Config merging
    // ------------------------------------------------------------------

    /**
     * Merge runtime config overrides with definition defaults.
     *
     * <p>When the user runs {@code /halo config linear-damping <value>},
     * the runtime config carries the override.  If no override has been
     * set (all values match their HaloConfig defaults), the definition's
     * own damping config is used as-is.</p>
     */
    static HaloDampingConfig mergeDampingConfig(HaloDefinition definition) {
        HaloConfig runtime = HaloManager.getInstance().getConfig();

        boolean overridden =
            Math.abs(runtime.getLinearDampingFactor() - 0.3) > 1e-9
            || Math.abs(runtime.getAngularDampingFactor() - 0.3) > 1e-9
            || Math.abs(runtime.getMaxLinearDistance() - 1.0) > 1e-9
            || Math.abs(runtime.getMaxAngularDegrees() - 45.0) > 1e-9;

        if (overridden) {
            return new HaloDampingConfig(
                runtime.getLinearDampingFactor(),
                runtime.getAngularDampingFactor(),
                runtime.getMaxLinearDistance(),
                runtime.getMaxAngularDegrees()
            );
        }
        return definition.damping();
    }

    /**
     * Return the effective scale, preferring the runtime config override
     * if the user has changed it via {@code /halo config scale}.
     */
    private static float getRuntimeScaleOverride(HaloDefinition definition) {
        HaloConfig runtime = HaloManager.getInstance().getConfig();
        if (Math.abs(runtime.getHaloScale() - 1.0) > 1e-9) {
            return (float) runtime.getHaloScale();
        }
        return (float) definition.positioning().scale();
    }
}
