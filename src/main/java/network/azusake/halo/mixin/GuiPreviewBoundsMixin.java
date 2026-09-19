package network.azusake.halo.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import network.azusake.halo.config.HaloModConfigStore;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;

/** Keep head accessories inside the 26.2 offscreen preview texture without moving the player. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiPreviewBoundsMixin {
    @WrapMethod(method = "entity")
    private void halo$headroom(EntityRenderState state, float scale, Vector3fc translation,
            Quaternionfc rotation, Quaternionfc camera, int x0, int y0, int x1, int y1, Operation<Void> original) {
        if (state instanceof AvatarRenderState && scale > 0 && HaloModConfigStore.get().isPlayerPreviewHaloEnabled()) {
            int padding = (int)Math.ceil(scale);
            original.call(state, scale, new Vector3f(translation).add(0, padding / (2f * scale), 0),
                rotation, camera, x0 - padding, y0 - padding, x1 + padding, y1);
        } else original.call(state, scale, translation, rotation, camera, x0, y0, x1, y1);
    }
}
