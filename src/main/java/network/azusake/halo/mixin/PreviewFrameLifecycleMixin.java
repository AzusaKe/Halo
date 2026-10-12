package network.azusake.halo.mixin;

import net.minecraft.client.renderer.GameRenderer;
import network.azusake.halo.render.PlayerPreviewRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World scopes start before extraction; preview sessions cover the subsequent drawing phase. */
@Mixin(GameRenderer.class)
public abstract class PreviewFrameLifecycleMixin {
    @Inject(method = "extract", at = @At("HEAD"))
    private void halo$beginWorldFrame(CallbackInfo ci) {
        // 26.3 extracts the world before GameRenderer.render. Resetting there discards
        // the capture claim and prevents the later NeoForge solid-stage draw entirely.
        network.azusake.halo.physics.RenderHeadCapture.beginRenderFrame();
        network.azusake.halo.render.HaloRenderer.getInstance().beginFrame();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void halo$beginViews(CallbackInfo ci) {
        PlayerPreviewRenderer.beginFrame();
    }
    @Inject(method = "render", at = @At("RETURN"))
    private void halo$endViews(CallbackInfo ci) {
        PlayerPreviewRenderer.endFrame();
    }
}
