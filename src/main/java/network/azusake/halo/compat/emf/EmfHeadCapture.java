package network.azusake.halo.compat.emf;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import network.azusake.halo.physics.RenderHeadCapture;

/** Captures the EMF player head through Halo's deferred render scope. */
public final class EmfHeadCapture {
    private EmfHeadCapture() {}

    public static void capture(PoseStack matrices, ModelPart part) {
        if (matrices != null && part != null && part.visible && EmfHeadMath.isHeadPart(part)) {
            RenderHeadCapture.captureEmf(matrices, part);
        }
    }
}
