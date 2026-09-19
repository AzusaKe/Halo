package network.azusake.halo.render;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.resources.Identifier;
import network.azusake.halo.core.render.*;
import java.util.*;
import static network.azusake.halo.platform.PlatformTypes.game;

/** Immutable 26.1 pipeline state; per-draw material values belong to HaloDrawSubmitter. */
public final class HaloMeshShader {
    private record Key(boolean lit, boolean blend, boolean cull, boolean depth, boolean write,
                       boolean legacy, boolean gui, DrawBatch.Topology topology) {}
    record Material(RenderType type, RenderSetup setup) {}
    private static final Map<Key, RenderPipeline> PIPELINES = new HashMap<>();
    private record TextureKey(Key key, Identifier base, Identifier mask) {}
    private static final Map<TextureKey, Material> MATERIALS = new HashMap<>();
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("halo", "textures/halo/white.png");
    private HaloMeshShader() {}
    public static void clear() { MATERIALS.clear(); }
    static Material material(DrawBatch draw, RenderEnvironment environment) {
        Key key = new Key(draw.directionalLighting(), draw.blend(), draw.cull(), draw.depthTest(),
            draw.depthWrite(), draw.material() instanceof MaterialState.Legacy, environment == RenderEnvironment.GUI, draw.topology());
        var mask = draw.material() instanceof MaterialState.Mesh m ? m.mask() : null;
        Identifier texture = draw.textured() && draw.texture() != null ? game(draw.texture()) : WHITE;
        return MATERIALS.computeIfAbsent(new TextureKey(key, texture, mask == null ? WHITE : game(mask.texture())), k -> {
            RenderPipeline pipeline = PIPELINES.computeIfAbsent(key, HaloMeshShader::pipeline);
            var setup = RenderSetup.builder(pipeline).withTexture("Sampler0", texture)
                .withTexture("HaloMask", k.mask()).useLightmap().useOverlay().createRenderSetup();
            return new Material(RenderType.create("halo", setup), setup);
        });
    }
    private static RenderPipeline pipeline(Key key) {
        var builder = RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("halo", "pipeline/material_" + PIPELINES.size()))
            .withVertexShader(Identifier.fromNamespaceAndPath("halo", "core/halo"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("halo", "core/halo"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Lighting", UniformType.UNIFORM_BUFFER)
            .withUniform("HaloMaterial", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0").withSampler("Sampler1").withSampler("Sampler2").withSampler("HaloMask")
            .withVertexFormat(DefaultVertexFormat.ENTITY, key.topology() == DrawBatch.Topology.QUADS ? VertexFormat.Mode.QUADS : VertexFormat.Mode.TRIANGLES)
            .withCull(key.cull()).withColorTargetState(key.blend() ? new ColorTargetState(BlendFunction.TRANSLUCENT) : ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(key.depth() ? CompareOp.LESS_THAN_OR_EQUAL : CompareOp.ALWAYS_PASS, key.write()));
        if (key.lit() && !key.gui()) builder.withShaderDefine("HALO_LIT");
        if (key.gui()) builder.withShaderDefine("HALO_GUI");
        RenderPipeline pipeline = RenderPipelines.register(builder.build());
        if (!key.gui()) network.azusake.halo.compat.iris.IrisMeshBridge.assign(pipeline, key.lit(), key.blend());
        return pipeline;
    }
}
