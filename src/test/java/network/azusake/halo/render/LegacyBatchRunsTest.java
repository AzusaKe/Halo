package network.azusake.halo.render;

import java.util.List;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.render.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyBatchRunsTest {
    private static DrawBatch batch(String texture, int variant) {
        return new DrawBatch(variant == 1 ? DrawBatch.Topology.TRIANGLES : DrawBatch.Topology.QUADS,
            List.of(),new Identifier(texture), variant != 2,variant == 3,variant != 4,variant != 5,variant != 6,
            variant == 7 ? .8f : 1,variant == 8 ? .8f : 1,variant == 9 ? .8f : 1,variant == 10 ? .5f : 1,
            variant == 11 ? new MaterialState.Mesh(null) : MaterialState.LEGACY,
            variant == 12 ? new LightSample(3,4) : LightSample.FULL_BRIGHT,variant == 13);
    }
    @Test void onlyAdjacentIdenticalStateIsCombined() {
        var a= batch("halo:a",0); var b=batch("halo:b",0);
        var sequence=List.of(a,a,b,a,a);
        assertEquals(2,LegacyBatchRuns.end(sequence,0));
        assertEquals(3,LegacyBatchRuns.end(sequence,2));
        assertEquals(5,LegacyBatchRuns.end(sequence,3));
        for(int variant=1;variant<=13;variant++) {
            assertFalse(LegacyBatchRuns.compatible(a,batch("halo:a",variant)),"state "+variant);
            assertFalse(LegacyBatchRuns.compatible(batch("halo:a",variant),a),"state "+variant);
        }
    }
    @Test void cachedBrightnessRetainsUnsignedByteVertexColorPrecision() {
        for(float value:new float[]{0,.003f,.47f,.737f,1,1.4f,-.1f})
            assertEquals(Byte.toUnsignedInt((byte)(int)(value*255f))/255f,HaloDrawSubmitter.quantizedColor(value));
    }
}
