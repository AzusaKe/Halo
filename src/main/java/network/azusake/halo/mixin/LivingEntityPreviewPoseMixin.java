package network.azusake.halo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import network.azusake.halo.render.PlayerPreviewCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Posed vanilla head fallback when invisibility skips ModelPart.render entirely. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityPreviewPoseMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
    private void halo$posedHead(LivingEntity entity, float yaw, float delta, PoseStack matrices,
                                MultiBufferSource vertices, int light, CallbackInfo ci) {
        if (!PlayerPreviewCapture.isActive()) return;
        var model = ((LivingEntityRenderer<?, ?>) (Object) this).getModel();
        if (model instanceof PlayerModel<?> player && !player.young) {
            PlayerPreviewCapture.capturePosedHead(entity, matrices, player.getHead());
            network.azusake.halo.compat.emf.EmfHeadCapture.capturePreviewPose(entity, matrices, player.getHead());
        }
    }
}
