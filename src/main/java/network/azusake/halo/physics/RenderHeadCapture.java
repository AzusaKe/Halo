package network.azusake.halo.physics;

import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Per-frame capture of the player head's rendered transform.
 *
 * <p>Hooks installed by the client mixins bracket {@code AvatarRenderer.submit}
 * (ThreadLocal context) and snapshot the model root matrix plus the head
 * {@link ModelPart}'s final pose when the head part is actually rendered in
 * the deferred draw phase.  Because 26.1 defers model drawing until after all
 * entities have submitted, the per-frame entity association is bridged with a
 * FIFO queue: each player submit pushes its UUID, and each rendered player head
 * pops the next UUID.  Captures are keyed by entity UUID and cleared once per
 * frame ({@link #clearFrame}), so a missing entry means "not rendered this
 * frame" — consumers should fall back to their previous provider.</p>
 *
 * <p>This is the default player-anchor capture path: hooks bracket
 * {@code PlayerEntityRenderer.render} with {@link #begin}/{@link #end} and
 * snapshot the head {@link ModelPart}'s rendered transform, then the anchor
 * pipeline converts the camera-relative matrix back to world space via the
 * per-frame view matrix recorded by {@link #setViewMatrix}.</p>
 */
public final class RenderHeadCapture {

    private static final ThreadLocal<PlayerModel> CURRENT_MODEL = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<UUID>> PENDING_UUIDS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<UUID, CapturedHead> CAPTURES = new ConcurrentHashMap<>();
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
     * Called at the HEAD of {@code AvatarRenderer.submit}.  Records the player
     * model for the upcoming deferred draw and enqueues the entity UUID so the
     * next rendered player head can be attributed to this entity.
     */
    public static void beginSubmit(UUID entityUuid, PlayerModel model) {
        CURRENT_MODEL.set(model);
        PENDING_UUIDS.get().addLast(entityUuid);
    }

    /** Drop all captures from the previous frame; call before entity rendering. */
    public static void clearFrame() {
        CAPTURES.clear();
        PENDING_UUIDS.get().clear();
        CURRENT_MODEL.remove();
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
        UUID entityUuid = PENDING_UUIDS.get().pollFirst();
        if (entityUuid == null) {
            return;
        }
        CAPTURES.put(entityUuid, new CapturedHead(
            new Matrix4f(matrices.last().pose()),
            part.x, part.y, part.z,
            part.xRot, part.yRot, part.zRot,
            part.xScale, part.yScale, part.zScale
        ));
    }

    public static CapturedHead get(UUID uuid) {
        return CAPTURES.get(uuid);
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
}
