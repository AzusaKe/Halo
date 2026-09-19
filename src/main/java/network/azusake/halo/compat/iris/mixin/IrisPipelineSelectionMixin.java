package network.azusake.halo.compat.iris.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import network.azusake.halo.compat.iris.IrisMeshBridge;
import org.spongepowered.asm.mixin.Mixin;
/** Iris owns native pipeline wrappers; substitute only Halo's private shader. */
@Mixin(RenderSystem.class)
public abstract class IrisPipelineSelectionMixin {
    @WrapMethod(method = "getCompiledPipelineNullable")
    private static CompiledRenderPipeline halo$selection(RenderPipeline pipeline, Operation<CompiledRenderPipeline> original) {
        var previous = IrisMeshBridge.selecting(pipeline);
        try { return original.call(pipeline); }
        finally { IrisMeshBridge.selecting(previous); }
    }
}
