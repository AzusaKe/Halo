package network.azusake.halo.compat.replaymod.mixin;

import network.azusake.halo.compat.replaymod.ReplayMarkerSupport;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.replaymod.replaystudio.filter.DimensionTracker",remap=false)
public abstract class DimensionTrackerMixin {
    @Inject(method="onPacket",at=@At("HEAD"),require=1)
    private void halo$context(@Coerce Object stream,@Coerce Object data,CallbackInfoReturnable<Boolean> ci){ReplayMarkerSupport.contextPacket(data);}
}
