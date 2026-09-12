package network.azusake.halo.compat.iris.mixin;

import network.azusake.halo.compat.iris.IrisMeshBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class IrisMeshPipelineMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void halo$prepareMesh(CallbackInfo ci) { IrisMeshBridge.prepare(this); }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void halo$forgetMesh(CallbackInfo ci) { IrisMeshBridge.forget(this); }
}
