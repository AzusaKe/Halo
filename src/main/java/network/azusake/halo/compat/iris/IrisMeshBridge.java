package network.azusake.halo.compat.iris;

import java.io.ByteArrayInputStream;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFactory;
import network.azusake.halo.render.HaloRenderer;
import org.slf4j.LoggerFactory;

/** Isolated 1.21.1 Iris bridge. Iris owns variant programs, framebuffers, samplers and disposal. */
public final class IrisMeshBridge {
    private enum Variant { FLAT, LIT_SOLID, LIT_TRANSLUCENT }
    private record Program(ShaderProgram shader, boolean smoothNormalPatch) {}
    private record Programs(Program flat, Program litSolid, Program litTranslucent) {}
    private static final Map<Object, Programs> PROGRAMS = new IdentityHashMap<>();
    private static boolean building;
    private static boolean buildingSmoothNormalPatch;
    private static Variant buildingVariant;
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
            Field resolverField = pipeline.getClass().getDeclaredField("resolver");
            resolverField.setAccessible(true);
            Object resolver = resolverField.get(pipeline);
            Method create = pipeline.getClass().getDeclaredMethod("createShader", String.class, Optional.class, keys);
            create.setAccessible(true);
            Program flat = tryCreate(pipeline, resolver, create, keys, "PARTICLES", Variant.FLAT);
            Program litSolid = tryCreate(pipeline, resolver, create, keys,
                "ENTITIES_SOLID_DIFFUSE", Variant.LIT_SOLID);
            Program litTranslucent = tryCreate(pipeline, resolver, create, keys,
                "ENTITIES_TRANSLUCENT", Variant.LIT_TRANSLUCENT);
            if (flat == null && litSolid == null && litTranslucent == null)
                throw new IllegalStateException("All Iris mesh variants failed");
            PROGRAMS.put(pipeline, new Programs(flat, litSolid, litTranslucent));
            LoggerFactory.getLogger("HaloMeshShader").info(
                "Prepared Iris mesh materials: flat={}, lit-solid={} (smooth-normal={}), lit-translucent={} (smooth-normal={})",
                flat != null, litSolid != null, litSolid != null && litSolid.smoothNormalPatch(),
                litTranslucent != null, litTranslucent != null && litTranslucent.smoothNormalPatch());
            if (litSolid != null || litTranslucent != null)
                HaloRenderer.getInstance().rebuildMeshBuffersForShaderPipeline();
        } catch (ReflectiveOperationException | RuntimeException error) {
            Throwable cause = error instanceof InvocationTargetException invocation ? invocation.getCause() : error;
            LoggerFactory.getLogger("HaloMeshShader").error("Could not prepare Iris mesh material; mesh is paused for this shader pipeline", cause);
        }
    }

    public static void forget(Object pipeline) { PROGRAMS.remove(pipeline); }

    public static ShaderProgram currentProgram() {
        return currentProgram(false, false);
    }

    public static ShaderProgram currentProgram(boolean directionalLighting) {
        return currentProgram(directionalLighting, false);
    }

    public static ShaderProgram currentProgram(boolean directionalLighting, boolean translucent) {
        if (getManager == null || getPipeline == null) return null;
        try {
            Programs programs = PROGRAMS.get(getPipeline.invoke(getManager.invoke(null)));
            if (programs == null) return null;
            Program program = !directionalLighting ? programs.flat()
                : translucent ? programs.litTranslucent() : programs.litSolid();
            return program == null ? null : program.shader();
        }
        catch (ReflectiveOperationException error) { return null; }
    }

    /**
     * Returns the isolated lit variant only when the shader source matched the
     * generic world-position derivative face-normal idiom. The caller keeps a
     * native entity RenderLayer active so Iris still owns its material state.
     */
    public static ShaderProgram currentSmoothNormalProgram(boolean translucent) {
        if (getManager == null || getPipeline == null) return null;
        try {
            Programs programs = PROGRAMS.get(getPipeline.invoke(getManager.invoke(null)));
            if (programs == null) return null;
            Program program = translucent ? programs.litTranslucent() : programs.litSolid();
            return program != null && program.smoothNormalPatch() ? program.shader() : null;
        } catch (ReflectiveOperationException error) { return null; }
    }

    public static ResourceFactory resources(ResourceFactory original) {
        if (!building) return original;
        return id -> original.getResource(id).map(resource -> new Resource(resource.getPack(), () -> {
            String source;
            try (var input = resource.getInputStream()) { source = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
            if (IrisMeshShaderSource.replacesWorldDerivativeFaceNormal(id.getPath(), source,
                    buildingVariant != Variant.FLAT)) buildingSmoothNormalPatch = true;
            return new ByteArrayInputStream(IrisMeshShaderSource.patch(id.getPath(), source,
                buildingVariant != Variant.FLAT).getBytes(StandardCharsets.UTF_8));
        }));
    }

    private static Program create(Object pipeline, Object resolver, Method create, Class<?> keys,
                                  String keyName, Variant variant) throws ReflectiveOperationException {
        Object key = keys.getField(keyName).get(null);
        Object programId = keys.getMethod("getProgram").invoke(key);
        Object source = resolver.getClass().getMethod("resolve", programId.getClass()).invoke(resolver, programId);
        building = true;
        buildingVariant = variant;
        buildingSmoothNormalPatch = false;
        ShaderProgram program;
        boolean smoothNormalPatch;
        try {
            program = (ShaderProgram) create.invoke(pipeline,
                "halo_mesh_" + variant.name().toLowerCase(Locale.ROOT) + "_" + (++generation), source, key);
            smoothNormalPatch = buildingSmoothNormalPatch;
        } finally {
            building = false;
            buildingVariant = null;
            buildingSmoothNormalPatch = false;
        }
        List<String> uniforms = new ArrayList<>(List.of("HaloMaskEnabled", "HaloMaskMode", "HaloMaskThreshold",
            "HaloMaskOffset", "HaloMaskTexture", "HaloLightCoord", "HaloLegacyAlphaCutoff"));
        for (String uniform : uniforms) {
            if (program.getUniform(uniform) == null && program.getUniform("iris_" + uniform) == null)
                throw new IllegalStateException("Missing " + uniform + " in " + variant.name().toLowerCase(Locale.ROOT));
        }
        return new Program(program, smoothNormalPatch);
    }

    private static Program tryCreate(Object pipeline, Object resolver, Method create, Class<?> keys,
                                     String keyName, Variant variant) {
        try {
            return create(pipeline, resolver, create, keys, keyName, variant);
        } catch (ReflectiveOperationException | RuntimeException error) {
            Throwable cause = error instanceof InvocationTargetException invocation ? invocation.getCause() : error;
            LoggerFactory.getLogger("HaloMeshShader").error("Could not prepare Iris {} mesh material",
                variant.name().toLowerCase(Locale.ROOT), cause);
            return null;
        }
    }
}
