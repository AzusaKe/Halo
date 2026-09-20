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
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;execute(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder$Inspector;)V",
        shift = At.Shift.AFTER))
    private void halo$afterWorldPasses(CallbackInfo ci) {
        HaloRenderer.getInstance().submitDeferredMeshes();
    }
}
