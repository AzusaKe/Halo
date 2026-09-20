package network.azusake.halo.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import network.azusake.halo.render.PlayerPreviewCapture;
import network.azusake.halo.compat.emf.EmfHeadCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityPreviewPoseMixin {
    @Inject(method="submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void halo$posedHead(LivingEntityRenderState state,PoseStack stack,SubmitNodeCollector collector,CameraRenderState camera,CallbackInfo ci){
        if(!PlayerPreviewCapture.isActive()||!(state instanceof AvatarRenderState avatar))return;
        var client=Minecraft.getInstance();
        if(client.level==null||!(client.level.getEntity(avatar.id) instanceof LivingEntity entity))return;
        Object candidate=((LivingEntityRenderer<?,?,?>)(Object)this).getModel();
        if(candidate instanceof PlayerModel model){
            network.azusake.halo.core.runtime.PreviewAnchorHost.beginEntityRender(entity.getUUID(),entity.getId());
            try{model.setupAnim(avatar);PlayerPreviewCapture.capturePosedHead(entity,stack,model.getHead());EmfHeadCapture.capturePreviewPose(entity,stack,model.getHead());}
            finally{network.azusake.halo.core.runtime.PreviewAnchorHost.endEntityRender();}
        }
    }
}
