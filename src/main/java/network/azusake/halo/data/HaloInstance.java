package network.azusake.halo.data;

import network.azusake.halo.animation.StartupAnimationConfig;
import network.azusake.halo.animation.TransitionAnimationResult;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

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
    private final ResourceLocation definitionId;

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

    /** Current transition state driving the animation lifecycle. */
    private HaloTransitionState transitionState = HaloTransitionState.NORMAL;

    /** Epoch-millis timestamp when the current transition started. */
    private long transitionStartTime = 0;

    /**
     * The idle-animation phase (seconds since creation) frozen for the
     * current transition.  Startup end points align to the idle value at this
     * phase (the resume moment); shutdown head points align to the idle value
     * at this phase (the hide moment).
     */
    private double transitionFreezeAnimTime = 0;

    /**
     * Per-group patched transition animations for the current transition.
     * Built lazily on the render thread with endpoint alignment applied, then
     * reused every frame.  Cleared whenever a new transition starts.
     */
    private final Map<String, TransitionAnimationResult> transitionAnimCache =
        new ConcurrentHashMap<>();

    /**
     * Per-group visual values the renderer was actually drawing when this
     * ENDING transition was triggered (populated only for mid-transition
     * hides).  Used to head-patch the shutdown queues so the fade-out starts
     * from the exact on-screen state.  Cleared whenever a new transition
     * starts.
     */
    private Map<String, GroupVisualSnapshot> hideVisuals = Map.of();

    /**
     * Whether the current ENDING/NULL state was caused by sleep/invisibility
     * hiding (recoverable on wake) rather than an explicit hide command
     * (permanent removal).
     */
    private boolean hiddenByState = false;

    public HaloInstance(UUID entityUuid, ResourceLocation definitionId) {
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

    public ResourceLocation getDefinitionId() {
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

    /**
     * Reactivate a deactivated (NULL) instance so it can render again.
     * Used when a halo hidden by sleep/invisibility becomes visible again.
     */
    public void reactivate() {
        this.active = true;
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

    public HaloTransitionState getTransitionState() {
        return transitionState;
    }

    public void setTransitionState(HaloTransitionState transitionState) {
        this.transitionState = transitionState;
    }

    public boolean isHiddenByState() {
        return hiddenByState;
    }

    public void setHiddenByState(boolean hiddenByState) {
        this.hiddenByState = hiddenByState;
    }

    public long getTransitionStartTime() {
        return transitionStartTime;
    }

    /**
     * The frozen idle-animation phase for the current transition, in seconds.
     */
    public double getTransitionFreezeAnimTime() {
        return transitionFreezeAnimTime;
    }

    /**
     * The idle-animation phase the renderer would use right now — i.e. the
     * current {@code animTime}.  After a completed startup transition the
     * idle animation lags wall-clock time by the startup duration (it was
     * frozen while the startup played), so transitions triggered later must
     * align to {@code rawAnimTime - startupDur}, not the raw elapsed time.
     *
     * @param startupConfig the definition's startup config (may be null)
     * @return the idle phase in seconds for the current moment
     */
    public double currentAnimTime(StartupAnimationConfig startupConfig) {
        return currentAnimTime(System.currentTimeMillis(), startupConfig);
    }

    /**
     * Testable variant of {@link #currentAnimTime(StartupAnimationConfig)}
     * with an explicit wall-clock moment.
     *
     * @param nowMillis     the wall-clock moment in epoch millis
     * @param startupConfig the definition's startup config (may be null)
     * @return the idle phase in seconds at {@code nowMillis}
     */
    double currentAnimTime(long nowMillis, StartupAnimationConfig startupConfig) {
        double raw = (nowMillis - createdAtTime) / 1000.0;
        if (transitionStartTime > 0) {
            if (transitionState == HaloTransitionState.STARTING
                    || transitionState == HaloTransitionState.ENDING) {
                // The idle animation is frozen at the captured phase.
                return transitionFreezeAnimTime;
            }
            double startupDur = startupConfig != null ? startupConfig.maxDuration() : 0.0;
            double elapsed = (nowMillis - transitionStartTime) / 1000.0;
            if (elapsed >= startupDur) {
                // NORMAL after a completed startup — the idle resumed at
                // freeze and has been playing since, so it lags wall-clock
                // time by exactly the startup duration.
                return Math.max(0.0, raw - startupDur);
            }
        }
        return raw;
    }

    /**
     * Start a transition animation.  The direction (startup/shutdown) is
     * determined by the current {@link #transitionState}.
     *
     * @param freezeAnimTime the idle-animation phase (seconds since creation)
     *                       to freeze while the transition plays; used both as
     *                       the frozen {@code animTime} during the transition
     *                       and as the endpoint-alignment phase for per-group
     *                       patched animations
     */
    public void startTransition(double freezeAnimTime) {
        this.transitionStartTime = System.currentTimeMillis();
        this.transitionFreezeAnimTime = freezeAnimTime;
        this.transitionAnimCache.clear();
        this.hideVisuals = Map.of();
    }

    /**
     * Stash the per-group visuals to use as the shutdown head (mid-transition
     * hides).  Call after {@link #startTransition(double)} so the transition
     * does not clear them again.
     *
     * @param hideVisuals per-group offset/scale/alpha snapshots, or an empty map
     */
    public void setHideVisuals(Map<String, GroupVisualSnapshot> hideVisuals) {
        this.hideVisuals = hideVisuals != null ? hideVisuals : Map.of();
    }

    /**
     * The per-group visuals stashed for the current ENDING transition, or an
     * empty map when this hide did not happen mid-transition.
     */
    public Map<String, GroupVisualSnapshot> getHideVisuals() {
        return hideVisuals;
    }

    /**
     * Get the per-instance patched transition animation for a group, or
     * {@code null} when not yet built this transition.
     *
     * @param groupKey the group id (empty string for unnamed groups)
     */
    public TransitionAnimationResult getTransitionAnimation(String groupKey) {
        return transitionAnimCache.get(groupKey);
    }

    /**
     * Cache a per-instance patched transition animation for a group.
     *
     * @param groupKey the group id (empty string for unnamed groups)
     * @param anim     the endpoint-aligned animation
     */
    public void putTransitionAnimation(String groupKey, TransitionAnimationResult anim) {
        transitionAnimCache.put(groupKey, anim);
    }

    /**
     * Return the elapsed time in seconds since the current transition started.
     */
    public double getTransitionElapsed() {
        return (System.currentTimeMillis() - transitionStartTime) / 1000.0;
    }

    /**
     * Check whether this instance is currently within an active transition.
     * Uses {@link #transitionState} to determine which duration to compare against.
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
        if (transitionState == HaloTransitionState.STARTING && startupConfig != null) {
            totalDuration = startupConfig.maxDuration();
        } else if (transitionState == HaloTransitionState.ENDING) {
            if (shutdownConfig != null) {
                totalDuration = shutdownConfig.maxDuration();
            } else if (startupConfig != null) {
                totalDuration = startupConfig.maxDuration();
            }
        }
        return elapsed < totalDuration;
    }
}
