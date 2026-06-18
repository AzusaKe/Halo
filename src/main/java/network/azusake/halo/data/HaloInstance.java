package network.azusake.halo.data;

import network.azusake.halo.physics.DampingPhysics;
import network.azusake.halo.physics.HaloDampingState;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

import java.util.UUID;

/**
 * Per-entity runtime state for a single halo.
 * Mutable — physics and animation systems write to this every tick.
 */
public class HaloInstance {

    private final UUID entityUuid;
    private final Identifier definitionId;

    // Physics output: current offset from the entity anchor
    private Vec3d relativePosition;
    private Quaternionf relativeRotation;

    // Previous tick values for tickDelta interpolation on the render thread
    private Vec3d prevRelativePosition;
    private Quaternionf prevRelativeRotation;

    // When true the next physics step snaps instantly (ignores damping)
    private boolean needsSnap;

    // Whether this halo instance is currently active (rendered and ticked)
    private boolean active = true;

    // Epoch-millis timestamp when this instance was created
    private final long createdAtTime;

    // Physics-level damping state (drives computeDampedPosition / computeDampedRotation)
    private final HaloDampingState dampingState = new HaloDampingState();

    public HaloInstance(UUID entityUuid, Identifier definitionId) {
        this.entityUuid = entityUuid;
        this.definitionId = definitionId;
        this.relativePosition = Vec3d.ZERO;
        this.relativeRotation = new Quaternionf();
        this.prevRelativePosition = Vec3d.ZERO;
        this.prevRelativeRotation = new Quaternionf();
        this.needsSnap = true;
        this.active = true;
        this.createdAtTime = System.currentTimeMillis();
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    public UUID getEntityUuid() {
        return entityUuid;
    }

    public Identifier getDefinitionId() {
        return definitionId;
    }

    public Vec3d getRelativePosition() {
        return relativePosition;
    }

    public Quaternionf getRelativeRotation() {
        return relativeRotation;
    }

    public Vec3d getPrevRelativePosition() {
        return prevRelativePosition;
    }

    public Quaternionf getPrevRelativeRotation() {
        return prevRelativeRotation;
    }

    public boolean isNeedsSnap() {
        return needsSnap;
    }

    // -----------------------------------------------------------------------
    // Setters
    // -----------------------------------------------------------------------

    public void setRelativePosition(Vec3d relativePosition) {
        this.relativePosition = relativePosition;
    }

    public void setRelativeRotation(Quaternionf relativeRotation) {
        this.relativeRotation = relativeRotation;
    }

    public void setPrevRelativePosition(Vec3d prevRelativePosition) {
        this.prevRelativePosition = prevRelativePosition;
    }

    public void setPrevRelativeRotation(Quaternionf prevRelativeRotation) {
        this.prevRelativeRotation = prevRelativeRotation;
    }

    public void setNeedsSnap(boolean needsSnap) {
        this.needsSnap = needsSnap;
    }

    /**
     * Advance the previous-frame state to match current state.
     * Called at the end of each physics tick.
     */
    public void advanceFrame() {
        this.prevRelativePosition = this.relativePosition;
        this.prevRelativeRotation = new Quaternionf(this.relativeRotation);
    }

    /**
     * Force the next physics update to snap instantly (skip damping).
     */
    public void markNeedsSnap() {
        this.needsSnap = true;
    }

    // -----------------------------------------------------------------------
    // Damping physics integration
    // -----------------------------------------------------------------------

    /**
     * Advance damping physics by one frame or tick.
     *
     * @param target     world-space position the halo should converge toward
     *                   (entity head anchor + head-relative offset)
     * @param haloCenter current world-space halo centre (previous tick's stored position)
     * @param damping    damping configuration for this halo
     * @param deltaTime  seconds since the last update (frame or tick)
     */
    public void tickDamping(Vec3d target, Vec3d haloCenter, HaloDampingConfig damping, double deltaTime) {
        // Position damping: offset from target shrinks by factor k_f each tick
        Vec3d offsetFromTarget = haloCenter.subtract(target);
        boolean wasSnap = dampingState.isNeedsSnap();
        Vec3d dampedOffset = DampingPhysics.computeDampedPosition(
            offsetFromTarget, Vec3d.ZERO, damping, dampingState, deltaTime
        );
        boolean didSnap = wasSnap && !dampingState.isNeedsSnap();

        // Store absolute world-space halo position
        this.prevRelativePosition = this.relativePosition;
        this.relativePosition = target.add(dampedOffset);

        // After a snap (first spawn or teleport), prev and current may be
        // thousands of blocks apart, causing interpolation to sweep across
        // the map.  Equalise them so the renderer shows a single stable position.
        if (didSnap || this.prevRelativePosition.lengthSquared() < 0.001) {
            this.prevRelativePosition = this.relativePosition;
        }

        if (didSnap && network.azusake.halo.lifecycle.EntityHaloTracker.isDebugMode()) {
            var srv = network.azusake.halo.lifecycle.EntityHaloTracker.getServer();
            if (srv != null) {
                double dist = this.prevRelativePosition.distanceTo(this.relativePosition);
                var msg = net.minecraft.text.Text.literal(
                    String.format("§e[HaloDebug] §fSNAP §acorrected | §7old=(%.1f, %.1f, %.1f) new=(%.1f, %.1f, %.1f) §8dist=§7%.2f",
                        this.prevRelativePosition.x, this.prevRelativePosition.y, this.prevRelativePosition.z,
                        this.relativePosition.x, this.relativePosition.y, this.relativePosition.z,
                        dist));
                srv.getPlayerManager().broadcast(msg, false);
            }
        }

        // Rotation damping
        Quaternionf newRot = DampingPhysics.computeDampedRotation(
            this.relativeRotation, new Quaternionf(), damping, dampingState, deltaTime
        );
        this.prevRelativeRotation = new Quaternionf(this.relativeRotation);
        this.relativeRotation = newRot;
    }

    /**
     * Notify the damping system that the attached entity has teleported.
     * The next tick will snap instantly rather than sliding.
     */
    public void markTeleported() {
        dampingState.markTeleport();
        this.needsSnap = true;
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /**
     * Whether this halo is active (should be rendered and ticked).
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Deactivate this halo so it stops rendering and ticking.
     * Once deactivated the instance cannot be reactivated — create a new one instead.
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * Epoch-millis timestamp when this instance was created.
     */
    public long getCreatedAtTime() {
        return createdAtTime;
    }

    // -----------------------------------------------------------------------
    // Render interpolation
    // -----------------------------------------------------------------------

    /**
     * Interpolated position for client-side rendering.
     *
     * @param tickDelta  partial-tick progress (0.0 – 1.0)
     * @return linearly interpolated world-space halo centre
     */
    public Vec3d getInterpolatedPosition(double tickDelta) {
        return prevRelativePosition.lerp(relativePosition, tickDelta);
    }

    /**
     * Interpolated rotation for client-side rendering.
     *
     * @param tickDelta  partial-tick progress (0.0 – 1.0)
     * @return spherically-linear-interpolated relative rotation quaternion
     */
    public Quaternionf getInterpolatedRotation(double tickDelta) {
        return new Quaternionf(prevRelativeRotation).slerp(relativeRotation, (float) tickDelta);
    }

    // -----------------------------------------------------------------------
    // Package-private accessors (for physics package)
    // -----------------------------------------------------------------------

    HaloDampingState getDampingState() {
        return dampingState;
    }
}
