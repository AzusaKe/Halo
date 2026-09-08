package network.azusake.halo.compat.emf;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import network.azusake.halo.physics.RenderHeadCapture;

/** Captures the EMF head through Halo's 26.2 deferred-submit pipeline. */
public final class EmfHeadCapture {

    private EmfHeadCapture() {
    }

    /**
     * Called at the head of EMFModelPart.render, after EMF's model animation
     * state has been applied but before that part mutates the live pose stack.
     * The shared capture records the raw root/pose and caches an entity-relative
     * main-pass anchor for the next deferred lookup.
     */
    public static void capture(PoseStack matrices, ModelPart part) {
        if (matrices == null || part == null || !part.visible
            || !EmfHeadMath.isHeadPart(part)) {
            return;
        }
        RenderHeadCapture.captureEmf(matrices, part);
    }
}


