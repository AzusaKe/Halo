package network.azusake.halo.compat.ysm.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import network.azusake.halo.compat.ysm.YsmHeadCapture;
import network.azusake.halo.compat.ysm.YsmV265Adapter;
import network.azusake.halo.compat.ysm.YsmV265Symbols;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replaces the YSM base-model call with a capture + original invoke.
 *
 * <p>{@code @ModifyArgs} generated {@code Args$1} and crashed Forge 1.20.1
 * world reload. {@code @Redirect} observes the same arguments at the same call
 * site without synthetic Args classes, then calls the original through a
 * MethodHandle so rendering is unchanged.</p>
 */
@Pseudo
@Mixin(targets = YsmV265Symbols.ENTITY_GEO_RENDERER, remap = false)
public abstract class YsmGeoEntityRendererMixin {

    @Redirect(
        method = YsmV265Symbols.RENDER_METHOD + YsmV265Symbols.ENTITY_RENDER_DESCRIPTOR,
        at = @At(value = "INVOKE", target = YsmV265Symbols.ENTITY_BASE_RENDER_INVOKE, remap = false),
        remap = false,
        require = 0
    )
    private void halo$captureYsmEntityHead(
        @Coerce Object owner,
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
        float alpha
    ) throws Throwable {
        YsmHeadCapture.captureAndReleaseEntity(animatedModel, matrices);
        YsmV265Adapter.invokeBaseRender(
            owner, animatedModel, animatable, tickDelta, renderLayer, matrices,
            vertexConsumers, textureIndex, vertexConsumer, light, overlay,
            red, green, blue, alpha);
    }
}
