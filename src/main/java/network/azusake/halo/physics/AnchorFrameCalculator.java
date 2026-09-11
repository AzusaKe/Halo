package network.azusake.halo.physics;

import network.azusake.halo.config.HaloConfig;
import network.azusake.halo.anchor.AnchorCaptureCoordinator;
import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorRotation;
import network.azusake.halo.api.v2.AnchorVec3;
import network.azusake.halo.data.HaloDampingConfig;
import network.azusake.halo.data.HaloDefinition;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.data.OrientationMode;
import network.azusake.halo.manager.HaloManager;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(AnchorFrameCalculator.class);

    /** Reference tick duration in seconds (50 ms = 20 TPS). */
    private static final double REFERENCE_TICK = 0.05;

    /**
     * Angular damping factor for LOCKED-mode spin.
     * High enough to follow the player's look responsively,
     * but with enough smoothing to eliminate head-yaw noise.
     */
    private static final double LOCKED_ANGULAR_K = 0.5;

    // ---- per-frame position state ----
    private final Map<UUID, Vec3> prevFramePos = new HashMap<>();

    // ---- last good provider anchor per entity.  A transient non-finite
    // anchor (bad provider frame) holds this anchor instead of poisoning the
    // damping state — NaN is sticky and would hide the halo until its
    // per-frame state is dropped (sleep/invis hide or rejoin). ----
    private final Map<UUID, AnchorPose> lastGoodAnchors = new HashMap<>();

    // ---- per-instance rotation / spin damping state ----
    private final Map<UUID, HaloDampingState> rotationStates = new HashMap<>();

    // ---- per-instance locked-spin quaternion (damped) ----
    private final Map<UUID, Quaternionf> lockedSpinStates = new HashMap<>();

    // ---- per-instance SYNC relative orientation (captured on first frame) ----
    private final Map<UUID, Quaternionf> syncRelativeStates = new HashMap<>();

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
        return calculate(instance, entity, definition, camera.position(), tickDelta, frameDt);
    }

    public AnchorFrame calculate(
        HaloInstance instance,
        LivingEntity entity,
        HaloDefinition definition,
        Vec3 cameraPosition,
        float tickDelta,
        double frameDt
    ) {
        UUID uuid = instance.getEntityUuid();

        // 1. Prefer the local first-person camera. Otherwise consume the most
        // recent accepted render submission, then use Halo's internal fallback.
        AnchorVec3 entityPosition = interpolatedPosition(entity, tickDelta);
        AnchorPose pose = isLocalFirstPerson(entity)
            ? PlayerAnchorProvider.getInstance().resolve(entity, tickDelta)
            : AnchorCaptureCoordinator.resolve(
                entity.getUUID(), entity.getId(), entity.level(), entityPosition);
        if (pose == null) {
            pose = entity instanceof Player
                ? PlayerAnchorProvider.getInstance().resolve(entity, tickDelta)
                : DefaultAnchorResolver.resolve(entity, tickDelta);
        }
        // Hold the last good pose if an integration ever returns transient bad data.
        if (!isFinite(pose)) {
            AnchorPose lastGood = lastGoodAnchors.get(uuid);
            pose = lastGood != null ? lastGood : DefaultAnchorResolver.resolve(entity, tickDelta);
        } else {
            lastGoodAnchors.put(uuid, pose);
        }
        Vec3 headAnchor = toVec3(pose.position());
        Quaternionf headRotation = toQuaternion(pose.rotation());

        // 2. Head-relative offset → world-space target position
        Vec3 offset = getEffectiveOffset(definition);
        Vec3 headRelOffset = computeHeadRelativeOffset(headRotation, offset);
        Vec3 targetPos = headAnchor.add(headRelOffset);

        // 3. Merge damping config
        HaloDampingConfig damping = mergeDampingConfig(definition);

        // 4. Frame-rate-independent position damping
        Vec3 prevPos = prevFramePos.get(uuid);
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

        // dampPosition never returns NaN/∞, so a single bad frame cannot
        // poison prevFramePos.
        Vec3 haloWorldPos = dampPosition(prevPos, targetPos, kF, damping.maxLinearDistance());

        prevFramePos.put(uuid, haloWorldPos);

        // 5. toHead direction: from halo centre toward entity head
        Vec3 toHead = headAnchor.subtract(haloWorldPos).normalize();

        // 6. Head frame vectors come directly from the API v2 quaternion.
        Vec3 forward = rotate(new Vec3(0, 0, 1), headRotation);
        Vec3 headUp = rotate(new Vec3(0, 1, 0), headRotation);

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
        Vec3 worldForward;

        switch (mode) {
            case LOCKED -> {
                // Sphere-model LOCKED spin (see computeLockedSpin javadoc):
                // The anchor point P = direction from head to halo target on the sphere.
                // The "up pole" is 90° from P along the headUp great circle.
                // The halo's +Z should point toward this pole like a compass.
                Vec3 P = headRelOffset.normalize(); // anchor-point radial direction
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
                worldForward = new Vec3(0, 0, 1); // will be rotated below
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
                worldForward = new Vec3(0, 0, 1);
            }
            case SYNC -> {
                // Head orientation from Euler angles.  rotateY(−yaw) ×
                // rotateX(pitch) × rotateZ(roll) reproduces Minecraft's
                // forward = (−sin yaw·cos pitch, −sin pitch, cos yaw·cos pitch)
                // and matches the roll-aware HeadFrameMath basis.
                Quaternionf Q_head = new Quaternionf(headRotation);

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
                    Vec3 P = headRelOffset.normalize();
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
                worldForward = new Vec3(0, 0, 1);
            }
            default -> {
                worldOrientation = Q_lookAt;
                worldForward = new Vec3(0, 0, 1);
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
        Vec3 camPos = cameraPosition;
        Vec3 camRelPos = new Vec3(
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
        lastGoodAnchors.keySet().retainAll(activeUuids);
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
    static Quaternionf computeLookAtOrientation(Vec3 toHead) {
        // Degenerate direction (NaN from a bad frame, or exactly zero when the
        // halo sits on the head centre) has no meaningful look-at — returning
        // identity avoids the zero-axis NaN quaternion that would otherwise
        // poison the per-instance rotation state.
        if (!Double.isFinite(toHead.x) || !Double.isFinite(toHead.y) || !Double.isFinite(toHead.z)
                || (toHead.x == 0 && toHead.y == 0 && toHead.z == 0)) {
            return new Quaternionf();
        }
        Vec3 from = new Vec3(0, -1, 0); // definition -Y (billboard normal)
        double dot = from.dot(toHead);
        if (dot > 0.9999) {
            return new Quaternionf(); // identity — already aligned
        }
        if (dot < -0.9999) {
            // Opposite directions — 180° around any ⟂ axis; use +X
            return new Quaternionf().rotateAxis((float) Math.PI, 1, 0, 0);
        }
        Vec3 axis = from.cross(toHead).normalize();
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
    static Quaternionf computeLockedSpin(Quaternionf Q_lookAt, Vec3 toHead,
                                          Vec3 headUp, Vec3 P) {
        // ---- 1. Locate the "up pole" on the sphere ----
        // The up pole is headUp projected onto the tangent plane at P (⟂ P),
        // then normalized.  It is a fixed point 90° from P on the sphere.
        double dotUP = headUp.dot(P);
        Vec3 t_up_raw = headUp.subtract(P.scale(dotUP));
        double tLen = t_up_raw.length();
        Vec3 Pole_up; // unit vector — the up pole position on the sphere
        if (tLen > 1e-9) {
            Pole_up = t_up_raw.normalize();
        } else {
            // headUp is parallel to P — fall back to world-up projected onto ⟂ P
            Vec3 fallback = new Vec3(0, 1, 0).subtract(P.scale(P.y));
            double fbLen = fallback.length();
            if (fbLen > 1e-9) {
                Pole_up = fallback.normalize();
            } else {
                // P is also world-up → use an arbitrary horizontal reference
                Pole_up = Math.abs(P.x) < 0.9
                    ? new Vec3(1, 0, 0).cross(P).normalize()
                    : new Vec3(0, 0, 1).cross(P).normalize();
            }
        }

        // ---- 2. Direction from halo to the up pole ----
        // The halo sits at radial direction R = -toHead on the sphere.
        // The great-circle direction from R toward Pole_up is Pole_up projected
        // onto the halo's tangent plane (⟂ toHead).
        double dotPT = Pole_up.dot(toHead);
        Vec3 Z_proj = Pole_up.subtract(toHead.scale(dotPT));
        double zLen = Z_proj.length();
        Vec3 Z_target; // where definition +Z SHOULD point (world space)
        if (zLen > 1e-9) {
            Z_target = Z_proj.normalize();
        } else {
            // Pole is parallel to toHead → spin is irrelevant; no correction
            return new Quaternionf(); // identity spin
        }

        // ---- 3. Where definition +Z currently points after Q_lookAt ----
        Vec3 Z_current = rotate(new Vec3(0, 0, 1), Q_lookAt);

        // ---- 4. Signed angle from Z_current to Z_target around toHead ----
        double cosPhi = Z_current.dot(Z_target);
        cosPhi = Math.max(-1.0, Math.min(1.0, cosPhi));
        double phi = Math.acos(cosPhi);

        Vec3 crossZF = Z_current.cross(Z_target);
        if (crossZF.dot(toHead) < 0) {
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
    private static Vec3 rotate(Vec3 v, Quaternionf q) {
        // q * v * q⁻¹
        Quaternionf qv = new Quaternionf((float) v.x, (float) v.y, (float) v.z, 0);
        Quaternionf qConj = new Quaternionf(q).conjugate();
        Quaternionf result = q.mul(qv, new Quaternionf()).mul(qConj);
        return new Vec3(result.x, result.y, result.z);
    }

    // ------------------------------------------------------------------
    // Head-relative offset
    // ------------------------------------------------------------------

    private static Vec3 getEffectiveOffset(HaloDefinition definition) {
        HaloConfig runtime = HaloManager.getInstance().getConfig();
        Vec3 rtOffset = runtime.getPositionOffset();
        if (Math.abs(rtOffset.x) < 1e-9
            && Math.abs(rtOffset.y - 0.2) < 1e-9
            && Math.abs(rtOffset.z) < 1e-9) {
            return definition.positioning().offset();
        }
        return rtOffset;
    }

    static Vec3 computeHeadRelativeOffset(float yawDeg, float pitchDeg, float rollDeg, Vec3 offset) {
        return computeHeadRelativeOffset(buildHeadQuaternion(yawDeg, pitchDeg, rollDeg), offset);
    }

    static Vec3 computeHeadRelativeOffset(Quaternionf rotation, Vec3 offset) {
        Vec3 right = rotate(new Vec3(-1, 0, 0), rotation);
        Vec3 up = rotate(new Vec3(0, 1, 0), rotation);
        Vec3 behind = rotate(new Vec3(0, 0, -1), rotation);
        return right.scale(offset.x).add(up.scale(offset.y)).add(behind.scale(offset.z));
    }

    /**
     * Frame-rate-independent exponential damping toward {@code targetPos},
     * clamped to {@code maxDist} blocks.
     *
     * <p>The result is <em>always finite</em>: a NaN/∞ target or damped value
     * (a transient bad provider frame) is neutralised by holding the last
     * good position (or the target, or the origin), so a single bad frame can
     * never permanently poison the per-frame damping state.</p>
     *
     * @param prevPos   previous damped position (may be {@code null})
     * @param targetPos the target position this frame
     * @param kF        the frame-rate-independent blend factor in [0, 1]
     * @param maxDist   maximum linear distance from the target
     * @return the next damped position, always finite
     */
    static Vec3 dampPosition(Vec3 prevPos, Vec3 targetPos, double kF, double maxDist) {
        if (prevPos == null) {
            prevPos = targetPos;
        }
        Vec3 damped = prevPos.add(targetPos.subtract(prevPos).scale(kF));

        boolean dampedFinite = Double.isFinite(damped.x) && Double.isFinite(damped.y) && Double.isFinite(damped.z);
        boolean targetFinite = Double.isFinite(targetPos.x) && Double.isFinite(targetPos.y) && Double.isFinite(targetPos.z);
        if (!dampedFinite || !targetFinite) {
            // Never write NaN/∞ into the damping state: snap to the finite
            // reference we have (target → previous position → origin).
            if (targetFinite) {
                return targetPos;
            }
            if (prevPos != null && Double.isFinite(prevPos.x) && Double.isFinite(prevPos.y) && Double.isFinite(prevPos.z)) {
                return prevPos;
            }
            return new Vec3(0, 0, 0);
        }

        double dist = damped.distanceTo(targetPos);
        if (dist > maxDist && dist > 1e-9) {
            Vec3 toTarget = targetPos.subtract(damped).normalize();
            return targetPos.subtract(toTarget.scale(maxDist));
        }
        return damped;
    }

    /**
     * Whether a provider anchor is usable this frame (all components finite).
     * NaN/∞ anchors are transient garbage and must not enter the damping
     * state (see {@link #calculate}).
     */
    static boolean isFinite(AnchorPose pose) {
        if (pose == null) {
            return false;
        }
        AnchorVec3 center = pose.position();
        AnchorRotation rotation = pose.rotation();
        return Double.isFinite(center.x()) && Double.isFinite(center.y()) && Double.isFinite(center.z())
            && Double.isFinite(rotation.x()) && Double.isFinite(rotation.y())
            && Double.isFinite(rotation.z()) && Double.isFinite(rotation.w());
    }

    private static boolean isLocalFirstPerson(LivingEntity entity) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        return client != null && entity == client.player
            && client.options.getCameraType().isFirstPerson();
    }

    private static AnchorVec3 interpolatedPosition(LivingEntity entity, float tickDelta) {
        return new AnchorVec3(
            entity.xOld + (entity.getX() - entity.xOld) * tickDelta,
            entity.yOld + (entity.getY() - entity.yOld) * tickDelta,
            entity.zOld + (entity.getZ() - entity.zOld) * tickDelta
        );
    }

    private static Vec3 toVec3(AnchorVec3 value) {
        return new Vec3(value.x(), value.y(), value.z());
    }

    private static Quaternionf toQuaternion(AnchorRotation value) {
        return new Quaternionf((float) value.x(), (float) value.y(),
            (float) value.z(), (float) value.w());
    }

    /**
     * Head orientation quaternion from yaw/pitch/roll (degrees).
     *
     * <p>{@code rotateY(−yaw) · rotateX(pitch) · rotateZ(roll)} reproduces
     * Minecraft's head/camera Euler convention
     * ({@code Quaternionf.rotationYXZ(−yaw, pitch, roll)}): the local +Z axis
     * maps to the look direction and +Y to the (possibly rolled) head-up
     * vector, matching {@link HeadFrameMath}.</p>
     */
    static Quaternionf buildHeadQuaternion(float yawDeg, float pitchDeg, float rollDeg) {
        float yawRad = (float) Math.toRadians(yawDeg);
        float pitchRad = (float) Math.toRadians(pitchDeg);
        float rollRad = (float) Math.toRadians(rollDeg);
        return new Quaternionf()
            .rotateY(-yawRad)
            .rotateX(pitchRad)
            .rotateZ(rollRad);
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
