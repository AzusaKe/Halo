package network.azusake.halo.render;

import network.azusake.halo.HaloMod;
import network.azusake.halo.data.HaloDefinition;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.physics.AnchorFrame;
import network.azusake.halo.physics.AnchorFrameCalculator;
import network.azusake.halo.shape.BillboardPrimitive;
import network.azusake.halo.shape.GlowLayer;
import network.azusake.halo.shape.HaloLayer;
import network.azusake.halo.shape.PulseConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Renders halo models at their computed world-space {@link AnchorFrame}.
 *
 * <p>All pose computation is delegated to {@link AnchorFrameCalculator}.
 * This class is a <em>pure rendering consumer</em> — it receives an
 * {@link AnchorFrame} and draws each layer of the {@code HaloModel}
 * with its own local transform relative to the anchor frame origin.</p>
 *
 * <h3>Billboard convention</h3>
 * <p>Billboard primitives are drawn on the <b>XZ plane</b> (horizontal,
 * normal = -Y in definition-local space).  The layer's accumulated
 * transform (anchor frame + layer local) determines the final world-space
 * orientation — no separate billboard-facing rotation is applied.</p>
 */
public final class HaloRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(HaloMod.MOD_ID);

    private static final HaloRenderer INSTANCE = new HaloRenderer();

    public static final boolean DEBUG_RENDERING = false;

    private final AnchorFrameCalculator frameCalculator = AnchorFrameCalculator.getInstance();

    /** Timestamp (nanoTime) of the previous render frame, for delta-time. */
    private long prevFrameNanos;
    /** Whether we have seen at least one frame. */
    private boolean firstFrame = true;
    /** EMA-smoothed frame delta-time, to suppress nanoTime jitter. */
    private double smoothedDt = -1;

    private HaloRenderer() { /* singleton */ }

    public static HaloRenderer getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // Public entry point
    // ------------------------------------------------------------------

    /**
     * Render every visible halo for the current frame.
     */
    public void renderHalos(MatrixStack matrices, Camera camera, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        var visible = HaloClientManager.getInstance().getVisibleHalos(camera);

        // Clean stale entries from the frame calculator's internal maps
        Set<UUID> activeUuids = visible.stream()
            .map(HaloInstance::getEntityUuid)
            .collect(Collectors.toSet());
        frameCalculator.retainOnly(activeUuids);

        // Compute frame delta with EMA smoothing to suppress nanoTime jitter
        long frameNanos = System.nanoTime();
        double rawDt;
        if (firstFrame) {
            rawDt = 0.0;
            firstFrame = false;
        } else {
            rawDt = (frameNanos - prevFrameNanos) / 1_000_000_000.0;
            rawDt = Math.max(0.001, Math.min(rawDt, 0.1));
        }
        prevFrameNanos = frameNanos;

        // EMA smoothing: blend raw frame-time into a rolling average
        if (smoothedDt < 0) {
            smoothedDt = rawDt;
        } else {
            // Weight new frame at 20% — smooth but responsive
            smoothedDt = smoothedDt * 0.8 + rawDt * 0.2;
        }
        final double dt = smoothedDt;

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

    private boolean renderSingleHalo(HaloInstance instance, MatrixStack matrices,
                                      Camera camera, float tickDelta, MinecraftClient client,
                                      double dt) {
        // ---- resolve entity ----
        LivingEntity entity = findEntityByUuid(client, instance.getEntityUuid());
        if (entity == null || !entity.isAlive()) {
            return false;
        }

        // ---- resolve definition ----
        HaloDefinition def = HaloJsonLoader.getDefinition(instance.getDefinitionId()).orElse(null);
        if (def == null) {
            LOG.warn("[HaloRenderer] definition not found: id={}", instance.getDefinitionId());
            return false;
        }

        // ---- compute anchor frame ----
        AnchorFrame frame = frameCalculator.calculate(instance, entity, def, camera, tickDelta, dt);

        // ---- camera-relative position ----
        Vec3d crp = frame.cameraRelativePos();
        if (Math.abs(crp.x) > 1000 || Math.abs(crp.y) > 1000 || Math.abs(crp.z) > 1000) {
            return false;
        }

        // ---- compute light at halo position for non-glowing layers ----
        BlockPos lightPos = BlockPos.ofFloored(frame.worldPosition());
        int blockLight = client.world.getLightingProvider().get(LightType.BLOCK).getLightLevel(lightPos);
        int skyLight = client.world.getLightingProvider().get(LightType.SKY).getLightLevel(lightPos);
        // Brightness multiplier: use the stronger of block/sky light, with a floor so
        // the layer never vanishes completely.  This approximates the lightmap without
        // depending on its texture unit being bound — works with both vanilla and Iris.
        float brightness = Math.max(blockLight, skyLight) / 15.0f;
        brightness = Math.max(brightness, 0.04f);

        // ---- render model layers ----
        var model = def.model();
        if (model.layers().isEmpty()) {
            return true; // empty model, nothing to draw
        }

        matrices.push();
        try {
            // Step 1: Anchor frame → world
            matrices.translate(crp.x, crp.y, crp.z);
            applyQuaternionRotation(matrices, frame.worldOrientation());
            matrices.scale(frame.scale(), frame.scale(), frame.scale());

            // Step 2: Per-layer local transform + primitive draw
            for (HaloLayer layer : model.layers()) {
                matrices.push();
                try {
                    matrices.translate(layer.position().x, layer.position().y, layer.position().z);
                    applyQuaternionRotation(matrices, layer.rotation());
                    matrices.scale(layer.scale(), layer.scale(), layer.scale());

                    if (layer.primitive() instanceof BillboardPrimitive bp) {
                        renderBillboard(bp, matrices, layer.glowing(), brightness);
                    }
                } finally {
                    matrices.pop();
                }
            }
        } finally {
            matrices.pop();
        }

        return true;
    }

    // ------------------------------------------------------------------
    // Billboard primitive (XZ plane, normal = -Y)
    // ------------------------------------------------------------------

    /**
     * Draw a billboard quad on the XZ plane (horizontal, normal = -Y).
     * The layer's accumulated matrix-stack transform provides the
     * world-space placement — no per-quad facing rotation is applied.
     */
    private void renderBillboard(BillboardPrimitive billboard, MatrixStack matrices, boolean glowing, float brightness) {
        float hw = billboard.size().x / 2.0f;  // half-width (X)
        float hd = billboard.size().y / 2.0f;  // half-depth (Z) — size.y maps to Z axis

        if (DEBUG_RENDERING) {
            hw *= 5.0f;
            hd *= 5.0f;
        }

        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.getBuffer();

        boolean hasTexture = bindTextureSafe(billboard.texture());

        // Billboard quads are translucent — disable face culling
        RenderSystem.disableCull();

        if (DEBUG_RENDERING) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
        }

        if (hasTexture) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            // XZ plane at Y=0, normal = -Y (faces downward toward entity head).
            // Vertex winding from BELOW (-Y) is CCW → front face faces -Y:
            // (-hw, 0, -hd)  →  (+hw, 0, -hd)  →  (+hw, 0, +hd)  →  (-hw, 0, +hd)
            if (glowing) {
                // Original fullbright path — unchanged from before glowing toggle was added.
                // Always renders at max brightness regardless of shader packs.
                RenderSystem.setShader(GameRenderer::getPositionTexProgram);
                builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
                builder.vertex(positionMatrix, -hw, 0.0f, -hd).texture(0.0f, 1.0f).next();
                builder.vertex(positionMatrix,  hw, 0.0f, -hd).texture(1.0f, 1.0f).next();
                builder.vertex(positionMatrix,  hw, 0.0f, +hd).texture(1.0f, 0.0f).next();
                builder.vertex(positionMatrix, -hw, 0.0f, +hd).texture(0.0f, 0.0f).next();
            } else {
                // Light-responsive: tint texture by ambient light level at halo position
                RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
                builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                builder.vertex(positionMatrix, -hw, 0.0f, -hd).texture(0.0f, 1.0f).color(brightness, brightness, brightness, 1f).next();
                builder.vertex(positionMatrix,  hw, 0.0f, -hd).texture(1.0f, 1.0f).color(brightness, brightness, brightness, 1f).next();
                builder.vertex(positionMatrix,  hw, 0.0f, +hd).texture(1.0f, 0.0f).color(brightness, brightness, brightness, 1f).next();
                builder.vertex(positionMatrix, -hw, 0.0f, +hd).texture(0.0f, 0.0f).color(brightness, brightness, brightness, 1f).next();
            }
        } else {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            if (DEBUG_RENDERING) {
                RenderSystem.disableBlend();
            } else {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }
            builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            float r, g, b;
            if (glowing) {
                r = 1f; g = 1f; b = 1f;
            } else {
                r = brightness; g = brightness; b = brightness;
            }
            builder.vertex(positionMatrix, -hw, 0.0f, -hd).color(r, g, b, 1.0f).next();
            builder.vertex(positionMatrix,  hw, 0.0f, -hd).color(r, g, b, 1.0f).next();
            builder.vertex(positionMatrix,  hw, 0.0f, +hd).color(r, g, b, 1.0f).next();
            builder.vertex(positionMatrix, -hw, 0.0f, +hd).color(r, g, b, 1.0f).next();
        }

        tessellator.draw();

        RenderSystem.enableCull();

        if (DEBUG_RENDERING) {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
        RenderSystem.disableBlend();

        // ---- glow layer (also XZ plane) ----
        if (glowing && billboard.glow() != null) {
            renderGlowLayer(billboard.glow(), matrices);
        }
    }

    // ------------------------------------------------------------------
    // Glow layer (additive, XZ plane)
    // ------------------------------------------------------------------

    private void renderGlowLayer(GlowLayer glow, MatrixStack matrices) {
        float ghw = glow.size().x / 2.0f;
        float ghd = glow.size().y / 2.0f;

        if (DEBUG_RENDERING) {
            ghw *= 5.0f;
            ghd *= 5.0f;
        }

        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

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

        // Slight Y offset to prevent z-fighting with base billboard
        float y = DEBUG_RENDERING ? 0.0f : -0.01f;
        builder.vertex(positionMatrix, -ghw, y, -ghd).color(r, g, b, alpha).next();
        builder.vertex(positionMatrix,  ghw, y, -ghd).color(r, g, b, alpha).next();
        builder.vertex(positionMatrix,  ghw, y, +ghd).color(r, g, b, alpha).next();
        builder.vertex(positionMatrix, -ghw, y, +ghd).color(r, g, b, alpha).next();

        tessellator.draw();

        if (DEBUG_RENDERING) {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.depthMask(true);
        }
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------
    // Rotation helpers
    // ------------------------------------------------------------------

    static void applyQuaternionRotation(MatrixStack matrices, Quaternionf quat) {
        Matrix4f rotMatrix = new Matrix4f().rotation(quat);
        matrices.peek().getPositionMatrix().mul(rotMatrix);
    }

    // ------------------------------------------------------------------
    // Entity helpers
    // ------------------------------------------------------------------

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
