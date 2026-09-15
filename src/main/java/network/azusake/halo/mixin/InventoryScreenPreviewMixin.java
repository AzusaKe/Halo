package network.azusake.halo.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import network.azusake.halo.render.PlayerPreviewRenderer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenPreviewMixin {
    @Redirect(method = "drawEntity(Lnet/minecraft/client/gui/DrawContext;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/entity/LivingEntity;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;runAsFancy(Ljava/lang/Runnable;)V"))
    private static void halo$renderPreview(Runnable render, DrawContext context, int x, int y, int size,
                                           Quaternionf rotation, Quaternionf cameraRotation, LivingEntity entity) {
        PlayerPreviewRenderer.renderPlayer(context, entity, java.util.List.of(x, y, size), () -> RenderSystem.runAsFancy(render));
    }
}
