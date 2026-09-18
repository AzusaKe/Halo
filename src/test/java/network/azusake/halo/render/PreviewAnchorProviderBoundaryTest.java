package network.azusake.halo.render;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewAnchorProviderBoundaryTest {
    @Test void builtInPreviewProvidersUseThePublicApiWithoutInternalScopeDependencies() throws Exception {
        for (String provider : List.of("ysm/YsmPreviewCapture", "emf/EmfPreviewCapture")) {
            String resource = "network/azusake/halo/compat/" + provider + ".class";
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input, resource);
                String pool = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertTrue(pool.contains("network/azusake/halo/api/v2/HaloAnchorApi"), resource);
                for (String forbidden : List.of("PlayerPreviewCapture", "PlayerPreviewRenderer", "HaloPreviewApi", "PreviewAnchorCoordinator", "PreviewAnchorHost", "PreviewAnchorScope", "PreviewSession"))
                    assertFalse(pool.contains(forbidden), provider + " depends on " + forbidden);
            }
        }
    }
}
