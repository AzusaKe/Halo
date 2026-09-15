package network.azusake.halo.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import network.azusake.halo.render.PlayerPreviewCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Posed vanilla head fallback when invisibility skips ModelPart.render entirely. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityPreviewPoseMixin {
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
    private void halo$posedHead(LivingEntity entity, float yaw, float delta, MatrixStack matrices,
                                VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        if (!PlayerPreviewCapture.isActive()) return;
        var model = ((LivingEntityRenderer<?, ?>) (Object) this).getModel();
        if (model instanceof PlayerEntityModel<?> player && !player.child)
            PlayerPreviewCapture.capturePosedHead(entity, matrices, player.getHead());
    }
}
