package network.azusake.halo.anchor;

import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorRotation;
import network.azusake.halo.api.v2.AnchorSource;
import network.azusake.halo.api.v2.AnchorVec3;
import network.azusake.halo.api.v2.HaloAnchorApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnchorCaptureCoordinatorTest {

    private final Object world = new Object();
    private final UUID entityUuid = UUID.randomUUID();

    @BeforeEach
    @AfterEach
    void reset() {
        AnchorCaptureCoordinator.resetForTests();
    }

    @Test
    void validatesSourceIdsAndRejectsDuplicates() {
        assertThrows(IllegalArgumentException.class, () -> HaloAnchorApi.register("not an id"));
        AnchorSource source = HaloAnchorApi.register("example:head");
        assertThrows(IllegalStateException.class, () -> HaloAnchorApi.register("example:head"));
        source.close();
        HaloAnchorApi.register("example:head").close();
    }

    @Test
    void onlyAcceptsMatchingMainPassScope() {
        AnchorSource source = HaloAnchorApi.register("example:head");
        AnchorPose pose = pose(10.0, 20.0, 30.0);
        AnchorCaptureCoordinator.beginFrame(world);

        assertFalse(source.submit(entityUuid, pose));
        AnchorCaptureCoordinator.beginEntityRender(entityUuid, 7, world,
            new AnchorVec3(1.0, 2.0, 3.0), false);
        assertFalse(source.submit(entityUuid, pose));
        AnchorCaptureCoordinator.endEntityRender();

        AnchorCaptureCoordinator.beginEntityRender(entityUuid, 7, world,
            new AnchorVec3(1.0, 2.0, 3.0), true);
        assertFalse(source.submit(UUID.randomUUID(), pose));
        assertTrue(source.submit(entityUuid, pose));
        AnchorCaptureCoordinator.endEntityRender();
    }

    @Test
    void lastAcceptedSubmissionWinsAndPositionRebases() {
        AnchorSource first = HaloAnchorApi.register("example:first");
        AnchorSource second = HaloAnchorApi.register("example:second");
        AnchorCaptureCoordinator.beginFrame(world);
        AnchorCaptureCoordinator.beginEntityRender(entityUuid, 7, world,
            new AnchorVec3(1.0, 2.0, 3.0), true);
        assertTrue(first.submit(entityUuid, pose(2.0, 4.0, 6.0)));
        assertTrue(second.submit(entityUuid, pose(3.0, 5.0, 7.0)));
        AnchorCaptureCoordinator.endEntityRender();

        AnchorPose resolved = AnchorCaptureCoordinator.resolve(entityUuid, 7, world,
            new AnchorVec3(11.0, 12.0, 13.0));
        assertNotNull(resolved);
        assertEquals(new AnchorVec3(13.0, 15.0, 17.0), resolved.position());

        second.close();
        assertNull(AnchorCaptureCoordinator.resolve(entityUuid, 7, world,
            new AnchorVec3(11.0, 12.0, 13.0)));
    }

    @Test
    void retainsOneFrameAndRejectsOldOrWrongEntityIdentity() {
        AnchorSource source = HaloAnchorApi.register("example:head");
        AnchorCaptureCoordinator.beginFrame(world);
        AnchorCaptureCoordinator.beginEntityRender(entityUuid, 7, world,
            new AnchorVec3(0.0, 0.0, 0.0), true);
        assertTrue(source.submit(entityUuid, pose(1.0, 2.0, 3.0)));
        AnchorCaptureCoordinator.endEntityRender();

        AnchorCaptureCoordinator.beginFrame(world);
        assertNotNull(AnchorCaptureCoordinator.resolve(entityUuid, 7, world,
            new AnchorVec3(0.0, 0.0, 0.0)));
        assertNull(AnchorCaptureCoordinator.resolve(entityUuid, 8, world,
            new AnchorVec3(0.0, 0.0, 0.0)));

        AnchorCaptureCoordinator.beginFrame(world);
        assertNull(AnchorCaptureCoordinator.resolve(entityUuid, 7, world,
            new AnchorVec3(0.0, 0.0, 0.0)));
    }

    @Test
    void worldSwitchAndClosedSourceClearCaptures() {
        AnchorSource source = HaloAnchorApi.register("example:head");
        AnchorCaptureCoordinator.beginFrame(world);
        AnchorCaptureCoordinator.beginEntityRender(entityUuid, 7, world,
            new AnchorVec3(0.0, 0.0, 0.0), true);
        assertTrue(source.submit(entityUuid, pose(1.0, 2.0, 3.0)));
        AnchorCaptureCoordinator.endEntityRender();

        Object otherWorld = new Object();
        AnchorCaptureCoordinator.beginFrame(otherWorld);
        assertNull(AnchorCaptureCoordinator.resolve(entityUuid, 7, otherWorld,
            new AnchorVec3(0.0, 0.0, 0.0)));
        source.close();
        source.close();
        assertFalse(source.submit(entityUuid, pose(1.0, 2.0, 3.0)));
    }

    @Test
    void entityUnloadImmediatelyInvalidatesCapture() {
        AnchorSource source = HaloAnchorApi.register("example:head");
        AnchorCaptureCoordinator.beginFrame(world);
        AnchorCaptureCoordinator.beginEntityRender(entityUuid, 7, world,
            new AnchorVec3(0.0, 0.0, 0.0), true);
        assertTrue(source.submit(entityUuid, pose(1.0, 2.0, 3.0)));
        AnchorCaptureCoordinator.endEntityRender();

        AnchorCaptureCoordinator.clearEntity(entityUuid);
        assertNull(AnchorCaptureCoordinator.resolve(entityUuid, 7, world,
            new AnchorVec3(0.0, 0.0, 0.0)));
    }

    private static AnchorPose pose(double x, double y, double z) {
        return new AnchorPose(new AnchorVec3(x, y, z), new AnchorRotation(0, 0, 0, 1));
    }
}
