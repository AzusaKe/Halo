package network.azusake.halo.compat.caustica;

import dev.comfyfluffy.caustica.minecraft.api.*;
import dev.comfyfluffy.caustica.api.geometry.*;
import dev.comfyfluffy.caustica.api.scene.*;
import dev.comfyfluffy.caustica.api.program.ShaderData;
import network.azusake.halo.core.render.*;
import network.azusake.halo.render.HaloMeshResources;
import net.minecraft.client.Minecraft;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

/** One producer per world epoch. Scene publication retains independent immutable GPU claims. */
final class CausticaSession implements MinecraftWorldSessionContribution {
    private final MinecraftWorldSessionContext world;
    private final CausticaPrograms programs;
    private final CausticaGpu gpu;
    private final CausticaGpu.Parameters parameters;
    private final ExecutorService loader=Executors.newFixedThreadPool(2,Thread.ofPlatform().daemon().name("halo-caustica-assets-",0).factory());
    private final CausticaPlacements instances;
    private final Set<String> diagnostics=new HashSet<>();
    private final CausticaRevisions<CausticaAssets> revisions=new CausticaRevisions<>(CausticaAssets::close);
    private CausticaAssets active;
    private boolean stopped;
    CausticaSession(MinecraftWorldSessionContext world) {
        this.world=world;instances=new CausticaPlacements(world.renderSession().scene(),world.scene());gpu=new CausticaGpu(world.renderSession());parameters=gpu.new Parameters();
        programs=new CausticaPrograms(world.renderSession().program());reload();CausticaBridge.opened(this);
        CausticaBridge.LOG.warn("Halo Caustica experimental adapter 1, API 0.8.0 / shader ABI 6. Primary alpha is threshold coverage; raster blending/order/depth flags have no RT equivalent.");
    }
    synchronized void reload() {
        if(stopped)return;
        var next=new CausticaAssets(HaloMeshResources.snapshot(),Minecraft.getInstance().getResourceManager(),world.renderSession(),gpu,programs,loader);
        revisions.request(next,next.ready);
    }
    @Override public void resourcePackChanged(ResourcePackEpoch epoch){reload();}
    synchronized void frame(SceneFrame frame) {
        if(stopped || frame==null)return;
        var ready=revisions.current();if(ready!=active){active=ready;diagnostics.clear();}
        if(active==null)return;
        // Keep the previous usable scene until a complete resource/definition revision is ready.
        if(active.snapshot.definitions()!=HaloMeshResources.snapshot().definitions() || active.snapshot.visuals().generation()!=frame.visualGeneration())return;
        var metric=CausticaMetrics.begin();
        var pages=new ArrayList<CausticaGpu.Parameters.Page>();
        var inners=new HashMap<SceneDraw.Key,SceneDraw>();
        for(var draw:frame.draws())if(draw.kind()==SceneDraw.Kind.RING && draw.key().part()==1)inners.put(draw.key().primary(),draw);
        var page=new CausticaGpu.Parameters.Page[1];
        try(var transaction=instances.begin()) {
            for(var draw:frame.draws()) {
                if(draw.key().part()!=0)continue;
                GeometryTransform transform;
                try{transform=CausticaMaterial.transform(draw);}catch(IllegalArgumentException e){warn(draw,"singular", "Singular/non-finite transform skipped");continue;}
                var inner=inners.get(draw.key());
                if(draw.kind()==SceneDraw.Kind.MESH && draw.cull())warn(draw,"cull","Single-sided mesh shown double-sided by Caustica");
                if(inner!=null && (inner.alpha()!=draw.alpha() || !Objects.equals(inner.texture(),draw.texture())))warn(draw,"ring-alpha","Ring coverage uses outer alpha on both sides; differing inner texture alpha is approximate");
                if(draw.material() instanceof MaterialState.Mesh material && material.mask()!=null && active.texture(material.mask().texture(),false)==null){warn(draw,"mask","Unavailable mask: instance skipped");continue;}
                var material=CausticaMaterial.encode(draw,inner,active);
                var mesh=active.meshes.get(new CausticaAssets.MeshKey(draw.geometry(),material.opaque()));if(mesh==null)continue;
                transaction.put(draw.key(),mesh,transform,material.bytes(),() -> {
                    if(page[0]==null || page[0].used+CausticaMaterial.SIZE>page[0].buffer.size){page[0]=parameters.page(active.textureOwner());pages.add(page[0]);}
                    long address=page[0].write(ByteBuffer.wrap(material.bytes()));
                    return CausticaPrograms.INSTANCE.data(address,page[0].owner);
                });
            }
            pages.forEach(CausticaGpu.Parameters.Page::flush);
            int edits=transaction.commit();
            CausticaMetrics.end(metric,instances.size(),edits);
        } finally { pages.forEach(CausticaGpu.Parameters.Page::close); }
    }
    private void warn(SceneDraw draw,String type,String message) {
        String key=draw.key().definition()+":"+draw.key().definitionRevision()+":"+type;
        if(diagnostics.add(key))CausticaBridge.LOG.warn("{} [{}]",message,draw.key().definition());
    }
    synchronized void hide(){if(!stopped)instances.hide();}
    @Override public synchronized void stop(){if(stopped)return;hide();stopped=true;CausticaBridge.closed(this);revisions.close();active=null;loader.shutdown();parameters.close();programs.close();}
    @Override public void close(){stop();}
}
