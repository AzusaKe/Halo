package network.azusake.halo.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import network.azusake.halo.core.render.DrawBatch;
import network.azusake.halo.core.render.LightSample;
import network.azusake.halo.core.render.MaterialState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static network.azusake.halo.platform.PlatformTypes.game;
import network.azusake.halo.compat.iris.IrisMeshBridge;
import network.azusake.halo.physics.OptionalIrisPassDetector;

/** Version-specific shader adapter. GameRenderer owns registered programs and their reload/disposal. */
public final class HaloMeshShader {
    private static final Logger LOG = LoggerFactory.getLogger("HaloMeshShader");
    private static final network.azusake.halo.core.Identifier WHITE_TEXTURE =
        new network.azusake.halo.core.Identifier("minecraft:textures/misc/white.png");
    private static ShaderProgram flatProgram;
    private static ShaderProgram litProgram;
    private HaloMeshShader() {}

    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            flatProgram = null;
            litProgram = null;
            try {
                context.register(new Identifier("halo", "mesh"), VertexFormats.POSITION_TEXTURE_COLOR, loaded -> {
                    for (String uniform : new String[]{"MaskEnabled", "MaskMode", "MaskThreshold", "MaskOffset",
                            "LightCoord", "LegacyAlphaCutoff"}) {
                        if (loaded.getUniform(uniform) == null) {
                            LOG.error("Halo mesh shader is missing {}; mesh rendering disabled until resource reload", uniform);
                            return;
                        }
                    }
                    flatProgram = loaded;
                });
                context.register(new Identifier("halo", "mesh_lit"),
                    VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, loaded -> {
                    for (String uniform : new String[]{"MaskEnabled", "MaskMode", "MaskThreshold", "MaskOffset",
                            "LightCoord", "LegacyAlphaCutoff", "NormalMat"}) {
                        if (loaded.getUniform(uniform) == null) {
                            LOG.error("Halo lit mesh shader is missing {}; directional rendering disabled until resource reload", uniform);
                            return;
                        }
                    }
                    litProgram = loaded;
                });
            } catch (IOException | RuntimeException ex) {
                LOG.error("Could not load Halo mesh shader; mesh rendering disabled until resource reload", ex);
            }
        });
    }

    public static boolean bind(MinecraftClient client, DrawBatch batch, MaterialState.Mesh material) {
        return bind(client, batch, material, RenderEnvironment.WORLD);
    }

    static boolean bind(MinecraftClient client, DrawBatch batch, MaterialState.Mesh material, RenderEnvironment environment) {
        ShaderProgram shader = bind(client, batch.texture(), material, batch.light(), false,
            batch.directionalLighting(), false, environment);
        if (shader != null && batch.directionalLighting()) setNormalMatrix(shader, RenderSystem.getModelViewMatrix());
        return shader != null;
    }

    public static boolean bindLegacy(MinecraftClient client, DrawBatch batch) {
        return bindLegacy(client, batch, RenderEnvironment.WORLD);
    }

    static boolean bindLegacy(MinecraftClient client, DrawBatch batch, RenderEnvironment environment) {
        ShaderProgram shader = bind(client, batch.texture(), null, batch.light(), true,
            batch.directionalLighting(), false, environment);
        if (shader != null && batch.directionalLighting()) setNormalMatrix(shader, RenderSystem.getModelViewMatrix());
        return shader != null;
    }

    public static ShaderProgram bind(MinecraftClient client, network.azusake.halo.core.render.MeshDraw draw) {
        return bind(client, draw, RenderEnvironment.WORLD);
    }

    static ShaderProgram bind(MinecraftClient client, network.azusake.halo.core.render.MeshDraw draw, RenderEnvironment environment) {
        return bind(client, draw.texture(), draw.material(), draw.light(), false,
            draw.directionalLighting(), draw.blend(), environment);
    }

    private static ShaderProgram bind(MinecraftClient client, network.azusake.halo.core.Identifier texture,
                                      MaterialState.Mesh material, LightSample light, boolean legacyAlphaCutoff,
                                      boolean directionalLighting, boolean translucent, RenderEnvironment environment) {
        boolean iris = environment == RenderEnvironment.WORLD && OptionalIrisPassDetector.hasShaderPack();
        ShaderProgram shader = iris ? IrisMeshBridge.currentProgram(directionalLighting, translucent)
            : directionalLighting ? litProgram : flatProgram;
        if (shader == null) return null;
        // A shader pack may store translucent color separately and reconstruct its position
        // from depthtex0 during compositing/fog. Leaving only the background depth makes
        // nearby masked meshes look like distant glass/sky (Bliss, Iteration RP).
        // This pass runs after Iris copies opaque depth to depthtex1. Write the surviving
        // fragments to depthtex0; alpha still comes from the material, and zero alpha is
        // discarded by the shader. Core's sorted order is preserved. Native rendering
        // continues to use the batch's depthWrite flag.
        if (iris) RenderSystem.depthMask(true);
        var mask = material == null ? null : material.mask();
        var baseTexture = texture == null ? WHITE_TEXTURE : texture;
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, client.getTextureManager().getTexture(game(baseTexture)).getGlId());
        int maskSlot = iris && directionalLighting ? 3 : 1;
        if (iris && directionalLighting) client.gameRenderer.getOverlayTexture().setupOverlayColor();
        RenderSystem.setShaderTexture(maskSlot,
            client.getTextureManager().getTexture(game(mask == null ? baseTexture : mask.texture())).getGlId());
        String prefix = iris ? (shader.getUniform("HaloMaskEnabled") != null ? "Halo" : "iris_Halo") : "";
        shader.getUniformOrDefault(prefix + "MaskEnabled").set(mask == null ? 0 : 1);
        shader.getUniformOrDefault(prefix + "MaskMode").set(mask != null && mask.mode() == MaterialState.MaskMode.STEP ? 1 : 0);
        shader.getUniformOrDefault(prefix + "MaskThreshold").set(mask == null ? 0.5f : mask.threshold());
        shader.getUniformOrDefault(prefix + "MaskOffset").set(mask == null ? 0f : mask.offsetU(), mask == null ? 0f : mask.offsetV());
        LightSample sample = light.available() ? light : LightSample.FULL_BRIGHT;
        shader.getUniformOrDefault(prefix + "LightCoord").set(sample.block() << 4, sample.sky() << 4);
        shader.getUniformOrDefault(prefix + "LegacyAlphaCutoff").set(legacyAlphaCutoff ? 1 : 0);
        return shader;
    }

    static void setNormalMatrix(ShaderProgram shader, Matrix4f transform) {
        Matrix3f matrix = new Matrix3f(transform);
        float determinant = matrix.determinant();
        if (Float.isFinite(determinant) && Math.abs(determinant) > 1.0e-8f) matrix.invert().transpose();
        else matrix.identity();
        String name = shader.getUniform("HaloNormalMat") != null ? "HaloNormalMat"
            : shader.getUniform("iris_HaloNormalMat") != null ? "iris_HaloNormalMat" : "NormalMat";
        shader.getUniformOrDefault(name).set(matrix);
    }
}
