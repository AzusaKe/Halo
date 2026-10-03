package network.azusake.halo.compat.caustica.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import network.azusake.halo.physics.*;
import network.azusake.halo.compat.emf.EmfHeadCapture;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Samples the already posed base head, including direct cuboid emission that skips ModelPart.render. */
@Pseudo
@Mixin(targets="dev.comfyfluffy.caustica.minecraft.client.entity.RtEntityCollectorBase",remap=false)
public abstract class CausticaEntityMixin {
    @WrapMethod(method="submitModel")
    private <S> void halo$scope(Model<? super S> model,S state,PoseStack matrices,RenderType type,
            int light,int overlay,int tint,TextureAtlasSprite sprite,int outline,ModelFeatureRenderer.CrumblingOverlay crumble,Operation<Void> original) {
        var previous=RenderHeadCapture.suspendForPreview();
        boolean scoped=state instanceof EntityRenderSnapshot.Holder holder && holder.halo$snapshot()!=null;
        if(scoped)RenderHeadCapture.beginDraw(((EntityRenderSnapshot.Holder)state).halo$snapshot(),model instanceof PlayerModel player?player:null);
        try{original.call(model,state,matrices,type,light,overlay,tint,sprite,outline,crumble);}
        finally{if(scoped)RenderHeadCapture.endEntityRender();RenderHeadCapture.restoreContext(previous);}
    }
    @Inject(method="submitModel",at=@At(value="INVOKE",target="Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",shift=At.Shift.AFTER),require=1)
    private <S> void halo$posed(Model<? super S> model,S state,PoseStack matrices,RenderType type,
            int light,int overlay,int tint,TextureAtlasSprite sprite,int outline,ModelFeatureRenderer.CrumblingOverlay crumble,CallbackInfo ci) {
        var entity=RenderHeadCapture.currentEntity();if(entity==null || !(model instanceof PlayerModel player) || !entity.player())return;
        var camera=Minecraft.getInstance().gameRenderer.mainCamera().position();
        var root=new PoseStack();root.translate(entity.position().x()-camera.x,entity.position().y()-camera.y,entity.position().z()-camera.z);
        root.mulPose(matrices.last().pose());
        EmfHeadCapture.capture(root,player.getHead());RenderHeadCapture.capture(root,player.getHead());
    }
}
