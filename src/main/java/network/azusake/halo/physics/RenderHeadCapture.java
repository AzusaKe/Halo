package network.azusake.halo.physics;

import network.azusake.halo.anchor.AnchorCaptureCoordinator;
import network.azusake.halo.api.v2.*;
import network.azusake.halo.core.runtime.PreviewAnchorHost;
import network.azusake.halo.render.PlayerPreviewCapture;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.culling.Frustum;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.util.*;

/** Captures the final model pose inside each deferred model submission's copied entity scope. */
public final class RenderHeadCapture {
    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();
    private static final AnchorSource VANILLA_SOURCE = HaloAnchorApi.register("halo:vanilla");
    private static final Map<UUID, CapturedHead> CAPTURES = new HashMap<>();
    private static Matrix4f viewMatrix;
    private static Vec3 cameraPos;
    private static float frameTickDelta;
    private static long frameId;
    private static long captureFrame = Long.MIN_VALUE;
    private RenderHeadCapture() {}

    public record Context(EntityRenderSnapshot entity, PlayerModel model, boolean auxiliary) {}
    public static Context suspendForPreview() {
        Context previous = CONTEXT.get(); CONTEXT.remove(); return previous;
    }
    public static void restoreContext(Context previous) {
        if (previous == null) CONTEXT.remove(); else CONTEXT.set(previous);
    }
    public static void beginDraw(EntityRenderSnapshot entity, PlayerModel model) {
        boolean main = OptionalIrisPassDetector.isMainPass();
        CONTEXT.set(new Context(entity, model, !main));
        if (HaloAnchorApi.isPreviewRendering()) PreviewAnchorHost.beginEntityRender(entity.uuid(), entity.runtimeId());
        else AnchorCaptureCoordinator.beginEntityRender(entity.uuid(), entity.runtimeId(), entity.world(), entity.position(), main);
    }
    public static void endEntityRender() {
        if (HaloAnchorApi.isPreviewRendering()) PreviewAnchorHost.endEntityRender();
        else AnchorCaptureCoordinator.endEntityRender();
        CONTEXT.remove();
    }
    public static void beginRenderFrame() { frameId++; }
    public static long getFrameId() { return frameId; }
    public static void beginFrame(Matrix4f frameView, Vec3 position, Frustum frustum, float tickDelta, Object world) {
        if (captureFrame == frameId || !OptionalIrisPassDetector.isMainPass()) return;
        captureFrame = frameId;
        CAPTURES.clear();
        viewMatrix = new Matrix4f(frameView);
        cameraPos = new Vec3(position.x, position.y, position.z);
        frameTickDelta = tickDelta;
        AnchorCaptureCoordinator.beginFrame(world);
        network.azusake.halo.compat.emf.EmfHeadCapture.beginFrame(viewMatrix, cameraPos);
    }
    public static EntityRenderSnapshot currentEntity() {
        Context context = CONTEXT.get(); return context == null ? null : context.entity();
    }
    public static boolean isAuxiliaryPass() {
        Context context = CONTEXT.get(); return context == null || context.auxiliary();
    }
    public static float getFrameTickDelta() { return frameTickDelta; }
    public static CapturedHead get(UUID uuid) { return CAPTURES.get(uuid); }
    /** Identity, not the PlayerModel type, separates the body from cape/armor feature draws. */
    public static boolean isPlayerHead(ModelPart part) {
        Context context = CONTEXT.get();
        return context != null && context.model() != null
            && context.model() == context.entity().playerModel() && part == context.model().getHead();
    }
    public static void capture(PoseStack matrices, ModelPart part) {
        Context context = CONTEXT.get();
        if (!isPlayerHead(part)) return;
        var entity = context.entity();
        if (HaloAnchorApi.isPreviewRendering()) {
            PlayerPreviewCapture.capture(entity.uuid(), entity.runtimeId(), matrices, part); return;
        }
        if (context.auxiliary() || viewMatrix == null || cameraPos == null || CAPTURES.containsKey(entity.uuid())) return;
        CapturedHead captured = new CapturedHead(new Matrix4f(matrices.last().pose()),
            part.x, part.y, part.z, part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale);
        CAPTURES.put(entity.uuid(), captured);
        try {
            VANILLA_SOURCE.submit(entity.uuid(), RenderHeadMath.toAnchorPose(captured,
                network.azusake.halo.platform.PlatformTypes.core(cameraPos), viewMatrix));
        } catch (RuntimeException ignored) { /* An invalid model pose falls back to the vanilla entity facts. */ }
    }

    public record CapturedHead(
        Matrix4f rootMatrix,
        float pivotX, float pivotY, float pivotZ,
        float pitch, float yaw, float roll,
        float xScale, float yScale, float zScale
    ) implements RenderHeadMath.HeadCapture {}
}
