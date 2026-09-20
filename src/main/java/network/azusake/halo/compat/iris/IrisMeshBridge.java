package network.azusake.halo.compat.iris;

import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.Uniform;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;
import net.neoforged.fml.ModList;
import org.slf4j.LoggerFactory;

/** Optional Iris 26.2 bridge. Halo alone owns the cloned programs and releases them with their pipeline. */
public final class IrisMeshBridge {
    private record Kind(boolean lit,boolean translucent){}
    private static final Map<RenderPipeline,Kind> KINDS=new IdentityHashMap<>();
    private static final Map<Object,Map<Kind,GlProgram>> PROGRAMS=new IdentityHashMap<>();
    private static final ThreadLocal<Kind> BUILDING=new ThreadLocal<>();
    private static Method getManager, getPipeline;
    private static boolean reportedSelection;
    private static long generation;
    private IrisMeshBridge(){}
    @SuppressWarnings("unchecked")
    public static <T> T withoutVertexExtension(Supplier<T> action) {
        if (!ModList.get().isLoaded("iris")) return action.get();
        try {
            var skip = (ThreadLocal<Boolean>) Class.forName("net.irisshaders.iris.vertices.ImmediateState")
                .getField("skipExtension").get(null);
            Boolean previous = skip.get();
            skip.set(true);
            try { return action.get(); }
            finally { if (previous == null) skip.remove(); else skip.set(previous); }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot isolate Halo's native vertex layout", error);
        }
    }
    public static void requireExtendedEntityFormat(VertexFormat format) {
        try {
            if (format != Class.forName("net.irisshaders.iris.vertices.IrisVertexFormats").getField("ENTITY").get(null))
                throw new IllegalStateException("Iris did not extend Halo's resident entity vertices");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot validate Iris entity layout", error);
        }
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    public static void assign(RenderPipeline pipeline,boolean lit,boolean translucent){
        KINDS.put(pipeline,new Kind(lit,translucent));
        if(!ModList.get().isLoaded("iris"))return;
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
                    var layout=com.mojang.blaze3d.pipeline.BindGroupLayout.builder();
                    for(String name:List.of("DynamicTransforms","Projection","Fog","Globals","HaloMaterial"))
                        layout.withUniform(name,UniformType.UNIFORM_BUFFER);
                    for(String name:List.of("Sampler0","Sampler1","Sampler2","HaloMask")) layout.withSampler(name);
                    shader.setupBindGroupLayouts(List.of(layout.build()));
                    // Vanilla compacts unused samplers. Iris owns fixed units 0/1/2;
                    // preserve these even when the pack optimizes overlay/lightmap out.
                    for (var binding : Map.of("Sampler0",0,"Sampler1",1,"Sampler2",2,"HaloMask",3).entrySet()) {
                        if (shader.getUniforms().get(binding.getKey()) instanceof Uniform.Sampler sampler)
                            shader.getUniforms().put(binding.getKey(),new Uniform.Sampler(sampler.location(),binding.getValue()));
                    }
                    shaders.put(kind,shader);
                }finally{BUILDING.remove();}
            }
            LoggerFactory.getLogger("halo").info("Prepared {} private Iris 26.2 material programs",shaders.size());
        }catch(ReflectiveOperationException|RuntimeException ex){LoggerFactory.getLogger("halo").error("Cannot prepare Halo Iris materials",ex);}
        network.azusake.halo.render.HaloRenderer.getInstance().rebuildMeshBuffersForShaderPipeline();
    }
    public static void forget(Object pipeline){
        var shaders=PROGRAMS.remove(pipeline);if(shaders!=null)shaders.values().forEach(GlProgram::close);

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
