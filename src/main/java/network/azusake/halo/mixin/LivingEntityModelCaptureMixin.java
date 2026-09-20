package network.azusake.halo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import network.azusake.halo.physics.EntityRenderSnapshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cape and armor models also extend PlayerModel; only the renderer's body owns the head. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityModelCaptureMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("HEAD"))
    private void halo$bindBaseModel(LivingEntityRenderState state, PoseStack stack,
                                  SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        var holder = (EntityRenderSnapshot.Holder) state;
        var snapshot = holder.halo$snapshot();
        if (snapshot == null || !snapshot.player()) return;
        var model = ((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel();
        holder.halo$snapshot(snapshot.withPlayerModel(model instanceof PlayerModel playerModel ? playerModel : null));
    }
}
