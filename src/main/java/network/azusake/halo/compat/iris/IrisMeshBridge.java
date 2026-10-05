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

/** Isolated 1.20.1 Iris bridge. Iris owns variant programs, framebuffers, samplers and disposal. */
public final class IrisMeshBridge {
    private enum Variant { FLAT, LIT_SOLID, LIT_TRANSLUCENT, BA, SHADOW }
    private record Programs(ShaderProgram flat, ShaderProgram litSolid, ShaderProgram litTranslucent, ShaderProgram ba, ShaderProgram shadow) {}
    private static final Map<Object, Programs> PROGRAMS = new IdentityHashMap<>();
    private static boolean building;
    private static Variant buildingVariant;
    private static long generation;
    private static Method getManager, getPipeline;
    private static int baMaskTexture, baSpecTexture;
    public static boolean renderingShadow;
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
            ShaderProgram flat = tryCreate(pipeline, resolver, create, keys, "PARTICLES", Variant.FLAT);
            ShaderProgram litSolid = tryCreate(pipeline, resolver, create, keys,
                "ENTITIES_SOLID_DIFFUSE", Variant.LIT_SOLID);
            ShaderProgram litTranslucent = tryCreate(pipeline, resolver, create, keys,
                "ENTITIES_TRANSLUCENT", Variant.LIT_TRANSLUCENT);
            if (flat == null && litSolid == null && litTranslucent == null)
                throw new IllegalStateException("All Iris mesh variants failed");
            ShaderProgram ba = tryCreate(pipeline, resolver, create, keys, "ENTITIES_TRANSLUCENT", Variant.BA);
            Method createShadow=pipeline.getClass().getDeclaredMethod("createShadowShader",String.class,Optional.class,keys);
            createShadow.setAccessible(true);
            ShaderProgram shadow=ba==null ? null : tryCreate(pipeline,resolver,createShadow,keys,"SHADOW_ENTITIES_CUTOUT",Variant.SHADOW);
            PROGRAMS.put(pipeline, new Programs(flat, litSolid, litTranslucent, ba,shadow));
            LoggerFactory.getLogger("HaloMeshShader").info(
                "Prepared Iris mesh materials: flat={}, lit-solid={}, lit-translucent={}, BA={}, shadow={}",
                flat != null, litSolid != null, litTranslucent != null,ba!=null,shadow!=null);
            if (litSolid != null || litTranslucent != null)
                HaloRenderer.getInstance().rebuildMeshBuffersForShaderPipeline();
        } catch (ReflectiveOperationException | RuntimeException error) {
            Throwable cause = error instanceof InvocationTargetException invocation ? invocation.getCause() : error;
            LoggerFactory.getLogger("HaloMeshShader").error("Could not prepare Iris mesh material; mesh is paused for this shader pipeline", cause);
        }
    }

    public static void forget(Object pipeline) { PROGRAMS.remove(pipeline); }
    public static Set<Integer> materialSamplerUnits(Set<Integer> reserved) {
        if (!building || buildingVariant == Variant.FLAT) return reserved;
        var result = new HashSet<>(reserved); result.add(3); return result;
    }
    public static void setBaTextures(int mask, int spec) { baMaskTexture=mask; baSpecTexture=spec; }
    public static void addBaSamplers(Object builder) {
        if (!building || buildingVariant != Variant.BA) return;
        try {
            Class<?> holder = Class.forName("net.irisshaders.iris.gl.sampler.SamplerHolder");
            Method add = holder.getMethod("addDynamicSampler", java.util.function.IntSupplier.class, String[].class);
            add.invoke(builder, (java.util.function.IntSupplier) () -> baMaskTexture, new String[]{"iris_HaloBA_mask"});
            add.invoke(builder, (java.util.function.IntSupplier) () -> baSpecTexture, new String[]{"iris_HaloBA_spec"});
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("BA sampler registration failed",error); }
    }
    public static ShaderProgram baProgram() {
        if (getManager == null) return null;
        try {
            Programs programs=PROGRAMS.get(getPipeline.invoke(getManager.invoke(null)));
            return programs == null ? null : programs.ba();
        } catch (ReflectiveOperationException error) { return null; }
    }

    public static ShaderProgram currentProgram() {
        return currentProgram(false, false);
    }
    public static ShaderProgram shadowProgram() {
        if (getManager == null) return null;
        try {
            Programs programs=PROGRAMS.get(getPipeline.invoke(getManager.invoke(null)));
            return programs == null ? null : programs.shadow();
        } catch (ReflectiveOperationException error) { return null; }
    }

    public static ShaderProgram currentProgram(boolean directionalLighting) {
        return currentProgram(directionalLighting, false);
    }

    public static ShaderProgram currentProgram(boolean directionalLighting, boolean translucent) {
        if (getManager == null || getPipeline == null) return null;
        try {
            Programs programs = PROGRAMS.get(getPipeline.invoke(getManager.invoke(null)));
            if (programs == null) return null;
            if (!directionalLighting) return programs.flat();
            return translucent ? programs.litTranslucent() : programs.litSolid();
        }
        catch (ReflectiveOperationException error) { return null; }
    }

    public static ResourceFactory resources(ResourceFactory original) {
        if (!building) return original;
        return id -> original.getResource(id).map(resource -> new Resource(resource.getPack(), () -> {
            String source;
            try (var input = resource.getInputStream()) { source = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
            String patched=buildingVariant==Variant.SHADOW ? IrisMeshShaderSource.patchShadow(id.getPath(),source)
                : IrisMeshShaderSource.patch(id.getPath(), source, buildingVariant != Variant.FLAT);
            if (buildingVariant == Variant.BA && id.getPath().endsWith(".json")) patched=BaProgramUniforms.patchJson(patched);
            if (buildingVariant == Variant.BA && id.getPath().endsWith(".fsh"))
                patched=patched.replaceAll("\\bHaloBA_", "iris_HaloBA_");
            if (buildingVariant == Variant.BA && id.getPath().endsWith(".vsh"))
                patched=patched.replace("halo_meshMain();", "halo_meshMain(); normalMat.w=0.9;");
            if (Boolean.getBoolean("halo.ba.dumpShaders") && buildingVariant==Variant.BA) {
                java.nio.file.Path dump=java.nio.file.Path.of("ba-shader-dump",id.getPath());
                java.nio.file.Files.createDirectories(dump.getParent());
                java.nio.file.Files.writeString(dump,patched);
            }
            return new ByteArrayInputStream(patched.getBytes(StandardCharsets.UTF_8));
        }));
    }

    private static ShaderProgram create(Object pipeline, Object resolver, Method create, Class<?> keys,
                                        String keyName, Variant variant) throws ReflectiveOperationException {
        Object key = keys.getField(keyName).get(null);
        Object programId = keys.getMethod("getProgram").invoke(key);
        // Bliss 2.1.2 has no entities_translucent source; Iris falls back to the deferred entity
        // program. Clone the audited water/forward source with ENTITY vertex inputs instead.
        if (variant==Variant.BA) programId=programId.getClass().getField("Water").get(null);
        Object source = resolver.getClass().getMethod("resolve", programId.getClass()).invoke(resolver, programId);
        building = true;
        buildingVariant = variant;
        ShaderProgram program;
        try {
            program = (ShaderProgram) create.invoke(pipeline,
                "halo_mesh_" + variant.name().toLowerCase(Locale.ROOT) + "_" + (++generation), source, key);
        } finally {
            building = false;
            buildingVariant = null;
        }
        List<String> uniforms = new ArrayList<>(List.of("HaloMaskEnabled", "HaloMaskMode", "HaloMaskThreshold",
            "HaloMaskOffset", "HaloMaskTexture", "HaloLightCoord", "HaloLegacyAlphaCutoff"));
        if (variant==Variant.SHADOW) uniforms.remove("HaloLightCoord");
        for (String uniform : uniforms) {
            if (program.getUniform(uniform) == null && program.getUniform("iris_" + uniform) == null)
                throw new IllegalStateException("Missing " + uniform + " in " + variant.name().toLowerCase(Locale.ROOT));
        }
        if (variant == Variant.BA && program.getUniform("HaloBA_type") == null)
            throw new IllegalStateException("Pack has no Halo BA v1 forward branch; BA falls back to ordinary material");
        return program;
    }

    private static ShaderProgram tryCreate(Object pipeline, Object resolver, Method create, Class<?> keys,
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
