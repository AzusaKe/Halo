package network.azusake.halo.compat.emf.mixin;

import network.azusake.halo.compat.emf.EmfHeadCapture;
import network.azusake.halo.compat.emf.Emf261Symbols;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Named-namespace hook used by Loom's 1.21.1 development runtime. */
@Pseudo
@Mixin(targets = Emf261Symbols.MODEL_PART, remap = false)
public abstract class EmfModelPartNamedMixin {

    @Inject(
        method = Emf261Symbols.RENDER_METHOD_NAMED + Emf261Symbols.RENDER_DESCRIPTOR_NAMED,
        at = @At("HEAD"),
        remap = false,
        cancellable = true,
        require = 0
    )
    private void halo$captureEmfHeadNamed(
        PoseStack matrices,
        VertexConsumer vertices,
        int light,
        int overlay,
        int color,
        CallbackInfo ci
    ) {
        EmfHeadCapture.capture(matrices, (net.minecraft.client.model.geom.ModelPart) (Object) this);
        if (EmfHeadCapture.isPreviewPoseOnly()) ci.cancel();
    }
}
