package network.azusake.halo.compat.emf.mixin;

import network.azusake.halo.compat.emf.Emf1211Symbols;
import network.azusake.halo.compat.emf.EmfHeadCapture;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Official/Mojmap hook for the verified NeoForge 1.21.1 EMF ABI. */
@Pseudo
@Mixin(targets = Emf1211Symbols.MODEL_PART, remap = false)
public abstract class EmfModelPartNamedMixin {

    @Inject(
        method = Emf1211Symbols.RENDER_METHOD + Emf1211Symbols.RENDER_DESCRIPTOR,
        at = @At("HEAD"),
        remap = false,
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
        EmfHeadCapture.capture(matrices, (ModelPart) (Object) this);
    }
}
