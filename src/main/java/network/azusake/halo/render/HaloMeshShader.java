package network.azusake.halo.render;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
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
    private static ShaderInstance flatProgram;
    private static ShaderInstance litProgram;
    private HaloMeshShader() {}

    static boolean useEntityLayer(RenderEnvironment environment, boolean shaderPack,
                                  boolean directionalLighting, boolean hasMask) {
        return environment == RenderEnvironment.WORLD && shaderPack && directionalLighting && !hasMask;
    }

    /**
     * Activates the vanilla entity material contract so Iris can bind the pack's
     * entity G-buffer program plus LabPBR normal/specular textures. A private
     * smooth-normal variant may replace only the program while this layer's
     * material state remains active. Masked materials stay on the isolated path.
     */
    static EntityLayerBinding openEntityLayer(network.azusake.halo.core.Identifier texture,
                                              boolean translucent, RenderEnvironment environment,
                                              boolean directionalLighting, boolean hasMask,
                                              LightSample light) {
        boolean shaderPack = OptionalIrisPassDetector.hasShaderPack();
        if (!useEntityLayer(environment, shaderPack, directionalLighting, hasMask)) return null;
        var base = texture == null ? WHITE_TEXTURE : texture;
        RenderType layer = translucent
            ? RenderType.entityTranslucent(game(base), false)
            : RenderType.entitySolid(game(base));
        layer.setupRenderState();
        ShaderInstance nativeShader = RenderSystem.getShader();
        if (nativeShader == null) {
            layer.clearRenderState();
            return null;
        }
        ShaderInstance smoothNormalShader = IrisMeshBridge.currentSmoothNormalProgram(translucent);
        ShaderInstance shader = smoothNormalShader == null ? nativeShader : smoothNormalShader;
        if (smoothNormalShader != null) {
            String prefix = shader.getUniform("HaloMaskEnabled") != null ? "Halo" : "iris_Halo";
            shader.safeGetUniform(prefix + "MaskEnabled").set(0);
            shader.safeGetUniform(prefix + "LegacyAlphaCutoff").set(0);
            LightSample sample = light.available() ? light : LightSample.FULL_BRIGHT;
            shader.safeGetUniform(prefix + "LightCoord").set(sample.block() << 4, sample.sky() << 4);
            RenderSystem.setShader(() -> shader);
        }
        return new EntityLayerBinding(layer, shader);
    }

    static final class EntityLayerBinding implements AutoCloseable {
        private final RenderType layer;
        final ShaderInstance shader;
        private EntityLayerBinding(RenderType layer, ShaderInstance shader) {
            this.layer = layer;
            this.shader = shader;
        }
        @Override public void close() { layer.clearRenderState(); }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterShadersEvent context) -> {
            flatProgram = null;
            litProgram = null;
            try {
                context.registerShader(new ShaderInstance(context.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("halo", "mesh"), DefaultVertexFormat.POSITION_TEX_COLOR), loaded -> {
                    for (String uniform : new String[]{"MaskEnabled", "MaskMode", "MaskThreshold", "MaskOffset",
                            "LightCoord", "LegacyAlphaCutoff"}) {
                        if (loaded.getUniform(uniform) == null) {
                            LOG.error("Halo mesh shader is missing {}; mesh rendering disabled until resource reload", uniform);
                            return;
                        }
                    }
                    flatProgram = loaded;
                });
                context.registerShader(new ShaderInstance(context.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("halo", "mesh_lit"),
                    DefaultVertexFormat.NEW_ENTITY), loaded -> {
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

    public static boolean bind(Minecraft client, DrawBatch batch, MaterialState.Mesh material) {
        return bind(client, batch, material, RenderEnvironment.WORLD);
    }

    static boolean bind(Minecraft client, DrawBatch batch, MaterialState.Mesh material, RenderEnvironment environment) {
        ShaderInstance shader = bind(client, batch.texture(), material, batch.light(), false,
            batch.directionalLighting(), false, environment);
        if (shader != null && batch.directionalLighting()) setNormalMatrix(shader, RenderSystem.getModelViewMatrix());
        return shader != null;
    }

    public static boolean bindLegacy(Minecraft client, DrawBatch batch) {
        return bindLegacy(client, batch, RenderEnvironment.WORLD);
    }

    static boolean bindLegacy(Minecraft client, DrawBatch batch, RenderEnvironment environment) {
        ShaderInstance shader = bind(client, batch.texture(), null, batch.light(), true,
            batch.directionalLighting(), false, environment);
        if (shader != null && batch.directionalLighting()) setNormalMatrix(shader, RenderSystem.getModelViewMatrix());
        return shader != null;
    }

    public static ShaderInstance bind(Minecraft client, network.azusake.halo.core.render.MeshDraw draw) {
        return bind(client, draw, RenderEnvironment.WORLD);
    }

    static ShaderInstance bind(Minecraft client, network.azusake.halo.core.render.MeshDraw draw, RenderEnvironment environment) {
        return bind(client, draw.texture(), draw.material(), draw.light(), false,
            draw.directionalLighting(), draw.blend(), environment);
    }

    /** One lexical OBJ submission. All state setters and ShaderProgram apply/clear still run. */
    static final class Submission {
        private final Minecraft client;
        private final RenderEnvironment environment;
        private boolean iris;
        private final MeshSubmissionCache<PreparedProgram, AbstractTexture> cache;

        Submission(Minecraft client, RenderEnvironment environment) {
            this.client = client;
            this.environment = environment;
            iris = environment == RenderEnvironment.WORLD && OptionalIrisPassDetector.hasShaderPack();
            cache = new MeshSubmissionCache<>(variant -> {
                boolean lit = variant != 0;
                ShaderInstance shader = iris ? IrisMeshBridge.currentProgram(lit, variant == 2)
                    : lit ? litProgram : flatProgram;
                return shader == null ? null : new PreparedProgram(shader, iris, lit);
            }, id -> client.getTextureManager().getTexture(game(id)));
        }

        PreparedProgram bind(network.azusake.halo.core.render.MeshDraw draw) {
            PreparedProgram program = cache.program(draw.directionalLighting(), draw.blend());
            if (program == null) return null;
            if (iris) RenderSystem.depthMask(true);
            var mask = draw.material().mask();
            var base = draw.texture();
            RenderSystem.setShader(program);
            RenderSystem.setShaderTexture(0, cache.base(base).getId());
            int maskSlot = iris && draw.directionalLighting() ? 3 : 1;
            if (iris && draw.directionalLighting()) client.gameRenderer.overlayTexture().setupOverlayColor();
            RenderSystem.setShaderTexture(maskSlot, cache.mask(mask == null ? base : mask.texture()).getId());
            program.maskEnabled.set(mask == null ? 0 : 1);
            program.maskMode.set(mask != null && mask.mode() == MaterialState.MaskMode.STEP ? 1 : 0);
            program.maskThreshold.set(mask == null ? 0.5f : mask.threshold());
            program.maskOffset.set(mask == null ? 0f : mask.offsetU(), mask == null ? 0f : mask.offsetV());
            LightSample sample = draw.light().available() ? draw.light() : LightSample.FULL_BRIGHT;
            program.lightCoord.set(sample.block() << 4, sample.sky() << 4);
            program.legacyAlphaCutoff.set(0);
            return program;
        }

        void invalidate() {
            cache.invalidate();
            iris = environment == RenderEnvironment.WORLD && OptionalIrisPassDetector.hasShaderPack();
        }
    }

    static final class PreparedProgram implements java.util.function.Supplier<ShaderInstance> {
        final ShaderInstance shader;
        private final AbstractUniform maskEnabled, maskMode, maskThreshold, maskOffset, lightCoord, legacyAlphaCutoff;
        private final AbstractUniform normalMatrix;

        PreparedProgram(ShaderInstance shader, boolean iris, boolean lit) {
            this.shader = shader;
            String prefix = iris ? (shader.getUniform("HaloMaskEnabled") != null ? "Halo" : "iris_Halo") : "";
            maskEnabled = shader.safeGetUniform(prefix + "MaskEnabled");
            maskMode = shader.safeGetUniform(prefix + "MaskMode");
            maskThreshold = shader.safeGetUniform(prefix + "MaskThreshold");
            maskOffset = shader.safeGetUniform(prefix + "MaskOffset");
            lightCoord = shader.safeGetUniform(prefix + "LightCoord");
            legacyAlphaCutoff = shader.safeGetUniform(prefix + "LegacyAlphaCutoff");
            String name = !lit ? "" : shader.getUniform("HaloNormalMat") != null ? "HaloNormalMat"
                : shader.getUniform("iris_HaloNormalMat") != null ? "iris_HaloNormalMat" : "NormalMat";
            normalMatrix = lit ? shader.safeGetUniform(name) : null;
        }

        @Override public ShaderInstance get() { return shader; }
        void normal(Matrix3f matrix) { normalMatrix.set(matrix); }
    }

    private static ShaderInstance bind(Minecraft client, network.azusake.halo.core.Identifier texture,
                                      MaterialState.Mesh material, LightSample light, boolean legacyAlphaCutoff,
                                      boolean directionalLighting, boolean translucent, RenderEnvironment environment) {
        boolean iris = environment == RenderEnvironment.WORLD && OptionalIrisPassDetector.hasShaderPack();
        ShaderInstance shader = iris ? IrisMeshBridge.currentProgram(directionalLighting, translucent)
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
        RenderSystem.setShaderTexture(0, client.getTextureManager().getTexture(game(baseTexture)).getId());
        int maskSlot = iris && directionalLighting ? 3 : 1;
        if (iris && directionalLighting) client.gameRenderer.overlayTexture().setupOverlayColor();
        RenderSystem.setShaderTexture(maskSlot,
            client.getTextureManager().getTexture(game(mask == null ? baseTexture : mask.texture())).getId());
        String prefix = iris ? (shader.getUniform("HaloMaskEnabled") != null ? "Halo" : "iris_Halo") : "";
        shader.safeGetUniform(prefix + "MaskEnabled").set(mask == null ? 0 : 1);
        shader.safeGetUniform(prefix + "MaskMode").set(mask != null && mask.mode() == MaterialState.MaskMode.STEP ? 1 : 0);
        shader.safeGetUniform(prefix + "MaskThreshold").set(mask == null ? 0.5f : mask.threshold());
        shader.safeGetUniform(prefix + "MaskOffset").set(mask == null ? 0f : mask.offsetU(), mask == null ? 0f : mask.offsetV());
        LightSample sample = light.available() ? light : LightSample.FULL_BRIGHT;
        shader.safeGetUniform(prefix + "LightCoord").set(sample.block() << 4, sample.sky() << 4);
        shader.safeGetUniform(prefix + "LegacyAlphaCutoff").set(legacyAlphaCutoff ? 1 : 0);
        return shader;
    }

    static void setNormalMatrix(ShaderInstance shader, Matrix4f transform) {
        Matrix3f matrix = new Matrix3f(transform);
        float determinant = matrix.determinant();
        if (Float.isFinite(determinant) && Math.abs(determinant) > 1.0e-8f) matrix.invert().transpose();
        else matrix.identity();
        setNormalMatrix(shader, matrix);
    }

    static void setNormalMatrix(ShaderInstance shader, Matrix3f matrix) {
        String name = shader.getUniform("HaloNormalMat") != null ? "HaloNormalMat"
            : shader.getUniform("iris_HaloNormalMat") != null ? "iris_HaloNormalMat" : "NormalMat";
        shader.safeGetUniform(name).set(matrix);
    }
}
