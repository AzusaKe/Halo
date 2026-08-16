package network.azusake.halo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import network.azusake.halo.physics.RenderHeadCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bracket {@link AvatarRenderer#submit} with the head-capture context so
 * {@link ModelPartHeadCaptureMixin} can attribute the deferred head draw to
 * the player being submitted.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class PlayerEntityRendererHeadCaptureMixin {

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("HEAD")
    )
    private void halo$beginHeadCapture(
        LivingEntityRenderState state,
        PoseStack matrices,
        SubmitNodeCollector submitNodeCollector,
        CameraRenderState camera,
        CallbackInfo ci
    ) {
        if (!((Object) this instanceof AvatarRenderer) || !(state instanceof AvatarRenderState avatarState)
                || Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(avatarState.id);
        if (entity instanceof AbstractClientPlayer) {
            PlayerModel model = ((LivingEntityRenderer<?, ?, PlayerModel>) (Object) this).getModel();
            RenderHeadCapture.beginSubmit(entity.getUUID(), model);
        }
    }
}
