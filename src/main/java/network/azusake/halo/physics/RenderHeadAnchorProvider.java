package network.azusake.halo.physics;

import network.azusake.halo.compat.emf.EmfHeadCapture;
import network.azusake.halo.compat.emf.EmfHeadMath;
import network.azusake.halo.compat.ysm.YsmHeadCapture;
import network.azusake.halo.compat.ysm.YsmHeadMath;
import network.azusake.halo.api.EntityAnchorProvider;
import network.azusake.halo.api.HeadAnchor;
import network.azusake.halo.config.HaloModConfigStore;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
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
        if (entity instanceof AbstractClientPlayer player) {
            boolean ysmEnabled = HaloModConfigStore.get().isExperimentalYsmAnchorEnabled();
            if (ysmEnabled) {
                YsmHeadCapture.markProviderInvoked(true);
            }
            Minecraft client = Minecraft.getInstance();
            boolean localPlayer = client != null && player == client.player;
            boolean firstPerson = client != null && client.options.getCameraType().isFirstPerson();
            if (!shouldUseRenderCapture(localPlayer, firstPerson)) {
                // Iris may render the local player's YSM body in shadow or
                // auxiliary passes even though first-person is not a stable
                // main-camera body render.  The camera is authoritative here.
                YsmHeadCapture.discard(player.getUUID());
                EmfHeadCapture.discard(player.getUUID());
                return fallback.resolve(entity, tickDelta);
            }
            if (ysmEnabled && !YsmHeadCapture.isVisibleToMainCamera(player)) {
                YsmHeadCapture.markProviderOutsideFrustum(true);
                YsmHeadCapture.discard(player.getUUID());
                EmfHeadCapture.discard(player.getUUID());
                if (ysmEnabled) {
                    YsmHeadCapture.markLocalFirstPersonFallback();
                }
                return fallback.resolve(entity, tickDelta);
            }

            FrameContext frame = frameContext();
            HeadAnchor ysmCurrent = resolveYsm(YsmHeadCapture.getCurrent(player.getUUID()));
            if (isFinite(ysmCurrent)) {
                YsmHeadCapture.markAnchorConsumed(false);
                return ysmCurrent;
            }

            HeadAnchor emfCurrent = resolveEmf(EmfHeadCapture.getCurrent(player.getUUID()), player);
            if (isFinite(emfCurrent)) {
                return emfCurrent;
            }

            if (frame != null) {
                HeadAnchor vanilla = resolveVanilla(RenderHeadCapture.get(player.getUUID()), frame, player);
                if (isFinite(vanilla)) {
                    return vanilla;
                }
            }

            HeadAnchor ysmPrevious = resolveYsm(YsmHeadCapture.getPrevious(player.getUUID()));
            if (isFinite(ysmPrevious)) {
                YsmHeadCapture.markAnchorConsumed(true);
                return ysmPrevious;
            }

            HeadAnchor emfPrevious = resolveEmf(EmfHeadCapture.getPrevious(player.getUUID()), player);
            if (isFinite(emfPrevious)) {
                return emfPrevious;
            }
            if (ysmEnabled) {
                YsmHeadCapture.markProviderMiss(true);
            }
        }
        return fallback.resolve(entity, tickDelta);
    }

    private static HeadAnchor resolveEmf(
        EmfHeadCapture.CapturedHead captured,
        AbstractClientPlayer player
    ) {
        if (captured == null) {
            return null;
        }
        HeadAnchor anchor = EmfHeadMath.toHeadAnchor(captured);
        if (!isFinite(anchor)) {
            LOGGER.warn("[EMF Compat] captured anchor not finite for uuid={} — falling back", player.getUUID());
            return null;
        }
        return anchor;
    }

    private static FrameContext frameContext() {
        Minecraft client = Minecraft.getInstance();
        Camera camera = client != null && client.gameRenderer != null
            ? client.gameRenderer.getMainCamera()
            : null;
        Matrix4f viewMatrix = RenderHeadCapture.getViewMatrix();
        return camera != null && viewMatrix != null
            ? new FrameContext(camera.getPosition(), viewMatrix)
            : null;
    }

    private static HeadAnchor resolveYsm(YsmHeadCapture.CapturedHead captured) {
        if (captured == null) {
            return null;
        }
        double[] rawOffset = HaloModConfigStore.get().getExperimentalYsmHeadLocalOffset();
        Vec3 localOffset = new Vec3(rawOffset[0], rawOffset[1], rawOffset[2]);
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
        AbstractClientPlayer player
    ) {
        if (captured == null) {
            return null;
        }
        HeadAnchor anchor = RenderHeadMath.toHeadAnchor(captured, frame.cameraPos, frame.viewMatrix);
        if (!isFinite(anchor)) {
            LOGGER.warn("[RenderHead] captured anchor not finite for uuid={} — falling back", player.getUUID());
            return null;
        }
        return anchor;
    }

    private static boolean isFinite(HeadAnchor anchor) {
        if (anchor == null) {
            return false;
        }
        Vec3 center = anchor.headCenter();
        return Double.isFinite(center.x) && Double.isFinite(center.y) && Double.isFinite(center.z)
            && Float.isFinite(anchor.yaw()) && Float.isFinite(anchor.pitch()) && Float.isFinite(anchor.roll());
    }

    static boolean shouldUseRenderCapture(boolean localPlayer, boolean firstPerson) {
        return !localPlayer || !firstPerson;
    }

    private record FrameContext(Vec3 cameraPos, Matrix4f viewMatrix) {
    }
}
