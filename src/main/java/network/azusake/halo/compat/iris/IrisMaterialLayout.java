package network.azusake.halo.compat.iris;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout.UniformDescription;
import com.mojang.renderpearl.backend.opengl.Uniform;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Translate the reflected, possibly optimized layout into Halo's reserved GPU bindings. */
final class IrisMaterialLayout {
    private IrisMaterialLayout() {}
    static Map<Integer, Uniform> bindings(List<UniformDescription> uniforms) {
        var bindings = new HashMap<Integer, Uniform>();
        for (int i = 0; i < uniforms.size(); i++) {
            if (uniforms.get(i).name().equals("HaloMaterial")) bindings.put(i, new Uniform.Ubo(7));
            if (uniforms.get(i).name().equals("HaloMask")) bindings.put(i, new Uniform.Sampler(3));
        }
        return Map.copyOf(bindings);
    }
}
