package network.azusake.halo.compat.iris.mixin;
import com.mojang.renderpearl.backend.opengl.GlProgram;
import network.azusake.halo.compat.iris.IrisMeshBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ShaderMap", remap = false)
public abstract class IrisShaderSelectionMixin {
    @Inject(method = "getShader", at = @At("RETURN"), cancellable = true)
    private void halo$material(CallbackInfoReturnable<GlProgram> ci) {
        ci.setReturnValue(IrisMeshBridge.selectedProgram(ci.getReturnValue()));
    }
}
