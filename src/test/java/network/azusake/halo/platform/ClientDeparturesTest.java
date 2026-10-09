package network.azusake.halo.platform;

import com.google.gson.JsonParser;
import java.util.Map;
import java.util.UUID;
import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorRotation;
import network.azusake.halo.api.v2.AnchorVec3;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.Vec3d;
import network.azusake.halo.core.render.FrameScene;
import network.azusake.halo.core.runtime.ClientRuntime;
import network.azusake.halo.data.HaloDefinition;
import network.azusake.halo.json.HaloDefinitionDeserializer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientDeparturesTest {
    private static final UUID ENTITY = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Identifier DEFINITION = new Identifier("halo:test");

    private ClientRuntime runtime() {
        var definition = new HaloDefinitionDeserializer().deserialize(JsonParser.parseString("""
            {"id":"halo:test","layers":[{"id":"ring","primitive":
            {"type":"billboard","texture":"halo:test.png","size":[1,1]}}]}
            """), HaloDefinition.class, null);
        var runtime = new ClientRuntime(() -> 10_000L);
        runtime.definitions(Map.of(DEFINITION, definition));
        runtime.attach(ENTITY, DEFINITION, false);
        return runtime;
    }

    private FrameScene frame(boolean loaded, int runtimeId) {
        var pos = new Vec3d(0, 64, 0);
        var anchor = new AnchorPose(new AnchorVec3(0, 65.6, 0), new AnchorRotation(0, 0, 0, 1));
        var entity = new FrameScene.EntitySample(ENTITY, runtimeId, pos, true, false, false, anchor, anchor);
        return new FrameScene(1, 10_000L, 10_000_000_000L,
            new FrameScene.CameraSample(new Vec3d(0, 64, 3), new Vec3d(0, 1, 0), new Vec3d(1, 0, 0)),
            loaded ? Map.of(ENTITY, entity) : Map.of(),
            new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1}, p -> .5f, t -> true);
    }

    @Test void healthyEntityReturnsThroughNormalFramesWithoutAnotherAttachPacket() {
        var runtime = runtime();
        assertFalse(runtime.renderFrame(frame(true, 1)).legacyBatches().isEmpty());
        for (int id = 2; id <= 4; id++) {
            var previous = runtime.getInstance(ENTITY);
            ClientDepartures.depart(runtime, ENTITY, false, false);
            assertNull(runtime.getInstance(ENTITY));
            assertEquals(DEFINITION, runtime.assignments().get(ENTITY));
            assertTrue(runtime.renderFrame(frame(false, id)).legacyBatches().isEmpty());
            assertFalse(runtime.renderFrame(frame(true, id)).legacyBatches().isEmpty());
            assertNotSame(previous, runtime.getInstance(ENTITY));
        }
    }

    @Test void confirmedNonPlayerDeathDoesNotRestoreOwnershipWhenAnEntityAppears() {
        var runtime = runtime();
        ClientDepartures.depart(runtime, ENTITY, false, true);
        assertFalse(runtime.assignments().containsKey(ENTITY));
        assertNull(runtime.getInstance(ENTITY));
        assertTrue(runtime.renderFrame(frame(true, 2)).legacyBatches().isEmpty());
    }

    @Test void playerDeathKeepsOwnershipAndRespawnRestoresThroughNormalFrames() {
        var runtime = runtime();
        var previous = runtime.getInstance(ENTITY);
        ClientDepartures.depart(runtime, ENTITY, true, true);
        assertNull(runtime.getInstance(ENTITY));
        assertEquals(DEFINITION, runtime.assignments().get(ENTITY));
        assertFalse(runtime.renderFrame(frame(true, 2)).legacyBatches().isEmpty());
        assertNotSame(previous, runtime.getInstance(ENTITY));
    }

    @Test void explicitHideBeforeUntrackingDoesNotReappearOnReturn() {
        var runtime = runtime();
        runtime.hide(ENTITY, DEFINITION);
        ClientDepartures.depart(runtime, ENTITY, false, false);
        assertFalse(runtime.assignments().containsKey(ENTITY));
        assertTrue(runtime.renderFrame(frame(true, 2)).legacyBatches().isEmpty());
    }

    @Test void disconnectClearsPreviouslyPreservedOwnership() {
        var runtime = runtime();
        ClientDepartures.depart(runtime, ENTITY, false, false);
        runtime.clear();
        assertTrue(runtime.assignments().isEmpty());
        assertTrue(runtime.renderFrame(frame(true, 2)).legacyBatches().isEmpty());
    }
}
