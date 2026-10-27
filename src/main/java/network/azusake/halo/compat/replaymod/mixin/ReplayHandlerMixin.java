package network.azusake.halo.compat.replaymod.mixin;

import network.azusake.halo.compat.replaymod.ReplayModBackend;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.replaymod.replay.ReplayHandler",remap=false)
public abstract class ReplayHandlerMixin {
    @Inject(method={"doJump","setQuickMode"},at=@At("HEAD"),require=1)
    private void halo$seek(CallbackInfo ci){ReplayModBackend.seekRequested();}
}
