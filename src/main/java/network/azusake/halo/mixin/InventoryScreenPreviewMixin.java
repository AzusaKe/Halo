package network.azusake.halo.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import network.azusake.halo.render.PlayerPreviewRenderer;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(GuiEntityRenderer.class)
public abstract class InventoryScreenPreviewMixin extends net.minecraft.client.gui.render.pip.PictureInPictureRenderer<GuiEntityRenderState> {
    protected InventoryScreenPreviewMixin(net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource) { super(bufferSource); }
    @WrapMethod(method="renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V")
    private void halo$preview(GuiEntityRenderState state,PoseStack stack,Operation<Void> original){
        PlayerPreviewRenderer.renderPlayer(state,stack,()->{original.call(state,stack);bufferSource.endBatch();});
    }
}
