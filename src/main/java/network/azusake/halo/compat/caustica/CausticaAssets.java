package network.azusake.halo.compat.caustica;

import dev.comfyfluffy.caustica.api.geometry.*;
import dev.comfyfluffy.caustica.api.resource.*;
import dev.comfyfluffy.caustica.api.session.RenderSessionContext;
import dev.comfyfluffy.caustica.api.vulkan.*;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.render.TriangleMesh;
import network.azusake.halo.render.HaloMeshResources;
import net.minecraft.server.packs.resources.ResourceManager;
import javax.imageio.ImageIO;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import static org.lwjgl.vulkan.VK13.*;

/** Loading-stage revision. Neither PNG reads nor triangle traversal occur during instance submission. */
final class CausticaAssets implements AutoCloseable {
    record TextureKey(Identifier id,boolean srgb) { }
    record MeshKey(TriangleMesh geometry,boolean opaque) { }
    final HaloMeshResources.Snapshot snapshot;
    final Map<TextureKey,CausticaGpu.Texture> textures=new ConcurrentHashMap<>();
    final Map<MeshKey,ReadyMesh<CausticaPrograms.Instance>> meshes=new ConcurrentHashMap<>();
    final CompletableFuture<Void> ready;
    private final RenderSessionContext context;
    private final CausticaGpu gpu;
    private final CausticaPrograms programs;
    private final Executor executor;
    private volatile boolean stopped;
    private ResourceOwner textureOwner;

