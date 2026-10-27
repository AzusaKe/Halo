package network.azusake.halo.compat.flashback.mixin;

import network.azusake.halo.compat.flashback.FlashbackBackend;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.moulberry.flashback.exporting.ExportJob",remap=false)
public abstract class ExportJobMixin {
    @Inject(method="run",at=@At("HEAD"),require=1)
    private void halo$start(CallbackInfo ci){FlashbackBackend.exportStart(this);}
    @Inject(method="doExport",at=@At(value="INVOKE",target="Lcom/moulberry/flashback/exporting/ExportJob;setServerTickAndWait(Lcom/moulberry/flashback/playback/ReplayServer;IZ)V"),require=1)
    private void halo$frame(CallbackInfo ci){FlashbackBackend.outputFrame(this);}
    @Inject(method="doExport",at=@At(value="INVOKE",target="Lcom/moulberry/flashback/state/EditorState;applyKeyframes(Lcom/moulberry/flashback/keyframe/handler/KeyframeHandler;F)V",shift=At.Shift.AFTER),require=1)
    private void halo$ready(CallbackInfo ci){FlashbackBackend.outputReady();}
    @Inject(method="run",at=@At("RETURN"),require=1)
    private void halo$end(CallbackInfo ci){FlashbackBackend.exportEnd();}
}
