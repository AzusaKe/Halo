package network.azusake.halo.mixin;

import network.azusake.halo.physics.RenderHeadCapture;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
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
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
        at = @At("HEAD")
    )
    private void halo$captureHeadTransform(
        PoseStack matrices,
        VertexConsumer vertices,
        int light,
        int overlay,
        int color,
        CallbackInfo ci
    ) {
        RenderHeadCapture.capture(matrices, (ModelPart) (Object) this);
    }
}
