package network.azusake.halo.compat.ysm.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import network.azusake.halo.physics.EntityRenderSnapshot;
import network.azusake.halo.physics.RenderHeadCapture;
import org.spongepowered.asm.mixin.Mixin;

/** YSM evaluates its copied bone buffer during submission, before deferred vanilla model draws. */
@Mixin(EntityRenderDispatcher.class)
public abstract class YsmEntityRenderContextMixin {
    @WrapMethod(method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V")
    private void halo$ysmScope(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
            PoseStack matrices, SubmitNodeCollector collector, Operation<Void> original) {
        var snapshot = ((EntityRenderSnapshot.Holder) state).halo$snapshot();
        if (snapshot == null || !snapshot.living()) {
            original.call(state, camera, x, y, z, matrices, collector);
            return;
        }
        var previous = RenderHeadCapture.suspendForPreview();
        RenderHeadCapture.beginDraw(snapshot, null);
        try { original.call(state, camera, x, y, z, matrices, collector); }
        finally { RenderHeadCapture.endEntityRender(); RenderHeadCapture.restoreContext(previous); }
    }
}
