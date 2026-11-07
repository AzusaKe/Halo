package network.azusake.halo.compat.replaymod.mixin;

import network.azusake.halo.compat.replaymod.ReplayMarkerSupport;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.replaymod.editor.gui.MarkerProcessor",remap=false)
public abstract class MarkerProcessorMixin {
    @Inject(method="apply",at=@At("HEAD"),require=1)
    private static void halo$begin(CallbackInfoReturnable<?> ci){ReplayMarkerSupport.begin();}
    @Inject(method="apply",at=@At("RETURN"),require=1)
    private static void halo$end(CallbackInfoReturnable<?> ci){ReplayMarkerSupport.end();}
}
