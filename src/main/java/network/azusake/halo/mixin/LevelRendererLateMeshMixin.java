package network.azusake.halo.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import network.azusake.halo.physics.OptionalIrisPassDetector;
import network.azusake.halo.render.HaloRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla alpha meshes need the cloud colour/depth before they blend with the scene. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererLateMeshMixin {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/LevelRenderer;renderDebug(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/Camera;)V"))
    private void halo$afterCloudsAndWeather(CallbackInfo ci) {
        // Before renderDebug the fabulous targets have also been composited and
        // the main target rebound. World fog/projection are still in effect.
        if (OptionalIrisPassDetector.isMainPass() && !OptionalIrisPassDetector.hasShaderPack()) {
            HaloRenderer.getInstance().submitDeferredMeshes();
        }
    }
}
