package network.azusake.halo.mixin;

import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.resource.ResourceFactory;
import network.azusake.halo.compat.iris.IrisMeshBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Wraps resources only while the optional Iris bridge builds its private mesh variant. */
@Mixin(ShaderProgram.class)
public abstract class MeshShaderResourceMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static ResourceFactory halo$meshResources(ResourceFactory original) {
        return IrisMeshBridge.resources(original);
    }
}
