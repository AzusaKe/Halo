package network.azusake.halo.compat.ysm.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import network.azusake.halo.compat.ysm.YsmEntityRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Brackets world entity submission so the optional YSM hook rejects previews. */
@Mixin(EntityRenderDispatcher.class)
public abstract class YsmEntityRenderContextMixin {
    @Inject(
        method = "extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
        at = @At("RETURN")
    )
    private <E extends Entity> void halo$registerYsmEntityState(
        E entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir
    ) {
        YsmEntityRenderContext.register(entity, cir.getReturnValue());
    }

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
        at = @At("HEAD")
    )
    private void halo$beginYsmEntity(
        EntityRenderState state, CameraRenderState camera,
        double x, double y, double z, PoseStack matrices,
        SubmitNodeCollector collector, CallbackInfo ci
    ) {
        YsmEntityRenderContext.begin(state);
    }

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
        at = @At("RETURN")
    )
    private void halo$endYsmEntity(
        EntityRenderState state, CameraRenderState camera,
        double x, double y, double z, PoseStack matrices,
        SubmitNodeCollector collector, CallbackInfo ci
    ) {
        YsmEntityRenderContext.end();
    }
}
