package network.azusake.halo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import network.azusake.halo.physics.RenderHeadCapture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Brackets every entity render attempt so API v2 can reject auxiliary passes. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderContextMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void halo$beginAnchorRender(Entity entity, double x, double y, double z,
        float yaw, float tickDelta, PoseStack matrices, MultiBufferSource buffers,
        int light, CallbackInfo ci) {
        RenderHeadCapture.beginEntityRender(entity, matrices, tickDelta);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("RETURN"))
    private void halo$endAnchorRender(Entity entity, double x, double y, double z,
        float yaw, float tickDelta, PoseStack matrices, MultiBufferSource buffers,
        int light, CallbackInfo ci) {
        RenderHeadCapture.endEntityRender();
    }
}
