package network.azusake.halo.compat.ysm;

import network.azusake.halo.api.EntityAnchorProvider;
import network.azusake.halo.api.HeadAnchor;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.physics.RenderHeadCapture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Optional YSM render-anchor wrapper for every living entity class. */
public final class YsmEntityAnchorProvider implements EntityAnchorProvider {

    private final EntityAnchorProvider fallback;

    public YsmEntityAnchorProvider(EntityAnchorProvider fallback) {
        this.fallback = fallback;
    }

    @Override
    public HeadAnchor resolve(LivingEntity entity, float tickDelta) {
        if (!HaloModConfigStore.get().isExperimentalYsmAnchorEnabled()) {
            return fallback.resolve(entity, tickDelta);
        }

        FrameContext frame = frameContext();
        if (frame != null) {
            HeadAnchor current = resolveCapture(YsmHeadCapture.getCurrent(entity.getUuid()), frame);
            if (isFinite(current)) {
                YsmHeadCapture.markAnchorConsumed(false);
                return current;
            }

            HeadAnchor previous = resolveCapture(YsmHeadCapture.getPrevious(entity.getUuid()), frame);
            if (isFinite(previous)) {
                YsmHeadCapture.markAnchorConsumed(true);
                return previous;
            }
        }
        return fallback.resolve(entity, tickDelta);
    }

    private static FrameContext frameContext() {
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = client != null && client.gameRenderer != null
            ? client.gameRenderer.getCamera()
            : null;
        Matrix4f viewMatrix = RenderHeadCapture.getViewMatrix();
        return camera != null && viewMatrix != null
            ? new FrameContext(camera.getPos(), viewMatrix)
            : null;
    }

    private static HeadAnchor resolveCapture(YsmHeadCapture.CapturedHead captured, FrameContext frame) {
        if (captured == null) {
            return null;
        }
        double[] rawOffset = HaloModConfigStore.get().getExperimentalYsmHeadLocalOffset();
        HeadAnchor anchor = YsmHeadMath.toHeadAnchor(
            captured.headMatrix(),
            new Vec3d(rawOffset[0], rawOffset[1], rawOffset[2]),
            frame.cameraPos,
            frame.viewMatrix
        );
        if (!isFinite(anchor)) {
            YsmHeadCapture.markAnchorConversionFailed();
            return null;
        }
        return anchor;
    }

    private static boolean isFinite(HeadAnchor anchor) {
        if (anchor == null) {
            return false;
        }
        Vec3d center = anchor.headCenter();
        return Double.isFinite(center.x) && Double.isFinite(center.y) && Double.isFinite(center.z)
            && Float.isFinite(anchor.yaw()) && Float.isFinite(anchor.pitch()) && Float.isFinite(anchor.roll());
    }

    private record FrameContext(Vec3d cameraPos, Matrix4f viewMatrix) {
    }
}
