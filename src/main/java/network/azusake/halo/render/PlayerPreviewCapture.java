package network.azusake.halo.render;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.physics.RenderHeadCapture;
import org.joml.Matrix4f;

/** Render-call-local captures. They never enter API v2's world capture buffers. */
public final class PlayerPreviewCapture implements AutoCloseable {
    private static final ThreadLocal<Deque<PlayerPreviewCapture>> SCOPES = ThreadLocal.withInitial(ArrayDeque::new);
    private final LivingEntity wearer;
    private final Matrix4f root;
    private final RenderHeadCapture.Context previousContext;
    private AnchorPose head;
    private AnchorPose posedHead;
    private boolean closed;

    private PlayerPreviewCapture(LivingEntity wearer, Matrix4f root) {
        this.wearer = wearer;
        this.root = new Matrix4f(root);
        previousContext = RenderHeadCapture.suspendForPreview();
        SCOPES.get().push(this);
    }
    public static PlayerPreviewCapture open(LivingEntity wearer, Matrix4f root) {
        return new PlayerPreviewCapture(wearer, root);
    }
    public static boolean isActive() { return !SCOPES.get().isEmpty(); }
    public AnchorPose head() { return head != null ? head : posedHead; }
    public Matrix4f root() { return new Matrix4f(root); }

    public static void capture(LivingEntity wearer, MatrixStack matrices, ModelPart part) {
        PlayerPreviewCapture scope = SCOPES.get().peek();
        // Replaced model parts need their own compat provider. Do not guess their anchor.
        if (scope == null || scope.wearer != wearer || part.getClass() != ModelPart.class
                || !part.visible || part.hidden || scope.head != null) return;
        scope.head = PreviewHeadMath.toAnchor(scope.root, new RenderHeadCapture.CapturedHead(
            new Matrix4f(matrices.peek().getPositionMatrix()), part.pivotX, part.pivotY, part.pivotZ,
            part.pitch, part.yaw, part.roll, part.xScale, part.yScale, part.zScale));
    }

    /** Vanilla computes head angles even when invisibility suppresses the base-model draw. */
    public static void capturePosedHead(LivingEntity wearer, MatrixStack matrices, ModelPart part) {
        PlayerPreviewCapture scope = SCOPES.get().peek();
        if (scope == null || scope.wearer != wearer || part.getClass() != ModelPart.class
                || !part.visible || part.hidden) return;
        scope.posedHead = PreviewHeadMath.toAnchor(scope.root, new RenderHeadCapture.CapturedHead(
            new Matrix4f(matrices.peek().getPositionMatrix()), part.pivotX, part.pivotY, part.pivotZ,
            part.pitch, part.yaw, part.roll, part.xScale, part.yScale, part.zScale));
    }

    @Override public void close() {
        if (closed) return;
        if (SCOPES.get().peek() != this) throw new IllegalStateException("Preview scopes must close in reverse order");
        closed = true;
        SCOPES.get().pop();
        if (SCOPES.get().isEmpty()) SCOPES.remove();
        RenderHeadCapture.restoreContext(previousContext);
    }
}
