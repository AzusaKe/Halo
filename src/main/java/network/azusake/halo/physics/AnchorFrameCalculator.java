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

    // ---- per-instance SYNC relative orientation (captured on first frame) ----
    private final Map<UUID, Quaternionf> syncRelativeStates = new HashMap<>();

    // ---- per-instance per-entity-type anchor providers ----
    private final PlayerAnchorProvider playerProvider = PlayerAnchorProvider.getInstance();
    private final FallbackAnchorProvider fallbackProvider = FallbackAnchorProvider.getInstance();

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

        // 1. Pose-aware head anchor via provider (player vs fallback)
        EntityAnchorProvider provider = (entity instanceof PlayerEntity)
            ? playerProvider
            : fallbackProvider;
        HeadAnchor ha = provider.resolve(entity, tickDelta);
        Vec3d headAnchor = ha.headCenter();
        float yaw = ha.yaw();
        float pitch = ha.pitch();
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

        // 6. Head frame vectors (world space): forward, right, headUp
        //    Reuse the same computation as computeHeadRelativeOffset for consistency.
        float pitchRad = (float) Math.toRadians(pitch);
        Vec3d forward = new Vec3d(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        ).normalize();
        Vec3d worldUp = new Vec3d(0, 1, 0);
        Vec3d right;
        if (Math.abs(forward.dotProduct(worldUp)) > 0.999) {
            // Match computeHeadRelativeOffset fallback — see comment there.
            right = new Vec3d(-Math.cos(yawRad), 0, -Math.sin(yawRad));
        } else {
            right = forward.crossProduct(worldUp).normalize();
        }
        Vec3d headUp = right.crossProduct(forward).normalize();

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
                // Sphere-model LOCKED spin (see computeLockedSpin javadoc):
                // The anchor point P = direction from head to halo target on the sphere.
                // The "up pole" is 90° from P along the headUp great circle.
                // The halo's +Z should point toward this pole like a compass.
                Vec3d P = headRelOffset.normalize(); // anchor-point radial direction
                Quaternionf Q_target = computeLockedSpin(Q_lookAt, toHead, headUp, P);

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
            case SYNC -> {
                // Head orientation from Euler angles (no roll component).
                // rotateY(−yaw) × rotateX(pitch) reproduces Minecraft's
                // forward = (−sin yaw·cos pitch, −sin pitch, cos yaw·cos pitch).
                // def +Z → look direction, def +Y → ≈ world‑up.
                Quaternionf Q_head = new Quaternionf()
                    .rotateY(-yawRad)
                    .rotateX(pitchRad);

                // Retrieve or capture the fixed relative rotation.
                // First frame: Q_halo(0) = Q_syncOffset × Q_LOCKED.
                //   Q_rel = Q_head(0)⁻¹ × Q_halo(0)
                // Subsequent frames:
                //   Q_halo(t) = Q_head(t) × Q_rel
                //            = Q_head(t) × Q_head(0)⁻¹ × Q_syncOffset × Q_LOCKED(0)
                // The head delta Q_head(t) × Q_head(0)⁻¹ is applied on the
                // OUTSIDE — the halo rotates with the head in world space.
                Quaternionf Q_rel = syncRelativeStates.get(uuid);
                if (Q_rel == null || needsSnap) {
                    Vec3d P = headRelOffset.normalize();
                    Quaternionf Q_lockedSpin = computeLockedSpin(Q_lookAt, toHead, headUp, P);
                    Quaternionf Q_LOCKED = Q_lockedSpin.mul(Q_lookAt, new Quaternionf());

                    Quaternionf Q_syncOffset = definition.model().syncOffset();
                    Quaternionf Q_halo_0 = new Quaternionf(Q_syncOffset).mul(Q_LOCKED);
                    Quaternionf Q_head_0_inv = new Quaternionf(Q_head).conjugate();
                    Q_rel = new Quaternionf(Q_head_0_inv).mul(Q_halo_0);

                    syncRelativeStates.put(uuid, new Quaternionf(Q_rel));
                }

                // Q_halo(t) = Q_head(t) × Q_rel
                worldOrientation = new Quaternionf(Q_head).mul(Q_rel);
                worldForward = new Vec3d(0, 0, 1);
            }
            default -> {
                worldOrientation = Q_lookAt;
                worldForward = new Vec3d(0, 0, 1);
            }
        }

        // 9b. Angular momentum damping (LOCKED/FREE only)
        // When enabled, the orientation from the mode switch becomes a target,
        // and the actual orientation is slerped toward it with rotational inertia.
        if (damping.allowAngularMomentum() && mode != OrientationMode.SYNC) {
            Quaternionf targetOrientation = worldOrientation;
            Quaternionf prevDamped = rotState.prevDampedOrientation;

            if (prevDamped == null || needsSnap) {
                // First frame or teleport: snap to target immediately
                rotState.prevDampedOrientation = new Quaternionf(targetOrientation);
            } else {
                // Frame-rate-independent slerp toward target
                double momK = Math.max(0.001, Math.min(damping.angularMomentumFactor(), 0.999));
                double momExp = frameDt / REFERENCE_TICK;
                if (momExp <= 0.0) momExp = 1.0;
                if (momExp > 10.0) momExp = 10.0;
                double momKF = 1.0 - Math.pow(1.0 - momK, momExp);
                momKF = Math.max(0.0, Math.min(1.0, momKF));

                Quaternionf damped = new Quaternionf(prevDamped);
                damped.slerp(targetOrientation, (float) momKF);

                // Clamp angular deviation from target
                Quaternionf deltaFromTarget = new Quaternionf(targetOrientation).conjugate().mul(damped);
                double wAbs = Math.min(1.0, Math.abs((double) deltaFromTarget.w));
                double halfAngleRad = Math.acos(wAbs);
                float angleDeg = (float) Math.toDegrees(2.0 * halfAngleRad);
                double maxAngle = damping.maxAngularMomentumDegrees();

                if (angleDeg > maxAngle && angleDeg > 0.0001f) {
                    float sinHalf = (float) Math.sin(halfAngleRad);
                    if (sinHalf > 0.0001f) {
                        float axisX = deltaFromTarget.x / sinHalf;
                        float axisY = deltaFromTarget.y / sinHalf;
                        float axisZ = deltaFromTarget.z / sinHalf;
                        float clampedHalf = (float) Math.toRadians(maxAngle / 2.0);
                        float newSinHalf = (float) Math.sin(clampedHalf);
                        deltaFromTarget.x = axisX * newSinHalf;
                        deltaFromTarget.y = axisY * newSinHalf;
                        deltaFromTarget.z = axisZ * newSinHalf;
                        deltaFromTarget.w = (float) Math.cos(clampedHalf);
                    }
                    // Reconstruct damped = target * clampedDelta
                    damped = new Quaternionf(targetOrientation).mul(deltaFromTarget);
                }

                rotState.prevDampedOrientation = new Quaternionf(damped);
            }
            worldOrientation = rotState.prevDampedOrientation;
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
        syncRelativeStates.keySet().retainAll(activeUuids);
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
     * Compute a spin quaternion around {@code toHead} that aligns the halo's
     * definition +Z with the <em>up pole</em> of the sphere model.
     *
     * <h3>Sphere model</h3>
     * <p>Imagine a sphere centred at the entity's head.  The halo's anchor
     * point {@code P} lies on this sphere at the offset direction from the
     * head.  At {@code P} we draw four great-circle lines on the sphere
     * (up / down / left / right).  The up line follows the {@code headUp}
     * direction; its midpoint between {@code P} and the antipode {@code -P}
     * is the <b>up pole</b> — a fixed reference point 90° from {@code P}
     * along the head-up great circle.</p>
     *
     * <p>In {@link OrientationMode#LOCKED}, the halo's +Z axis should point
     * toward this up pole like a compass needle.  This keeps the relative
     * orientation between halo and head consistent regardless of how the
     * player tilts their head, and <b>avoids the 180° flip</b> that occurs
     * when a horizontal-only reference direction becomes parallel to the
     * normal.</p>
     *
     * @param Q_lookAt shortest-arc rotation mapping definition -Y → toHead
     * @param toHead   unit vector from halo toward entity head (halo normal)
     * @param headUp   entity's head-up direction (world space)
     * @param P        unit vector from head to the halo's anchor point
     *                 (= {@code normalize(headRelOffset)})
     * @return spin quaternion around toHead
     */
    static Quaternionf computeLockedSpin(Quaternionf Q_lookAt, Vec3d toHead,
                                          Vec3d headUp, Vec3d P) {
        // ---- 1. Locate the "up pole" on the sphere ----
        // The up pole is headUp projected onto the tangent plane at P (⟂ P),
        // then normalized.  It is a fixed point 90° from P on the sphere.
        double dotUP = headUp.dotProduct(P);
        Vec3d t_up_raw = headUp.subtract(P.multiply(dotUP));
        double tLen = t_up_raw.length();
        Vec3d Pole_up; // unit vector — the up pole position on the sphere
        if (tLen > 1e-9) {
            Pole_up = t_up_raw.normalize();
        } else {
            // headUp is parallel to P — fall back to world-up projected onto ⟂ P
            Vec3d fallback = new Vec3d(0, 1, 0).subtract(P.multiply(P.y));
            double fbLen = fallback.length();
            if (fbLen > 1e-9) {
                Pole_up = fallback.normalize();
            } else {
                // P is also world-up → use an arbitrary horizontal reference
                Pole_up = Math.abs(P.x) < 0.9
                    ? new Vec3d(1, 0, 0).crossProduct(P).normalize()
                    : new Vec3d(0, 0, 1).crossProduct(P).normalize();
            }
        }

        // ---- 2. Direction from halo to the up pole ----
        // The halo sits at radial direction R = -toHead on the sphere.
        // The great-circle direction from R toward Pole_up is Pole_up projected
        // onto the halo's tangent plane (⟂ toHead).
        double dotPT = Pole_up.dotProduct(toHead);
        Vec3d Z_proj = Pole_up.subtract(toHead.multiply(dotPT));
        double zLen = Z_proj.length();
        Vec3d Z_target; // where definition +Z SHOULD point (world space)
        if (zLen > 1e-9) {
            Z_target = Z_proj.normalize();
        } else {
            // Pole is parallel to toHead → spin is irrelevant; no correction
            return new Quaternionf(); // identity spin
        }

        // ---- 3. Where definition +Z currently points after Q_lookAt ----
        Vec3d Z_current = rotate(new Vec3d(0, 0, 1), Q_lookAt);

        // ---- 4. Signed angle from Z_current to Z_target around toHead ----
        double cosPhi = Z_current.dotProduct(Z_target);
        cosPhi = Math.max(-1.0, Math.min(1.0, cosPhi));
        double phi = Math.acos(cosPhi);

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
            // When forward is nearly parallel to worldUp, the cross product
            // degenerates.  Use a fallback that is continuous with the
            // cross-product result:  forward × worldUp  normalised to
            // (–cos yaw, 0, –sin yaw) for cos(pitch) > 0 (the MC pitch
            // range).  Negating keeps headUp from flipping and avoids the
            // halo jumping from front to back.
            right = new Vec3d(-Math.cos(yawRad), 0, -Math.sin(yawRad));
        } else {
            right = forward.crossProduct(worldUp).normalize();
        }
        Vec3d headUp = right.crossProduct(forward).normalize();
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
            || Math.abs(runtime.getMaxAngularDegrees() - 45.0) > 1e-9
            || runtime.isAllowAngularMomentum()
            || Math.abs(runtime.getAngularMomentumFactor() - 0.3) > 1e-9
            || Math.abs(runtime.getMaxAngularMomentumDegrees() - 45.0) > 1e-9;

        if (overridden) {
            return new HaloDampingConfig(
                runtime.getLinearDampingFactor(),
                runtime.getAngularDampingFactor(),
                runtime.getMaxLinearDistance(),
                runtime.getMaxAngularDegrees(),
                runtime.isAllowAngularMomentum(),
                runtime.getAngularMomentumFactor(),
                runtime.getMaxAngularMomentumDegrees()
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
