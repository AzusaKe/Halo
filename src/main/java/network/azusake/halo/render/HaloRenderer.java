package network.azusake.halo.render;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;
import network.azusake.halo.api.v2.*;
import network.azusake.halo.anchor.AnchorCaptureCoordinator;
import network.azusake.halo.core.render.*;
import network.azusake.halo.physics.*;
import network.azusake.halo.platform.*;
import org.joml.Matrix4f;
import java.util.*;
import static network.azusake.halo.platform.PlatformTypes.*;

public final class HaloRenderer {
    private static final HaloRenderer INSTANCE=new HaloRenderer();
    private Object previousWorld;
    private long worldToken;
    private boolean shaderVertexLayout;
    private final FrameSubmission<Pending> submissions = new FrameSubmission<>();
    private HaloMeshBufferCache meshBuffers=HaloMeshBufferCache.empty(),primitiveBuffers=HaloMeshBufferCache.empty();
    private PrimitiveRenderMode primitiveMode=PrimitiveRenderMode.COMPATIBILITY;
    private record Pending(HaloDrawSubmitter.PreparedSubmission early, HaloDrawSubmitter.PreparedSubmission late) {}
    public static HaloRenderer getInstance(){return INSTANCE;}
    public PrimitiveRenderMode primitiveMode(){return primitiveMode;}
    public void beginFrame(){
        HaloDrawSubmitter.endFrame();
        boolean shaders=OptionalIrisPassDetector.hasShaderPack();
        if(shaders!=shaderVertexLayout) {
            shaderVertexLayout=shaders;
            rebuildMeshBuffersForShaderPipeline();
        }
        var requested="cached".equals(network.azusake.halo.config.HaloModConfigStore.get().getPrimitiveRenderBackend())?PrimitiveRenderMode.CACHED:PrimitiveRenderMode.COMPATIBILITY;
        if(requested!=primitiveMode){primitiveMode=requested;reloadPrimitiveBuffers(HaloMeshResources.snapshot(),true);}
    }
    public void reloadMeshBuffers(VisualResources resources){meshBuffers=meshBuffers.updated(resources);HaloMeshShader.clear();}
    public void reloadPrimitiveBuffers(HaloMeshResources.Snapshot snapshot,boolean force){
        if(force||primitiveMode==PrimitiveRenderMode.COMPATIBILITY)primitiveBuffers.close();
        if(primitiveMode==PrimitiveRenderMode.CACHED){
            var meshes=new LinkedHashMap<network.azusake.halo.core.Identifier,TriangleMesh>();
            snapshot.definitions().primitiveGeometries().forEach((id,g)->meshes.put(id,g.mesh()));
            primitiveBuffers=primitiveBuffers.updated(new VisualResources(snapshot.visuals().generation(),meshes,Map.of()));
        }
    }
    public void rebuildMeshBuffersForShaderPipeline(){meshBuffers.close();primitiveBuffers.close();reloadMeshBuffers(HaloMeshResources.snapshot().visuals());reloadPrimitiveBuffers(HaloMeshResources.snapshot(),true);}
    public void shutdown(){HaloDrawSubmitter.endFrame();meshBuffers.close();primitiveBuffers.close();clearWorld();}
    public void clearWorld(){previousWorld=null;submissions.clear();}
    public IdlePhaseTracker.RenderState readLastRenderState(UUID uuid){return HaloClientState.get().renderer().readLastRenderState(uuid);}
    public void clearIdlePhases(){HaloClientState.get().renderer().clearIdlePhases();}
    public void renderHalos(PoseStack matrices,Camera camera,float tickDelta){
        if(!OptionalIrisPassDetector.isMainPass()||!submissions.begin(RenderHeadCapture.getFrameId()))return;
        Minecraft client=Minecraft.getInstance();
        if(client.level==null) return;
        if(previousWorld!=client.level) { previousWorld=client.level;worldToken++; }
        HaloClientManager.getInstance().restoreLocalOwnership();
        var runtime=HaloClientState.get();
        var assets = HaloMeshResources.snapshot();
        runtime.definitions(assets.definitions());
        Map<UUID,FrameScene.EntitySample> samples=new LinkedHashMap<>();
        var assignments=runtime.assignments();
        for(var entity:client.level.entitiesForRendering()) {
            if(!(entity instanceof LivingEntity living))continue;
            if(!assignments.containsKey(entity.getUUID()) && runtime.getInstance(entity.getUUID())==null)continue;
            var pos=new network.azusake.halo.core.Vec3d(
                entity.xOld+(entity.getX()-entity.xOld)*tickDelta,
                entity.yOld+(entity.getY()-entity.yOld)*tickDelta,
                entity.zOld+(entity.getZ()-entity.zOld)*tickDelta);
            AnchorPose fallback=living instanceof net.minecraft.world.entity.player.Player
                ? PlayerAnchorProvider.getInstance().resolve(living,tickDelta)
                : DefaultAnchorResolver.resolve(living,tickDelta);
            boolean localFirstPerson=living==client.player && client.options.getCameraType().isFirstPerson();
            AnchorPose captured=localFirstPerson ? fallback : AnchorCaptureCoordinator.resolve(
                entity.getUUID(),entity.getId(),client.level,new AnchorVec3(pos.x,pos.y,pos.z));
            samples.put(entity.getUUID(),new FrameScene.EntitySample(entity.getUUID(),entity.getId(),pos,
                entity.isAlive(),living.isSleeping(),entity.isInvisible(),captured==null?fallback:captured,fallback));
        }
        var up=camera.upVector();var right=camera.leftVector();
        FrameScene scene=new FrameScene(worldToken,System.currentTimeMillis(),System.nanoTime(),
            new FrameScene.CameraSample(core(camera.position()),
                new network.azusake.halo.core.Vec3d(up.x(),up.y(),up.z()),
                new network.azusake.halo.core.Vec3d(right.x(),right.y(),right.z())),
            samples,matrices.last().pose().get(new float[16]),
            pos -> {
                BlockPos block=BlockPos.containing(pos.x,pos.y,pos.z);
                return Math.max(client.level.getBrightness(LightLayer.BLOCK,block),client.level.getBrightness(LightLayer.SKY,block))/15f;
            }, id -> {
                try { client.getTextureManager().getTexture(game(id));return true; }
                catch(RuntimeException ex) { return false; }
            }, assets.visuals(), pos -> {
                BlockPos block=BlockPos.containing(pos.x,pos.y,pos.z);
                return new LightSample(
                    client.level.getBrightness(LightLayer.BLOCK, block),
                    client.level.getBrightness(LightLayer.SKY, block));
            }, primitiveMode);
        FrameOutput output = runtime.renderFrame(scene);

        var outer = new Matrix4f(RenderSystem.getModelViewMatrixCopy());
        boolean shaderPack = OptionalIrisPassDetector.hasShaderPack();
        var early = HaloDrawSubmitter.prepare(() -> submit(output, assets.visuals(), outer, shaderPack, RenderEnvironment.WORLD, false));
        var late = HaloDrawSubmitter.prepare(() -> submit(output, assets.visuals(), outer, shaderPack, RenderEnvironment.WORLD, true));
        submissions.publish(new Pending(early, late));
        if(client.hasSingleplayerServer())IntegratedBridge.publishDiagnostics(runtime.diagnostics());
    }
    public void submitSolidStage() {
        if (!OptionalIrisPassDetector.isMainPass()) return;
        Pending pending = submissions.claimEarly();
        if (pending != null) HaloDrawSubmitter.submit(pending.early());
    }
    public void submitDeferredMeshes() {
        if (!OptionalIrisPassDetector.isMainPass()) return;
        Pending pending = submissions.claimLate();
        if (pending != null) HaloDrawSubmitter.submit(pending.late());
    }
    public void submitPreview(FrameOutput output,VisualResources visuals){var outer = new Matrix4f(RenderSystem.getModelViewMatrixCopy()); submit(output,visuals,outer,false,RenderEnvironment.GUI,false);submit(output,visuals,outer,false,RenderEnvironment.GUI,true);}
    private void submit(FrameOutput output,VisualResources visuals,Matrix4f outer,boolean shaderPack,RenderEnvironment environment,boolean late){
        var client=Minecraft.getInstance();
        if(!late){
            HaloDrawSubmitter.submitBatches(client,output.legacyBatches(),environment,outer);
            for(var primitive:output.primitiveDraws())if(!primitiveBuffers.drawPrimitive(output.visualGeneration(),primitive,outer,environment))
                HaloDrawSubmitter.submitBatches(client,List.of(primitive.expand()),environment,outer);
        }
        var workspace=new MeshDrawWorkspace();
        for(var draw:MeshDrawViewSorter.backToFront(output.meshes(),visuals,outer)){
            if(submitBeforeTranslucents(draw,shaderPack)==late)continue;
            if(!meshBuffers.draw(output.visualGeneration(),draw,outer,environment,workspace))
                HaloDrawSubmitter.submitBatches(client,new FrameOutput(output.visualGeneration(),List.of(),List.of(draw))
                    .expandedBatches(visuals,outer.m02(),outer.m12(),outer.m22(),outer.m32()),environment,outer);
        }
    }
    static boolean submitBeforeTranslucents(MeshDraw draw,boolean shaderPack){return shaderPack&&draw.directionalLighting()&&!draw.blend();}
    static int packLight(LightSample light){return HaloDrawSubmitter.packLight(light);}
    static LivingEntity findEntityByUuid(Minecraft client,UUID uuid){if(client.level!=null)for(var entity:client.level.entitiesForRendering())if(entity instanceof LivingEntity living&&entity.getUUID().equals(uuid))return living;return null;}
}
