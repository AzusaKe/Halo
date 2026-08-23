package network.azusake.halo.physics;

import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-frame capture of the player head's rendered transform.
 *
 * <p>Hooks installed by the client mixins register the base player body model
 * per entity at submit time ({@link #registerPlayer}), then — because 26.1
 * defers model drawing until after all entities have submitted — set the
 * draw-time capture context from the entity render state right before each
 * model is drawn ({@link #beginDraw}, invoked from {@code PlayerModel.setupAnim}).
 * The {@link ModelPart#render} hook snapshots the head part's final pose when
 * the base player body's head is actually rendered.  Captures are keyed by
 * entity UUID and cleared once per frame ({@link #clearFrame}), so a missing
 * entry means "not rendered this frame" — consumers should fall back to their
 * previous provider.</p>
 *
 * <p>Unlike a submission-order FIFO, this does not depend on the deferred draw
 * phase executing models in the same order they were submitted — entity submit
 * nodes are bucketed by render type and translucent models are sorted by
 * distance, so the draw order is not guaranteed.  The render state carried by
 * each model submit identifies the entity being drawn.</p>
 */
public final class RenderHeadCapture {

    private static final Logger LOGGER = LoggerFactory.getLogger("halo");
    private static final long DIAGNOSTIC_INTERVAL_NANOS = 1_000_000_000L;

    private static final ThreadLocal<PlayerModel> CURRENT_MODEL = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_UUID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> CURRENT_ENTITY_ID = new ThreadLocal<>();
    private static final Map<UUID, CapturedHead> CAPTURES = new ConcurrentHashMap<>();
    /** Base player body model per entity id, registered at submit time. */
    private static final Map<Integer, PlayerModel> BASE_MODEL_BY_ENTITY_ID = new ConcurrentHashMap<>();
    /** Entity UUID per entity id, registered at submit time. */
    private static final Map<Integer, UUID> UUID_BY_ENTITY_ID = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> REGISTER_FRAME_BY_ENTITY_ID = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> BEGIN_DRAW_FRAME_BY_ENTITY_ID = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> CAPTURE_FRAME_BY_UUID = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LOOKUP_FRAME_BY_UUID = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_LATE_CAPTURE_LOG_NANOS = new ConcurrentHashMap<>();
    private static volatile long frameId;
    /**
     * The frame's view matrix (world → camera space), captured once per frame
     * before entities render.  On 1.21.1+ the world-render matrix stack has an
     * identity root, so captured head matrices are already camera-relative
     * world space and this is set to the identity matrix — consumers must NOT
     * un-rotate them (doing so double-rotates the anchor).  Kept for the
     * conversion API contract; pass identity when the capture is already in
     * world space.
     */
    private static volatile Matrix4f viewMatrix;

    private RenderHeadCapture() { /* utility class */ }

    /**
     * Called at the HEAD of {@code AvatarRenderer.submit}.  Records the base
     * player body model and entity UUID for the entity id so the deferred draw
     * phase can attribute the head render to the right player.
     */
    public static void registerPlayer(int entityId, UUID entityUuid, PlayerModel model) {
        BASE_MODEL_BY_ENTITY_ID.put(entityId, model);
        UUID_BY_ENTITY_ID.put(entityId, entityUuid);
        REGISTER_FRAME_BY_ENTITY_ID.put(entityId, frameId);
    }

    /**
     * Called at the HEAD of {@code PlayerModel.setupAnim} during the deferred
     * draw phase, right before the model is rendered.  Sets the capture context
     * only for the base player body model of the entity being drawn; layer and
     * armour models clear the context so their head parts are not captured.
     */
    public static void beginDraw(int entityId, PlayerModel model) {
        if (BASE_MODEL_BY_ENTITY_ID.get(entityId) == model) {
            CURRENT_MODEL.set(model);
            CURRENT_UUID.set(UUID_BY_ENTITY_ID.get(entityId));
            CURRENT_ENTITY_ID.set(entityId);
            BEGIN_DRAW_FRAME_BY_ENTITY_ID.put(entityId, frameId);
        } else {
            CURRENT_MODEL.remove();
            CURRENT_UUID.remove();
            CURRENT_ENTITY_ID.remove();
        }
    }

    /** Drop all captures from the previous frame; call before entity rendering. */
    public static void clearFrame() {
        frameId++;
        CAPTURES.clear();
        BASE_MODEL_BY_ENTITY_ID.clear();
        UUID_BY_ENTITY_ID.clear();
        REGISTER_FRAME_BY_ENTITY_ID.clear();
        BEGIN_DRAW_FRAME_BY_ENTITY_ID.clear();
        CAPTURE_FRAME_BY_UUID.clear();
        LOOKUP_FRAME_BY_UUID.clear();
        CURRENT_MODEL.remove();
        CURRENT_UUID.remove();
        CURRENT_ENTITY_ID.remove();
    }

    /** Record the frame's view matrix (world → camera). */
    public static void setViewMatrix(Matrix4f value) {
        viewMatrix = value;
    }

    /** The frame's view matrix, or {@code null} before the first frame. */
    public static Matrix4f getViewMatrix() {
        return viewMatrix;
    }

    /**
     * Called at the HEAD of {@link ModelPart#render} for every part while a
     * player is rendering.  Only the head part of the current player model is
     * snapshotted.
     */
    public static void capture(PoseStack matrices, ModelPart part) {
        PlayerModel model = CURRENT_MODEL.get();
        if (model == null || part != model.getHead()) {
            return;
        }
        UUID entityUuid = CURRENT_UUID.get();
        if (entityUuid == null) {
            return;
        }
        CAPTURES.put(entityUuid, new CapturedHead(
            new Matrix4f(matrices.last().pose()),
            part.x, part.y, part.z,
            part.xRot, part.yRot, part.zRot,
            part.xScale, part.yScale, part.zScale
        ));
        CAPTURE_FRAME_BY_UUID.put(entityUuid, frameId);

        if (LOOKUP_FRAME_BY_UUID.getOrDefault(entityUuid, -1L) == frameId
                && shouldLog(LAST_LATE_CAPTURE_LOG_NANOS, entityUuid)) {
            LOGGER.info("[HaloAnchorDiag] stage=capture-after-lookup frame={} entityId={} uuid={} "
                    + "registered={} beganDraw={} captures={}",
                frameId, CURRENT_ENTITY_ID.get(), entityUuid,
                REGISTER_FRAME_BY_ENTITY_ID.getOrDefault(CURRENT_ENTITY_ID.get(), -1L) == frameId,
                BEGIN_DRAW_FRAME_BY_ENTITY_ID.getOrDefault(CURRENT_ENTITY_ID.get(), -1L) == frameId,
                CAPTURES.size());
        }
    }

    public static CapturedHead get(UUID uuid) {
        return CAPTURES.get(uuid);
    }

    /** Snapshot the capture pipeline at the exact provider lookup point. */
    public static DiagnosticSnapshot noteLookup(int entityId, UUID uuid) {
        LOOKUP_FRAME_BY_UUID.put(uuid, frameId);
        return new DiagnosticSnapshot(
            frameId,
            UUID_BY_ENTITY_ID.get(entityId) != null,
            uuid.equals(UUID_BY_ENTITY_ID.get(entityId)),
            REGISTER_FRAME_BY_ENTITY_ID.getOrDefault(entityId, -1L) == frameId,
            BEGIN_DRAW_FRAME_BY_ENTITY_ID.getOrDefault(entityId, -1L) == frameId,
            CAPTURE_FRAME_BY_UUID.getOrDefault(uuid, -1L) == frameId,
            BASE_MODEL_BY_ENTITY_ID.size(),
            CAPTURES.size()
        );
    }

    private static boolean shouldLog(Map<UUID, Long> lastLogNanos, UUID uuid) {
        long now = System.nanoTime();
        Long previous = lastLogNanos.get(uuid);
        if (previous != null && now - previous < DIAGNOSTIC_INTERVAL_NANOS) {
            return false;
        }
        lastLogNanos.put(uuid, now);
        return true;
    }

    /**
     * The model root matrix (camera-relative, including entity position, body
     * rotation, the {@code scale(-1,-1,1)} flip and player scale) at the moment
     * the head part starts rendering, plus the head part's own pose
     * (pivot in 1/16 model units, angles in radians, per-part scale).
     */
    public record CapturedHead(
        Matrix4f rootMatrix,
        float pivotX, float pivotY, float pivotZ,
        float pitch, float yaw, float roll,
        float xScale, float yScale, float zScale
    ) {}

    public record DiagnosticSnapshot(
        long frameId,
        boolean entityIdKnown,
        boolean uuidMatchesEntityId,
        boolean registeredThisFrame,
        boolean beganDrawThisFrame,
        boolean capturedThisFrame,
        int registeredPlayers,
        int capturedPlayers
    ) {}
}
