package network.azusake.halo.mixin;

import net.minecraft.client.render.GameRenderer;
import network.azusake.halo.api.client.preview.v1.HaloPreviewApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One automatic-view inventory per host render frame, including UI renders while paused. */
@Mixin(GameRenderer.class)
public abstract class PreviewFrameLifecycleMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void halo$beginViews(float delta, long start, boolean tick, CallbackInfo ci) {
        HaloPreviewApi.beginFrame();
    }
    @Inject(method = "render", at = @At("RETURN"))
    private void halo$endViews(float delta, long start, boolean tick, CallbackInfo ci) {
        HaloPreviewApi.endFrame();
    }
}
