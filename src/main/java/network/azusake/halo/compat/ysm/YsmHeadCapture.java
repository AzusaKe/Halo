package network.azusake.halo.compat.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import network.azusake.halo.api.HeadAnchor;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.physics.RenderHeadCapture;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One-frame, entity-relative cache of YSM Head locator captures. */
public final class YsmHeadCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("halo");
    private static final Map<UUID, CachedAnchor> CURRENT = new ConcurrentHashMap<>();
    private static final Map<UUID, CachedAnchor> PREVIOUS = new ConcurrentHashMap<>();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static volatile CaptureFrame frame;
    private static volatile boolean adapterBroken;

    private YsmHeadCapture() {}

    public static void beginFrame(Matrix4f viewMatrix, Vec3 cameraPos, Frustum frustum, float tickDelta) {
        PREVIOUS.clear();
        PREVIOUS.putAll(CURRENT);
        CURRENT.clear();
        frame = viewMatrix == null || cameraPos == null || frustum == null
            ? null : new CaptureFrame(new Matrix4f(viewMatrix), cameraPos,
                new Frustum(frustum), tickDelta, currentLevel());
    }

    /** Called at the common YSM geometry submission method after animation is evaluated. */
    public static void capture(Object renderData, PoseStack matrices) {
        if (!HaloModConfigStore.get().isExperimentalYsmAnchorEnabled()) {
            infoOnce("disabled", "[YSM Compat] capture hook active; experimentalYsmAnchorEnabled=false");
            return;
        }
        if (adapterBroken || renderData == null || matrices == null) return;
        LivingEntity entity = YsmEntityRenderContext.current();
        CaptureFrame current = frame;
        if (entity == null || current == null || current.levelIdentity != currentLevel()
                || !isVisible(current, entity) || CURRENT.containsKey(entity.getUUID())) return;
        if (!RenderHeadCapture.isCurrentMainPass()) return;

        try {
            Matrix4f head = YsmV265Adapter.captureHeadMatrix(
                renderData, new Matrix4f(matrices.last().pose()));
            if (head == null) {
                warnOnce("unusable-head", "[YSM Compat] no usable Head locator in rendered model ("
                    + YsmV265Adapter.lastFailureDetail() + "); using fallback");
                return;
            }
            double[] raw = HaloModConfigStore.get().getExperimentalYsmHeadLocalOffset();
            HeadAnchor world = YsmHeadMath.toHeadAnchor(
                head, new Vec3(raw[0], raw[1], raw[2]), current.cameraPos, current.viewMatrix);
            if (!finite(world)) {
                warnOnce("conversion", "[YSM Compat] Head matrix conversion was non-finite; using fallback");
                return;
            }
            Vec3 entityPosition = interpolatedPosition(entity, current.tickDelta);
            Vec3 relative = world.headCenter().subtract(entityPosition);
            if (!plausible(relative, entity.getBbHeight())) return;
            CachedAnchor cached = new CachedAnchor(entity.getId(), current.levelIdentity, relative,
                world.yaw(), world.pitch(), world.roll());
            if (CURRENT.putIfAbsent(entity.getUUID(), cached) == null) {
                infoOnce("captured", "[YSM Compat] Head locator captured for a YSM-rendered living entity");
            }
        } catch (Throwable error) {
            adapterBroken = true;
            warnOnce("adapter", "[YSM Compat] hotfix symbol adapter failed; disabled for this session: "
                + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    public static HeadAnchor resolveCurrent(LivingEntity entity, float tickDelta) {
        return resolve(CURRENT.get(entity.getUUID()), entity, tickDelta);
    }

    public static HeadAnchor resolvePrevious(LivingEntity entity, float tickDelta) {
        return resolve(PREVIOUS.get(entity.getUUID()), entity, tickDelta);
    }

    public static boolean isVisibleToMainCamera(LivingEntity entity) {
        CaptureFrame current = frame;
        return current != null && entity != null && current.levelIdentity == currentLevel()
            && isVisible(current, entity);
    }

    public static void discard(UUID uuid) {
        CURRENT.remove(uuid);
        PREVIOUS.remove(uuid);
    }

    private static HeadAnchor resolve(CachedAnchor cached, LivingEntity entity, float tickDelta) {
        if (cached == null || cached.entityId != entity.getId()
                || cached.levelIdentity != currentLevel()) return null;
        HeadAnchor anchor = cached.resolve(interpolatedPosition(entity, tickDelta));
        return finite(anchor) ? anchor : null;
    }

    private static boolean isVisible(CaptureFrame current, LivingEntity entity) {
        try {
            return current.frustum.isVisible(entity.getBoundingBox());
        } catch (Throwable error) {
            warnOnce("frustum", "[YSM Compat] main-camera frustum check failed; capture rejected");
            return false;
        }
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

    private static boolean plausible(Vec3 relative, double height) {
        if (relative == null || !Double.isFinite(relative.x) || !Double.isFinite(relative.y)
                || !Double.isFinite(relative.z)) return false;
        double maximum = Math.max(4.0, Math.abs(height) * 2.0 + 1.0);
        return relative.lengthSqr() <= maximum * maximum;
    }

    private static boolean finite(HeadAnchor anchor) {
        if (anchor == null) return false;
        Vec3 p = anchor.headCenter();
        return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z)
            && Float.isFinite(anchor.yaw()) && Float.isFinite(anchor.pitch())
            && Float.isFinite(anchor.roll());
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

    private record CachedAnchor(
        int entityId, Object levelIdentity, Vec3 relativeCenter, float yaw, float pitch, float roll
    ) {
        HeadAnchor resolve(Vec3 entityPosition) {
            return new HeadAnchor(entityPosition.add(relativeCenter), yaw, pitch, roll);
        }
    }

}
