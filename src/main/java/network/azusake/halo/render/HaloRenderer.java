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
import static network.azusake.halo.platform.PlatformTypes.*;

/** Fabric render adapter: capture world facts, then submit core-generated batches. */
public final class HaloRenderer {
    private static final HaloRenderer INSTANCE=new HaloRenderer();
    private Object previousWorld;
    private long worldToken;
    public void clearWorld() { previousWorld = null; }
    public static HaloRenderer getInstance() { return INSTANCE; }
    public IdlePhaseTracker.RenderState readLastRenderState(UUID uuid) { return HaloClientState.get().renderer().readLastRenderState(uuid); }
    public void clearIdlePhases() { HaloClientState.get().renderer().clearIdlePhases(); }
    public void renderHalos(MatrixStack matrices,Camera camera,float tickDelta) {
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.world==null) return;
        if(previousWorld!=client.world) { previousWorld=client.world;worldToken++; }
        HaloClientManager.getInstance().restoreLocalOwnership();
        var runtime=HaloClientState.get();
        runtime.definitions(HaloJsonLoader.snapshot());
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
            });
        try {
            for(DrawBatch batch:runtime.render(scene)) submit(client,batch);
            if (client.isInSingleplayer()) IntegratedBridge.publishDiagnostics(runtime.diagnostics());
        }
        finally { RenderSystem.setShaderColor(1,1,1,1);RenderSystem.enableCull();RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableBlend(); }
    }
    private static void submit(MinecraftClient client,DrawBatch b) {
        if(b.cull())RenderSystem.enableCull();else RenderSystem.disableCull();
        if(b.blend()){RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();}else RenderSystem.disableBlend();
        if(b.depthTest())RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(b.depthWrite());RenderSystem.setShaderColor(b.red(),b.green(),b.blue(),b.alpha());
        if(b.textured()) {
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
