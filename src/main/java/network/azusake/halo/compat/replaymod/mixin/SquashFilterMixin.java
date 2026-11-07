package network.azusake.halo.compat.replaymod.mixin;

import network.azusake.halo.compat.replaymod.ReplayMarkerSupport;
import network.azusake.halo.compat.replaymod.SquashStateAccess;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.replaymod.replaystudio.filter.SquashFilter",remap=false)
public abstract class SquashFilterMixin implements SquashStateAccess {
    @Unique private ReplayMarkerSupport.State halo$replayState;
    @Unique private ReplayMarkerSupport.State halo$state(){
        if(halo$replayState==null)halo$replayState=ReplayMarkerSupport.filterState();return halo$replayState;
    }
    @Override public void halo$copyReplayState(ReplayMarkerSupport.State state){halo$replayState=state;}
    @Inject(method="<init>",at=@At("RETURN"),require=1)
    private void halo$seed(CallbackInfo ci){halo$state();}
    @Inject(method="onPacket",at=@At("HEAD"),cancellable=true,require=1)
    private void halo$fold(@Coerce Object stream,@Coerce Object data,CallbackInfoReturnable<Boolean> ci){
        if(ReplayMarkerSupport.capture(halo$state(),data))ci.setReturnValue(false);
    }
    @Inject(method="copy",at=@At("RETURN"),require=1)
    private void halo$copy(CallbackInfoReturnable<Object> ci){
        ((SquashStateAccess)ci.getReturnValue()).halo$copyReplayState(ReplayMarkerSupport.copy(halo$state()));
    }
    @Inject(method="onEnd",at=@At("RETURN"),require=1)
    private void halo$flush(@Coerce Object stream,long timestamp,CallbackInfo ci){ReplayMarkerSupport.flush(halo$state(),stream,timestamp);}
}
