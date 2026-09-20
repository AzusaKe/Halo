package network.azusake.halo.render;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.render.ObjMeshLoader;
import network.azusake.halo.core.render.VisualResources;
import network.azusake.halo.core.runtime.DefinitionSnapshot;
import network.azusake.halo.core.runtime.VisualAssetLoader;
import network.azusake.halo.json.HaloJsonLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static network.azusake.halo.platform.PlatformTypes.game;

import com.mojang.blaze3d.platform.NativeImage;

/** Client loading stage. Rendering only reads the published immutable pair. */
public final class HaloMeshResources {
    private static final Logger LOG = LoggerFactory.getLogger("HaloMeshResources");
    public record Snapshot(DefinitionSnapshot definitions, VisualResources visuals) {}
    private static volatile Snapshot published = new Snapshot(HaloJsonLoader.snapshot(), VisualResources.EMPTY);
    private static VisualAssetLoader loader;
    private static long generation;
    private static final java.util.Set<Identifier> warmed = new java.util.HashSet<>();
    private HaloMeshResources() {}

    public static Snapshot snapshot() { return published; }

    /** Called by the client definition reload listener on the resource apply/client thread. */
    public static void reload(ResourceManager manager, DefinitionSnapshot definitions) {
        warmed.clear();
        loader = new VisualAssetLoader(++generation, new VisualAssetLoader.Source() {
            @Override public String model(Identifier id) throws IOException {
                var resource = manager.getResource(game(id)).orElseThrow(() -> new IOException("Missing model resource"));
                try (var input = resource.open()) {
                    byte[] bytes = input.readNBytes(ObjMeshLoader.MAX_TEXT_LENGTH + 1);
                    if (bytes.length > ObjMeshLoader.MAX_TEXT_LENGTH) throw new IOException("OBJ exceeds 16 MiB limit");
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            }
            @Override public VisualResources.TextureInfo texture(Identifier id) throws IOException {
                var resource = manager.getResource(game(id)).orElseThrow(() -> new IOException("Missing texture resource"));
                VisualResources.TextureInfo info;
                try (var input = resource.open(); var image = NativeImage.read(NativeImage.Format.RGBA, input)) {
                    boolean opaque = true;
                    outer: for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                        if ((image.getPixelRGBA(x, y) >>> 24) != 255) { opaque = false; break outer; }
                    }
                    info = new VisualResources.TextureInfo(image.getWidth(), image.getHeight(), opaque);
                }
                // Preload through Minecraft's owner during loading, never first-load a PNG inside a mesh draw.
                Minecraft.getInstance().getTextureManager().getTexture(game(id));
                return info;
            }
        }, problem -> LOG.warn("Skipping mesh asset {}: {}", problem.resource(), problem.message()));
        VisualResources visuals = loader.load(definitions.assets());
        HaloRenderer.getInstance().reloadMeshBuffers(visuals);
        preloadLegacy(definitions);
        Snapshot next = new Snapshot(definitions, visuals);
        HaloRenderer.getInstance().reloadPrimitiveBuffers(next, false);
        published = next;
    }

    private static void preloadLegacy(DefinitionSnapshot definitions) {
        definitions.primitiveGeometries();
        for (Identifier id : definitions.legacyTextures()) {
            if (!warmed.add(id)) continue;
            try { Minecraft.getInstance().getTextureManager().getTexture(game(id)); }
            catch (RuntimeException error) { LOG.warn("Could not preload legacy texture {}", id, error); }
        }
    }

    /** Data-pack/integrated definition changes are reconciled during tick, outside the render path. */
    public static void refreshDefinitions() {
        DefinitionSnapshot definitions = HaloJsonLoader.snapshot();
        Snapshot previous = published;
        if (definitions == previous.definitions()) return;
        VisualResources visuals = previous.visuals();
        if (!definitions.assets().equals(previous.definitions().assets()) && loader != null) {
            visuals = loader.load(definitions.assets());
            HaloRenderer.getInstance().reloadMeshBuffers(visuals);
        }
        preloadLegacy(definitions);
        Snapshot next = new Snapshot(definitions, visuals);
        HaloRenderer.getInstance().reloadPrimitiveBuffers(next, false);
        published = next;
    }
}
