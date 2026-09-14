package network.azusake.halo.render;

import net.minecraft.client.render.LightmapTextureManager;
import network.azusake.halo.core.render.LightSample;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HaloRendererLightTest {
    @Test void coreLightChannelsMapToVanillaPackedCoordinates() {
        assertEquals(LightmapTextureManager.pack(3, 12), HaloRenderer.packLight(new LightSample(3, 12)));
        assertEquals(LightmapTextureManager.MAX_LIGHT_COORDINATE,
            HaloRenderer.packLight(LightSample.UNAVAILABLE));
        assertEquals(LightmapTextureManager.MAX_LIGHT_COORDINATE,
            HaloRenderer.packLight(LightSample.FULL_BRIGHT));
    }
}
