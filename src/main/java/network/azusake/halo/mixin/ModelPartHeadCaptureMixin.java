package network.azusake.halo.mixin;

import network.azusake.halo.physics.RenderHeadCapture;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Snapshots the head part's rendered transform when the current player model's
 * head is actually drawn.
 */
@Mixin(ModelPart.class)
public abstract class ModelPartHeadCaptureMixin {

    @Inject(
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V",
        at = @At("HEAD")
    )
    private void halo$captureHeadTransform(
        MatrixStack matrices,
        VertexConsumer vertices,
        int light,
        int overlay,
        int color,
        CallbackInfo ci
    ) {
        RenderHeadCapture.capture(matrices, (ModelPart) (Object) this);
    }
}
