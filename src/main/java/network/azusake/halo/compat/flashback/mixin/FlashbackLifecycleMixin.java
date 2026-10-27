package network.azusake.halo.compat.flashback.mixin;

import network.azusake.halo.compat.flashback.FlashbackBackend;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.moulberry.flashback.Flashback",remap=false)
public abstract class FlashbackLifecycleMixin {
    @Inject(method="updateIsInReplay",at=@At("RETURN"),require=1)
    private static void halo$lifecycle(CallbackInfo ci){FlashbackBackend.lifecycle();}
}
