package network.azusake.halo.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.joml.Matrix4f;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.core.Vec3d;
import network.azusake.halo.core.render.*;
import network.azusake.halo.core.runtime.PreviewSession;
import network.azusake.halo.core.runtime.PreviewOptions;
import network.azusake.halo.platform.HaloClientState;

/** Internal Minecraft preview integration; public only for adapter lifecycle and Mixin hooks. */
public final class PlayerPreviewRenderer {
    private static final PreviewSessionPool VIEWS = new PreviewSessionPool(options -> HaloClientState.get().openPreview(options));
    private PlayerPreviewRenderer() {}

    public static void beginFrame() {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (!HaloModConfigStore.get().isPlayerPreviewHaloEnabled()) { VIEWS.close(); return; }
        VIEWS.beginFrame(client.screen, client.level, motionOptions(), System.nanoTime());
    }
    public static void endFrame() { VIEWS.endFrame(); }
    public static void resetAutomaticMotion() { VIEWS.resetMotion(); }
    public static void clearAutomaticViews() { VIEWS.close(); PlayerPreviewCapture.clearAnchorScopes(); }
    private static PreviewOptions motionOptions() {
        return HaloModConfigStore.get().isPlayerPreviewHaloPhysicsEnabled() ? PreviewOptions.PHYSICS : PreviewOptions.RIGID;
    }

    /** Submit after the host's model buffers are flushed, while its projection and GUI lighting are active. */
    private static void draw(PreviewSession session, PreviewFrame frame) {
        RenderSystem.assertOnRenderThread();
        if (!HaloModConfigStore.get().isPlayerPreviewHaloEnabled()) return;
        var output = session.render(frame);
        HaloRenderer.getInstance().submitPreview(output, frame.visuals());
    }

    /**
     * Wrap a vanilla player renderer at its final preview root. Flushes model buffers before drawing
     * the halo. Invoked by the InventoryScreen.drawEntity adapter hook.
     */
    public static void renderPlayer(GuiEntityRenderState state, PoseStack stack, Runnable renderEntity) {
        var client=net.minecraft.client.Minecraft.getInstance();
        if(!(state.renderState() instanceof AvatarRenderState avatar)||client.level==null
            ||!(client.level.getEntity(avatar.id) instanceof Player entity)){renderEntity.run();return;}
        Object viewIdentity=java.util.List.of(state.x0(),state.y0(),state.x1(),state.y1());
        Matrix4f root=new Matrix4f(stack.last().pose()).translate(state.translation()).rotate(state.rotation());
        try(var capture=PlayerPreviewCapture.open(entity,root)){
            renderEntity.run();
            var head=capture.head();
            if(head==null||!entity.isAlive()||!HaloModConfigStore.get().isPlayerPreviewHaloEnabled())return;
            var assets=HaloMeshResources.snapshot();
            try(var lease=VIEWS.acquire(viewIdentity,entity.getUUID(),entity.getId(),motionOptions())){
                draw(lease.session(),new PreviewFrame(entity.getUUID(),entity.getId(),head,
                    new FrameScene.CameraSample(new Vec3d(0,0,0),new Vec3d(0,-1,0),new Vec3d(1,0,0)),
                    root.get(new float[16]),System.currentTimeMillis(),VIEWS.frameNanos(),LightSample.FULL_BRIGHT,
                    id->true,assets.visuals(),PreviewFrame.Projection.ORTHOGRAPHIC,HaloRenderer.getInstance().primitiveMode()));
            }
        }
    }
}
