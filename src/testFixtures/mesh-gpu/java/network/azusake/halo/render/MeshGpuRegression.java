package network.azusake.halo.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.Vec3d;
import network.azusake.halo.core.render.*;
import network.azusake.halo.core.runtime.ClientRuntime;
import network.azusake.halo.api.v2.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.BufferUtils;
import java.nio.file.*;
import java.util.*;

@Mod.EventBusSubscriber(modid = "halo", value = Dist.CLIENT)
public final class MeshGpuRegression {
    private static boolean done;
    private static int rounds;
    private static final StringBuilder result = new StringBuilder();
    private static final Identifier MODEL = new Identifier("halo:test_triangle");
    private static final Identifier TEXTURE = new Identifier("halo:test_red");
    @SubscribeEvent public static void tick(TickEvent.RenderTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        String world = System.getProperty("halo.meshGpuWorld");
        boolean ready = world == null ? mc.screen instanceof TitleScreen
            : mc.level != null && mc.player != null && mc.player.tickCount > 40;
        if (done || !Boolean.getBoolean("halo.meshGpuTest") || e.phase != TickEvent.Phase.END
                || !ready || mc.getOverlay() != null) return;
        done = true;
        boolean passed = false;
        result.append("Resource generation round ").append(++rounds).append('\n');
        try {
            DynamicTexture red = new DynamicTexture(16,16,false);
            for (int y=0;y<16;y++) for (int x=0;x<16;x++) red.getPixels().setPixelRGBA(x,y,0xff2040ff);
            red.upload();
            mc.getTextureManager().register(new ResourceLocation("halo", "test_red"), red);
            TriangleMesh triangle = new TriangleMesh(new float[]{-1,-1,0, 1,-1,0, 0,1,0},
                new float[]{0,0,1,0,0.5f,1}, new int[]{0,1,2});
            VisualResources visuals = new VisualResources(999,Map.of(MODEL,triangle),Map.of());
            TextureTarget target = new TextureTarget(32,32,true,false);
            Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
            var sorting = RenderSystem.getVertexSorting();
            var stack = RenderSystem.getModelViewStack();
            stack.pushPose(); stack.setIdentity(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
            try (var cache = HaloMeshBufferCache.empty().updated(visuals);
                 var fallback = HaloMeshBufferCache.empty()) {
                for (var environment : RenderEnvironment.values()) {
                    for (var buffers : List.of(cache,fallback)) {
                        for (boolean mixed : List.of(false,true)) {
                            target.bindWrite(true);
                            RenderSystem.clearColor(0,0,0,0);
                            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,false);
                            var draws = mixed ? List.of(draw(true,4),draw(false,0)) : List.of(draw(false,0));
                            HaloDrawSubmitter.submitMeshes(mc,new HaloDrawSubmitter.Submission(999,visuals,draws,
                                new Matrix4f(),new Matrix4f()),buffers,environment);
                            var pixel = BufferUtils.createByteBuffer(4);
                            GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
                            result.append(environment).append(buffers == cache ? " VBO" : " CPU")
                                .append(mixed ? " lit->flat " : " flat ")
                                .append(pixel.get(0)&255).append(',').append(pixel.get(1)&255).append(',')
                                .append(pixel.get(2)&255).append(',').append(pixel.get(3)&255)
                                .append(" GL error=").append(GL11.glGetError()).append('\n');
                            if (!isRed(pixel))
                                throw new AssertionError("Color depends on preceding lit draw");
                        }
                    }
                }
                testPrimitives(mc, cache, fallback, target);
                testResources(mc, target, triangle);
            } finally {
                stack.popPose(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(projection,sorting);
                target.destroyBuffers(); mc.getMainRenderTarget().bindWrite(true);
            }
            passed = true;
        } catch (Throwable error) {
            result.append(error).append('\n');
            for (var line : error.getStackTrace()) result.append(line).append('\n');
        }
        if (passed && rounds < 2) {
            mc.reloadResourcePacks().thenRun(() -> done = false);
            return;
        }
        if (passed && rounds == 2 && world != null) {
            result.append("Disconnecting to title and reopening isolated world\n");
            mc.level.disconnect();
            mc.clearLevel(new TitleScreen());
            mc.setScreen(new TitleScreen());
            done = false;
            mc.createWorldOpenFlows().loadLevel(new TitleScreen(), world);
            return;
        }
        result.append(passed ? "PASS\n" : "FAIL\n");
        try { Files.writeString(Path.of("mesh-gpu-result.txt"),result); }
        catch (Exception error) { throw new RuntimeException(error); }
        System.out.println("MESH_GPU_RESULT\n"+result);
        mc.stop();
    }
    private static MeshDraw draw(boolean lit,float x) {
        return new MeshDraw(MODEL,TEXTURE,new Matrix4f().translate(x,0,0).get(new float[16]),
            false,false,false,false,1,1,1,1,false,new MaterialState.Mesh(null),LightSample.FULL_BRIGHT,lit);
    }

    private static boolean isRed(java.nio.ByteBuffer pixel) {
        return (pixel.get(0)&255)>200 && (pixel.get(1)&255)>40 && (pixel.get(1)&255)<80
            && (pixel.get(2)&255)>15 && (pixel.get(2)&255)<50 && (pixel.get(3)&255)==255;
    }

    private static void testPrimitives(Minecraft mc, HaloMeshBufferCache cache, HaloMeshBufferCache fallback,
                                       TextureTarget target) {
        var geometry = new PrimitiveGeometry(MODEL,new float[]{-1,-1,0,1,-1,0,0,1,0},
            new float[]{0,0,1,0,.5f,1},new float[]{0,0,1,0,0,1,0,0,1},new int[]{0,1,2},DrawBatch.Topology.TRIANGLES);
        var draws = new ArrayList<PrimitiveDraw>();
        for (boolean lit : List.of(true,false)) {
            var state = new DrawBatch(DrawBatch.Topology.TRIANGLES,List.of(),TEXTURE,true,false,false,false,false,
                1,1,1,1,MaterialState.LEGACY,LightSample.FULL_BRIGHT,lit);
            draws.add(new PrimitiveDraw(geometry,new Matrix4f().translate(lit?4:0,0,0).get(new float[16]),state,1));
        }
        for (var environment : RenderEnvironment.values()) for (var buffers : List.of(cache,fallback)) {
            target.bindWrite(true);
            RenderSystem.clearColor(0,0,0,0);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,false);
            HaloDrawSubmitter.submitPrimitives(mc,new FrameOutput(999,List.of(),List.of(),draws),buffers,environment);
            var pixel = BufferUtils.createByteBuffer(4);
            GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
            if (!isRed(pixel))
                throw new AssertionError("Primitive lit->flat failed: " + environment);
            result.append(environment).append(buffers==cache?" primitive VBO":" primitive CPU").append(" lit->flat PASS\n");
        }
    }

