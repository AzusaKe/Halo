package network.azusake.halo.compat.ysm.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import network.azusake.halo.compat.ysm.YsmHeadCapture;
import network.azusake.halo.compat.ysm.YsmV265Symbols;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Common YSM geometry hook, shared by player and replaced living renderers. */
@Pseudo
@Mixin(targets = YsmV265Symbols.GEO_RENDERER, remap = false)
public interface YsmGeoRendererMixin {
    @Inject(
        method = YsmV265Symbols.RENDER_METHOD + YsmV265Symbols.RENDER_DESCRIPTOR,
        at = @At("HEAD"), remap = false, require = 0
    )
    private void halo$captureYsmHead(
        @Coerce Object renderData, @Coerce Object flags, PoseStack matrices,
        SubmitNodeCollector collector, RenderType renderType, CallbackInfo ci
    ) {
        YsmHeadCapture.capture(renderData, matrices);
    }
}
