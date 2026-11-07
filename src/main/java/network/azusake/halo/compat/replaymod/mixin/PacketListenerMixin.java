package network.azusake.halo.compat.replaymod.mixin;

import network.azusake.halo.compat.replaymod.ReplayModBackend;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.replaymod.recording.packet.PacketListener",remap=false)
public abstract class PacketListenerMixin {
    @Inject(method="<init>",at=@At("RETURN"),require=1)
    private void halo$recordingCreated(CallbackInfo ci){ReplayModBackend.recorderCreated(this);}
    @Inject(method="save",at=@At("HEAD"),require=1)
    private void halo$initialSnapshot(CallbackInfo ci){ReplayModBackend.beforeSave(this);}
}
