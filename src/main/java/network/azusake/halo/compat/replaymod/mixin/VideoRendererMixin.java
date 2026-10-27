package network.azusake.halo.compat.replaymod.mixin;

import network.azusake.halo.compat.replaymod.ReplayModBackend;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.replaymod.render.rendering.VideoRenderer",remap=false)
public abstract class VideoRendererMixin {
    @Inject(method="updateForNextFrame",at=@At("HEAD"),require=1)
    private void halo$frame(CallbackInfoReturnable<Float> ci){ReplayModBackend.outputFrame(this);}
    @Inject(method={"finish","cancel","setFailure"},at=@At("HEAD"),require=1)
    private void halo$cleanup(CallbackInfo ci){ReplayModBackend.exportEnded();}
}
