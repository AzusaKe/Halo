package network.azusake.halo.physics;

import network.azusake.halo.compat.ysm.YsmHeadCapture;
import network.azusake.halo.compat.ysm.YsmHeadMath;
import network.azusake.halo.api.EntityAnchorProvider;
import network.azusake.halo.api.HeadAnchor;
import network.azusake.halo.config.HaloModConfigStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default player {@link EntityAnchorProvider} that anchors the halo to the
 * player's <em>actually rendered</em> head (captured via mixin each frame).
 *
 * <p>Because the data comes from the render result itself (pose adaptation is
 * already baked in by {@code setAngles}), no per-pose configuration is needed
 * while a capture exists.  Whenever no capture is available this frame — the
 * local player in first person (vanilla does not render its body), a culled
 * entity, an empty render layer, or a mod that replaced the player renderer —
 * this provider delegates to {@link PlayerAnchorProvider}, so the
 * {@code entity_anchors/player.json} fallback path stays intact.</p>
 */
public final class RenderHeadAnchorProvider implements EntityAnchorProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger("halo");

    private final EntityAnchorProvider fallback;

    public RenderHeadAnchorProvider(EntityAnchorProvider fallback) {
        this.fallback = fallback;
    }

    @Override
    public HeadAnchor resolve(LivingEntity entity, float tickDelta) {
        if (entity instanceof AbstractClientPlayerEntity player) {
            MinecraftClient client = MinecraftClient.getInstance();
            boolean localPlayer = client != null && player == client.player;
            boolean firstPerson = client != null && client.options.getPerspective().isFirstPerson();
            if (!shouldUseRenderCapture(localPlayer, firstPerson)) {
                // Iris may render the local player's YSM body in shadow or
                // auxiliary passes even though first-person is not a stable
                // main-camera body render.  The camera is authoritative here.
                YsmHeadCapture.discard(player.getUuid());
                return fallback.resolve(entity, tickDelta);
            }
            if (HaloModConfigStore.get().isExperimentalYsmAnchorEnabled()
                && !YsmHeadCapture.isVisibleToMainCamera(player)) {
                YsmHeadCapture.discard(player.getUuid());
                return fallback.resolve(entity, tickDelta);
            }

            FrameContext frame = frameContext();
            // Prefer the renderer that actually produced this frame.  If YSM
            // deactivates its model and vanilla renders instead, the current
            // vanilla capture correctly wins over stale YSM data.
            HeadAnchor ysmCurrent = resolveYsm(YsmHeadCapture.getCurrent(player.getUuid()));
            if (isFinite(ysmCurrent)) {
                YsmHeadCapture.markAnchorConsumed(false);
                return ysmCurrent;
            }

            if (frame != null) {
                HeadAnchor vanilla = resolveVanilla(RenderHeadCapture.get(player.getUuid()), frame, player);
                if (isFinite(vanilla)) {
                    return vanilla;
                }
            }

            HeadAnchor ysmPrevious = resolveYsm(YsmHeadCapture.getPrevious(player.getUuid()));
            if (isFinite(ysmPrevious)) {
                YsmHeadCapture.markAnchorConsumed(true);
                return ysmPrevious;
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

    private static HeadAnchor resolveYsm(YsmHeadCapture.CapturedHead captured) {
        if (captured == null) {
            return null;
        }
        double[] rawOffset = HaloModConfigStore.get().getExperimentalYsmHeadLocalOffset();
        Vec3d localOffset = new Vec3d(rawOffset[0], rawOffset[1], rawOffset[2]);
        HeadAnchor anchor = YsmHeadMath.toHeadAnchor(captured, localOffset);
        if (!isFinite(anchor)) {
            YsmHeadCapture.markAnchorConversionFailed();
            return null;
        }
        return anchor;
    }

    private static HeadAnchor resolveVanilla(
        RenderHeadCapture.CapturedHead captured,
        FrameContext frame,
        AbstractClientPlayerEntity player
    ) {
        if (captured == null) {
            return null;
        }
        HeadAnchor anchor = RenderHeadMath.toHeadAnchor(captured, frame.cameraPos, frame.viewMatrix);
        if (!isFinite(anchor)) {
            LOGGER.warn("[RenderHead] captured anchor not finite for uuid={} — falling back", player.getUuid());
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

    static boolean shouldUseRenderCapture(boolean localPlayer, boolean firstPerson) {
        return !localPlayer || !firstPerson;
    }

    private record FrameContext(Vec3d cameraPos, Matrix4f viewMatrix) {
    }
}
