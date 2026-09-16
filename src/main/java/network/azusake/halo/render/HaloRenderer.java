package network.azusake.halo.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import network.azusake.halo.api.v2.*;
import network.azusake.halo.anchor.AnchorCaptureCoordinator;
import network.azusake.halo.core.render.*;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.physics.*;
import network.azusake.halo.platform.*;
import java.util.*;
import org.joml.Matrix4f;
import com.mojang.blaze3d.systems.VertexSorter;
import static network.azusake.halo.platform.PlatformTypes.*;

/** Fabric render adapter: capture world facts, then submit core-generated batches. */
public final class HaloRenderer {
    private static final HaloRenderer INSTANCE=new HaloRenderer();
    private Object previousWorld;
    private long worldToken;
    private record DeferredMeshes(long generation, VisualResources visuals, List<MeshDraw> draws,
                                  Matrix4f modelView, Matrix4f projection, VertexSorter sorting) {}
    private DeferredMeshes deferredMeshes;
    private HaloMeshBufferCache meshBuffers = HaloMeshBufferCache.empty();
    private HaloMeshBufferCache primitiveBuffers = HaloMeshBufferCache.empty();
    private PrimitiveRenderMode primitiveMode = PrimitiveRenderMode.COMPATIBILITY;
    public PrimitiveRenderMode primitiveMode() { return primitiveMode; }

    /** Called at GameRenderer.render HEAD, also while a GUI is open/paused. */
    public void beginFrame() {
        PrimitiveRenderMode requested = "cached".equals(network.azusake.halo.config.HaloModConfigStore.get().getPrimitiveRenderBackend())
            ? PrimitiveRenderMode.CACHED : PrimitiveRenderMode.COMPATIBILITY;
        if (requested == primitiveMode) return;
        primitiveMode = requested;
        reloadPrimitiveBuffers(HaloMeshResources.snapshot(), true);
    }

