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

/** Named-namespace hook used by Loom's 1.21.1 development runtime. */
@Pseudo
@Mixin(targets = Emf1211Symbols.MODEL_PART, remap = false)
public abstract class EmfModelPartNamedMixin {

    @Inject(
        method = Emf1211Symbols.RENDER_METHOD_NAMED + Emf1211Symbols.RENDER_DESCRIPTOR_NAMED,
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private void halo$captureEmfHeadNamed(
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
