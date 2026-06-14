package com.example.halo.render;

import com.example.halo.HaloMod;
import com.example.halo.data.HaloDefinition;
import com.example.halo.data.HaloInstance;
import com.example.halo.json.HaloJsonLoader;
import com.example.halo.shape.BillboardShape;
import com.example.halo.shape.GlowLayer;
import com.example.halo.shape.MultiBillboardShape;
import com.example.halo.shape.PulseConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Renders halo billboards on entity heads using OpenGL.
 *
 * <p>Each halo is drawn as a camera-facing textured quad (billboard) with an
 * optional additive glow layer.  Positions and rotations are frame-interpolated
 * via {@link HaloInstance#getInterpolatedPosition(double)} and
 * {@link HaloInstance#getInterpolatedRotation(double)} for smooth motion
 * independent of frame rate.</p>
 */
public final class HaloRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(HaloMod.MOD_ID);

    private static final HaloRenderer INSTANCE = new HaloRenderer();

    // ---- debug: throttle log output ----
    private int frameCount;
    private int renderedCount;
    private long lastLogTime;
    private boolean firstRenderLogged;
    private static final long LOG_INTERVAL_MS = 3000; // log summary every 3 s

    /**
     * Toggle to enable debug rendering overrides.
     * When {@code true}: depth test and face culling are disabled,
     * the quad is drawn 5× larger, and magenta fallback is always used.
     * Set to {@code false} for normal gameplay.
     */
    public static final boolean DEBUG_RENDERING = false;

    private HaloRenderer() {
        // singleton
    }

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

        frameCount++;

        // One-time confirmation on first render frame
        if (!firstRenderLogged) {
            firstRenderLogged = true;
            int totalDefs = HaloJsonLoader.getDefinitions().size();
            int totalActive = com.example.halo.manager.HaloManager.getInstance().getActiveCount();
            LOG.info("[HaloRenderer] FIRST RENDER FRAME — loaded definitions: {}, active halos: {}",
                totalDefs, totalActive);
        }

        var visible = HaloClientManager.getInstance().getVisibleHalos(camera);

        // Periodic debug summary
        long now = System.currentTimeMillis();
        if (now - lastLogTime > LOG_INTERVAL_MS) {
            int total = com.example.halo.manager.HaloManager.getInstance().getActiveCount();
            LOG.info("[HaloRenderer] frame={} totalActive={} visible={} rendered={}",
                frameCount, total, visible.size(), renderedCount);
            lastLogTime = now;
            renderedCount = 0;
        }

        for (HaloInstance instance : visible) {
            try {
                if (renderSingleHalo(instance, matrices, camera, tickDelta, client)) {
                    renderedCount++;
                }
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
     * @return {@code true} if the halo was actually drawn
     */
    private boolean renderSingleHalo(HaloInstance instance, MatrixStack matrices,
                                      Camera camera, float tickDelta, MinecraftClient client) {
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

        // ---- compute world-space halo centre ----
        // Physics stores the absolute world-space halo position
        // (anchor + damped offset + head-relative offset).
        Vec3d haloWorldPos = instance.getInterpolatedPosition(tickDelta);
        Vec3d headAnchor = getHeadAnchorPosition(entity);

        if (haloWorldPos.lengthSquared() < 0.001) {
            // Physics hasn't initialised yet — use head anchor
            haloWorldPos = headAnchor;
        }

        // ---- target = head anchor + head-relative offset ----
        // Compute target so we can clamp to it (not just to head anchor).
        Vec3d headRelOffset = computeHeadRelativeOffset(
            entity, def.positioning().offset());
        Vec3d targetPos = headAnchor.add(headRelOffset);

        // ---- clamp: halo must NEVER be more than maxDist from TARGET ----
        // Physics clamps per-tick, but interpolation between ticks can
        // produce intermediate positions that exceed the limit.
        double distToTarget = haloWorldPos.distanceTo(targetPos);
        double maxDist = def.damping().maxLinearDistance();
        if (distToTarget > maxDist + 0.001) {
            Vec3d dir = haloWorldPos.subtract(targetPos).normalize();
            haloWorldPos = targetPos.add(dir.multiply(maxDist));
        }

        // Normal: from ACTUAL halo position toward head centre
        Vec3d toHead = headAnchor.subtract(haloWorldPos).normalize();

        // ---- camera-relative translation ----
        Vec3d camPos = camera.getPos();
        double rx = haloWorldPos.x - camPos.x;
        double ry = haloWorldPos.y - camPos.y;
        double rz = haloWorldPos.z - camPos.z;

        // Quick distance check — skip if absurdly far (crash guard)
        if (Math.abs(rx) > 1000 || Math.abs(ry) > 1000 || Math.abs(rz) > 1000) {
            LOG.debug("[HaloRenderer] halo too far from camera: rel=({})", new Vec3d(rx, ry, rz));
            return false;
        }

        matrices.push();
        try {
            // Translate to halo centre
            matrices.translate(rx, ry, rz);

            // Apply interpolated rotation
            applyQuaternionRotation(matrices, instance.getInterpolatedRotation(tickDelta));

            // Uniform scale
            float scale = (float) def.positioning().scale();
            matrices.scale(scale, scale, scale);

            // Render shape — toHead direction computed above from actual halo position
            if (def.shape() instanceof BillboardShape billboard) {
                renderBillboard(billboard, matrices, camera, toHead);
            } else if (def.shape() instanceof MultiBillboardShape multi) {
                for (BillboardShape layer : multi.layers()) {
                    renderBillboard(layer, matrices, camera, toHead);
                }
            }
        } finally {
            matrices.pop();
        }

        // Log position occasionally (DEBUG level, once per 120 frames per halo)
        if (frameCount % 120 == 0) {
            LOG.debug("[HaloRenderer] rendered halo for '{}' — world=({}) camRel=({})",
                entity.getName().getString(), haloWorldPos,
                new Vec3d(rx, ry, rz));
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

            if (DEBUG_RENDERING) {
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
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
     * Compute the world-space anchor point for a halo on the given entity.
     */
    static Vec3d getHeadAnchorPosition(LivingEntity entity) {
        if (entity instanceof PlayerEntity player) {
            return player.getEyePos();
        }
        return entity.getPos().add(0.0, entity.getHeight() * 0.85, 0.0);
    }

    /**
     * Convert a definition offset from head-relative space to world space.
     * offset.x = right, offset.y = head-up, offset.z = behind.
     */
    static Vec3d computeHeadRelativeOffset(LivingEntity entity, Vec3d offset) {
        float yawRad = (float) Math.toRadians(entity.getHeadYaw());
        float pitchRad = (float) Math.toRadians(entity.getPitch());

        Vec3d forward = new Vec3d(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        ).normalize();

        Vec3d worldUp = new Vec3d(0, 1, 0);
        Vec3d right;
        if (Math.abs(forward.dotProduct(worldUp)) > 0.999) {
            right = new Vec3d(Math.cos(yawRad), 0, Math.sin(yawRad));
        } else {
            right = worldUp.crossProduct(forward).normalize();
        }
        Vec3d headUp = forward.crossProduct(right).normalize();
        Vec3d behind = forward.multiply(-1);

        return right.multiply(offset.x)
            .add(headUp.multiply(offset.y))
            .add(behind.multiply(offset.z));
    }

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
