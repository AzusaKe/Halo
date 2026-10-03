package network.azusake.halo.compat.caustica.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import network.azusake.halo.compat.caustica.CausticaBridge;

/** rewrite 0cc9d0af bridge. No public world-frame callback exists in API 0.8.0. */
@Pseudo
@Mixin(targets="dev.comfyfluffy.caustica.minecraft.client.MinecraftFrameAdapter",remap=false)
public abstract class CausticaFrameMixin {
    @Inject(method="capture",at=@At("HEAD"),require=1)
    private void halo$begin(CallbackInfoReturnable<?> cir){CausticaBridge.beginCapture();}
    @Inject(method="capture",at=@At("RETURN"),require=1)
    private void halo$finish(CallbackInfoReturnable<?> cir){CausticaBridge.finishCapture(cir.getReturnValue()!=null);}
}
