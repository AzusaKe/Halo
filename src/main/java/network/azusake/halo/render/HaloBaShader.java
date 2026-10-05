package network.azusake.halo.render;

import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import network.azusake.halo.compat.iris.BaProgramUniforms;
import network.azusake.halo.compat.iris.IrisMeshBridge;
import network.azusake.halo.core.render.BaMaterial;
import network.azusake.halo.core.render.BaOrientation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static network.azusake.halo.platform.PlatformTypes.game;

/** Upload the complete material on every draw, before Iris applies its dynamic samplers. */
final class HaloBaShader {
    private HaloBaShader() {}
    static void bind(MinecraftClient client, ShaderProgram shader, BaMaterial material) {
        if (shader != IrisMeshBridge.baProgram()) return;
        IrisMeshBridge.setBaTextures(material.mask() == null ? 0 : client.getTextureManager().getTexture(game(material.mask())).getGlId(),
            material.spec() == null ? 0 : client.getTextureManager().getTexture(game(material.spec())).getGlId());
        shader.getUniformOrDefault("HaloBA_type").set(material.type().ordinal()+1);
        shader.getUniformOrDefault("HaloBA_hasMask").set(material.mask()==null ? 0 : 1);
        shader.getUniformOrDefault("HaloBA_hasSpec").set(material.spec()==null ? 0 : 1);
        shader.getUniformOrDefault("HaloBA_fixedLight").set(Float.parseFloat(System.getProperty("halo.ba.fixedLight", "-1")));
        BaProgramUniforms.values(material).forEach((name, value) -> {
            var uniform=shader.getUniformOrDefault(name);
            switch(value.size()) {
                case 1 -> uniform.set(value.get(0));
                case 2 -> uniform.set(value.get(0),value.get(1));
                case 3 -> uniform.set(value.get(0),value.get(1),value.get(2));
                case 4 -> uniform.set(value.get(0),value.get(1),value.get(2),value.get(3));
                default -> throw new IllegalArgumentException("BA uniform width");
            }
        });
    }

    static void orientation(ShaderProgram shader, BaMaterial material, Matrix4f modelView) {
        if (material==null || shader != IrisMeshBridge.baProgram()) return;
        // Export B(x,y,z) -> M(x,z,-y). Camera quaternion maps view vectors to M world.
        // BA_SPEC rotates Blender Incoming about Y by pi/2 before combining directions.
        Matrix3f viewToWorld = new Matrix3f().rotation(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());
        Matrix3f partToWorld = new Matrix3f(viewToWorld).mul(new Matrix3f(modelView));
        try {
            var sample=BaOrientation.sample(partToWorld.get(new float[9]),viewToWorld.get(new float[9]),material.parameters());
            shader.getUniformOrDefault("HaloBA_viewToReference").set(new Matrix3f().set(sample.viewToReference()));
            float[] direction=sample.objectDirection();
            shader.getUniformOrDefault("HaloBA_objectDirection").set(direction[0],direction[1],direction[2]);
        } catch(IllegalArgumentException singular) {
            shader.getUniformOrDefault("HaloBA_viewToReference").set(new Matrix3f());
            shader.getUniformOrDefault("HaloBA_objectDirection").set(0f,0f,1f);
        }
    }
}
