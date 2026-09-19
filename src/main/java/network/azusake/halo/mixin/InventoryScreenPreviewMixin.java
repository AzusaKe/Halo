package network.azusake.halo.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import network.azusake.halo.render.PlayerPreviewRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep the preview scope around deferred model preparation and execution, inside its PIP target. */
@Mixin(PictureInPictureRenderer.class)
public abstract class InventoryScreenPreviewMixin {
    @WrapOperation(method="prepare", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/render/pip/PictureInPictureRenderer;renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    private void halo$rememberRoot(PictureInPictureRenderer<?> renderer, PictureInPictureRenderState state,
            PoseStack stack, SubmitNodeCollector collector, Operation<Void> original,
            @Local(argsOnly=true) FeatureRenderDispatcher dispatcher, @Share("haloDrawn") LocalRef<Boolean> drawn) {
        if (state instanceof GuiEntityRenderState entity && collector instanceof SubmitNodeStorage storage) {
            PlayerPreviewRenderer.renderPlayer(entity, stack, () -> {
                original.call(renderer, state, stack, collector);
                dispatcher.renderAllFeatures(storage);
            });
            drawn.set(true);
        } else original.call(renderer, state, stack, collector);
    }

    @WrapOperation(method="prepare", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lnet/minecraft/client/renderer/SubmitNodeStorage;)V"))
    private void halo$preview(FeatureRenderDispatcher dispatcher, SubmitNodeStorage storage, Operation<Void> original,
            @Share("haloDrawn") LocalRef<Boolean> drawn) {
        if (!Boolean.TRUE.equals(drawn.get())) original.call(dispatcher, storage);
    }
}
