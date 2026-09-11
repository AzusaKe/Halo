package network.azusake.halo.compat.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorSource;
import network.azusake.halo.api.v2.HaloAnchorApi;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.physics.RenderHeadCapture;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Pushes the final YSM Head locator through API v2 on the verified 26.1 branch. */
public final class YsmHeadCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("halo");
    private static final AnchorSource SOURCE = HaloAnchorApi.register("halo:ysm");
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static volatile CaptureFrame frame;
    private static volatile boolean adapterBroken;

    private YsmHeadCapture() {}

    public static void beginFrame(Matrix4f viewMatrix, Vec3 cameraPos, Frustum frustum, float tickDelta) {
        frame = viewMatrix == null || cameraPos == null || frustum == null
            ? null : new CaptureFrame(new Matrix4f(viewMatrix), cameraPos,
                new Frustum(frustum), tickDelta, currentLevel());
    }

    /** Called after YSM has evaluated the effective geometry and Head locator. */
    public static void capture(Object renderData, PoseStack matrices) {
        if (!HaloModConfigStore.get().isExperimentalYsmAnchorEnabled()) {
            infoOnce("disabled", "[YSM Compat] capture hook active; experimentalYsmAnchorEnabled=false");
            return;
        }
        if (adapterBroken || renderData == null || matrices == null) return;
        LivingEntity entity = YsmEntityRenderContext.current();
        CaptureFrame current = frame;
        if (entity == null || current == null || current.levelIdentity != currentLevel()
                || !isVisible(current, entity) || !RenderHeadCapture.isCurrentMainPass()) return;

        try {
            Matrix4f head = YsmV265Adapter.captureHeadMatrix(
                renderData, new Matrix4f(matrices.last().pose()));
            if (head == null) {
                warnOnce("unusable-head", "[YSM Compat] no usable Head locator in rendered model ("
                    + YsmV265Adapter.lastFailureDetail() + "); using fallback");
                return;
            }
            double[] raw = HaloModConfigStore.get().getExperimentalYsmHeadLocalOffset();
            AnchorPose pose = YsmHeadMath.toAnchorPose(
                head, new Vec3(raw[0], raw[1], raw[2]), current.cameraPos, current.viewMatrix);
            if (pose == null || !plausible(pose, entity, current.tickDelta)) {
                warnOnce("conversion", "[YSM Compat] Head matrix conversion was invalid; using fallback");
                return;
            }
            if (SOURCE.submit(entity.getUUID(), pose)) {
                infoOnce("captured", "[YSM Compat] Head locator submitted for a YSM-rendered living entity");
            }
        } catch (Throwable error) {
            adapterBroken = true;
            warnOnce("adapter", "[YSM Compat] hotfix symbol adapter failed; disabled for this session: "
                + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private static boolean isVisible(CaptureFrame current, LivingEntity entity) {
        try {
            return current.frustum.isVisible(entity.getBoundingBox());
        } catch (Throwable error) {
            warnOnce("frustum", "[YSM Compat] main-camera frustum check failed; capture rejected");
            return false;
        }
    }

    private static boolean plausible(AnchorPose pose, LivingEntity entity, float tickDelta) {
        Vec3 entityPosition = interpolatedPosition(entity, tickDelta);
        Vec3 relative = new Vec3(pose.position().x(), pose.position().y(), pose.position().z())
            .subtract(entityPosition);
        double maximum = Math.max(4.0, Math.abs(entity.getBbHeight()) * 2.0 + 1.0);
        return relative.lengthSqr() <= maximum * maximum;
    }

    private static Vec3 interpolatedPosition(LivingEntity entity, float tickDelta) {
        return new Vec3(
            entity.xOld + (entity.getX() - entity.xOld) * tickDelta,
            entity.yOld + (entity.getY() - entity.yOld) * tickDelta,
            entity.zOld + (entity.getZ() - entity.zOld) * tickDelta);
    }

    private static Object currentLevel() {
        Minecraft client = Minecraft.getInstance();
        return client != null ? client.level : null;
    }

    private static void warnOnce(String key, String message) {
        if (REPORTED.add(key)) LOGGER.warn(message);
    }

    private static void infoOnce(String key, String message) {
        if (REPORTED.add(key)) LOGGER.info(message);
    }

    private record CaptureFrame(
        Matrix4f viewMatrix, Vec3 cameraPos, Frustum frustum, float tickDelta, Object levelIdentity
    ) {}
}
