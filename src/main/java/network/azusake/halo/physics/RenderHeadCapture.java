package network.azusake.halo.physics;

import network.azusake.halo.anchor.AnchorCaptureCoordinator;
import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorSource;
import network.azusake.halo.api.v2.AnchorVec3;
import network.azusake.halo.api.v2.HaloAnchorApi;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
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
    private static final ThreadLocal<Boolean> AUXILIARY_YSM_PASS =
        ThreadLocal.withInitial(() -> false);
    private static final AnchorSource VANILLA_SOURCE = HaloAnchorApi.register("halo:vanilla");
    private static final Map<UUID, CapturedHead> CAPTURES = new ConcurrentHashMap<>();
    /**
     * The frame's view matrix (world → camera space), captured once per frame
     * before entities render.  The captured head matrices are camera-relative,
     * so consumers must un-rotate them with this matrix to recover world
     * positions and angles.
     */
    private static volatile Matrix4f viewMatrix;
    private static volatile Vec3d cameraPos;
    private static volatile Frustum mainFrustum;
    private static volatile float frameTickDelta;

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
    public static void beginYsmEntity(Entity entity, MatrixStack matrices) {
        CURRENT_MODEL.remove();
        Matrix4f root = matrices == null ? null : matrices.peek().getPositionMatrix();
        if (entity instanceof LivingEntity living && matchesMainView(root)) {
            CURRENT_ENTITY.set(living);
            AUXILIARY_YSM_PASS.set(false);
        } else {
            CURRENT_ENTITY.remove();
            AUXILIARY_YSM_PASS.set(entity instanceof LivingEntity);
        }
    }

    /** Open the source-neutral render scope used by API v2 submissions. */
    public static void beginEntityRender(Entity entity, MatrixStack matrices, float tickDelta) {
        Matrix4f root = matrices == null ? null : matrices.peek().getPositionMatrix();
        boolean mainPass = entity instanceof LivingEntity living
            && matchesMainView(root)
            && isVisibleToMainCamera(living)
            && OptionalIrisPassDetector.isMainPass();
        if (entity instanceof LivingEntity living) {
            Vec3d position = interpolatedPosition(living, tickDelta);
            AnchorCaptureCoordinator.beginEntityRender(
                living.getUuid(), living.getId(), living.getWorld(),
                new AnchorVec3(position.x, position.y, position.z), mainPass);
            if (mainPass) {
                CURRENT_ENTITY.set(living);
                AUXILIARY_YSM_PASS.set(false);
            } else {
                CURRENT_ENTITY.remove();
                AUXILIARY_YSM_PASS.set(true);
            }
        }
    }

    public static void endEntityRender() {
        AnchorCaptureCoordinator.endEntityRender();
        end();
    }

    /**
     * Auxiliary shader passes use a different root transform. They must never
     * be paired with the main camera metadata used to restore world space.
     */
    static boolean matchesMainView(Matrix4f candidate) {
        Matrix4f expected = viewMatrix;
        if (candidate == null || expected == null) {
            return false;
        }
        float[] actualValues = new float[16];
        float[] expectedValues = new float[16];
        candidate.get(actualValues);
        expected.get(expectedValues);
        for (int i = 0; i < actualValues.length; i++) {
            if (!Float.isFinite(actualValues[i])
                || Math.abs(actualValues[i] - expectedValues[i]) > 1.0e-4f) {
                return false;
            }
        }
        return true;
    }

    /**
     * Called on normal returns from {@code PlayerEntityRenderer.render};
     * renderer-replacement compatibility hooks may also release early after
     * taking their own capture.
     */
    public static void end() {
        CURRENT_ENTITY.remove();
        CURRENT_MODEL.remove();
        AUXILIARY_YSM_PASS.remove();
    }

    /** Drop all captures from the previous frame; call before entity rendering. */
    public static void clearFrame() {
        CAPTURES.clear();
    }

    public static void beginFrame(Matrix4f frameViewMatrix, Vec3d frameCameraPos,
                                  Frustum frustum, float tickDelta, Object worldIdentity) {
        clearFrame();
        viewMatrix = frameViewMatrix == null ? null : new Matrix4f(frameViewMatrix);
        cameraPos = frameCameraPos;
        mainFrustum = frustum == null ? null : new Frustum(frustum);
        frameTickDelta = tickDelta;
        AnchorCaptureCoordinator.beginFrame(worldIdentity);
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

    /** Whether the current YSM render was rejected for using a non-main root matrix. */
    public static boolean isAuxiliaryYsmPass() {
        return AUXILIARY_YSM_PASS.get();
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
        Vec3d frameCameraPos = cameraPos;
        Matrix4f frameViewMatrix = viewMatrix;
        if (entity == null || frameCameraPos == null || frameViewMatrix == null) {
            return;
        }
        CapturedHead captured = new CapturedHead(
            new Matrix4f(matrices.peek().getPositionMatrix()),
            part.pivotX, part.pivotY, part.pivotZ,
            part.pitch, part.yaw, part.roll,
            part.xScale, part.yScale, part.zScale
        );
        CAPTURES.put(entity.getUuid(), captured);
        try {
            AnchorPose pose = RenderHeadMath.toAnchorPose(captured, network.azusake.halo.platform.PlatformTypes.core(frameCameraPos), frameViewMatrix);
            VANILLA_SOURCE.submit(entity.getUuid(), pose);
        } catch (RuntimeException ignored) {
            // Invalid capture data is a normal safe-fallback condition.
        }
    }

    public static CapturedHead get(UUID uuid) {
        return CAPTURES.get(uuid);
    }

    public static float getFrameTickDelta() {
        return frameTickDelta;
    }

    private static boolean isVisibleToMainCamera(LivingEntity entity) {
        Frustum frustum = mainFrustum;
        return frustum != null && frustum.isVisible(entity.getVisibilityBoundingBox());
    }

    private static Vec3d interpolatedPosition(LivingEntity entity, float tickDelta) {
        return new Vec3d(
            entity.prevX + (entity.getX() - entity.prevX) * tickDelta,
            entity.prevY + (entity.getY() - entity.prevY) * tickDelta,
            entity.prevZ + (entity.getZ() - entity.prevZ) * tickDelta
        );
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
    ) implements RenderHeadMath.HeadCapture {}
}
