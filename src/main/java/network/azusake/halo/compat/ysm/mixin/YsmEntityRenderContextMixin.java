package network.azusake.halo.compat.ysm.mixin;

import network.azusake.halo.physics.RenderHeadCapture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Brackets YSM's dispatcher-level replacement render with its source entity. */
@Mixin(EntityRenderDispatcher.class)
public abstract class YsmEntityRenderContextMixin {

    @Inject(
        method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD")
    )
    private void halo$beginYsmEntityRender(
        Entity entity,
        double x,
        double y,
        double z,
        float yaw,
        float tickDelta,
        PoseStack matrices,
        MultiBufferSource vertexConsumers,
        int light,
        CallbackInfo ci
    ) {
        RenderHeadCapture.beginYsmEntity(entity, matrices);
    }

    @Inject(
        method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("RETURN")
    )
    private void halo$endYsmEntityRender(
        Entity entity,
        double x,
        double y,
        double z,
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