    CausticaAssets(HaloMeshResources.Snapshot snapshot,ResourceManager manager,RenderSessionContext context,
                   CausticaGpu gpu,CausticaPrograms programs,Executor executor) {
        this.snapshot=snapshot;this.context=context;this.gpu=gpu;this.programs=programs;this.executor=executor;
        ready=programs.readiness.thenComposeAsync(ignored -> prepare(manager),executor);
        ready.whenComplete((ok,error) -> {
            if(error!=null && !stopped)CausticaBridge.LOG.error("Halo asset preparation failed",error);
        });
    }
    private CompletableFuture<Void> prepare(ResourceManager manager) {
        var pending=new ArrayList<CompletableFuture<?>>();
        if(stopped)return CompletableFuture.completedFuture(null);
        for(var id:snapshot.definitions().baseTextures()) {
            pending.add(texture(manager,id,true,false));
            pending.add(texture(manager,companion(id,"_n"),false,true));
            pending.add(texture(manager,companion(id,"_s"),false,true));
        }
        for(var id:snapshot.definitions().maskTextures())pending.add(texture(manager,id,false,false));
        var geometry=Collections.newSetFromMap(new IdentityHashMap<TriangleMesh,Boolean>());
        snapshot.definitions().primitiveGeometries().forEach((id,g) -> { if(!id.toString().endsWith("_inner"))geometry.add(g.mesh()); });
        geometry.addAll(snapshot.visuals().meshes().values());
        for(var mesh:geometry)pending.add(geometry(mesh));
        return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).thenRun(() -> {
            synchronized(this) {
                if(stopped)return;
                var retained=textures.values().stream().map(t -> t.owner().retain()).toList();
                textureOwner=context.resources().create(() -> retained.forEach(ResourceOwner::close));
            }
        });
    }
    static Identifier companion(Identifier id,String suffix) {
        var text=id.toString();return new Identifier(text.endsWith(".png")?text.substring(0,text.length()-4)+suffix+".png":text+suffix);
    }
    private CompletableFuture<Void> texture(ResourceManager manager,Identifier id,boolean srgb,boolean optional) {
        return CompletableFuture.supplyAsync(() -> {
            if(stopped)return null;
            var resource=manager.getResource(network.azusake.halo.platform.PlatformTypes.game(id));
            if(resource.isEmpty()) { if(!optional)CausticaBridge.LOG.warn("Missing Halo texture {}",id);return null; }
            try(var stream=resource.get().open()) {
                var image=ImageIO.read(stream);if(image==null)throw new IllegalArgumentException("Not a PNG: "+id);
                int w=image.getWidth(),h=image.getHeight();
                ByteBuffer pixels=ByteBuffer.allocateDirect(Math.multiplyExact(Math.multiplyExact(w,h),4));boolean opaque=true;
                for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                    int color=image.getRGB(x,y);int a=color>>>24;opaque &= a==255;
                    pixels.put((byte)(color>>>16)).put((byte)(color>>>8)).put((byte)color).put((byte)a);
                }
                pixels.flip();return new Pixels(w,h,pixels,opaque);
            } catch(Exception error) { CausticaBridge.LOG.warn("Cannot decode Halo texture {}",id,error);return null; }
        },executor).thenCompose(pixels -> pixels==null?CompletableFuture.completedFuture(null):gpu.texture(pixels.width,pixels.height,pixels.data,srgb,pixels.opaque))
            .thenAccept(texture -> {
                if(texture==null)return;
                synchronized(this) { if(stopped)texture.owner().close();else textures.put(new TextureKey(id,srgb),texture); }
            });
    }
    private record Pixels(int width,int height,ByteBuffer data,boolean opaque) { }
    private CompletableFuture<Void> geometry(TriangleMesh mesh) {
        var vertices=gpu.buffer(Math.multiplyExact(mesh.vertexCount(),32));
        var indices=gpu.buffer(Math.multiplyExact(mesh.triangleCount(),12));
        var root=gpu.buffer(16);
        try {
            for(int i=0;i<mesh.vertexCount();i++)vertices.bytes.putFloat(mesh.x(i)).putFloat(mesh.y(i)).putFloat(mesh.z(i))
                .putFloat(mesh.u(i)).putFloat(mesh.v(i)).putFloat(mesh.normalX(i)).putFloat(mesh.normalY(i)).putFloat(mesh.normalZ(i));
            for(int i=0;i<mesh.triangleCount()*3;i++)indices.bytes.putInt(mesh.index(i));
            root.bytes.putLong(vertices.address).putLong(indices.address);vertices.flush();indices.flush();root.flush();
        } catch(Throwable error) { root.close();indices.close();vertices.close();throw error; }
        var owner=context.resources().create(() -> { root.close();indices.close();vertices.close(); });
        var initialized=new CompletableFuture<Void>();var dependency=owner.retain();
        try {
            context.compute().submit(command -> {
                try(var stack=MemoryStack.stackPush()) {
                    var barrier=VkMemoryBarrier2.calloc(1,stack).sType$Default().srcStageMask(VK_PIPELINE_STAGE_2_HOST_BIT)
                        .srcAccessMask(VK_ACCESS_2_HOST_WRITE_BIT).dstStageMask(VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT)
                        .dstAccessMask(VK_ACCESS_2_MEMORY_READ_BIT);
                    vkCmdPipelineBarrier2(command,VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barrier));
                }
            },List.of(dependency),completion -> {
                if(completion instanceof GpuComputeCompletion.Succeeded)initialized.complete(null);
                else initialized.completeExceptionally(new IllegalStateException("Geometry initialization "+completion));
            });
        } catch(Throwable error) { dependency.close();initialized.completeExceptionally(error); }
        var result=initialized.thenCompose(ok -> {
            CausticaMetrics.geometryBytes.add((long)mesh.vertexCount()*32+(long)mesh.triangleCount()*12+16);
            var pending=new ArrayList<CompletableFuture<?>>();
            for(boolean opaque:new boolean[]{true,false}) {
                try(var binding=CausticaPrograms.GEOMETRY.data(root.address,owner)) {
                    var slot=new MeshBuild.SurfaceSlot<>(programs.surface(),binding,opaque?new MeshBuild.CoveragePolicy.Opaque():new MeshBuild.CoveragePolicy.Stochastic(1f/255));
                    var build=new MeshBuild<>(new MeshBuild.Stream(new VulkanDeviceAddressRange(new VulkanDeviceAddress(vertices.address),vertices.size),32,owner),
                        new MeshBuild.Stream(new VulkanDeviceAddressRange(new VulkanDeviceAddress(indices.address),indices.size),4,owner),
                        mesh.vertexCount(),new MeshBuild.IndexRevision(1),MeshBuild.BuildPolicy.STATIC,
                        List.of(new MeshBuild.Geometry<CausticaPrograms.Instance>(slot,null,0,mesh.triangleCount()*3)));
                    CausticaMetrics.blasBuilds.increment();
                    pending.add(context.meshes().prepare(CausticaPrograms.INSTANCE,build).thenAccept(prepared -> {
                        synchronized(this) { if(stopped)prepared.close();else meshes.put(new MeshKey(mesh,opaque),prepared); }
                    }));
                }
            }
            return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
        });
        return result.whenComplete((ok,error) -> owner.close());
    }
    CausticaGpu.Texture texture(Identifier id,boolean srgb) { return textures.get(new TextureKey(id,srgb)); }
    synchronized ResourceOwner textureOwner() { return textureOwner; }
    @Override public synchronized void close() {
        if(stopped)return;stopped=true;
        meshes.values().forEach(ReadyMesh::close);meshes.clear();
        textures.values().forEach(t -> t.owner().close());textures.clear();
        if(textureOwner!=null)textureOwner.close();
    }
}