    private static void testResources(Minecraft mc, TextureTarget target, TriangleMesh triangle) {
        var snapshot = HaloMeshResources.snapshot();
        var models = new HashMap<>(snapshot.visuals().meshes());
        models.put(MODEL,triangle);
        var visuals = new VisualResources(snapshot.visuals().generation(),models,snapshot.visuals().textures());
        var ids = new ArrayList<>(List.of("halo:mesh_demo","halo:mesh_preserve_demo","halo:mesh_mask_demo",
            "halo:mesh_step_demo","halo:mesh_mask_resolution_demo"));
        if (models.keySet().stream().anyMatch(id -> id.toString().contains("seia_mesh_import"))) ids.add("model:seia");
        try (var buffers = HaloMeshBufferCache.empty().updated(visuals)) {
            for (String id : ids) {
                long[] time = {1000};
                var runtime = new ClientRuntime(() -> time[0]);
                runtime.definitions(snapshot.definitions());
                UUID entityId = new UUID(0,1);
                runtime.attach(entityId,new Identifier(id),false);
                var anchor = new AnchorPose(new AnchorVec3(0,0,0),new AnchorRotation(0,0,0,1));
                var entity = new FrameScene.EntitySample(entityId,1,new Vec3d(0,0,0),true,false,false,anchor,anchor);
                var frame = new FrameScene(1,1000,1000000000L,
                    new FrameScene.CameraSample(new Vec3d(0,0,0),new Vec3d(0,1,0),new Vec3d(1,0,0)),
                    Map.of(entityId,entity),new Matrix4f().get(new float[16]),pos->1f,texture->true,visuals,
                    pos->LightSample.FULL_BRIGHT);
                runtime.renderFrame(frame);
                time[0]=2000;
                frame = new FrameScene(1,2000,2000000000L,frame.camera(),frame.entities(),frame.rootTransform(),
                    frame.lights(),frame.textures(),visuals,pos->LightSample.FULL_BRIGHT);
                var original = runtime.renderFrame(frame).meshes();
                if (original.isEmpty()) throw new AssertionError("Missing fixture " + id);
                Matrix4f rotation = new Matrix4f().rotateX(.45f).rotateY(.35f);
                Vector3f min = new Vector3f(Float.POSITIVE_INFINITY), max = new Vector3f(Float.NEGATIVE_INFINITY);
                for (var draw : original) {
                    var mesh = models.get(draw.model());
                    Matrix4f transform = new Matrix4f(rotation).mul(new Matrix4f().set(draw.localToView()));
                    for (int v=0;v<mesh.vertexCount();v++) {
                        Vector3f point = transform.transformPosition(new Vector3f(mesh.x(v),mesh.y(v),mesh.z(v)));
                        min.min(point); max.max(point);
                    }
                }
                Vector3f size = new Vector3f(max).sub(min), center = new Vector3f(max).add(min).mul(.5f);
                Matrix4f view = new Matrix4f().scale(1.7f/Math.max(size.x,Math.max(size.y,size.z)))
                    .translate(-center.x,-center.y,-center.z).mul(rotation);
                List<MeshDraw> draws = original.stream().map(d -> new MeshDraw(d.model(),d.texture(),
                    new Matrix4f(view).mul(new Matrix4f().set(d.localToView())).get(new float[16]),
                    d.cull(),d.blend(),d.depthTest(),d.depthWrite(),d.red(),d.green(),d.blue(),d.alpha(),
                    d.mirrored(),d.material(),d.light(),d.directionalLighting())).toList();
                byte[] reference = pixels(mc,target,visuals,buffers,draws);
                var mixed = new ArrayList<MeshDraw>(); mixed.add(draw(true,4)); mixed.addAll(draws);
                byte[] actual = pixels(mc,target,visuals,buffers,mixed);
                if (!Arrays.equals(reference,actual)) throw new AssertionError(id+" changed after lit draw");
                int colored = 0;
                for (int p=0;p<actual.length;p+=4) if ((actual[p]&255)+(actual[p+1]&255)+(actual[p+2]&255)>100) colored++;
                if (colored < 5) throw new AssertionError(id+" was not visibly rendered");
                result.append(id).append(" identical pixels after lit draw; colored pixels=").append(colored).append('\n');
            }
        }
    }
    private static byte[] pixels(Minecraft mc, TextureTarget target, VisualResources visuals,
                                 HaloMeshBufferCache buffers,List<MeshDraw> draws) {
        target.bindWrite(true);
        RenderSystem.depthMask(true);
        RenderSystem.clearColor(0,0,0,0);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,false);
        HaloDrawSubmitter.submitMeshes(mc,new HaloDrawSubmitter.Submission(visuals.generation(),visuals,draws,
            new Matrix4f(),new Matrix4f()),buffers,RenderEnvironment.WORLD);
        var pixels = BufferUtils.createByteBuffer(32*32*4);
        GL11.glReadPixels(0,0,32,32,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        byte[] result = new byte[pixels.remaining()]; pixels.get(result); return result;
    }
}
