package network.azusake.halo.physics;

import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/**
 * Internal state tracking for halo damping physics.
 *
 * <p>Stores the previous frame's damped position and rotation so the
 * {@link DampingPhysics} engine can compute smooth interpolation across
 * ticks.  Also carries a {@link #needsSnap} flag that forces an instant
 * snap on the next update (used on first spawn and after teleports).</p>
 */
public class HaloDampingState {

    /** The damped relative position computed on the previous tick. */
    Vec3d prevRelativePosition;

    /** The damped relative rotation computed on the previous tick. */
    Quaternionf prevRelativeRotation;

    /**
     * When {@code true} the next call to
     * {@link DampingPhysics#computeDampedPosition} or
     * {@link DampingPhysics#computeDampedRotation} will snap instantly
     * instead of damping.
     */
    boolean needsSnap;

    /** Timestamp (nanoseconds) of the most recent tick, for diagnostics. */
    long lastTickTime;

    /**
     * Creates a fresh damping state ready for its first tick.
     * {@link #needsSnap} is initialised to {@code true} so the halo
     * appears at the anchor immediately rather than sliding in from the
     * world origin.
     */
    public HaloDampingState() {
        this.needsSnap = true;
        this.prevRelativePosition = Vec3d.ZERO;
        this.prevRelativeRotation = new Quaternionf();
        this.lastTickTime = System.nanoTime();
    }

    /**
     * Records the result of a damping tick so it can be used as the
     * "previous" value during the next tick's interpolation.
     *
     * @param position  the damped relative position just computed
     * @param rotation  the damped relative rotation just computed
     */
    public void recordTick(Vec3d position, Quaternionf rotation) {
        this.prevRelativePosition = position;
        this.prevRelativeRotation = new Quaternionf(rotation);
        this.lastTickTime = System.nanoTime();
    }

    /**
     * Flags that the halo has teleported (or just spawned).
     * The next physics update will snap instantly rather than damping.
     */
    public void markTeleport() {
        this.needsSnap = true;
    }

    /**
     * Whether the next damping tick will snap instantly.
     */
    public boolean isNeedsSnap() {
        return needsSnap;
    }
}
