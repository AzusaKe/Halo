package network.azusake.halo.compat.ysm.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import network.azusake.halo.compat.ysm.YsmHeadCapture;
import network.azusake.halo.compat.ysm.YsmV265Symbols;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
@Pseudo @Mixin(targets=YsmV265Symbols.LIVING_GEO_RENDERER,remap=false)
public abstract class YsmGeoReplacedEntityRendererMixin {
 @ModifyArgs(method=YsmV265Symbols.RENDER_METHOD+YsmV265Symbols.LIVING_RENDER_DESCRIPTOR,
  at=@At(value="INVOKE",target=YsmV265Symbols.LIVING_BASE_RENDER_INVOKE,remap=false),remap=false,require=0)
 private void halo$captureYsmHead(Args args){YsmHeadCapture.captureAndReleaseEntity(args.get(YsmV265Symbols.RENDER_MODEL_ARGUMENT),(PoseStack)args.get(YsmV265Symbols.RENDER_POSE_STACK_ARGUMENT));}
}
