package network.azusake.halo.compat.iris;

import java.io.ByteArrayInputStream;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFactory;
import org.slf4j.LoggerFactory;

/** Isolated 1.20.1 Iris bridge. Iris owns variant programs, framebuffers, samplers and disposal. */
public final class IrisMeshBridge {
    private static final Map<Object, ShaderProgram> PROGRAMS = new IdentityHashMap<>();
    private static boolean building;
    private static long generation;
    private static Method getManager, getPipeline;
    private IrisMeshBridge() {}

    public static void prepare(Object pipeline) {
        try {
            ClassLoader loader = IrisMeshBridge.class.getClassLoader();
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris", false, loader);
            getManager = iris.getMethod("getPipelineManager");
            getPipeline = getManager.getReturnType().getMethod("getPipelineNullable");
            Class<?> keys = Class.forName("net.irisshaders.iris.pipeline.programs.ShaderKey", false, loader);
            Object key = keys.getField("TEXTURED_COLOR").get(null);
            Object programId = keys.getMethod("getProgram").invoke(key);
            Field resolverField = pipeline.getClass().getDeclaredField("resolver");
            resolverField.setAccessible(true);
            Object resolver = resolverField.get(pipeline);
            Object source = resolver.getClass().getMethod("resolve", programId.getClass()).invoke(resolver, programId);
            Method create = pipeline.getClass().getDeclaredMethod("createShader", String.class, Optional.class, keys);
            create.setAccessible(true);
            building = true;
            ShaderProgram program;
            try { program = (ShaderProgram) create.invoke(pipeline, "halo_mesh_" + (++generation), source, key); }
            finally { building = false; }
            for (String uniform : List.of("HaloMaskEnabled", "HaloMaskMode", "HaloMaskThreshold", "HaloMaskOffset", "HaloMaskTexture"))
                if (program.getUniform(uniform) == null && program.getUniform("iris_" + uniform) == null)
                    throw new IllegalStateException("Missing " + uniform);
            PROGRAMS.put(pipeline, program);
            LoggerFactory.getLogger("HaloMeshShader").info("Prepared mesh material inside Iris gbuffers_textured pipeline");
        } catch (ReflectiveOperationException | RuntimeException error) {
            Throwable cause = error instanceof InvocationTargetException invocation ? invocation.getCause() : error;
            LoggerFactory.getLogger("HaloMeshShader").error("Could not prepare Iris mesh material; mesh is paused for this shader pipeline", cause);
        }
    }

    public static void forget(Object pipeline) { PROGRAMS.remove(pipeline); }

    public static ShaderProgram currentProgram() {
        if (getManager == null || getPipeline == null) return null;
        try { return PROGRAMS.get(getPipeline.invoke(getManager.invoke(null))); }
        catch (ReflectiveOperationException error) { return null; }
    }

    public static ResourceFactory resources(ResourceFactory original) {
        if (!building) return original;
        return id -> original.getResource(id).map(resource -> new Resource(resource.getPack(), () -> {
            String source;
            try (var input = resource.getInputStream()) { source = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
            return new ByteArrayInputStream(IrisMeshShaderSource.patch(id.getPath(), source).getBytes(StandardCharsets.UTF_8));
        }));
    }
}
