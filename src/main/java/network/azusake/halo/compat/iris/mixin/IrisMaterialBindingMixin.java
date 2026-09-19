package network.azusake.halo.compat.iris.mixin;
import com.mojang.renderpearl.backend.opengl.GlProgram;
import com.mojang.renderpearl.backend.opengl.Uniform;
import network.azusake.halo.compat.iris.IrisMeshBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Extend Iris's standard bindings only for programs owned by Halo. */
@Mixin(GlProgram.class)
public abstract class IrisMaterialBindingMixin {
    @Inject(method = "getUniform", at = @At("RETURN"), cancellable = true)
    private void halo$binding(int index, CallbackInfoReturnable<Uniform> ci) {
        ci.setReturnValue(IrisMeshBridge.materialBinding((GlProgram)(Object)this, index, ci.getReturnValue()));
    }
}
