package network.azusake.halo.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import network.azusake.halo.api.v2.*;
import network.azusake.halo.core.runtime.PreviewAnchorHost;
import network.azusake.halo.core.runtime.PreviewAnchorScope;
import network.azusake.halo.physics.RenderHeadCapture;
import org.joml.Matrix4f;

/** Render-call-local captures. They never enter API v2's world capture buffers. */
public final class PlayerPreviewCapture implements AutoCloseable {
    private static final ThreadLocal<Deque<PlayerPreviewCapture>> SCOPES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final PreviewAnchorHost HOST = new PreviewAnchorHost();
    private final LivingEntity wearer;
    private final Matrix4f root;
    private final RenderHeadCapture.Context previousContext;
    private final PreviewAnchorScope anchors;
    private boolean closed;

    private PlayerPreviewCapture(LivingEntity wearer, Matrix4f root) {
        this.wearer = wearer;
        this.root = new Matrix4f(root);
        anchors = HOST.open(wearer.getUUID(), wearer.getId(), root.get(new float[16]));
        previousContext = RenderHeadCapture.suspendForPreview();
        SCOPES.get().push(this);
    }
    public static PlayerPreviewCapture open(LivingEntity wearer, Matrix4f root) {
        return new PlayerPreviewCapture(wearer, root);
    }
    public static boolean isActive() { return !SCOPES.get().isEmpty(); }
    public AnchorPose head() {
        var pose = anchors.resolved();
        return pose == null ? null : new AnchorPose(new AnchorVec3(pose.x(), pose.y(), pose.z()), pose.rotation());
    }
    public Matrix4f root() { return new Matrix4f(root); }
    public static void clearAnchorScopes() { HOST.clear(); }

    /** Eligible model capture scope. Nested entity features cannot supply the wearer's head. */
    public static PlayerPreviewCapture current() {
        var scope = SCOPES.get().peek();
        return scope != null && HaloAnchorApi.currentPreviewContext() == scope.anchors.context()
            ? scope : null;
    }
    private static PreviewAnchorPose previewPose(AnchorPose pose) {
        return pose == null ? null : new PreviewAnchorPose(pose.position().x(), pose.position().y(), pose.position().z(), pose.rotation());
    }

    public static void capture(LivingEntity wearer, PoseStack matrices, ModelPart part) {
        PlayerPreviewCapture scope = current();
        // Replaced model parts need their own compat provider. Do not guess their anchor.
        if (scope == null || scope.wearer != wearer || part.getClass() != ModelPart.class
                || !part.visible || part.skipDraw || scope.anchors.hasFallback(PreviewAnchorScope.Fallback.RENDERED)) return;
        var pose = PreviewHeadMath.toAnchor(scope.root, new RenderHeadCapture.CapturedHead(
            new Matrix4f(matrices.last().pose()), part.x, part.y, part.z,
            part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale));
        scope.anchors.submitFallback(previewPose(pose), PreviewAnchorScope.Fallback.RENDERED);
    }

    /** Vanilla computes head angles even when invisibility suppresses the base-model draw. */
    public static void capturePosedHead(LivingEntity wearer, PoseStack matrices, ModelPart part) {
        PlayerPreviewCapture scope = current();
        if (scope == null || scope.wearer != wearer || part.getClass() != ModelPart.class
                || !part.visible || part.skipDraw || scope.anchors.hasFallback(PreviewAnchorScope.Fallback.POSED)) return;
        var pose = PreviewHeadMath.toAnchor(scope.root, new RenderHeadCapture.CapturedHead(
            new Matrix4f(matrices.last().pose()), part.x, part.y, part.z,
            part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale));
        scope.anchors.submitFallback(previewPose(pose), PreviewAnchorScope.Fallback.POSED);
    }

    @Override public void close() {
        if (closed) return;
        if (SCOPES.get().peek() != this) throw new IllegalStateException("Preview scopes must close in reverse order");
        anchors.close();
        closed = true;
        SCOPES.get().pop();
        if (SCOPES.get().isEmpty()) SCOPES.remove();
        RenderHeadCapture.restoreContext(previousContext);
    }
}
