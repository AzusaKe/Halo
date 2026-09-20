package network.azusake.halo.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.LivingEntity;
import network.azusake.halo.physics.RenderHeadCapture;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ModelFeatureRenderer.class)
public abstract class EntityRenderContextMixin {
    @WrapMethod(method="renderModel")
    private void halo$drawScope(SubmitNodeStorage.ModelSubmit<?> submit, RenderType type, VertexConsumer vertices,
        OutlineBufferSource outline, MultiBufferSource.BufferSource crumbling, Operation<Void> original){
        if(submit.state() instanceof network.azusake.halo.physics.EntityRenderSnapshot.Holder holder
            && holder.halo$snapshot() != null && holder.halo$snapshot().living()) {
            var snapshot = holder.halo$snapshot();
            var model = submit.model() instanceof PlayerModel playerModel ? playerModel : null;
            var previous=RenderHeadCapture.suspendForPreview();
            RenderHeadCapture.beginDraw(snapshot,model);
            try{original.call(submit,type,vertices,outline,crumbling);}
            finally{RenderHeadCapture.endEntityRender();RenderHeadCapture.restoreContext(previous);}
        }else original.call(submit,type,vertices,outline,crumbling);
    }
}
