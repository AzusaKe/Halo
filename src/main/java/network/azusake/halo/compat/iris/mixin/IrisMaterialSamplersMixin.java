package network.azusake.halo.compat.iris.mixin;

import java.util.Set;
import network.azusake.halo.compat.iris.IrisMeshBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Reserve the mask unit only for Halo's clones, keeping Iris's PBR samplers disjoint. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ExtendedShader", remap = false)
public abstract class IrisMaterialSamplersMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
        target = "Lnet/irisshaders/iris/gl/program/ProgramSamplers;builder(ILjava/util/Set;)Lnet/irisshaders/iris/gl/program/ProgramSamplers$Builder;"), index = 1)
    private Set<Integer> halo$reserveMask(Set<Integer> reserved) {
        return IrisMeshBridge.materialSamplerUnits(reserved);
    }
}
