package com.example.halo.data;

import com.example.halo.physics.DampingPhysics;
import com.example.halo.physics.HaloDampingState;
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
     * Advance damping physics by one tick.
     *
     * @param anchorPos  world-space position of the entity head anchor
     * @param haloCenter current world-space halo centre
     * @param damping    damping configuration for this halo definition
     */
    public void tickDamping(Vec3d anchorPos, Vec3d haloCenter, HaloDampingConfig damping) {
        // Position damping
        Vec3d currentRel = haloCenter.subtract(anchorPos);
        Vec3d newRel = DampingPhysics.computeDampedPosition(
            currentRel, Vec3d.ZERO, damping, dampingState
        );

        // Convert back to world-space and store
        this.prevRelativePosition = this.relativePosition;
        this.relativePosition = anchorPos.add(newRel);

        // Rotation damping
        Quaternionf newRot = DampingPhysics.computeDampedRotation(
            this.relativeRotation, new Quaternionf(), damping, dampingState
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

    /**
     * Interpolated position for client-side rendering.
     *
     * @param tickDelta  partial-tick progress (0.0 – 1.0)
     * @return linearly interpolated world-space halo centre
     */
    public Vec3d getInterpolatedPosition(double tickDelta) {
        return prevRelativePosition.lerp(relativePosition, tickDelta);
    }

    // -----------------------------------------------------------------------
    // Package-private accessors (for physics package)
    // -----------------------------------------------------------------------

    HaloDampingState getDampingState() {
        return dampingState;
    }
}
