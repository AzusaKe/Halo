package network.azusake.halo.compat.emf;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.world.phys.Vec3;
import network.azusake.halo.anchor.AnchorCaptureCoordinator;
import network.azusake.halo.api.v2.AnchorVec3;
import network.azusake.halo.core.runtime.PreviewAnchorHost;
import network.azusake.halo.physics.EntityRenderSnapshot;
import network.azusake.halo.physics.RenderHeadCapture;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EmfBaseModelCaptureTest {
    private final UUID wearer = UUID.randomUUID();
    private final Object world = new Object();
    private final PlayerModel body = new PlayerModel(root(), true);
    private final PlayerModel cape = new PlayerCapeModel(root());
    private final PlayerModel armor = new PlayerModel(root(), true);
    private final EntityRenderSnapshot snapshot = new EntityRenderSnapshot(wearer, 42, world,
        new AnchorVec3(0, 0, 0), true, true).withPlayerModel(body);

    @AfterEach
    void reset() {
        RenderHeadCapture.restoreContext(null);
        AnchorCaptureCoordinator.clearCaptures();
        EmfHeadCapture.clearForTests();
    }

    @Test
    void capeAndArmorCannotConsumeOrReplaceWorldCapture() {
        AnchorCaptureCoordinator.beginFrame(world);
        EmfHeadCapture.beginFrame(new Matrix4f(), Vec3.ZERO);
        AnchorCaptureCoordinator.beginEntityRender(wearer, 42, world, snapshot.position(), true);
        try {
            capture(cape, false);
            capture(armor, false);
            assertNull(EmfHeadCapture.getCurrent(wearer));
            body.head.setPos(8, 16, 24);
            capture(body, false);
            var expected = EmfHeadCapture.getCurrent(wearer);
            assertNotNull(expected);
            capture(cape, false);
            capture(armor, false);
            assertSame(expected, EmfHeadCapture.getCurrent(wearer));
            var resolved = AnchorCaptureCoordinator.resolve(wearer, 42, world, snapshot.position());
            assertNotNull(resolved);
            assertEquals(.5, resolved.position().x(), 1e-6);
            assertEquals(.75, resolved.position().y(), 1e-6);
            assertEquals(1.5, resolved.position().z(), 1e-6);
        } finally {
            AnchorCaptureCoordinator.endEntityRender();
        }
    }

    @Test
    void previewAlsoRejectsCapeBeforeAnimatedBody() {
        var host = new PreviewAnchorHost();
        try (var scope = host.open(wearer, 42, new Matrix4f().get(new float[16]))) {
            PreviewAnchorHost.beginEntityRender(wearer, 42);
            try {
                capture(cape, false);
                capture(armor, false);
                assertNull(scope.resolved());
                body.head.setPos(8, 16, 24);
                capture(body, false);
                assertNotNull(scope.resolved());
                assertEquals(.5, scope.resolved().x(), 1e-6);
                assertEquals(.75, scope.resolved().y(), 1e-6);
                capture(cape, false);
                assertEquals(.5, scope.resolved().x(), 1e-6);
            } finally {
                PreviewAnchorHost.endEntityRender();
            }
        }
    }

    @Test
    void shadowCannotConsumeMainWorldCapture() {
        EmfHeadCapture.beginFrame(new Matrix4f(), Vec3.ZERO);
        capture(body, true);
        assertNull(EmfHeadCapture.getCurrent(wearer));
    }

    private void capture(PlayerModel model, boolean auxiliary) {
        RenderHeadCapture.restoreContext(new RenderHeadCapture.Context(snapshot, model, auxiliary));
        EmfHeadCapture.captureNamedHead(new PoseStack(), model.head);
        RenderHeadCapture.restoreContext(null);
    }

    private static ModelPart root() {
        return new ModelPart(List.of(), Map.of(
            "head", new ModelPart(List.of(), Map.of("hat", leaf())),
            "body", new ModelPart(List.of(), Map.of("jacket", leaf(), "cape", leaf())),
            "left_arm", new ModelPart(List.of(), Map.of("left_sleeve", leaf())),
            "right_arm", new ModelPart(List.of(), Map.of("right_sleeve", leaf())),
            "left_leg", new ModelPart(List.of(), Map.of("left_pants", leaf())),
            "right_leg", new ModelPart(List.of(), Map.of("right_pants", leaf()))));
    }

    private static ModelPart leaf() { return new ModelPart(List.of(), Map.of()); }

}
