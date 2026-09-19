package network.azusake.halo.render;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import network.azusake.halo.api.v2.*;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.Vec3d;
import network.azusake.halo.core.render.*;
import network.azusake.halo.core.runtime.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LabPbrFixtureTest {
    private static String resource(Identifier id) {
        return "/labpbr-26.2/assets/" + id.getNamespace() + "/" + id.getPath();
    }
    private static String text(String name) throws IOException {
        try (var stream = LabPbrFixtureTest.class.getResourceAsStream(name)) {
            assertNotNull(stream, name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test void diagnosticPackProducesLitTexturedNormalsForAllThreePrimitivesAndBothBackends() throws Exception {
        for (String channel : List.of("control", "normal", "smoothness", "metal", "emission")) {
            var id = new Identifier("halo_verify:" + channel);
            var resources = new DefinitionResources();
            assertTrue(resources.reload(1, List.of(new ResourceInput(id, "labpbr-fixture",
                text("/labpbr-26.2/assets/halo_verify/halo_definitions/" + channel + ".json")))).isEmpty());
            var loader = new VisualAssetLoader(1, new VisualAssetLoader.Source() {
                public String model(Identifier model) throws IOException { return text(resource(model)); }
                public VisualResources.TextureInfo texture(Identifier texture) throws IOException {
                    try (var stream = getClass().getResourceAsStream(resource(texture))) {
                        assertNotNull(stream);
                        var image = ImageIO.read(stream);
                        return new VisualResources.TextureInfo(image.getWidth(), image.getHeight(), true);
                    }
                }
            }, problem -> fail(problem.toString()));
            var visuals = loader.load(resources.snapshot().assets());
            var texture = new Identifier("halo_verify:textures/" + channel + ".png");
            assertNotNull(getClass().getResource(resource(new Identifier("halo_verify:textures/" + channel + "_n.png"))));
            assertNotNull(getClass().getResource(resource(new Identifier("halo_verify:textures/" + channel + "_s.png"))));
            for (var mode : PrimitiveRenderMode.values()) {
                var runtime = new ClientRuntime(() -> 10_000L);
                runtime.definitions(resources.snapshot());
                var uuid = new UUID(0, 1);
                runtime.attach(uuid, id, false);
                var anchor = new AnchorPose(new AnchorVec3(0, 1.6, 0), new AnchorRotation(0,0,0,1));
                var sample = new FrameScene.EntitySample(uuid, 1, new Vec3d(0,0,0), true, false, false, anchor, anchor);
                var output = runtime.renderFrame(new FrameScene(1, 10_000, 10_000_000_000L,
                    new FrameScene.CameraSample(new Vec3d(0,1,4), new Vec3d(0,1,0), new Vec3d(1,0,0)),
                    Map.of(uuid, sample), new float[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1},
                    p -> .5f, t -> true, visuals, p -> new LightSample(3,12), mode));
                assertEquals(1, output.meshes().size());
                var batches = output.expandedBatches(visuals);
                assertTrue(batches.stream().anyMatch(b -> b.topology() == DrawBatch.Topology.QUADS));
                assertTrue(batches.stream().anyMatch(b -> b.topology() == DrawBatch.Topology.TRIANGLES));
                for (var batch : batches) {
                    assertTrue(batch.directionalLighting());
                    assertEquals(texture, batch.texture());
                    assertEquals(new LightSample(3,12), batch.light());
                    assertFalse(batch.vertices().isEmpty());
                    for (var v : batch.vertices()) {
                        float length = v.normalX()*v.normalX()+v.normalY()*v.normalY()+v.normalZ()*v.normalZ();
                        assertEquals(1f, length, 1e-4f);
                        assertTrue(Float.isFinite(v.u()) && Float.isFinite(v.v()));
                    }
                }
            }
        }
    }
}
