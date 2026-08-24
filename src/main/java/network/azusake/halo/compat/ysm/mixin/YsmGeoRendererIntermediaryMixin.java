package network.azusake.halo.compat.ysm.mixin;

import network.azusake.halo.compat.ysm.YsmHeadCapture;
import network.azusake.halo.compat.ysm.YsmV265Symbols;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Production/intermediary hook for the verified YSM release jar. */
@Pseudo
@Mixin(targets = YsmV265Symbols.GEO_RENDERER, remap = false)
public interface YsmGeoRendererIntermediaryMixin {

    @Inject(
        method = YsmV265Symbols.RENDER_METHOD + YsmV265Symbols.RENDER_DESCRIPTOR_INTERMEDIARY,
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private void halo$captureYsmHeadIntermediary(
        @Coerce Object animatedModel,
        @Coerce Object animatable,
        float tickDelta,
        RenderLayer renderLayer,
        MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
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
