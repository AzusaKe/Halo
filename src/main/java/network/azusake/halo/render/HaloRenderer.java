package network.azusake.halo.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
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
    private record DeferredMeshes(List<DrawBatch> batches, Matrix4f modelView, Matrix4f projection, VertexSorter sorting) {}
    private DeferredMeshes deferredMeshes;
    public void clearWorld() { previousWorld = null; deferredMeshes = null; }
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
            }, assets.visuals());
        List<DrawBatch> batches = runtime.render(scene);
        {
            var meshes = new ArrayList<DrawBatch>();
            var legacy = new ArrayList<DrawBatch>();
            for (var batch : batches) {
                if (batch.material() instanceof MaterialState.Mesh) meshes.add(batch);
                else legacy.add(batch);
            }
            if (!meshes.isEmpty()) deferredMeshes = new DeferredMeshes(List.copyOf(meshes),
                new Matrix4f(RenderSystem.getModelViewMatrix()), new Matrix4f(RenderSystem.getProjectionMatrix()),
                RenderSystem.getVertexSorting());
            batches = legacy;
        }
        submitBatches(client, batches);
        if (client.isInSingleplayer()) IntegratedBridge.publishDiagnostics(runtime.diagnostics());
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
            submitBatches(MinecraftClient.getInstance(), pending.batches());
        } finally {
            modelView.pop();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
        }
    }

    private static void submitBatches(MinecraftClient client, List<DrawBatch> batches) {
        int previousTexture0 = RenderSystem.getShaderTexture(0);
        int previousTexture1 = RenderSystem.getShaderTexture(1);
        var previousShader = RenderSystem.getShader();
        try {
            for(DrawBatch batch:batches) submit(client,batch);
        }
        finally {
            RenderSystem.setShaderTexture(0, previousTexture0);
            RenderSystem.setShaderTexture(1, previousTexture1);
            if (previousShader != null) RenderSystem.setShader(() -> previousShader);
            RenderSystem.setShaderColor(1,1,1,1);RenderSystem.enableCull();RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableBlend();
        }
    }
    private static void submit(MinecraftClient client,DrawBatch b) {
        if(b.cull())RenderSystem.enableCull();else RenderSystem.disableCull();
        if(b.blend()){RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();}else RenderSystem.disableBlend();
        if(b.depthTest())RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(b.depthWrite());RenderSystem.setShaderColor(b.red(),b.green(),b.blue(),b.alpha());
        if (b.material() instanceof MaterialState.Mesh mesh) {
            if (!HaloMeshShader.bind(client, b, mesh)) return;
        } else if(b.textured()) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            RenderSystem.setShaderTexture(0,client.getTextureManager().getTexture(game(b.texture())).getGlId());
        } else RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        var builder=Tessellator.getInstance().getBuffer();
        builder.begin(b.topology()==DrawBatch.Topology.QUADS?VertexFormat.DrawMode.QUADS:VertexFormat.DrawMode.TRIANGLES,
            b.textured()?VertexFormats.POSITION_TEXTURE_COLOR:VertexFormats.POSITION_COLOR);
        for(var v:b.vertices()) {
            builder.vertex(v.x(),v.y(),v.z());
            if(b.textured())builder.texture(v.u(),v.v());
            builder.color(v.red(),v.green(),v.blue(),v.alpha()).next();
        }
        Tessellator.getInstance().draw();
    }
    static LivingEntity findEntityByUuid(MinecraftClient client,UUID uuid) {
        if(client.world!=null) for(var entity:client.world.getEntities())
            if(entity instanceof LivingEntity living && entity.getUuid().equals(uuid))return living;
        return null;
    }
}
