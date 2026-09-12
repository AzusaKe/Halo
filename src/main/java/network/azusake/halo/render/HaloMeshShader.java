package network.azusake.halo.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import network.azusake.halo.core.render.DrawBatch;
import network.azusake.halo.core.render.MaterialState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static network.azusake.halo.platform.PlatformTypes.game;
import network.azusake.halo.compat.iris.IrisMeshBridge;
import network.azusake.halo.physics.OptionalIrisPassDetector;

/** Version-specific shader adapter. GameRenderer owns registered programs and their reload/disposal. */
public final class HaloMeshShader {
    private static final Logger LOG = LoggerFactory.getLogger("HaloMeshShader");
    private static ShaderProgram program;
    private HaloMeshShader() {}

    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            program = null;
            try {
                context.register(new Identifier("halo", "mesh"), VertexFormats.POSITION_TEXTURE_COLOR, loaded -> {
                    for (String uniform : new String[]{"MaskEnabled", "MaskMode", "MaskThreshold", "MaskOffset"}) {
                        if (loaded.getUniform(uniform) == null) {
                            LOG.error("Halo mesh shader is missing {}; mesh rendering disabled until resource reload", uniform);
                            return;
                        }
                    }
                    program = loaded;
                });
            } catch (IOException | RuntimeException ex) {
                LOG.error("Could not load Halo mesh shader; mesh rendering disabled until resource reload", ex);
            }
        });
    }

    public static boolean bind(MinecraftClient client, DrawBatch batch, MaterialState.Mesh material) {
        boolean iris = OptionalIrisPassDetector.hasShaderPack();
        ShaderProgram shader = iris ? IrisMeshBridge.currentProgram() : program;
        if (shader == null) return false;
        // A shader pack may store translucent color separately and reconstruct its position
        // from depthtex0 during compositing/fog. Leaving only the background depth makes
        // nearby masked meshes look like distant glass/sky (Bliss, Iteration RP).
        // This pass runs after Iris copies opaque depth to depthtex1. Write the surviving
        // fragments to depthtex0; alpha still comes from the material, and zero alpha is
        // discarded by the shader. Core's sorted order is preserved. Native rendering
        // continues to use the batch's depthWrite flag.
        if (iris) RenderSystem.depthMask(true);
        var mask = material.mask();
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, client.getTextureManager().getTexture(game(batch.texture())).getGlId());
        RenderSystem.setShaderTexture(1, client.getTextureManager().getTexture(game(mask == null ? batch.texture() : mask.texture())).getGlId());
        String prefix = iris ? (shader.getUniform("HaloMaskEnabled") != null ? "Halo" : "iris_Halo") : "";
        shader.getUniformOrDefault(prefix + "MaskEnabled").set(mask == null ? 0 : 1);
        shader.getUniformOrDefault(prefix + "MaskMode").set(mask != null && mask.mode() == MaterialState.MaskMode.STEP ? 1 : 0);
        shader.getUniformOrDefault(prefix + "MaskThreshold").set(mask == null ? 0.5f : mask.threshold());
        shader.getUniformOrDefault(prefix + "MaskOffset").set(mask == null ? 0f : mask.offsetU(), mask == null ? 0f : mask.offsetV());
        return true;
    }
}
