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
    public void shutdown() {
        Runnable close = () -> {
            deferredMeshes = null;
            HaloMeshBufferCache previous = meshBuffers;
            meshBuffers = HaloMeshBufferCache.empty();
            previous.close();
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
            });
        FrameOutput output = runtime.renderFrame(scene);
        if (!output.meshes().isEmpty()) deferredMeshes = new DeferredMeshes(output.visualGeneration(), assets.visuals(),
            output.meshes(), new Matrix4f(RenderSystem.getModelViewMatrix()),
            new Matrix4f(RenderSystem.getProjectionMatrix()), RenderSystem.getVertexSorting());
        submitBatches(client, output.legacyBatches());
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
            submitMeshes(MinecraftClient.getInstance(), pending);
        } finally {
            modelView.pop();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
        }
    }

    private void submitMeshes(MinecraftClient client, DeferredMeshes pending) {
        int previousTexture0 = RenderSystem.getShaderTexture(0);
        int previousTexture1 = RenderSystem.getShaderTexture(1);
        int previousTexture2 = RenderSystem.getShaderTexture(2);
        var previousShader = RenderSystem.getShader();
        try {
            // Direct VBO submission does not pass through a vanilla RenderLayer,
            // so its LIGHTMAP render phase cannot bind Sampler2 for us.
            client.gameRenderer.getLightmapTextureManager().enable();
            for (MeshDraw draw : pending.draws()) {
                applyState(draw.cull(), draw.blend(), draw.depthTest(), draw.depthWrite(),
                    draw.red(), draw.green(), draw.blue(), draw.alpha());
                var shader = HaloMeshShader.bind(client, draw);
                if (shader == null) continue;
                if (!meshBuffers.draw(pending.generation(), draw, pending.modelView(), pending.projection(), shader)) {
                    var fallback = new FrameOutput(pending.generation(), List.of(), List.of(draw))
                        .expandedBatches(pending.visuals());
                    for (DrawBatch batch : fallback) submit(client, batch);
                }
            }
        } finally {
            VertexBuffer.unbind();
            RenderSystem.setShaderTexture(0, previousTexture0);
            RenderSystem.setShaderTexture(1, previousTexture1);
            RenderSystem.setShaderTexture(2, previousTexture2);
            if (previousShader != null) RenderSystem.setShader(() -> previousShader);
            RenderSystem.setShaderColor(1,1,1,1);RenderSystem.enableCull();RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableBlend();
        }
    }

    private static void submitBatches(MinecraftClient client, List<DrawBatch> batches) {
        int previousTexture0 = RenderSystem.getShaderTexture(0);
        int previousTexture1 = RenderSystem.getShaderTexture(1);
        int previousTexture2 = RenderSystem.getShaderTexture(2);
        var previousShader = RenderSystem.getShader();
        try {
            // Tessellator submission likewise runs outside a RenderLayer. Bind
            // the current 16x16 vanilla lightmap before any lightmapped batch.
            client.gameRenderer.getLightmapTextureManager().enable();
            for(DrawBatch batch:batches) submit(client,batch);
        }
        finally {
            RenderSystem.setShaderTexture(0, previousTexture0);
            RenderSystem.setShaderTexture(1, previousTexture1);
            RenderSystem.setShaderTexture(2, previousTexture2);
            if (previousShader != null) RenderSystem.setShader(() -> previousShader);
            RenderSystem.setShaderColor(1,1,1,1);RenderSystem.enableCull();RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableBlend();
        }
    }
    private static void submit(MinecraftClient client,DrawBatch b) {
        applyState(b.cull(), b.blend(), b.depthTest(), b.depthWrite(), b.red(), b.green(), b.blue(), b.alpha());
        boolean nativeLight = b.light().available();
        if (b.material() instanceof MaterialState.Mesh mesh) {
            if (!HaloMeshShader.bind(client, b, mesh)) return;
        } else if (b.textured() && nativeLight) {
            if (!HaloMeshShader.bindLegacy(client, b)) return;
        } else if(b.textured()) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            RenderSystem.setShaderTexture(0,client.getTextureManager().getTexture(game(b.texture())).getGlId());
        } else if (nativeLight) RenderSystem.setShader(GameRenderer::getPositionColorLightmapProgram);
        else RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        var builder=Tessellator.getInstance().getBuffer();
        VertexFormat format = b.textured() ? VertexFormats.POSITION_TEXTURE_COLOR
            : nativeLight ? VertexFormats.POSITION_COLOR_LIGHT : VertexFormats.POSITION_COLOR;
        builder.begin(b.topology()==DrawBatch.Topology.QUADS?VertexFormat.DrawMode.QUADS:VertexFormat.DrawMode.TRIANGLES,
            format);
        int packedLight = packLight(b.light());
        for(var v:b.vertices()) {
            builder.vertex(v.x(),v.y(),v.z());
            if(b.textured())builder.texture(v.u(),v.v());
            builder.color(v.red(),v.green(),v.blue(),v.alpha());
            if (!b.textured() && nativeLight) builder.light(packedLight);
            builder.next();
        }
        Tessellator.getInstance().draw();
    }
    static int packLight(LightSample light) {
        LightSample sample = light.available() ? light : LightSample.FULL_BRIGHT;
        return net.minecraft.client.render.LightmapTextureManager.pack(sample.block(), sample.sky());
    }
    private static void applyState(boolean cull, boolean blend, boolean depthTest, boolean depthWrite,
                                   float red, float green, float blue, float alpha) {
        if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
        if(blend){RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();}else RenderSystem.disableBlend();
        if(depthTest)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(depthWrite);RenderSystem.setShaderColor(red,green,blue,alpha);
    }
    static LivingEntity findEntityByUuid(MinecraftClient client,UUID uuid) {
        if(client.world!=null) for(var entity:client.world.getEntities())
            if(entity instanceof LivingEntity living && entity.getUuid().equals(uuid))return living;
        return null;
    }
}
