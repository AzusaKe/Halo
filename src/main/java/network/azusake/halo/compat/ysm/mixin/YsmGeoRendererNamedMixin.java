package network.azusake.halo.compat.ysm.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import network.azusake.halo.compat.ysm.YsmHeadCapture;
import network.azusake.halo.compat.ysm.YsmV265Symbols;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Official-name hook for both NeoForge development and production runtimes. */
@Pseudo
@Mixin(targets = YsmV265Symbols.GEO_RENDERER, remap = false)
public interface YsmGeoRendererNamedMixin {

    @Inject(
        method = YsmV265Symbols.RENDER_METHOD + YsmV265Symbols.RENDER_DESCRIPTOR_NEOFORGE,
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private void halo$captureYsmHeadNamed(
        @Coerce Object animatedModel,
        @Coerce Object animatable,
        float tickDelta,
        RenderType renderLayer,
        PoseStack matrices,
        MultiBufferSource vertexConsumers,
        int textureIndex,
        VertexConsumer vertexConsumer,
        int light,
        int overlay,
        float red,
        float green,
        float blue,
        float alpha,
        CallbackInfo ci
    ) {
        YsmHeadCapture.captureAndReleaseEntity(animatedModel, matrices);
    }
}
