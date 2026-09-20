package network.azusake.halo.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Restores caller-owned state after immediate submission, including exceptional exits. */
final class HaloRenderState implements AutoCloseable {
    private final ShaderInstance shader = RenderSystem.getShader();
    private final float[] color = RenderSystem.getShaderColor().clone();
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    private final int arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int elementBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int[] shaderTextures = new int[4];
    private final int[] boundTextures = new int[4];
    private final float[][] genericColors;

    HaloRenderState() {
        this(false);
    }
    HaloRenderState(boolean constantVertexColor) {
        genericColors = constantVertexColor ? new float[][]{new float[4], new float[4]} : null;
        if (genericColors != null) {
            GL20.glGetVertexAttribfv(1, GL20.GL_CURRENT_VERTEX_ATTRIB, genericColors[0]);
            GL20.glGetVertexAttribfv(2, GL20.GL_CURRENT_VERTEX_ATTRIB, genericColors[1]);
        }
        for (int slot = 0; slot < 4; slot++) {
            shaderTextures[slot] = RenderSystem.getShaderTexture(slot);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + slot);
            boundTextures[slot] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        RenderSystem.activeTexture(activeTexture);
        HaloRenderDiagnostics.checkpoint("after-capture");
    }

    @Override public void close() {
        HaloRenderDiagnostics.checkpoint("after-draw");
        if (genericColors != null) {
            GL20.glVertexAttrib4fv(1, genericColors[0]);
            GL20.glVertexAttrib4fv(2, genericColors[1]);
        }
        // Immediate submission caches the last bound VertexBuffer. Restoring a
        // different raw VAO without invalidating that cache lets the next vanilla
        // entity upload skip its bind, potentially drawing with VAO 0 instead.
        BufferUploader.invalidate();
        GlStateManager._glBindVertexArray(vao);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
        // Element bindings belong to the VAO. In a core-profile context, binding
        // even EBO 0 with VAO 0 is GL_INVALID_OPERATION (Sodium enables its logging).
        if (vao != 0) GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
        for (int slot = 0; slot < 4; slot++) {
            RenderSystem.setShaderTexture(slot, shaderTextures[slot]);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + slot);
            GlStateManager._bindTexture(boundTextures[slot]);
        }
        RenderSystem.activeTexture(activeTexture);
        RenderSystem.setShader(() -> shader);
        GlStateManager._glUseProgram(program);
        RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(depthWrite);
    }
}
