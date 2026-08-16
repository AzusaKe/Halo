package network.azusake.halo.mixin;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import network.azusake.halo.physics.RenderHeadCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sets the draw-time head-capture context from the entity render state right
 * before a player body model is drawn.  26.1 defers model drawing until after
 * all entities have submitted, so the entity being drawn can only be identified
 * through the render state attached to the model submit — not by submission
 * order.  {@link RenderHeadCapture#beginDraw} accepts only the base player
 * body model, so layer and armour model heads are never captured.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelHeadCaptureMixin {

    @Inject(
        method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
        at = @At("HEAD")
    )
    private void halo$beginDrawHeadCapture(AvatarRenderState state, CallbackInfo ci) {
        RenderHeadCapture.beginDraw(state.id, (PlayerModel) (Object) this);
    }
}
