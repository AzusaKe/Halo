package network.azusake.halo.compat.flashback.mixin;

import network.azusake.halo.compat.flashback.FlashbackBackend;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.moulberry.flashback.playback.ReplayServer",remap=false)
public abstract class ReplayServerMixin {
    @Inject(method="handleGamePacket",at=@At("HEAD"),require=1)
    private void halo$timestamp(@Coerce Object buffer,CallbackInfo ci){FlashbackBackend.gamePacket(this,buffer);}
}
