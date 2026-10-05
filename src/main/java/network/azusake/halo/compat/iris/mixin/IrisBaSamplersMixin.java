package network.azusake.halo.compat.iris.mixin;

import network.azusake.halo.compat.iris.IrisMeshBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.gl.program.ProgramSamplers$Builder", remap = false)
public abstract class IrisBaSamplersMixin {
    @Inject(method = "build", at = @At("HEAD"))
    private void halo$registerBaSamplers(CallbackInfoReturnable<?> ci) { IrisMeshBridge.addBaSamplers(this); }
}
