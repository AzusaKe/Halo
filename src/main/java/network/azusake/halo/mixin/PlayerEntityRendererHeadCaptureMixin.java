package network.azusake.halo.mixin;

import network.azusake.halo.physics.RenderHeadCapture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bracket {@link PlayerRenderer#render} with the head-capture context so
 * {@link ModelPartHeadCaptureMixin} can identify the current player model.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerEntityRendererHeadCaptureMixin {

    @Inject(
        method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD")
    )
    private void halo$beginHeadCapture(
        AbstractClientPlayer entity,
        float yaw,
        float tickDelta,
        PoseStack matrices,
        MultiBufferSource vertexConsumers,
        int light,
        CallbackInfo ci
    ) {
        PlayerModel<?> model = (PlayerModel<?>) ((PlayerRenderer) (Object) this).getModel();
        RenderHeadCapture.begin(entity, model);
    }

    @Inject(
        method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("TAIL")
    )
    private void halo$endHeadCapture(
        AbstractClientPlayer entity,
        float yaw,
        float tickDelta,
        PoseStack matrices,
        MultiBufferSource vertexConsumers,
        int light,
        CallbackInfo ci
    ) {
        RenderHeadCapture.end();
    }
}
