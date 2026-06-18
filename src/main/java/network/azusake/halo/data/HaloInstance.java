package network.azusake.halo.data;

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

    /** Epoch-millis timestamp when this instance was created. */
    private final long createdAtTime;

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

    /**
     * Epoch-millis timestamp when this instance was created.
     */
    public long getCreatedAtTime() {
        return createdAtTime;
    }
}
