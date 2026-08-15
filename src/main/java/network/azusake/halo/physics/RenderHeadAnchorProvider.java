package network.azusake.halo.physics;

import network.azusake.halo.api.EntityAnchorProvider;
import network.azusake.halo.api.HeadAnchor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

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

    private final EntityAnchorProvider fallback;

    public RenderHeadAnchorProvider(EntityAnchorProvider fallback) {
        this.fallback = fallback;
    }

    @Override
    public HeadAnchor resolve(LivingEntity entity, float tickDelta) {
        if (entity instanceof AbstractClientPlayerEntity player) {
            RenderHeadCapture.CapturedHead captured = RenderHeadCapture.get(player.getUuid());
            if (captured != null) {
                MinecraftClient client = MinecraftClient.getInstance();
                Camera camera = client != null && client.gameRenderer != null
                    ? client.gameRenderer.getCamera()
                    : null;
                if (camera != null) {
                    Vec3d cameraPos = camera.getPos();
                    Matrix4f viewMatrix = RenderHeadCapture.getViewMatrix();
                    if (viewMatrix != null) {
                        return RenderHeadMath.toHeadAnchor(captured, cameraPos, viewMatrix);
                    }
                }
            }
        }
        return fallback.resolve(entity, tickDelta);
    }
}
