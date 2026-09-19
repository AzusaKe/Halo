package network.azusake.halo.compat.iris;

import com.mojang.renderpearl.backend.opengl.GlProgram;
import com.mojang.renderpearl.backend.opengl.Uniform;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

/** Optional Iris 26.3 bridge. Halo alone owns the cloned programs and releases them with their pipeline. */
public final class IrisMeshBridge {
    private record Kind(boolean lit,boolean translucent){}
    private static final Map<RenderPipeline,Kind> KINDS=new IdentityHashMap<>();
    private static final Map<Object,Map<Kind,GlProgram>> PROGRAMS=new IdentityHashMap<>();
    private static final ThreadLocal<Kind> BUILDING=new ThreadLocal<>();
    private static Method getManager, getPipeline;
    private static boolean reportedSelection;
    private static long generation;
    private static final ThreadLocal<RenderPipeline> SELECTING = new ThreadLocal<>();
    private static final Map<GlProgram, Map<Integer, Uniform>> MATERIAL_BINDINGS = new IdentityHashMap<>();
    public static RenderPipeline selecting(RenderPipeline pipeline) {
        RenderPipeline previous = SELECTING.get();
        if (pipeline == null) SELECTING.remove(); else SELECTING.set(pipeline);
        return previous;
    }
    public static GlProgram selectedProgram(GlProgram original) {
        RenderPipeline pipeline = SELECTING.get();
        GlProgram selected = pipeline == null ? null : program(pipeline);
        if (selected == null) return original;
        return selected;
    }
    public static Map<Integer, Uniform> materialLayout(GlProgram shader,
            List<com.mojang.renderpearl.api.pipeline.BindGroupLayout.UniformDescription> uniforms) {
        if (!MATERIAL_BINDINGS.containsKey(shader)) return null;
        return IrisMaterialLayout.bindings(uniforms);
    }
    public static void bindMaterialLayout(GlProgram shader, Map<Integer, Uniform> bindings) {
        if (bindings != null) MATERIAL_BINDINGS.put(shader, bindings);
    }
    public static Uniform materialBinding(GlProgram shader, int index, Uniform original) {
        var bindings = MATERIAL_BINDINGS.get(shader);
        return bindings == null ? original : bindings.getOrDefault(index, original);
    }
    private IrisMeshBridge(){}
    @SuppressWarnings({"unchecked","rawtypes"})
    public static void assign(RenderPipeline pipeline,boolean lit,boolean translucent){
        KINDS.put(pipeline,new Kind(lit,translucent));
        if(!FabricLoader.getInstance().isModLoaded("iris"))return;
        try{
            Class<?> api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Class<? extends Enum> program=(Class<? extends Enum>)Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Object id=Enum.valueOf(program,lit?(translucent?"ENTITIES_TRANSLUCENT":"ENTITIES"):"PARTICLES");
            api.getMethod("assignPipeline",RenderPipeline.class,program).invoke(api.getMethod("getInstance").invoke(null),pipeline,id);
        }catch(ReflectiveOperationException ex){LoggerFactory.getLogger("halo").error("Cannot assign Halo's Iris entity pipeline",ex);}
    }
    public static void prepare(Object pipeline){
        reportedSelection=false;
        Map<Kind,GlProgram> shaders=new HashMap<>();PROGRAMS.put(pipeline,shaders);
        try{
            Class<?> iris=Class.forName("net.irisshaders.iris.Iris");
            getManager=iris.getMethod("getPipelineManager");
            getPipeline=getManager.getReturnType().getMethod("getPipelineNullable");
            Class<?> keys=Class.forName("net.irisshaders.iris.pipeline.programs.ShaderKey");
            var resolverField=pipeline.getClass().getDeclaredField("resolver");resolverField.setAccessible(true);
            Object resolver=resolverField.get(pipeline);
            Class<?> patchType=Class.forName("net.irisshaders.iris.pipeline.transform.Patch");
            Object patch=patchType.getField("VANILLA").get(null);
            var create=pipeline.getClass().getDeclaredMethod("createShader",String.class,Optional.class,keys,patchType);create.setAccessible(true);
            for(var kind:List.of(new Kind(false,false),new Kind(false,true),new Kind(true,false),new Kind(true,true))){
                Object key=keys.getField(!kind.lit()?"PARTICLES":kind.translucent()?"ENTITIES_TRANSLUCENT":"ENTITIES_SOLID_DIFFUSE").get(null);
                Object id=keys.getMethod("getProgram").invoke(key);
                Object source=resolver.getClass().getMethod("resolve",id.getClass()).invoke(resolver,id);
                if(((Optional<?>)source).isEmpty())continue;
                BUILDING.set(kind);
                try{
                    Object supplier=create.invoke(pipeline,"halo_mesh_"+(++generation),source,key,patch);
                    GlProgram shader=(GlProgram)((Supplier<?>)supplier.getClass().getMethod("shader").invoke(supplier)).get();
                    int block = org.lwjgl.opengl.GL31.glGetUniformBlockIndex(shader.getProgramId(), "iris_HaloMaterial");
                    if (block != -1) org.lwjgl.opengl.GL31.glUniformBlockBinding(shader.getProgramId(), block, 7);
                    int mask = org.lwjgl.opengl.GL20.glGetUniformLocation(shader.getProgramId(), "HaloMask");
                    if (mask != -1) org.lwjgl.opengl.GL41.glProgramUniform1i(shader.getProgramId(), mask, 3);
                    MATERIAL_BINDINGS.put(shader, Map.of());
                    shaders.put(kind,shader);
                }finally{BUILDING.remove();}
            }
            LoggerFactory.getLogger("halo").info("Prepared {} private Iris 26.3 material programs",shaders.size());
        }catch(ReflectiveOperationException|RuntimeException ex){LoggerFactory.getLogger("halo").error("Cannot prepare Halo Iris materials",ex);}
        network.azusake.halo.render.HaloRenderer.getInstance().rebuildMeshBuffersForShaderPipeline();
    }
    public static void forget(Object pipeline){
        var shaders=PROGRAMS.remove(pipeline);if(shaders!=null)shaders.values().forEach(shader -> { MATERIAL_BINDINGS.remove(shader); shader.close(); });

    }
    public static GlProgram program(RenderPipeline pipeline){
        Kind kind=KINDS.get(pipeline);
        if(kind==null)return null;
        if(!network.azusake.halo.physics.OptionalIrisPassDetector.hasShaderPack()
            ||!network.azusake.halo.physics.OptionalIrisPassDetector.isMainPass())return null;
        Object active;
        try { active=getManager==null?null:getPipeline.invoke(getManager.invoke(null)); }
        catch (ReflectiveOperationException error) { return null; }
        var shaders=PROGRAMS.get(active);
        var selected=shaders==null?null:shaders.get(kind);
        if(selected!=null&&!reportedSelection){reportedSelection=true;LoggerFactory.getLogger("halo").info("Using Halo private Iris material pipeline");}
        return selected;
    }
    public static Set<Integer> materialSamplerUnits(Set<Integer> reserved) {
        if (BUILDING.get()==null) return reserved;
        var result=new HashSet<>(reserved);
        result.add(3);
        return result;
    }
    public static VertexFormat materialVertexFormat(VertexFormat original) {
        if (BUILDING.get()==null) return original;
        try {
            return (VertexFormat)Class.forName("net.irisshaders.iris.vertices.IrisVertexFormats").getField("ENTITY").get(null);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot link Halo's entity vertex layout",error);
        }
    }
    public static Map<?,String> patch(Map<?,String> stages){
        Kind kind=BUILDING.get();if(kind==null)return stages;
        Map<Object,String> patched=new HashMap<>();
        // A fragment replacement must have a matching vertex varying. Unsupported
        // vertex ABIs retain the original pack normal reconstruction in both stages.
        boolean smooth=kind.lit() && stages.entrySet().stream().anyMatch(stage ->
            stage.getKey().toString().equals("VERTEX") && stage.getValue()!=null
                && IrisMeshShaderSource.supportsWorldNormalVertex(stage.getValue()));
        for(var stage:stages.entrySet()){
            String type=stage.getKey().toString();String source=stage.getValue();
            if(source!=null&&(type.equals("VERTEX")||type.equals("FRAGMENT")))
                {
                if (type.equals("FRAGMENT") && kind.lit()) LoggerFactory.getLogger("halo").info("Halo generic derivative normal match: {}", IrisMeshShaderSource.replacesWorldDerivativeFaceNormal("halo.fsh",source,smooth));
                source=IrisMeshShaderSource.patch261(type.equals("VERTEX")?"halo.vsh":"halo.fsh",source,smooth);
            }
            patched.put(stage.getKey(),source);
        }
        return patched;
    }
}
