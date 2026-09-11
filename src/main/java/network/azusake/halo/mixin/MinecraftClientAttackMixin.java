package network.azusake.halo.mixin;

import net.minecraft.client.MinecraftClient;
import network.azusake.halo.client.HaloScepterClientInput;
import network.azusake.halo.item.HaloItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cancels crouching scepter attacks before vanilla can attack or mine. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientAttackMixin {

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void halo$interceptScepterAttack(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (HaloScepterClientInput.interceptSneakingAttack(client)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void halo$cancelScepterBlockBreaking(boolean breaking, CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.player != null
            && client.player.isSneaking()
            && client.player.getMainHandStack().isOf(HaloItems.HALO_SCEPTER)) {
            if (client.interactionManager != null) {
                client.interactionManager.cancelBlockBreaking();
            }
            ci.cancel();
        }
    }
}
