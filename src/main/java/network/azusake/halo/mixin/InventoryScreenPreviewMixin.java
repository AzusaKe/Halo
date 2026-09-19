package network.azusake.halo.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import network.azusake.halo.render.PlayerPreviewRenderer;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenPreviewMixin {
    @Unique private boolean halo$firstRender = true;

    @Inject(method = "render", at = @At("RETURN"))
    private void halo$discardInitialMousePose(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!halo$firstRender) return;
        halo$firstRender = false;
        // Survival inventory stores its real mouse position only after drawing the first frame.
        // Snap once more with that pose, then keep the normal persistent simulation.
        PlayerPreviewRenderer.resetAutomaticMotion();
    }

    @Redirect(method = "drawEntity(Lnet/minecraft/client/gui/DrawContext;FFFLorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/entity/LivingEntity;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;runAsFancy(Ljava/lang/Runnable;)V"))
    private static void halo$renderPreview(Runnable render, DrawContext context, float x, float y, float size,
                                           Vector3f translation, Quaternionf rotation,
                                           Quaternionf cameraRotation, LivingEntity entity) {
        PlayerPreviewRenderer.renderPlayer(context, entity, java.util.List.of(x, y, size), () -> RenderSystem.runAsFancy(render));
    }
}
