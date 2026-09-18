package network.azusake.halo.render;

import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.render.LightSample;
import network.azusake.halo.core.render.MaterialState;
import network.azusake.halo.core.render.MeshDraw;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.client.renderer.LightTexture;

class HaloRendererLightTest {
    @Test void coreLightChannelsMapToVanillaPackedCoordinates() {
        assertEquals(LightTexture.pack(3, 12), HaloRenderer.packLight(new LightSample(3, 12)));
        assertEquals(LightTexture.FULL_BRIGHT,
            HaloRenderer.packLight(LightSample.UNAVAILABLE));
        assertEquals(LightTexture.FULL_BRIGHT,
            HaloRenderer.packLight(LightSample.FULL_BRIGHT));
    }

    @Test void onlyOpaqueDirectionalMeshesEnterTheSolidShaderStage() {
        assertTrue(HaloRenderer.submitBeforeTranslucents(draw(true, false), true));
        assertFalse(HaloRenderer.submitBeforeTranslucents(draw(true, true), true));
        assertFalse(HaloRenderer.submitBeforeTranslucents(draw(false, false), true));
        assertFalse(HaloRenderer.submitBeforeTranslucents(draw(false, true), true));
        assertFalse(HaloRenderer.submitBeforeTranslucents(draw(true, false), false));
    }

    private static MeshDraw draw(boolean directionalLighting, boolean blend) {
        return new MeshDraw(new Identifier("test:model"), new Identifier("test:texture"), new float[]{
            1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1
        }, true, blend, true, !blend, 1,1,1,1, false,
            new MaterialState.Mesh(null), LightSample.FULL_BRIGHT, directionalLighting);
    }
}
