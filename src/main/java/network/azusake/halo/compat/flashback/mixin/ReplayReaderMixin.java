package network.azusake.halo.compat.flashback.mixin;

import network.azusake.halo.compat.flashback.FlashbackBackend;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.moulberry.flashback.io.ReplayReader",remap=false)
public abstract class ReplayReaderMixin {
    @Inject(method="handleSnapshot",at=@At("HEAD"),require=1)
    private void halo$begin(@Coerce Object server,CallbackInfo ci){FlashbackBackend.snapshotBegin(this,server);}
    @Inject(method="handleSnapshot",at=@At("RETURN"),require=1)
    private void halo$end(CallbackInfo ci){FlashbackBackend.snapshotEnd();}
}
