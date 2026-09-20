package network.azusake.halo.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import network.azusake.halo.config.HaloModConfigStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Seed the model and its halo from the current pointer, including the first preview frame. */
@Mixin(InventoryScreen.class)
public abstract class InventoryPreviewMouseMixin {
    @Shadow private float xMouse;
    @Shadow private float yMouse;

    @Inject(method = "extractBackground", at = @At("HEAD"))
    private void halo$currentPointer(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                     float tickDelta, CallbackInfo ci) {
        // Vanilla updates these only after extracting the background. Its initial (0, 0)
        // pose otherwise becomes the preview physics' starting anchor.
        if (HaloModConfigStore.get().isPlayerPreviewHaloEnabled()) {
            xMouse = mouseX;
            yMouse = mouseY;
        }
    }
}
