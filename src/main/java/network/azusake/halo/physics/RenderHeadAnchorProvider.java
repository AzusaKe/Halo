package network.azusake.halo.physics;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import network.azusake.halo.api.EntityAnchorProvider;
import network.azusake.halo.api.HeadAnchor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default player {@link EntityAnchorProvider} that anchors the halo to the
 * player's <em>actually rendered</em> head (captured via mixin each frame).
 *
 * <p>Because the data comes from the render result itself (pose adaptation is
 * already baked in by {@code setAngles}), no per-pose configuration is needed
 * while a valid main-camera capture exists.  Minecraft 26.2 draws the base
 * player model after Halo's submission point, so third-person rendering uses
 * the immediately preceding frame's main-pass capture.  Iris shadow captures
 * are rejected by {@link RenderHeadCapture}; the local first-person player
 * always delegates to {@link PlayerAnchorProvider}, whose camera anchor is the
 * authoritative rendered head in that view.</p>
 */
public final class RenderHeadAnchorProvider implements EntityAnchorProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger("halo");
    private static final long DIAGNOSTIC_INTERVAL_NANOS = 1_000_000_000L;
    private static final Map<UUID, Long> LAST_DIAGNOSTIC_LOG_NANOS = new ConcurrentHashMap<>();

    private final EntityAnchorProvider fallback;

    public RenderHeadAnchorProvider(EntityAnchorProvider fallback) {
        this.fallback = fallback;
    }

    @Override
    public HeadAnchor resolve(LivingEntity entity, float tickDelta) {
        RenderHeadCapture.DiagnosticSnapshot diagnostic = null;
        String path = "fallback:not-client-player";
        HeadAnchor resolved = null;
        if (entity instanceof AbstractClientPlayer player) {
            diagnostic = RenderHeadCapture.noteLookup(player.getId(), player.getUUID());
            Minecraft client = Minecraft.getInstance();
            boolean localPlayer = client != null && player == client.player;
            boolean firstPerson = client != null && client.options.getCameraType().isFirstPerson();
            if (shouldUseRenderCapture(localPlayer, firstPerson)) {
                HeadAnchor anchor = RenderHeadCapture.resolveMainPassAnchor(player, tickDelta);
                if (anchor != null && isFinite(anchor)) {
                    resolved = anchor;
                    path = "render-head:main-cache";
                } else if (anchor != null) {
                    path = "fallback:non-finite-main-cache";
                    LOGGER.warn("[RenderHead] cached main-pass anchor not finite for uuid={} "
                            + "center=({}, {}, {}) yaw={} pitch={} roll={} — falling back",
                        player.getUUID(),
                        anchor.headCenter().x, anchor.headCenter().y, anchor.headCenter().z,
                        anchor.yaw(), anchor.pitch(), anchor.roll());
                } else {
                    path = "fallback:no-main-capture";
                }
            } else {
                path = "fallback:first-person-camera";
            }
        }
        if (resolved == null) {
            resolved = fallback.resolve(entity, tickDelta);
        }
        logDiagnostic(entity, diagnostic, resolved, path);
        return resolved;
    }

    private static void logDiagnostic(LivingEntity entity,
                                      RenderHeadCapture.DiagnosticSnapshot diagnostic,
                                      HeadAnchor anchor, String path) {
        Minecraft client = Minecraft.getInstance();
        if (diagnostic == null || client == null || entity != client.player
                || !shouldLog(entity.getUUID())) {
            return;
        }
        Camera camera = client.gameRenderer != null ? client.gameRenderer.mainCamera() : null;
        Vec3 cameraPos = camera != null ? camera.position() : Vec3.ZERO;
        Vec3 relative = anchor.headCenter().subtract(cameraPos);
        LOGGER.info("[HaloAnchorDiag] stage=lookup frame={} path={} cameraType={} entityId={} uuid={} "
                + "lookupOrdinal={} idKnown={} uuidMatch={} registered={} beganDraw={} captured={} "
                + "registeredCount={} captureCount={} selectedCaptureOrdinal={} selectedCaptureTiming={} "
                + "selectedModel={} selectedIrisShadowPass={} currentCaptureAcceptedMainPass={} "
                + "mainCacheFrame={} mainCacheAge={} mainCacheUsable={} "
                + "camera=({},{},{}) cameraYawPitch=({},{}) entity=({},{},{}) entityYawPitch=({},{}) "
                + "anchor=({},{},{}) anchorYawPitchRoll=({},{},{}) anchorMinusCamera=({},{},{})",
            diagnostic.frameId(), path, client.options.getCameraType(), entity.getId(), entity.getUUID(),
            diagnostic.lookupOrdinal(),
            diagnostic.entityIdKnown(), diagnostic.uuidMatchesEntityId(),
            diagnostic.registeredThisFrame(), diagnostic.beganDrawThisFrame(), diagnostic.capturedThisFrame(),
            diagnostic.registeredPlayers(), diagnostic.capturedPlayers(),
            diagnostic.selectedCaptureOrdinal(), diagnostic.selectedCaptureTiming(),
            diagnostic.selectedModelIdentity(), diagnostic.selectedShadowPass(),
            diagnostic.currentCaptureAcceptedMainPass(),
            diagnostic.mainCacheFrame(), diagnostic.mainCacheAge(), diagnostic.mainCacheUsable(),
            cameraPos.x, cameraPos.y, cameraPos.z,
            camera != null ? camera.yRot() : Float.NaN, camera != null ? camera.xRot() : Float.NaN,
            entity.getX(), entity.getY(), entity.getZ(), entity.yHeadRot, entity.getXRot(),
            anchor.headCenter().x, anchor.headCenter().y, anchor.headCenter().z,
            anchor.yaw(), anchor.pitch(), anchor.roll(), relative.x, relative.y, relative.z);
    }

    private static boolean shouldLog(UUID uuid) {
        long now = System.nanoTime();
        Long previous = LAST_DIAGNOSTIC_LOG_NANOS.get(uuid);
        if (previous != null && now - previous < DIAGNOSTIC_INTERVAL_NANOS) {
            return false;
        }
        LAST_DIAGNOSTIC_LOG_NANOS.put(uuid, now);
        return true;
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
}
