package network.azusake.halo.compat.iris.mixin;
import java.util.Map;
import com.mojang.blaze3d.vertex.VertexFormat;
import network.azusake.halo.compat.iris.IrisMeshBridge;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
/** Changes only sources generated while the private Halo program factory is active. */
@Pseudo @Mixin(targets="net.irisshaders.iris.pipeline.programs.ShaderCreator",remap=false)
public abstract class IrisShaderSourceMixin {
    @ModifyVariable(method="create",at=@At("HEAD"),argsOnly=true)
    private static VertexFormat halo$entityLayout(VertexFormat format) {
        return IrisMeshBridge.materialVertexFormat(format);
    }
    @ModifyVariable(method="create",at=@At("STORE"),ordinal=0)
    private static Map<?,String> halo$materialSources(Map<?,String> sources){return IrisMeshBridge.patch(sources);}
}
