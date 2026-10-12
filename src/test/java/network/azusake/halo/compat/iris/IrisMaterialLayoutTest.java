package network.azusake.halo.compat.iris;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout.UniformDescription;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.backend.opengl.Uniform;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IrisMaterialLayoutTest {
    private static UniformDescription buffer(String name) { return new UniformDescription(name, UniformType.UNIFORM_BUFFER); }
    private static UniformDescription sampler(String name) { return new UniformDescription(name, UniformType.COMBINED_IMAGE_SAMPLER); }

    @Test void reflectsEachPipelineOrderWithoutReplacingNativeOrPbrBindings() {
        var lit = IrisMaterialLayout.bindings(List.of(buffer("Projection"), sampler("HaloMask"), buffer("HaloMaterial"), sampler("normals")));
        var flat = IrisMaterialLayout.bindings(List.of(buffer("HaloMaterial"), sampler("Sampler0"), sampler("HaloMask")));
        assertEquals(new Uniform.Sampler(3), lit.get(1));
        assertEquals(new Uniform.Ubo(7), lit.get(2));
        assertNull(lit.get(0)); assertNull(lit.get(3));
        assertEquals(new Uniform.Ubo(7), flat.get(0));
        assertEquals(new Uniform.Sampler(3), flat.get(2));
        assertNull(flat.get(1));
    }

    @Test void optimizedOutBindingsAreNotInvented() {
        assertTrue(IrisMaterialLayout.bindings(List.of(buffer("Projection"), sampler("Sampler0"))).isEmpty());
        assertEquals(1, IrisMaterialLayout.bindings(List.of(sampler("HaloMask"))).size());
    }
}
