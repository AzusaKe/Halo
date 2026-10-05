package network.azusake.halo.compat.iris.mixin;

import network.azusake.halo.render.HaloRenderer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Matrix4f;

@Pseudo
@Mixin(targets="net.irisshaders.iris.shadows.ShadowRenderer",remap=false)
public abstract class IrisBaShadowMixin {
    @Shadow public static Matrix4f MODELVIEW;
    @Shadow public static Matrix4f PROJECTION;
    @Inject(method="copyPreTranslucentDepth",at=@At("HEAD"))
    private void halo$submitShadows(CallbackInfo ci) {
        HaloRenderer.getInstance().submitBaShadows(MODELVIEW,PROJECTION);
    }
}