    public void reloadPrimitiveBuffers(HaloMeshResources.Snapshot snapshot, boolean force) {
        RenderSystem.assertOnRenderThread();
        if (force || primitiveMode == PrimitiveRenderMode.COMPATIBILITY) {
            primitiveBuffers.close(); primitiveBuffers = HaloMeshBufferCache.empty();
        }
        if (primitiveMode == PrimitiveRenderMode.CACHED) {
            var meshes = new LinkedHashMap<network.azusake.halo.core.Identifier, TriangleMesh>();
            var quads = new java.util.HashSet<network.azusake.halo.core.Identifier>();
            snapshot.definitions().primitiveGeometries().forEach((id, geometry) -> {
                meshes.put(id, geometry.mesh());
                if (geometry.topology() == DrawBatch.Topology.QUADS) quads.add(id);
            });
            primitiveBuffers = primitiveBuffers.updated(new VisualResources(snapshot.visuals().generation(), meshes, Map.of()), quads);
        }
    }
    public void clearWorld() { previousWorld = null; deferredMeshes = null; }
    public void reloadMeshBuffers(VisualResources resources) {
        Runnable reload = () -> {
            deferredMeshes = null;
            HaloMeshBufferCache previous = meshBuffers;
            HaloMeshBufferCache replacement = previous.updated(resources);
            meshBuffers = replacement;
        };
        if (RenderSystem.isOnRenderThread()) reload.run();
        else RenderSystem.recordRenderCall(reload::run);
    }
    /** Recreate vertex layouts after Iris enables its extended entity format. */
    public void rebuildMeshBuffersForShaderPipeline() {
        Runnable rebuild = () -> {
            deferredMeshes = null;
            HaloMeshBufferCache previous = meshBuffers;
            meshBuffers = HaloMeshBufferCache.empty().updated(HaloMeshResources.snapshot().visuals());
            previous.close();
            reloadPrimitiveBuffers(HaloMeshResources.snapshot(), true);
        };
        if (RenderSystem.isOnRenderThread()) rebuild.run();
        else RenderSystem.recordRenderCall(rebuild::run);
    }
    public void shutdown() {
        Runnable close = () -> {
            deferredMeshes = null;
            HaloMeshBufferCache previous = meshBuffers;
            meshBuffers = HaloMeshBufferCache.empty();
            previous.close();
            primitiveBuffers.close(); primitiveBuffers = HaloMeshBufferCache.empty();
        };
        if (RenderSystem.isOnRenderThread()) close.run();
        else RenderSystem.recordRenderCall(close::run);
    }
    public static HaloRenderer getInstance() { return INSTANCE; }
    public IdlePhaseTracker.RenderState readLastRenderState(UUID uuid) { return HaloClientState.get().renderer().readLastRenderState(uuid); }
    public void clearIdlePhases() { HaloClientState.get().renderer().clearIdlePhases(); }
    public void renderHalos(MatrixStack matrices,Camera camera,float tickDelta) {
        deferredMeshes = null;
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.world==null) return;
        if(previousWorld!=client.world) { previousWorld=client.world;worldToken++; }
        HaloClientManager.getInstance().restoreLocalOwnership();
        var runtime=HaloClientState.get();
        var assets = HaloMeshResources.snapshot();
        runtime.definitions(assets.definitions());
        Map<UUID,FrameScene.EntitySample> samples=new LinkedHashMap<>();
        var assignments=runtime.assignments();
        for(var entity:client.world.getEntities()) {
            if(!(entity instanceof LivingEntity living))continue;
            if(!assignments.containsKey(entity.getUuid()) && runtime.getInstance(entity.getUuid())==null)continue;
            var pos=new network.azusake.halo.core.Vec3d(
                entity.prevX+(entity.getX()-entity.prevX)*tickDelta,
                entity.prevY+(entity.getY()-entity.prevY)*tickDelta,
                entity.prevZ+(entity.getZ()-entity.prevZ)*tickDelta);
            AnchorPose fallback=living instanceof net.minecraft.entity.player.PlayerEntity
                ? PlayerAnchorProvider.getInstance().resolve(living,tickDelta)
                : DefaultAnchorResolver.resolve(living,tickDelta);
            boolean localFirstPerson=living==client.player && client.options.getPerspective().isFirstPerson();
            AnchorPose captured=localFirstPerson ? fallback : AnchorCaptureCoordinator.resolve(
                entity.getUuid(),entity.getId(),client.world,new AnchorVec3(pos.x,pos.y,pos.z));
            samples.put(entity.getUuid(),new FrameScene.EntitySample(entity.getUuid(),entity.getId(),pos,
                entity.isAlive(),living.isSleeping(),entity.isInvisible(),captured==null?fallback:captured,fallback));
        }
        var up=camera.getVerticalPlane();var right=camera.getDiagonalPlane();
        FrameScene scene=new FrameScene(worldToken,System.currentTimeMillis(),System.nanoTime(),
            new FrameScene.CameraSample(core(camera.getPos()),
                new network.azusake.halo.core.Vec3d(up.x,up.y,up.z),
                new network.azusake.halo.core.Vec3d(right.x,right.y,right.z)),
            samples,matrices.peek().getPositionMatrix().get(new float[16]),
            pos -> {
                BlockPos block=BlockPos.ofFloored(pos.x,pos.y,pos.z);
                return Math.max(client.world.getLightingProvider().get(LightType.BLOCK).getLightLevel(block),
                    client.world.getLightingProvider().get(LightType.SKY).getLightLevel(block))/15f;
            }, id -> {
                try { client.getTextureManager().getTexture(game(id));return true; }
                catch(RuntimeException ex) { return false; }
            }, assets.visuals(), pos -> {
                BlockPos block=BlockPos.ofFloored(pos.x,pos.y,pos.z);
                return new LightSample(
                    client.world.getLightLevel(LightType.BLOCK, block),
                    client.world.getLightLevel(LightType.SKY, block));
            }, primitiveMode);
        FrameOutput output = runtime.renderFrame(scene);
        boolean shaderPack = OptionalIrisPassDetector.hasShaderPack();
        List<MeshDraw> solidLitMeshes = new ArrayList<>();
        List<MeshDraw> lateMeshes = new ArrayList<>();
        for (MeshDraw draw : output.meshes()) {
            (submitBeforeTranslucents(draw, shaderPack) ? solidLitMeshes : lateMeshes).add(draw);
        }
        Matrix4f meshModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f meshProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter meshSorting = RenderSystem.getVertexSorting();
        submitBatches(client, output.legacyBatches());
        HaloDrawSubmitter.submitPrimitives(client, output, primitiveBuffers, RenderEnvironment.WORLD);
        if (!solidLitMeshes.isEmpty()) {
            // Solid entity shaders write deferred G-buffer data. Submit these while
            // Iris is still before beginTranslucents(), which consumes that data.
            submitMeshes(client, new DeferredMeshes(output.visualGeneration(), assets.visuals(), solidLitMeshes,
                meshModelView, meshProjection, meshSorting));
        }
        if (!lateMeshes.isEmpty()) deferredMeshes = new DeferredMeshes(output.visualGeneration(), assets.visuals(),
            lateMeshes, meshModelView, meshProjection, meshSorting);
        if (client.isInSingleplayer()) IntegratedBridge.publishDiagnostics(runtime.diagnostics());
    }

    static boolean submitBeforeTranslucents(MeshDraw draw, boolean shaderPack) {
        // Preserve the established late particle path for glowing meshes and the
        // sorted late path for real alpha blending. Vanilla keeps its previous late
        // submission too; only an active pack's opaque lit geometry must precede
        // Iris beginTranslucents(), which consumes the solid/deferred entity buffer.
        return shaderPack && draw.directionalLighting() && !draw.blend();
    }

    /** Submit after entity buffers have been flushed, while the world shader pipeline is still active. */
    public void submitDeferredMeshes() {
        DeferredMeshes pending = deferredMeshes;
        deferredMeshes = null;
        if (pending == null || MinecraftClient.getInstance().world == null) return;
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        modelView.push();
        try {
            modelView.peek().getPositionMatrix().set(pending.modelView());
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(pending.projection(), pending.sorting());
            submitMeshes(MinecraftClient.getInstance(), pending);
        } finally {
            modelView.pop();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
        }
    }

    private void submitMeshes(MinecraftClient client, DeferredMeshes pending) {
        HaloDrawSubmitter.submitMeshes(client, new HaloDrawSubmitter.Submission(pending.generation(),
            pending.visuals(), pending.draws(), pending.modelView(), pending.projection()), meshBuffers, RenderEnvironment.WORLD);
    }

    private static void submitBatches(MinecraftClient client, List<DrawBatch> batches) {
        HaloDrawSubmitter.submitBatches(client, batches, RenderEnvironment.WORLD);
    }

    /** Immediate GUI submission; never changes the pending world pass. */
    public void submitPreview(FrameOutput output, VisualResources visuals) {
        MinecraftClient client = MinecraftClient.getInstance();
        HaloDrawSubmitter.submitBatches(client, output.legacyBatches(), RenderEnvironment.GUI);
        HaloDrawSubmitter.submitPrimitives(client, output, primitiveBuffers, RenderEnvironment.GUI);
        HaloDrawSubmitter.submitMeshes(client, new HaloDrawSubmitter.Submission(output.visualGeneration(),
            visuals, output.meshes(), new Matrix4f(RenderSystem.getModelViewMatrix()),
            new Matrix4f(RenderSystem.getProjectionMatrix())), meshBuffers, RenderEnvironment.GUI);
    }

    static int packLight(LightSample light) { return HaloDrawSubmitter.packLight(light); }
    static LivingEntity findEntityByUuid(MinecraftClient client,UUID uuid) {
        if(client.world!=null) for(var entity:client.world.getEntities())
            if(entity instanceof LivingEntity living && entity.getUuid().equals(uuid))return living;
        return null;
    }
}
