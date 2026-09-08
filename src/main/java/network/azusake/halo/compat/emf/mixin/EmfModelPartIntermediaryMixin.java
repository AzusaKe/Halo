package network.azusake.halo.compat.emf.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import network.azusake.halo.compat.emf.Emf262Symbols;
import network.azusake.halo.compat.emf.EmfHeadCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric production/intermediary 26.2 EMF render hook. */
@Pseudo
@Mixin(targets = Emf262Symbols.MODEL_PART, remap = false)
public abstract class EmfModelPartIntermediaryMixin {

    @Inject(
        method = Emf262Symbols.RENDER_METHOD_INTERMEDIARY
            + Emf262Symbols.RENDER_DESCRIPTOR_INTERMEDIARY,
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private void halo$captureEmfHeadIntermediary(
        PoseStack matrices,
        VertexConsumer vertices,
        int light,
        int overlay,
        int color,
        CallbackInfo ci
    ) {
        EmfHeadCapture.capture(matrices, (ModelPart) (Object) this);
    }
}

