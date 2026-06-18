package network.azusake.halo.physics;

import network.azusake.halo.config.HaloConfig;
import network.azusake.halo.data.HaloDampingConfig;
import network.azusake.halo.data.HaloDefinition;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.data.OrientationMode;
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
 * Computes the world-space {@link AnchorFrame} for a halo instance each
 * render frame, using frame-rate-independent exponential damping.
 *
 * <h3>Orientation modes</h3>
 * <p>In both {@link OrientationMode#LOCKED} and {@link OrientationMode#FREE},
 * the halo normal (definition -Y) always points toward the entity's head.
 * The difference is only in the spin <em>around</em> the normal axis:</p>
 * <ul>
 *   <li><b>LOCKED</b> — spin is locked to the player's horizontal look
 *       direction.  An arrow on the halo maintains a fixed angle relative
 *       to where the player is looking.</li>
 *   <li><b>FREE</b> — spin is independently damped toward identity,
 *       giving the halo its own rotational inertia.</li>
 * </ul>
 *
 * <h3>Threading</h3>
 * <p>This class runs exclusively on the render thread.</p>
 */
public final class AnchorFrameCalculator {

    private static final AnchorFrameCalculator INSTANCE = new AnchorFrameCalculator();

    /** Reference tick duration in seconds (50 ms = 20 TPS). */
    private static final double REFERENCE_TICK = 0.05;

    /**
     * Angular damping factor for LOCKED-mode spin.
     * High enough to follow the player's look responsively,
     * but with enough smoothing to eliminate head-yaw noise.
     */
    private static final double LOCKED_ANGULAR_K = 0.5;

    // ---- per-frame position state ----
    private final Map<UUID, Vec3d> prevFramePos = new HashMap<>();

    // ---- per-instance rotation / spin damping state ----
    private final Map<UUID, HaloDampingState> rotationStates = new HashMap<>();

    // ---- per-instance locked-spin quaternion (damped) ----
    private final Map<UUID, Quaternionf> lockedSpinStates = new HashMap<>();

    private AnchorFrameCalculator() { /* singleton */ }

    public static AnchorFrameCalculator getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Compute the world-space anchor frame for one halo instance this frame.
     *
     * @param instance   the halo instance (provides UUID, snap flag)
     * @param entity     the living entity this halo is attached to
     * @param definition the parsed halo definition (model, offset, damping, scale)
     * @param camera     the player camera (for camera-relative coordinates)
     * @param tickDelta  partial-tick progress (0.0–1.0) for entity interpolation
     * @param frameDt    seconds since the previous render frame
     * @return a fully computed {@link AnchorFrame} ready for the renderer
     */
    public AnchorFrame calculate(
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
        float yawRad = (float) Math.toRadians(yaw);

        // 2. Head-relative offset → world-space target position
        Vec3d offset = getEffectiveOffset(definition);
        Vec3d headRelOffset = computeHeadRelativeOffset(yaw, pitch, offset);
        Vec3d targetPos = headAnchor.add(headRelOffset);

        // 3. Merge damping config
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

        // 6. Player horizontal look direction (XZ plane, ignoring pitch)
        Vec3d playerLookDir = new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad));

        // 7. Look-at orientation: shortest-arc rotation mapping definition -Y → toHead.
        //    This preserves the "up" direction as close to world-up as the
        //    rotation allows — matching the old axis-angle billboard-facing behaviour.
        Quaternionf Q_lookAt = computeLookAtOrientation(toHead);

        // 8. Per-instance rotation state
        HaloDampingState rotState = rotationStates.computeIfAbsent(uuid,
            k2 -> new HaloDampingState());
        if (needsSnap) {
            rotState.markTeleport();
        }

        // 9. Orientation mode — both modes start from Q_lookAt; only the spin
        //    around the normal axis differs.
        OrientationMode mode = definition.model().orientationMode();
        Quaternionf worldOrientation;
        Vec3d worldForward;

        switch (mode) {
            case LOCKED -> {
                // Compute the ideal spin that would align +Z with player look
                Quaternionf Q_target = computeLockedSpin(Q_lookAt, toHead, playerLookDir);

                // Retrieve or initialise the damped locked-spin state
                Quaternionf prevSpin = lockedSpinStates.get(uuid);
                if (prevSpin == null || needsSnap) {
                    prevSpin = new Quaternionf(); // identity — no spin offset
                }
                if (needsSnap) {
                    // Snap to target immediately
                    lockedSpinStates.put(uuid, new Quaternionf(Q_target));
                    worldOrientation = Q_target.mul(Q_lookAt, new Quaternionf());
                } else {
                    // Frame-rate-independent slerp toward the target spin
                    double spinExp = frameDt / REFERENCE_TICK;
                    if (spinExp <= 0.0) spinExp = 1.0;
                    if (spinExp > 10.0) spinExp = 10.0;
                    double spinKF = 1.0 - Math.pow(1.0 - LOCKED_ANGULAR_K, spinExp);
                    spinKF = Math.max(0.0, Math.min(1.0, spinKF));

                    Quaternionf dampedSpin = new Quaternionf(prevSpin);
                    dampedSpin.slerp(Q_target, (float) spinKF);

                    lockedSpinStates.put(uuid, new Quaternionf(dampedSpin));
                    worldOrientation = dampedSpin.mul(Q_lookAt, new Quaternionf());
                }
                worldForward = new Vec3d(0, 0, 1); // will be rotated below
            }
            case FREE -> {
                // Damp the spin angle around the normal independently toward identity
                DampingPhysics.computeDampedRotation(
                    rotState.prevRelativeRotation,
                    new Quaternionf(),          // target = identity (no spin)
                    damping,
                    rotState,
                    frameDt
                );
                Quaternionf Q_spin = new Quaternionf(rotState.prevRelativeRotation);
                worldOrientation = Q_spin.mul(Q_lookAt, new Quaternionf());
                worldForward = new Vec3d(0, 0, 1);
            }
            default -> {
                worldOrientation = Q_lookAt;
                worldForward = new Vec3d(0, 0, 1);
            }
        }

        // Rotate definition +Z through world orientation to get world forward
        worldForward = rotate(worldForward, worldOrientation);

        // 10. Scale
        float scale = getRuntimeScaleOverride(definition);

        // 11. Camera-relative position
        Vec3d camPos = camera.getPos();
        Vec3d camRelPos = new Vec3d(
            haloWorldPos.x - camPos.x,
            haloWorldPos.y - camPos.y,
            haloWorldPos.z - camPos.z
        );

        return new AnchorFrame(haloWorldPos, camRelPos, worldOrientation, worldForward, toHead, scale);
    }

    /**
     * Remove state entries for UUIDs that are no longer visible.
     */
    public void retainOnly(Set<UUID> activeUuids) {
        prevFramePos.keySet().retainAll(activeUuids);
        rotationStates.keySet().retainAll(activeUuids);
        lockedSpinStates.keySet().retainAll(activeUuids);
    }

    // ------------------------------------------------------------------
    // Orientation math
    // ------------------------------------------------------------------

    /**
     * Compute the shortest-arc quaternion that maps definition -Y (the halo
     * normal) to {@code toHead}.
     *
     * <p>This is equivalent to the axis-angle billboard-facing rotation used
     * in the old code — it preserves the definition +Y axis as close to
     * world-up as the rotation allows, which keeps the texture orientation
     * stable regardless of which direction the entity faces.</p>
     *
     * @param toHead unit vector from halo toward entity head (world space)
     * @return quaternion that rotates definition -Y to toHead
     */
    static Quaternionf computeLookAtOrientation(Vec3d toHead) {
        Vec3d from = new Vec3d(0, -1, 0); // definition -Y (billboard normal)
        double dot = from.dotProduct(toHead);
        if (dot > 0.9999) {
            return new Quaternionf(); // identity — already aligned
        }
        if (dot < -0.9999) {
            // Opposite directions — 180° around any ⟂ axis; use +X
            return new Quaternionf().rotateAxis((float) Math.PI, 1, 0, 0);
        }
        Vec3d axis = from.crossProduct(toHead).normalize();
        float angle = (float) Math.acos(dot);
        return new Quaternionf().rotateAxis(angle, (float) axis.x, (float) axis.y, (float) axis.z);
    }

    /**
     * Compute a spin quaternion around {@code toHead} (the normal axis)
     * that aligns definition +Z (as transformed by {@code Q_lookAt}) with
     * the projection of {@code playerLookDir} onto the plane
     * perpendicular to {@code toHead}.
     *
     * <p>This is used in {@link OrientationMode#LOCKED} — the full
     * orientation is {@code Q_spin * Q_lookAt}.</p>
     *
     * @param Q_lookAt      shortest-arc rotation mapping definition -Y → toHead
     * @param toHead        unit vector from halo toward entity head
     * @param playerLookDir player's horizontal look direction (XZ plane)
     * @return spin quaternion around toHead
     */
    static Quaternionf computeLockedSpin(Quaternionf Q_lookAt, Vec3d toHead, Vec3d playerLookDir) {
        // Where does definition +Z end up after the look-at rotation?
        Vec3d Z_current = rotate(new Vec3d(0, 0, 1), Q_lookAt);

        // Where we want +Z to go: playerLookDir projected onto plane ⟂ toHead
        double dotFN = playerLookDir.dotProduct(toHead);
        Vec3d F_proj = playerLookDir.subtract(toHead.multiply(dotFN));
        double fLen = F_proj.length();
        Vec3d Z_target;
        if (fLen > 1e-9) {
            Z_target = F_proj.normalize();
        } else {
            // Degenerate: player looking exactly along normal — no correction needed
            return new Quaternionf(); // identity spin
        }

        // Both Z_current and Z_target should be ⟂ toHead
        double cosPhi = Z_current.dotProduct(Z_target);
        cosPhi = Math.max(-1.0, Math.min(1.0, cosPhi));
        double phi = Math.acos(cosPhi);

        // Determine sign: cross(Z_current, Z_target) relative to toHead
        Vec3d crossZF = Z_current.crossProduct(Z_target);
        if (crossZF.dotProduct(toHead) < 0) {
            phi = -phi;
        }

        if (Math.abs(phi) < 1e-9) {
            return new Quaternionf(); // identity spin
        }

        return new Quaternionf().rotateAxis(
            (float) phi, (float) toHead.x, (float) toHead.y, (float) toHead.z);
    }

    /**
     * Rotate a vector by a quaternion.
     */
    private static Vec3d rotate(Vec3d v, Quaternionf q) {
        // q * v * q⁻¹
        Quaternionf qv = new Quaternionf((float) v.x, (float) v.y, (float) v.z, 0);
        Quaternionf qConj = new Quaternionf(q).conjugate();
        Quaternionf result = q.mul(qv, new Quaternionf()).mul(qConj);
        return new Vec3d(result.x, result.y, result.z);
    }

    // ------------------------------------------------------------------
    // Entity interpolation helpers
    // ------------------------------------------------------------------

    private static Vec3d getInterpolatedHeadAnchor(LivingEntity entity, float tickDelta) {
        double x = entity.prevX + (entity.getX() - entity.prevX) * tickDelta;
        double y = entity.prevY + (entity.getY() - entity.prevY) * tickDelta;
        double z = entity.prevZ + (entity.getZ() - entity.prevZ) * tickDelta;
        if (entity instanceof PlayerEntity player) {
            return new Vec3d(x, y + player.getStandingEyeHeight(), z);
        }
        return new Vec3d(x, y + entity.getHeight() * 0.85, z);
    }

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

    private static Vec3d getEffectiveOffset(HaloDefinition definition) {
        HaloConfig runtime = HaloManager.getInstance().getConfig();
        Vec3d rtOffset = runtime.getPositionOffset();
        if (Math.abs(rtOffset.x) < 1e-9
            && Math.abs(rtOffset.y - 0.2) < 1e-9
            && Math.abs(rtOffset.z) < 1e-9) {
            return definition.positioning().offset();
        }
        return rtOffset;
    }

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

    private static float getRuntimeScaleOverride(HaloDefinition definition) {
        HaloConfig runtime = HaloManager.getInstance().getConfig();
        if (Math.abs(runtime.getHaloScale() - 1.0) > 1e-9) {
            return (float) runtime.getHaloScale();
        }
        return (float) definition.positioning().scale();
    }
}
