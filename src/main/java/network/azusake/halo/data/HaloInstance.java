package network.azusake.halo.data;

import network.azusake.halo.animation.StartupAnimationConfig;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Per-entity runtime marker for a single halo.
 *
 * <p>After the pose/rendering decoupling this class is a lightweight
 * lifecycle marker — it stores the entity binding, definition reference,
 * snap flag, and creation timestamp.  All physics and pose computation
 * has moved to {@link network.azusake.halo.physics.AnchorFrameCalculator}
 * on the render thread.</p>
 *
 * <p>Mutable only for {@code needsSnap} (set by teleport hooks on the
 * server thread and consumed by the pose calculator on the render thread)
 * and {@code active} (set once on deactivation).</p>
 */
public class HaloInstance {

    private final UUID entityUuid;
    private final Identifier definitionId;

    /** When true the next pose calculation snaps instantly (ignores damping). */
    private boolean needsSnap;

    /** Whether this halo instance is currently active (rendered and tracked). */
    private boolean active = true;

    // ---- Per-tick entity state cache (written by tick handler, read by renderer) ----

    /** Cached entity invisible flag — updated once per client tick. */
    private volatile boolean entityInvisible;
    /** Cached entity sleeping flag — updated once per client tick. */
    private volatile boolean entitySleeping;

    /** Epoch-millis timestamp when this instance was created. */
    private final long createdAtTime;

    // ---- Transition animation state ----

    /** Whether this instance was visible on the previous frame. */
    private boolean wasVisible = false;
    /** Epoch-millis timestamp when the current transition started. */
    private long transitionStartTime = 0;
    /** Whether the current transition is a startup (true) or shutdown (false). */
    private boolean transitionIsStartup = true;
    /** Whether this instance is pending removal after its shutdown animation completes. */
    private boolean pendingRemoval = false;

    public HaloInstance(UUID entityUuid, Identifier definitionId) {
        this.entityUuid = entityUuid;
        this.definitionId = definitionId;
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

    public boolean isNeedsSnap() {
        return needsSnap;
    }

    // -----------------------------------------------------------------------
    // Snap flag
    // -----------------------------------------------------------------------

    public void setNeedsSnap(boolean needsSnap) {
        this.needsSnap = needsSnap;
    }

    /**
     * Force the next pose calculation to snap instantly (skip damping).
     */
    public void markNeedsSnap() {
        this.needsSnap = true;
    }

    /**
     * Notify that the attached entity has teleported.
     * The next pose calculation will snap the halo to the new position
     * instantly rather than sliding.
     */
    public void markTeleported() {
        this.needsSnap = true;
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /**
     * Whether this halo is active (should be rendered and tracked).
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Deactivate this halo so it stops rendering and tracking.
     * Once deactivated the instance cannot be reactivated — create a new one instead.
     */
    public void deactivate() {
        this.active = false;
    }

    // -----------------------------------------------------------------------
    // Per-tick entity state cache
    // -----------------------------------------------------------------------

    public boolean isEntityInvisible() {
        return entityInvisible;
    }

    public void setEntityInvisible(boolean entityInvisible) {
        this.entityInvisible = entityInvisible;
    }

    public boolean isEntitySleeping() {
        return entitySleeping;
    }

    public void setEntitySleeping(boolean entitySleeping) {
        this.entitySleeping = entitySleeping;
    }

    /**
     * Epoch-millis timestamp when this instance was created.
     */
    public long getCreatedAtTime() {
        return createdAtTime;
    }

    // -----------------------------------------------------------------------
    // Transition animation state
    // -----------------------------------------------------------------------

    public boolean isWasVisible() {
        return wasVisible;
    }

    public void setWasVisible(boolean wasVisible) {
        this.wasVisible = wasVisible;
    }

    public long getTransitionStartTime() {
        return transitionStartTime;
    }

    public boolean isTransitionIsStartup() {
        return transitionIsStartup;
    }

    /**
     * Start a transition animation in the given direction.
     *
     * @param startup {@code true} for startup, {@code false} for shutdown
     */
    public void startTransition(boolean startup) {
        this.transitionStartTime = System.currentTimeMillis();
        this.transitionIsStartup = startup;
    }

    /**
     * Return the elapsed time in seconds since the current transition started.
     */
    public double getTransitionElapsed() {
        return (System.currentTimeMillis() - transitionStartTime) / 1000.0;
    }

    /**
     * Check whether this instance is currently within an active transition.
     *
     * @param startupConfig  the startup animation config (may be null)
     * @param shutdownConfig the shutdown animation config (may be null)
     * @return {@code true} if a transition is in progress
     */
    public boolean isTransitioning(StartupAnimationConfig startupConfig, StartupAnimationConfig shutdownConfig) {
        if (transitionStartTime == 0) {
            return false;
        }
        double elapsed = getTransitionElapsed();
        double totalDuration = 0;
        if (transitionIsStartup && startupConfig != null) {
            totalDuration = startupConfig.maxDuration();
        } else if (!transitionIsStartup) {
            if (shutdownConfig != null) {
                totalDuration = shutdownConfig.maxDuration();
            } else if (startupConfig != null) {
                totalDuration = startupConfig.maxDuration();
            }
        }
        return elapsed < totalDuration;
    }

    public boolean isPendingRemoval() {
        return pendingRemoval;
    }

    public void setPendingRemoval(boolean pendingRemoval) {
        this.pendingRemoval = pendingRemoval;
    }
}
