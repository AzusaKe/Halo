package network.azusake.halo.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import network.azusake.halo.render.HaloRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draw after clouds/weather resolve, before Iris composites and the camera matrix is popped. */
@Mixin(LevelRenderer.class)
public abstract class LevelRenderCompletionMixin {
    @Inject(method = "executeSolid", at = @At("RETURN"))
    private void halo$solid(net.minecraft.client.renderer.chunk.ChunkSectionsToRender chunks,
            net.minecraft.client.renderer.feature.FeatureRenderDispatcher.PreparedFrame frame,
            com.mojang.renderpearl.api.commands.RenderPass pass, CallbackInfo ci) {
        network.azusake.halo.render.HaloDrawSubmitter.withPass(pass, () -> HaloRenderer.getInstance().submitSolidStage());
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;execute(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder$Inspector;)V",
        shift = At.Shift.AFTER))
    private void halo$afterWorldPasses(CallbackInfo ci) {
        HaloRenderer.getInstance().submitDeferredMeshes();
    }
}
