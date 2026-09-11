package network.azusake.halo.physics;

import com.mojang.blaze3d.vertex.PoseStack;
import network.azusake.halo.anchor.AnchorCaptureCoordinator;
import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorSource;
import network.azusake.halo.api.v2.AnchorVec3;
import network.azusake.halo.api.v2.HaloAnchorApi;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Render-scoped anchor capture for the 26.x deferred entity pipeline. */
public final class RenderHeadCapture {
    public static final boolean ANCHOR_DIAGNOSTICS_ENABLED = false;

    private static final AnchorSource VANILLA_SOURCE = HaloAnchorApi.register("halo:vanilla");
    private static final AnchorSource EMF_SOURCE = HaloAnchorApi.register("halo:emf");
    private static final ThreadLocal<PlayerModel> CURRENT_MODEL = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_UUID = new ThreadLocal<>();
    private static final Map<Integer, PlayerModel> BASE_MODEL_BY_ENTITY_ID = new ConcurrentHashMap<>();
    private static final Map<Integer, UUID> UUID_BY_ENTITY_ID = new ConcurrentHashMap<>();
    private static volatile Matrix4f viewMatrix = new Matrix4f();
    private static volatile float frameTickDelta;
    private static volatile long frameId;
    private static final ThreadLocal<Boolean> SCOPE_OPEN = ThreadLocal.withInitial(() -> false);

    private RenderHeadCapture() {
    }

    public static void registerPlayer(int entityId, UUID entityUuid, PlayerModel model) {
        BASE_MODEL_BY_ENTITY_ID.put(entityId, model);
        UUID_BY_ENTITY_ID.put(entityId, entityUuid);
    }

    public static void beginDraw(int entityId, PlayerModel model) {
        closeScope();
        Minecraft client = Minecraft.getInstance();
        Entity raw = client.level == null ? null : client.level.getEntity(entityId);
        UUID uuid = UUID_BY_ENTITY_ID.get(entityId);
        boolean baseModel = BASE_MODEL_BY_ENTITY_ID.get(entityId) == model;
        if (!baseModel || !(raw instanceof LivingEntity living) || uuid == null
            || !uuid.equals(living.getUUID())) {
            CURRENT_MODEL.remove();
            CURRENT_UUID.remove();
            return;
        }
        OptionalIrisDiagnostics.Snapshot iris = OptionalIrisDiagnostics.snapshot();
        boolean mainPass = iris.shadowPass() == OptionalIrisDiagnostics.Status.FALSE;
        Vec3 position = interpolatedPosition(living, frameTickDelta);
        AnchorCaptureCoordinator.beginEntityRender(uuid, entityId, living.level(),
            new AnchorVec3(position.x, position.y, position.z), mainPass);
        SCOPE_OPEN.set(true);
        if (mainPass) {
            CURRENT_MODEL.set(model);
            CURRENT_UUID.set(uuid);
        } else {
            CURRENT_MODEL.remove();
            CURRENT_UUID.remove();
        }
    }

    public static void clearFrame() {
        closeScope();
        frameId++;
        BASE_MODEL_BY_ENTITY_ID.clear();
        UUID_BY_ENTITY_ID.clear();
        CURRENT_MODEL.remove();
        CURRENT_UUID.remove();
        Minecraft client = Minecraft.getInstance();
        AnchorCaptureCoordinator.beginFrame(client == null ? null : client.level);
    }

    public static void setViewMatrix(Matrix4f value) {
        viewMatrix = value == null ? new Matrix4f() : new Matrix4f(value);
    }

    public static Matrix4f getViewMatrix() {
        return new Matrix4f(viewMatrix);
    }

    public static void setFrameTickDelta(float value) {
        frameTickDelta = value;
    }

    public static long getFrameId() {
        return frameId;
    }

    public static float getFrameTickDelta() {
        return frameTickDelta;
    }

    /** Shared render-backend gate used by the optional YSM dispatcher scope. */
    public static boolean isCurrentMainPass() {
        return OptionalIrisDiagnostics.snapshot().shadowPass() == OptionalIrisDiagnostics.Status.FALSE;
    }

    public static void capture(PoseStack matrices, ModelPart part) {
        captureHead(matrices, part, VANILLA_SOURCE);
    }

    public static void captureEmf(PoseStack matrices, ModelPart part) {
        captureHead(matrices, part, EMF_SOURCE);
    }

    private static void captureHead(PoseStack matrices, ModelPart part, AnchorSource source) {
        PlayerModel model = CURRENT_MODEL.get();
        UUID uuid = CURRENT_UUID.get();
        if (model == null || uuid == null || part != model.getHead() || matrices == null) return;
        CapturedHead captured = new CapturedHead(
            new Matrix4f(matrices.last().pose()),
            part.x, part.y, part.z, part.xRot, part.yRot, part.zRot,
            part.xScale, part.yScale, part.zScale);
        Minecraft client = Minecraft.getInstance();
        Camera camera = client.gameRenderer == null ? null : client.gameRenderer.getMainCamera();
        if (camera == null) return;
        try {
            AnchorPose pose = RenderHeadMath.toAnchorPose(captured, camera.position(), viewMatrix);
            source.submit(uuid, pose);
        } catch (RuntimeException ignored) {
            // Invalid render data safely falls through to the internal fallback.
        }
    }

    private static void closeScope() {
        if (SCOPE_OPEN.get()) {
            AnchorCaptureCoordinator.endEntityRender();
        }
        SCOPE_OPEN.remove();
    }

    private static Vec3 interpolatedPosition(LivingEntity entity, float tickDelta) {
        return new Vec3(
            entity.xo + (entity.getX() - entity.xo) * tickDelta,
            entity.yo + (entity.getY() - entity.yo) * tickDelta,
            entity.zo + (entity.getZ() - entity.zo) * tickDelta);
    }

    public record CapturedHead(
        Matrix4f rootMatrix,
        float pivotX, float pivotY, float pivotZ,
        float pitch, float yaw, float roll,
        float xScale, float yScale, float zScale
    ) {}
}
