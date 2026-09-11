package network.azusake.halo.compat.ysm.mixin;

import network.azusake.halo.compat.ysm.YsmHeadCapture;
import network.azusake.halo.compat.ysm.YsmV265Symbols;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import com.mojang.blaze3d.vertex.PoseStack;

/** Captures the final animated Head immediately before YSM renders a living model. */
@Pseudo
@Mixin(targets = YsmV265Symbols.LIVING_GEO_RENDERER, remap = false)
public abstract class YsmGeoReplacedEntityRendererMixin {

    @ModifyArgs(
        method = YsmV265Symbols.RENDER_METHOD + YsmV265Symbols.LIVING_RENDER_DESCRIPTOR,
        at = @At(
            value = "INVOKE",
            target = YsmV265Symbols.LIVING_BASE_RENDER_INVOKE,
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void halo$captureYsmHead(Args args) {
        Object animatedModel = args.get(YsmV265Symbols.RENDER_MODEL_ARGUMENT);
        PoseStack matrices = args.get(YsmV265Symbols.RENDER_POSE_STACK_ARGUMENT);
        YsmHeadCapture.captureAndReleaseEntity(animatedModel, matrices);
    }
}
