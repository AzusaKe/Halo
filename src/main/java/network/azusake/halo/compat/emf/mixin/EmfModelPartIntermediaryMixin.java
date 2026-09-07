package network.azusake.halo.compat.emf.mixin;

import network.azusake.halo.compat.emf.Emf1211Symbols;
import network.azusake.halo.compat.emf.EmfHeadCapture;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Production/intermediary hook for the EMF 1.21.1 ABI family. */
@Pseudo
@Mixin(targets = Emf1211Symbols.MODEL_PART, remap = false)
public abstract class EmfModelPartIntermediaryMixin {

    @Inject(
        method = Emf1211Symbols.RENDER_METHOD_INTERMEDIARY
            + Emf1211Symbols.RENDER_DESCRIPTOR_INTERMEDIARY,
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private void halo$captureEmfHeadIntermediary(
        MatrixStack matrices,
        VertexConsumer vertices,
        int light,
        int overlay,
        int color,
        CallbackInfo ci
    ) {
        EmfHeadCapture.capture(matrices, (ModelPart) (Object) this);
    }
}
