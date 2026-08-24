package network.azusake.halo.physics;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-frame capture of the player head's rendered transform.
 *
 * <p>Hooks installed by the client mixins bracket {@code PlayerEntityRenderer.render}
 * with {@link #begin}/{@link #end} (ThreadLocal context) and snapshot the model
 * root matrix plus the head {@link ModelPart}'s final pose when the head part is
 * actually rendered.  Captures are keyed by entity UUID and cleared once per
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

    private static final ThreadLocal<LivingEntity> CURRENT_ENTITY = new ThreadLocal<>();
    private static final ThreadLocal<PlayerEntityModel<?>> CURRENT_MODEL = new ThreadLocal<>();
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

    /** Called at the HEAD of {@code PlayerEntityRenderer.render}. */
    public static void begin(AbstractClientPlayerEntity entity, PlayerEntityModel<?> model) {
        CURRENT_ENTITY.set(entity);
        CURRENT_MODEL.set(model);
    }

    /**
     * Bracket an entity-dispatcher render so optional renderer integrations can
     * associate their model pass with any living entity, not only players.
     */
    public static void beginYsmEntity(Entity entity) {
        CURRENT_MODEL.remove();
        if (entity instanceof LivingEntity living) {
            CURRENT_ENTITY.set(living);
        } else {
            CURRENT_ENTITY.remove();
        }
    }

    /**
     * Called on normal returns from {@code PlayerEntityRenderer.render};
     * renderer-replacement compatibility hooks may also release early after
     * taking their own capture.
     */
    public static void end() {
        CURRENT_ENTITY.remove();
        CURRENT_MODEL.remove();
    }

    /** Drop all captures from the previous frame; call before entity rendering. */
    public static void clearFrame() {
        CAPTURES.clear();
    }

    /** Record the frame's view matrix (world → camera). */
    public static void setViewMatrix(Matrix4f value) {
        viewMatrix = value;
    }

    /** The frame's view matrix, or {@code null} before the first frame. */
    public static Matrix4f getViewMatrix() {
        return viewMatrix;
    }

    /** Current bracketed living entity, exposed to isolated renderer compat hooks. */
    public static LivingEntity getCurrentEntity() {
        return CURRENT_ENTITY.get();
    }

    /**
     * Called at the HEAD of {@link ModelPart#render} for every part while a
     * player is rendering.  Only the head part of the current player model is
     * snapshotted.
     */
    public static void capture(MatrixStack matrices, ModelPart part) {
        PlayerEntityModel<?> model = CURRENT_MODEL.get();
        if (model == null || part != model.getHead()) {
            return;
        }
        LivingEntity entity = CURRENT_ENTITY.get();
        if (entity == null) {
            return;
        }
        CAPTURES.put(entity.getUuid(), new CapturedHead(
            new Matrix4f(matrices.peek().getPositionMatrix()),
            part.pivotX, part.pivotY, part.pivotZ,
            part.pitch, part.yaw, part.roll,
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
