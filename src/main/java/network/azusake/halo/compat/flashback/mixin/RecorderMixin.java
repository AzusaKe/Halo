package network.azusake.halo.compat.flashback.mixin;

import java.util.function.Consumer;
import network.azusake.halo.compat.flashback.FlashbackBackend;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.moulberry.flashback.record.Recorder",remap=false)
public abstract class RecorderMixin {
    @Inject(method="writeCustomSnapshot",at=@At("TAIL"),require=1)
    private void halo$snapshot(Consumer<?> consumer,CallbackInfo ci){FlashbackBackend.snapshot(this,consumer);}
}
