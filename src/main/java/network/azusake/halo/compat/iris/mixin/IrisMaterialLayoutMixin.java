package network.azusake.halo.compat.iris.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.backend.opengl.GlProgram;
import com.mojang.renderpearl.backend.opengl.GlRenderPipeline;
import com.mojang.renderpearl.backend.opengl.Uniform;
import java.util.Map;
import network.azusake.halo.compat.iris.IrisMeshBridge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shader reflection orders bindings; declaration order is not the compiled ABI. */
@Mixin(GlRenderPipeline.class)
public abstract class IrisMaterialLayoutMixin {
    @Shadow @Final private GlProgram program;
    @Unique private Map<Integer, Uniform> halo$bindings;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void halo$layout(CallbackInfo ci, @Local(argsOnly = true) BackendRenderPipeline.CreateInfo info) {
        halo$bindings = IrisMeshBridge.materialLayout(program, info.uniforms());
    }

    @Inject(method = "bind", at = @At("HEAD"))
    private void halo$bindings(CallbackInfo ci) {
        IrisMeshBridge.bindMaterialLayout(program, halo$bindings);
    }
}
