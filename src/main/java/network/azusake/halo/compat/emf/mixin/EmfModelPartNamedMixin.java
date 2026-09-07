package network.azusake.halo.compat.emf.mixin;

import network.azusake.halo.compat.emf.Emf1201Symbols;
import network.azusake.halo.compat.emf.EmfHeadCapture;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Named Forge userdev hook for the deobfuscated EMF class. */
@Pseudo
@Mixin(targets = Emf1201Symbols.MODEL_PART, remap = false)
public abstract class EmfModelPartNamedMixin {

    @Inject(
        method = Emf1201Symbols.RENDER_METHOD_NAMED,
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private void halo$captureEmfHeadNamed(
        PoseStack matrices,
        VertexConsumer vertices,
        int light,
        int overlay,
        float red,
        float green,
        float blue,
        float alpha,
        CallbackInfo ci
    ) {
        EmfHeadCapture.capture(matrices, (ModelPart) (Object) this);
    }
}
