package network.azusake.halo.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import network.azusake.halo.render.PlayerPreviewRenderer;
import network.azusake.halo.render.HaloDrawSubmitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Capture during CPU model preparation, then submit into the host's actual PIP pass. */
@Mixin(PictureInPictureRenderer.class)
public abstract class InventoryScreenPreviewMixin {
    @WrapOperation(method="prepare", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/render/pip/PictureInPictureRenderer;renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    private void halo$prepare(PictureInPictureRenderer<?> renderer, PictureInPictureRenderState state,
            PoseStack stack, SubmitNodeCollector collector, Operation<Void> original,
            @Local(argsOnly=true) FeatureRenderDispatcher dispatcher,
            @Share("haloFrame") LocalRef<FeatureRenderDispatcher.PreparedFrame> frame,
            @Share("haloDraw") LocalRef<HaloDrawSubmitter.PreparedSubmission> draw) {
        if (state instanceof GuiEntityRenderState entity && collector instanceof SubmitNodeStorage storage) {
            draw.set(PlayerPreviewRenderer.preparePlayer(entity, stack, () -> {
                original.call(renderer, state, stack, collector);
                frame.set(dispatcher.prepareFrame(storage));
            }));
        } else original.call(renderer, state, stack, collector);
    }
    @WrapOperation(method="prepare", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;prepareFrame(Lnet/minecraft/client/renderer/SubmitNodeStorage;)Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;"))
    private FeatureRenderDispatcher.PreparedFrame halo$prepared(FeatureRenderDispatcher dispatcher, SubmitNodeStorage storage,
            Operation<FeatureRenderDispatcher.PreparedFrame> original,
            @Share("haloFrame") LocalRef<FeatureRenderDispatcher.PreparedFrame> frame) {
        return frame.get() == null ? original.call(dispatcher, storage) : frame.get();
    }
    @WrapOperation(method="prepare", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lcom/mojang/renderpearl/api/commands/RenderPass;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;)V"))
    private void halo$draw(RenderPass pass, FeatureRenderDispatcher.PreparedFrame frame, Operation<Void> original,
            @Share("haloDraw") LocalRef<HaloDrawSubmitter.PreparedSubmission> draw) {
        original.call(pass, frame);
        if (draw.get() != null) draw.get().submit(pass);
    }
}
