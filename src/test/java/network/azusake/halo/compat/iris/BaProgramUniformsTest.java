package network.azusake.halo.compat.iris;

import network.azusake.halo.core.render.BaMaterial;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BaProgramUniformsTest {
    @Test void alternatingInstancesUploadFullDefaultsAndDoNotMutatePreviousParameters() {
        var base=BaMaterial.defaults(BaMaterial.Type.HAIR);
        var a=new BaMaterial(BaMaterial.Type.HAIR,null,null,base.with("SpecStrength",.2f));
        var b=new BaMaterial(BaMaterial.Type.HAIR,null,null,base.with("SpecStrength",.9f));
        assertEquals(.2f,BaProgramUniforms.values(a).get("HaloBA_SpecStrength").get(0));
        assertEquals(.9f,BaProgramUniforms.values(b).get("HaloBA_SpecStrength").get(0));
        assertEquals(.2f,BaProgramUniforms.values(a).get("HaloBA_SpecStrength").get(0));
        assertEquals(BaProgramUniforms.values(a).keySet(),BaProgramUniforms.values(b).keySet());
        assertTrue(BaProgramUniforms.values(a).containsKey("HaloBA_mask_default"));
        assertEquals("HaloBA_From_Min",BaProgramUniforms.name("From Min"));
    }
}
