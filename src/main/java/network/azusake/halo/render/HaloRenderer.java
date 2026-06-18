package network.azusake.halo.render;

import network.azusake.halo.HaloMod;
import network.azusake.halo.data.HaloDefinition;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.physics.HaloPose;
import network.azusake.halo.physics.HaloPoseCalculator;
import network.azusake.halo.shape.BillboardShape;
import network.azusake.halo.shape.GlowLayer;
import network.azusake.halo.shape.MultiBillboardShape;
import network.azusake.halo.shape.PulseConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Renders halo billboards on entity heads.
 *
 * <p>All pose computation (entity interpolation, head-relative offset,
 * frame-rate-independent damping, rotation) is delegated to
 * {@link HaloPoseCalculator}.  This class is a <em>pure rendering
 * consumer</em> — it receives a {@link HaloPose} and draws the
 * billboard quads at the specified world-space placement.</p>
 *
 * <p>In the future, when the halo definition supports per-layer
 * spatial offsets and orientations, the renderer will iterate each
 * layer and apply its local transform relative to the pose origin,
 * enabling true multi-layer halos with spatial relationships.</p>
 */
public final class HaloRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(HaloMod.MOD_ID);

    private static final HaloRenderer INSTANCE = new HaloRenderer();

    public static final boolean DEBUG_RENDERING = false;

    private final HaloPoseCalculator poseCalculator = HaloPoseCalculator.getInstance();

    /** Timestamp (nanoTime) of the previous render frame, for delta-time. */
    private long prevFrameNanos;
    /** Whether we have seen at least one frame. */
    private boolean firstFrame = true;

    private HaloRenderer() { /* singleton */ }

    public static HaloRenderer getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // Public entry point
    // ------------------------------------------------------------------

    /**
     * Render every visible halo for the current frame.
     *
     * @param matrices  the current model-view-projection matrix stack
     * @param camera    the player's camera
     * @param tickDelta partial-tick progress (0.0–1.0) for interpolation
     */
    public void renderHalos(MatrixStack matrices, Camera camera, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        var visible = HaloClientManager.getInstance().getVisibleHalos(camera);

        // Clean stale entries from the pose calculator's internal maps
        Set<UUID> activeUuids = visible.stream()
            .map(HaloInstance::getEntityUuid)
            .collect(Collectors.toSet());
        poseCalculator.retainOnly(activeUuids);

        // Compute frame delta once (shared by all halos this frame)
        long frameNanos = System.nanoTime();
        double dt;
        if (firstFrame) {
            dt = 0.0;
            firstFrame = false;
        } else {
            dt = (frameNanos - prevFrameNanos) / 1_000_000_000.0;
            dt = Math.max(0.001, Math.min(dt, 0.1)); // 1ms–100ms
        }
        prevFrameNanos = frameNanos;

        for (HaloInstance instance : visible) {
            try {
                renderSingleHalo(instance, matrices, camera, tickDelta, client, dt);
            } catch (Exception e) {
                LOG.warn("[HaloRenderer] error rendering halo for entity {}: {}",
                    instance.getEntityUuid(), e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // Single halo
    // ------------------------------------------------------------------

    /**
     * Compute the halo's world-space pose via {@link HaloPoseCalculator},
     * then render its billboard shape at that pose.
     *
     * @return {@code true} if the halo was actually drawn
     */
    private boolean renderSingleHalo(HaloInstance instance, MatrixStack matrices,
                                      Camera camera, float tickDelta, MinecraftClient client,
                                      double dt) {
        // ---- resolve entity ----
        LivingEntity entity = findEntityByUuid(client, instance.getEntityUuid());
        if (entity == null) {
            LOG.debug("[HaloRenderer] entity not found: uuid={}", instance.getEntityUuid());
            return false;
        }
        if (!entity.isAlive()) {
            return false;
        }

        // ---- resolve definition ----
        HaloDefinition def = HaloJsonLoader.getDefinition(instance.getDefinitionId()).orElse(null);
        if (def == null) {
            LOG.warn("[HaloRenderer] definition not found: id={}", instance.getDefinitionId());
            return false;
        }

        // ---- compute pose (all position/orientation math is delegated) ----
        HaloPose pose = poseCalculator.calculate(instance, entity, def, camera, tickDelta, dt);

        // ---- camera-relative position (pre-computed by calculator) ----
        Vec3d crp = pose.cameraRelativePos();

        // Quick distance check — skip if absurdly far (crash guard)
        if (Math.abs(crp.x) > 1000 || Math.abs(crp.y) > 1000 || Math.abs(crp.z) > 1000) {
            LOG.debug("[HaloRenderer] halo too far from camera: rel=({})", crp);
            return false;
        }

        // ---- render shape at computed pose ----
        matrices.push();
        try {
            // Translate to halo centre (definition origin → world space)
            matrices.translate(crp.x, crp.y, crp.z);

            // Apply damped rotation (converges toward identity)
            applyQuaternionRotation(matrices, pose.orientation());

            // Uniform scale — runtime config can override definition
            matrices.scale(pose.scale(), pose.scale(), pose.scale());

            // Render shape — toHead direction from pose
            if (def.shape() instanceof BillboardShape billboard) {
                renderBillboard(billboard, matrices, camera, pose.toHeadDirection());
            } else if (def.shape() instanceof MultiBillboardShape multi) {
                for (BillboardShape layer : multi.layers()) {
                    renderBillboard(layer, matrices, camera, pose.toHeadDirection());
                }
            }
        } finally {
            matrices.pop();
        }

        return true;
    }

    // ------------------------------------------------------------------
    // Billboard quad
    // ------------------------------------------------------------------

    /**
     * Draw a single billboard quad whose +Z normal points toward {@code toHead}.
     */
    private void renderBillboard(BillboardShape billboard, MatrixStack matrices,
                                  Camera camera, Vec3d toHead) {
        matrices.push();
        try {
            // ---- face billboard normal toward head ----
            // Compute a rotation that aligns local +Z (the quad normal) with toHead.
            Vec3d localZ = new Vec3d(0, 0, 1);
            Vec3d axis = localZ.crossProduct(toHead).normalize();
            double dot = Math.max(-1.0, Math.min(1.0, localZ.dotProduct(toHead)));
            float angle = (float) Math.acos(dot);

            if (angle > 0.001f && axis.length() > 0.001f) {
                // Apply rotation around the computed axis
                matrices.multiply(new Quaternionf()
                    .rotateAxis(angle, (float) axis.x, (float) axis.y, (float) axis.z));
            } else if (dot < -0.999f) {
                // localZ and toHead are opposite — flip 180° around X
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0f));
            }

            float hw = billboard.size().x / 2.0f;
            float hh = billboard.size().y / 2.0f;

            if (DEBUG_RENDERING) {
                hw *= 5.0f;
                hh *= 5.0f;
            }

            Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder builder = tessellator.getBuffer();

            boolean hasTexture = bindTextureSafe(billboard.texture());

            // Billboards are translucent quads — disable face culling so
            // they are visible from any angle (same approach as vanilla particles).
            RenderSystem.disableCull();

            if (DEBUG_RENDERING) {
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
            } else {
                // Ensure halos participate in normal depth testing so they are
                // properly occluded by walls, blocks, and other entities instead
                // of rendering on top of everything.
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
            }

            if (hasTexture) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShader(GameRenderer::getPositionTexProgram);
                builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
                builder.vertex(positionMatrix, -hw, -hh, 0.0f).texture(0.0f, 1.0f).next();
                builder.vertex(positionMatrix,  hw, -hh, 0.0f).texture(1.0f, 1.0f).next();
                builder.vertex(positionMatrix,  hw,  hh, 0.0f).texture(1.0f, 0.0f).next();
                builder.vertex(positionMatrix, -hw,  hh, 0.0f).texture(0.0f, 0.0f).next();
            } else {
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                if (DEBUG_RENDERING) {
                    RenderSystem.disableBlend();
                } else {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                }
                builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                float fr = DEBUG_RENDERING ? 1.0f : 1.0f; // white fallback in normal mode
                float fg = DEBUG_RENDERING ? 0.0f : 1.0f;
                float fb = DEBUG_RENDERING ? 1.0f : 1.0f;
                builder.vertex(positionMatrix, -hw, -hh, 0.0f).color(fr, fg, fb, 1.0f).next();
                builder.vertex(positionMatrix,  hw, -hh, 0.0f).color(fr, fg, fb, 1.0f).next();
                builder.vertex(positionMatrix,  hw,  hh, 0.0f).color(fr, fg, fb, 1.0f).next();
                builder.vertex(positionMatrix, -hw,  hh, 0.0f).color(fr, fg, fb, 1.0f).next();
            }

            tessellator.draw();

            // Restore face culling (Minecraft's default is enabled)
            RenderSystem.enableCull();

            if (DEBUG_RENDERING) {
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
            }
            RenderSystem.disableBlend();
        } finally {
            matrices.pop();
        }

        // ---- glow layer ----
        if (billboard.glow() != null) {
            renderGlowLayer(billboard.glow(), matrices, camera, toHead);
        }
    }

    // ------------------------------------------------------------------
    // Glow layer (additive)
    // ------------------------------------------------------------------

    private void renderGlowLayer(GlowLayer glow, MatrixStack matrices,
                                  Camera camera, Vec3d toHead) {
        matrices.push();
        try {
            // Same billboard rotation as the base quad
            Vec3d localZg = new Vec3d(0, 0, 1);
            Vec3d axisG = localZg.crossProduct(toHead).normalize();
            double dotG = Math.max(-1.0, Math.min(1.0, localZg.dotProduct(toHead)));
            float angleG = (float) Math.acos(dotG);

            if (angleG > 0.001f && axisG.length() > 0.001f) {
                matrices.multiply(new Quaternionf()
                    .rotateAxis(angleG, (float) axisG.x, (float) axisG.y, (float) axisG.z));
            } else if (dotG < -0.999f) {
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0f));
            }

            Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

            float ghw = glow.size().x / 2.0f;
            float ghh = glow.size().y / 2.0f;

            if (DEBUG_RENDERING) {
                ghw *= 5.0f;
                ghh *= 5.0f;
            }

            // Unpack colour
            int color = glow.color();
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            // Pulsed alpha
            float alpha = glow.alpha();
            PulseConfig pulse = glow.pulse();
            if (pulse != null) {
                double t = (System.currentTimeMillis() / 1000.0);
                alpha += pulse.amplitude()
                    * (float) Math.sin(2.0 * Math.PI * pulse.frequency() * t + pulse.phase());
                alpha = Math.max(0.0f, Math.min(1.0f, alpha));
            }

            if (DEBUG_RENDERING) {
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
            } else {
                // Glow must be occluded by walls, but additive blending means
                // we must NOT write depth — otherwise the glow quad would block
                // other translucent geometry rendered behind it.
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(false);
            }

            // Additive blending
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE);
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);

            if (glow.texture() != null) {
                bindTextureSafe(glow.texture());
            }

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder builder = tessellator.getBuffer();
            builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            // Slight Z offset to prevent z-fighting with base billboard
            float z = DEBUG_RENDERING ? 0.0f : -0.01f;
            builder.vertex(positionMatrix, -ghw, -ghh, z).color(r, g, b, alpha).next();
            builder.vertex(positionMatrix,  ghw, -ghh, z).color(r, g, b, alpha).next();
            builder.vertex(positionMatrix,  ghw,  ghh, z).color(r, g, b, alpha).next();
            builder.vertex(positionMatrix, -ghw,  ghh, z).color(r, g, b, alpha).next();

            tessellator.draw();

            // Restore depth state
            if (DEBUG_RENDERING) {
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
            } else {
                // Restore depthMask to true (was set false for additive glow)
                RenderSystem.depthMask(true);
            }
            RenderSystem.disableBlend();
        } finally {
            matrices.pop();
        }
    }

    // ------------------------------------------------------------------
    // Rotation helpers
    // ------------------------------------------------------------------

    /**
     * Apply a {@link Quaternionf} to the current matrix-stack entry.
     */
    static void applyQuaternionRotation(MatrixStack matrices, Quaternionf quat) {
        Matrix4f rotMatrix = new Matrix4f().rotation(quat);
        matrices.peek().getPositionMatrix().mul(rotMatrix);
    }

    // ------------------------------------------------------------------
    // Entity helpers
    // ------------------------------------------------------------------

    /**
     * Find a living entity by UUID in the client world.
     */
    static LivingEntity findEntityByUuid(MinecraftClient client, UUID uuid) {
        if (client.world == null) {
            return null;
        }
        for (net.minecraft.entity.Entity e : client.world.getEntities()) {
            if (e.getUuid().equals(uuid) && e instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Texture binding
    // ------------------------------------------------------------------

    /**
     * Bind a texture by resource identifier.
     *
     * @return {@code true} if the texture was bound successfully,
     *         {@code false} if it fell back to the missing-texture sprite
     */
    private static boolean bindTextureSafe(Identifier textureId) {
        try {
            net.minecraft.client.texture.AbstractTexture tex =
                MinecraftClient.getInstance().getTextureManager().getTexture(textureId);
            RenderSystem.setShaderTexture(0, tex.getGlId());
            return true;
        } catch (Exception e) {
            LOG.debug("[HaloRenderer] texture not found: {} ({})", textureId, e.getMessage());
            RenderSystem.setShaderTexture(0, 0);
            return false;
        }
    }
}
