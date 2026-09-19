package network.azusake.halo.mixin;

import net.minecraft.client.renderer.GameRenderer;

import network.azusake.halo.render.PlayerPreviewRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One automatic-view inventory per host render frame, including UI renders while paused. */
@Mixin(GameRenderer.class)
public abstract class PreviewFrameLifecycleMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void halo$beginViews(CallbackInfo ci) {
        PlayerPreviewRenderer.beginFrame();
    }
    @Inject(method = "render", at = @At("RETURN"))
    private void halo$endViews(CallbackInfo ci) {
        PlayerPreviewRenderer.endFrame();
        network.azusake.halo.render.HaloFrameDiagnostics.endFrame();
    }
}
