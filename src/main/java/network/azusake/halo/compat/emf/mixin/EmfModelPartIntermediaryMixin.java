package network.azusake.halo.compat.emf.mixin;

import network.azusake.halo.compat.emf.EmfHeadCapture;
import network.azusake.halo.compat.emf.Emf1201Symbols;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Production/intermediary hook for the EMF 1.20.1 ABI family. */
@Pseudo
@Mixin(targets = Emf1201Symbols.MODEL_PART, remap = false)
public abstract class EmfModelPartIntermediaryMixin {

    @Inject(
        method = Emf1201Symbols.RENDER_METHOD_INTERMEDIARY
            + Emf1201Symbols.RENDER_DESCRIPTOR_INTERMEDIARY,
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private void halo$captureEmfHeadIntermediary(
        MatrixStack matrices,
        VertexConsumer vertices,
        int light,
        int overlay,
        float red,
        float green,
        float blue,
        float alpha,
        CallbackInfo ci
    ) {
        EmfHeadCapture.capture(matrices, (net.minecraft.client.model.ModelPart) (Object) this);
    }
}
