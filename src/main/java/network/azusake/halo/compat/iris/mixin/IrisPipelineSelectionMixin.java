package network.azusake.halo.compat.iris.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import network.azusake.halo.compat.iris.IrisMeshBridge;
import org.spongepowered.asm.mixin.Mixin;

/** Wrap all native/Iris early returns so Halo's private material variant wins for Halo pipelines. */
@Mixin(targets="com.mojang.blaze3d.opengl.GlDevice", priority=1100)
public abstract class IrisPipelineSelectionMixin {
    @WrapMethod(method="getOrCompilePipeline")
    private GlRenderPipeline halo$privateProgram(RenderPipeline pipeline, Operation<GlRenderPipeline> original) {
        var program = IrisMeshBridge.program(pipeline);
        return program == null ? original.call(pipeline) : new GlRenderPipeline(pipeline, program);
    }
}
