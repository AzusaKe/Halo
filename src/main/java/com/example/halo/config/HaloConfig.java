package com.example.halo.config;

import com.example.halo.data.HaloDampingConfig;
import com.example.halo.data.HaloPositioning;
import net.minecraft.util.math.Vec3d;

/**
 * Runtime halo configuration that can be tuned in-game via {@code /halo config}.
 *
 * <p>All setters enforce bounds clamping.  Values changed here take effect
 * immediately for all active and newly-spawned halo instances.</p>
 *
 * <p>Instances are owned by {@link com.example.halo.manager.HaloManager} —
 * do not create your own.</p>
 */
public class HaloConfig {

    // ------------------------------------------------------------------
    // Damping parameters
    // ------------------------------------------------------------------

    /** k: 0 = instant snap, 1 = never move.  Clamped to [0, 1]. */
    private double linearDampingFactor = 0.3;

    /** Angular interpolation weight.  Clamped to [0, 1]. */
    private double angularDampingFactor = 0.3;

    /** Maximum linear offset in blocks before hard-clamping.  Min 0.01. */
    private double maxLinearDistance = 1.0;

    /** Maximum angular offset in degrees before hard-clamping.  Min 1.0. */
    private double maxAngularDegrees = 45.0;

    // ------------------------------------------------------------------
    // Positioning parameters
    // ------------------------------------------------------------------

    /** Uniform scale multiplier.  Clamped to [0.1, 5.0]. */
    private double haloScale = 1.0;

    /** Position offset relative to the entity head anchor. */
    private Vec3d positionOffset = new Vec3d(0, 0.2, 0);

    /** Euler-angle rotation offset in degrees. */
    private Vec3d rotationOffset = new Vec3d(0, 0, 0);

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    public double getLinearDampingFactor() {
        return linearDampingFactor;
    }

    public double getAngularDampingFactor() {
        return angularDampingFactor;
    }

    public double getMaxLinearDistance() {
        return maxLinearDistance;
    }

    public double getMaxAngularDegrees() {
        return maxAngularDegrees;
    }

    public double getHaloScale() {
        return haloScale;
    }

    public Vec3d getPositionOffset() {
        return positionOffset;
    }

    public Vec3d getRotationOffset() {
        return rotationOffset;
    }

    // ------------------------------------------------------------------
    // Setters with bounds checking
    // ------------------------------------------------------------------

    public void setLinearDampingFactor(double value) {
        this.linearDampingFactor = Math.max(0.0, Math.min(1.0, value));
    }

    public void setAngularDampingFactor(double value) {
        this.angularDampingFactor = Math.max(0.0, Math.min(1.0, value));
    }

    public void setMaxLinearDistance(double value) {
        this.maxLinearDistance = Math.max(0.01, value);
    }

    public void setMaxAngularDegrees(double value) {
        this.maxAngularDegrees = Math.max(1.0, value);
    }

    public void setHaloScale(double value) {
        this.haloScale = Math.max(0.1, Math.min(5.0, value));
    }

    public void setPositionOffset(Vec3d offset) {
        this.positionOffset = offset;
    }

    public void setRotationOffset(Vec3d offset) {
        this.rotationOffset = offset;
    }

    // ------------------------------------------------------------------
    // Conversion helpers
    // ------------------------------------------------------------------

    /**
     * Produce a {@link HaloDampingConfig} snapshot from current runtime values.
     */
    public HaloDampingConfig toDampingConfig() {
        return new HaloDampingConfig(
            linearDampingFactor,
            angularDampingFactor,
            maxLinearDistance,
            maxAngularDegrees
        );
    }

    /**
     * Produce a {@link HaloPositioning} snapshot from current runtime values.
     */
    public HaloPositioning toPositioning() {
        return new HaloPositioning(positionOffset, haloScale);
    }
}
