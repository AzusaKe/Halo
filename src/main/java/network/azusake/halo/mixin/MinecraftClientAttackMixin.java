package network.azusake.halo.mixin;

import net.minecraft.client.Minecraft;
import network.azusake.halo.client.HaloScepterClientInput;
import network.azusake.halo.item.HaloItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cancels crouching scepter attacks before vanilla can attack or mine. */
@Mixin(Minecraft.class)
public abstract class MinecraftClientAttackMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void halo$interceptScepterAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = (Minecraft) (Object) this;
        if (HaloScepterClientInput.interceptSneakingAttack(client)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void halo$cancelScepterBlockBreaking(boolean breaking, CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (client.player != null
            && client.player.isShiftKeyDown()
            && client.player.getMainHandItem().is(HaloItems.HALO_SCEPTER.get())) {
            if (client.gameMode != null) {
                client.gameMode.stopDestroyBlock();
            }
            ci.cancel();
        }
    }
}
