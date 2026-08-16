package network.azusake.halo.render;

import network.azusake.halo.HaloMod;
import network.azusake.halo.data.HaloTransitionState;
import network.azusake.halo.animation.LayerAnimation;
import network.azusake.halo.animation.StartupAnimationConfig;
import network.azusake.halo.animation.TransitionAnimationResult;
import network.azusake.halo.animation.TransitionResolver;
import network.azusake.halo.data.GroupVisualSnapshot;
import network.azusake.halo.data.HaloDefinition;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;
import network.azusake.halo.physics.AnchorFrame;
import network.azusake.halo.physics.AnchorFrameCalculator;
import network.azusake.halo.shape.HaloPrimitive;
import network.azusake.halo.shape.RingPrimitive;
import network.azusake.halo.shape.BillboardPrimitive;
import network.azusake.halo.shape.HaloGroup;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Renders halo models at their computed world-space {@link AnchorFrame}.
 *
 * <p>All pose computation is delegated to {@link AnchorFrameCalculator}.
 * This class is a <em>pure rendering consumer</em> — it receives an
 * {@link AnchorFrame} and draws each layer of the {@code HaloModel}
 * with its own local transform relative to the anchor frame origin.</p>
 *
 * <h3>Billboard convention</h3>
 * <p>Billboard primitives are drawn on the <b>XZ plane</b> (horizontal,
 * normal = -Y in definition-local space).  The layer's accumulated
 * transform (anchor frame + layer local) determines the final world-space
 * orientation — no separate billboard-facing rotation is applied.</p>
 */
public final class HaloRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(HaloMod.MOD_ID);

    private static final HaloRenderer INSTANCE = new HaloRenderer();

    public static final boolean DEBUG_RENDERING = false;

    /**
     * When true, each ring segment is tinted with a distinct color so
     * the user can visually identify individual segments and diagnose
     * seam / overlap issues.  Set to false for normal rendering.
     */
    public static final boolean RING_DEBUG_SEGMENTS = false;

    /**
     * Throttle chat warnings for missing definitions — only show one per
     * halo instance per 30 seconds to avoid spamming the chat during
     * frame-by-frame rendering.
     */
    private long lastMissingDefWarningTime;

    private final AnchorFrameCalculator frameCalculator = AnchorFrameCalculator.getInstance();

    /** Per-entity previous sleep-hidden state, for edge detection. */
    private final Map<UUID, Boolean> prevSleepHidden = new HashMap<>();
    /** Per-entity previous invis-hidden state, for edge detection. */
    private final Map<UUID, Boolean> prevInvisHidden = new HashMap<>();
    /**
     * Last rendered render state per halo (idle phase, transition-active
     * flag, and the per-group offset/scale/alpha values drawn during a
     * transition).  Owned entirely by the renderer; the network layer reads
     * it for shutdown alignment when the shared instance was already removed
     * by the integrated server.
     */
    private final IdlePhaseTracker idlePhaseTracker = new IdlePhaseTracker();

    /** Timestamp (nanoTime) of the previous render frame, for delta-time. */
    private long prevFrameNanos;
    /** Whether we have seen at least one frame. */
    private boolean firstFrame = true;
    /** EMA-smoothed frame delta-time, to suppress nanoTime jitter. */
    private double smoothedDt = -1;

    // ------------------------------------------------------------------
    // 26.1 GPU pipeline state
    // ------------------------------------------------------------------

    /** Shared vertex-data allocator for building per-primitive meshes. */
    private static final ByteBufferBuilder BUFFER_ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private static RenderPipeline register(RenderPipeline pipeline) {
        return RenderPipelines.register(pipeline);
    }

    /** Solid-colour halo pipeline: POSITION_COLOR triangles, translucent, no cull. */
    private static final RenderPipeline PIPELINE_COLOR = register(
        RenderPipeline.builder(new RenderPipeline.Snippet[0])
            .withLocation(Identifier.fromNamespaceAndPath(HaloMod.MOD_ID, "pipeline/halo_color"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
            .withCull(false)
            .build());

    /** Textured halo pipeline: POSITION_TEX_COLOR triangles, translucent, no cull. */
    private static final RenderPipeline PIPELINE_TEX = register(
        RenderPipeline.builder(new RenderPipeline.Snippet[0])
            .withLocation(Identifier.fromNamespaceAndPath(HaloMod.MOD_ID, "pipeline/halo_tex"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
            .withCull(false)
            .build());

    /** Textured ring pipeline with face culling for separate inner/outer textures. */
    private static final RenderPipeline PIPELINE_TEX_CULL = register(
        RenderPipeline.builder(new RenderPipeline.Snippet[0])
            .withLocation(Identifier.fromNamespaceAndPath(HaloMod.MOD_ID, "pipeline/halo_tex_cull"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
            .withCull(true)
            .build());

    private HaloRenderer() { /* singleton */ }

    public static HaloRenderer getInstance() {
        return INSTANCE;
    }

    /**
     * The last rendered render state of a halo (idle phase + whether the last
     * frame was inside a transition + per-group visual values), or {@code null}
     * when there is no fresh record.  Used by the network layer to align a
     * shutdown head after the shared instance was already removed (integrated
     * server) — rendering state stays client-owned.
     *
     * @param uuid the halo's entity UUID
     * @return the last render state, or null when never rendered recently
     */
    public IdlePhaseTracker.RenderState readLastRenderState(UUID uuid) {
        return idlePhaseTracker.read(uuid, System.currentTimeMillis());
    }

    /** Drop all recorded phases (full sync / world change). */
    public void clearIdlePhases() {
        idlePhaseTracker.clear();
    }

    /**
     * A deferred halo primitive draw with its squared camera distance.
     * Draws are collected during scene-graph traversal and executed after
     * sorting far→near so translucent alpha blending matches depth order.
     */
    private record SortedDraw(double distanceSquared, Runnable draw) {
    }

    /**
     * Squared distance of a camera-relative position matrix translation from
     * the camera.  The pose stack is rooted at the camera ({@code renderHalos}
     * applies the {@code -cameraPos} translate), so the matrix translation is
     * already camera-relative world space.
     */
    private static double centerDistanceSquared(Matrix4f positionMatrix) {
        float x = positionMatrix.m30();
        float y = positionMatrix.m31();
        float z = positionMatrix.m32();
        return (double) x * x + (double) y * y + (double) z * z;
    }

    // ------------------------------------------------------------------
    // Public entry point
    // ------------------------------------------------------------------

    /**
     * Render every visible halo for the current frame.
     */
    public void renderHalos(PoseStack matrices, Vec3 cameraPos, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        Camera camera = client.gameRenderer.mainCamera();

        idlePhaseTracker.prune(System.currentTimeMillis());

        var visible = HaloClientManager.getInstance().getVisibleHalos(camera);

        // Clean stale entries from the frame calculator's internal maps
        Set<UUID> activeUuids = visible.stream()
            .map(HaloInstance::getEntityUuid)
            .collect(Collectors.toSet());
        frameCalculator.retainOnly(activeUuids);

        // Compute frame delta with EMA smoothing to suppress nanoTime jitter
        long frameNanos = System.nanoTime();
        double rawDt;
        if (firstFrame) {
            rawDt = 0.0;
            firstFrame = false;
        } else {
            rawDt = (frameNanos - prevFrameNanos) / 1_000_000_000.0;
            rawDt = Math.max(0.001, Math.min(rawDt, 0.1));
        }
        prevFrameNanos = frameNanos;

        // EMA smoothing: blend raw frame-time into a rolling average
        if (smoothedDt < 0) {
            smoothedDt = rawDt;
        } else {
            // Weight new frame at 20% — smooth but responsive
            smoothedDt = smoothedDt * 0.8 + rawDt * 0.2;
        }
        final double dt = smoothedDt;

        // 26.1: the level-render pose stack is rooted at the world origin;
        // translate by -camera position to reach camera-relative world space
        // (the camera view rotation is applied by the GPU at draw time).
        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        List<SortedDraw> draws = new ArrayList<>();
        try {
            for (HaloInstance instance : visible) {
                try {
                    renderSingleHalo(instance, matrices, camera, cameraPos, tickDelta, client, dt, draws);
                } catch (Exception e) {
                    LOG.warn("[HaloRenderer] error rendering halo for entity {}: {}", instance.getEntityUuid(), e.getMessage(), e);
                }
            }

            // Execute every collected halo primitive far→near so translucent
            // alpha blending matches camera depth order (near geometry blends
            // over far geometry instead of declaration order).  Each primitive
            // also writes the main depth target, so the nearest halo fragment
            // depth ends up in the main depth buffer — this lets the vanilla
            // transparency composite depth-sort clouds/weather against halos.
            draws.sort(Comparator.comparingDouble(SortedDraw::distanceSquared).reversed());
            for (SortedDraw sortedDraw : draws) {
                sortedDraw.draw().run();
            }

            // Maintain non-active instances (NULL / deactivated):
            //   - NULL + hiddenByState (sleep/invis): keep while still hidden,
            //     reactivate with STARTING (or NORMAL if no startup anim) when visible
            //   - everything else non-active (explicit hide's NULL, ENDING stuck on an
            //     inactive instance, dead entity, missing def) → permanently remove
            List<UUID> removals = new ArrayList<>();
            for (HaloInstance inst : HaloManager.getInstance().getAllInstances()) {
            if (inst.isActive()) continue; // active instances handled in renderSingleHalo

            UUID uuid = inst.getEntityUuid();
            LivingEntity entity = HaloRenderer.findEntityByUuid(client, uuid);
            if (entity == null || !entity.isAlive()) {
                removals.add(uuid);
                continue;
            }

            if (inst.getTransitionState() == HaloTransitionState.NULL && inst.isHiddenByState()) {
                HaloDefinition def = HaloJsonLoader.getDefinition(inst.getDefinitionId()).orElse(null);
                if (def == null) {
                    removals.add(uuid);
                    continue;
                }
                boolean sleepHidden = def.hideOnSleep() && entity.isSleeping();
                boolean invisHidden = !def.displayInInvisible() && entity.isInvisible();
                if (sleepHidden || invisHidden) {
                    continue; // entity still hidden — wait for wake-up
                }
                // Entity visible again → reactivate
                inst.reactivate();
                inst.setHiddenByState(false);
                inst.setEntitySleeping(entity.isSleeping());
                inst.setEntityInvisible(entity.isInvisible());
                if (def.startupAnimation().isPresent()) {
                    inst.setTransitionState(HaloTransitionState.STARTING);
                    inst.startTransition((System.currentTimeMillis() - inst.getCreatedAtTime()) / 1000.0);
                } else {
                    inst.setTransitionState(HaloTransitionState.NORMAL);
                }
                continue;
            }

            // Permanently useless: explicit hide's NULL, inactive ENDING (e.g. hide
            // targeted an already-hidden halo), dead/missing entity, missing def
            removals.add(uuid);
            }
            for (UUID uuid : removals) {
                HaloManager.getInstance().removeClientHalo(uuid);
                prevSleepHidden.remove(uuid);
                prevInvisHidden.remove(uuid);
            }
        } finally {
            matrices.popPose();
        }
    }

    // ------------------------------------------------------------------
    // Single halo
    // ------------------------------------------------------------------

    /**
     * When a halo is hidden while still inside a transition (e.g. during its
     * startup), the shutdown must start from the values the renderer was
     * actually drawing — not from the idle animation — or the fade-out would
     * jump.  Reads the per-group visuals recorded on the last transition
     * frame and stashes them on the instance for {@link #resolveAnimation}.
     */
    private void stashMidTransitionVisuals(HaloInstance instance) {
        IdlePhaseTracker.RenderState renderState = idlePhaseTracker.read(
            instance.getEntityUuid(), System.currentTimeMillis());
        if (renderState != null && renderState.transitionActive()
                && !renderState.groups().isEmpty()) {
            instance.setHideVisuals(renderState.groups());
        }
    }

    private boolean renderSingleHalo(HaloInstance instance, PoseStack matrices,
                                      Camera camera, Vec3 cameraPos, float tickDelta,
                                      Minecraft client, double dt, List<SortedDraw> draws) {
        // ---- resolve entity ----
        LivingEntity entity = findEntityByUuid(client, instance.getEntityUuid());
        if (entity == null || !entity.isAlive()) {
            instance.deactivate();
            return false;
        }

        // ---- resolve definition ----
        HaloDefinition def = HaloJsonLoader.getDefinition(instance.getDefinitionId()).orElse(null);
        if (def == null) {
            LOG.warn("[HaloRenderer] definition not found, deactivating halo: entity={} def={}",
                instance.getEntityUuid(), instance.getDefinitionId());
            instance.deactivate();

            // Notify the local player through chat so they know a resource pack is missing.
            // Throttled to at most one warning per 30 seconds to avoid chat spam.
            long now = System.currentTimeMillis();
            if (client.player != null && now - lastMissingDefWarningTime > 30_000) {
                lastMissingDefWarningTime = now;
                client.player.sendSystemMessage(Component.literal(
                    "§e[Halo] Missing definition: §f" + instance.getDefinitionId() +
                    "§e — install the resource pack or ask the server admin."
                ));
            }
            return false;
        }

        // ---- hide while sleeping (reads per-tick cache) ----
        boolean sleepHidden = def.hideOnSleep() && instance.isEntitySleeping();

        // ---- hide while invisible (reads per-tick cache) ----
        boolean invisHidden = !def.displayInInvisible() && instance.isEntityInvisible();

        boolean shouldRender = !sleepHidden && !invisHidden;

        // ---- Build transition context (used by state machine and rendering) ----
        StartupAnimationConfig startupConfig = def.startupAnimation().orElse(null);
        StartupAnimationConfig shutdownConfig = def.shutdownAnimation().orElse(null);

        // ---- State machine: sleep/invis edge detection (NORMAL → STARTING/ENDING) ----
        HaloTransitionState state = instance.getTransitionState();
        UUID uuid = instance.getEntityUuid();
        boolean prevSleep = prevSleepHidden.getOrDefault(uuid, false);
        boolean prevInvis = prevInvisHidden.getOrDefault(uuid, false);
        boolean wasHidden = prevSleep || prevInvis;
        boolean nowHidden = sleepHidden || invisHidden;

        if (state == HaloTransitionState.NORMAL) {
            if (!wasHidden && nowHidden) {
                // Entity entered sleep/invis → start shutdown animation
                if (shutdownConfig != null || startupConfig != null) {
                    instance.setHiddenByState(true);
                    // Align the shutdown head to the idle animation's actual
                    // phase right now (rawAnimTime - startupDur after a
                    // completed startup), not the raw wall-clock time.
                    double freeze = instance.currentAnimTime(startupConfig);
                    instance.setTransitionState(HaloTransitionState.ENDING);
                    instance.startTransition(freeze);
                    stashMidTransitionVisuals(instance);
                    state = HaloTransitionState.ENDING;
                }
            } else if (wasHidden && !nowHidden) {
                // Entity woke/became visible → start startup animation
                if (startupConfig != null) {
                    instance.setHiddenByState(false);
                    instance.setTransitionState(HaloTransitionState.STARTING);
                    instance.startTransition((System.currentTimeMillis() - instance.getCreatedAtTime()) / 1000.0);
                    state = HaloTransitionState.STARTING;
                }
            }
        }
        prevSleepHidden.put(uuid, sleepHidden);
        prevInvisHidden.put(uuid, invisHidden);

        // ---- State machine: terminal state and transition completion ----
        if (state == HaloTransitionState.ENDING && !instance.isTransitioning(startupConfig, shutdownConfig)) {
            // ENDING animation complete → mark as NULL but keep in map for potential reactivation
            instance.setTransitionState(HaloTransitionState.NULL);
            instance.deactivate();
            prevSleepHidden.remove(uuid);
            prevInvisHidden.remove(uuid);
            return false;
        }
        if (state == HaloTransitionState.STARTING && !instance.isTransitioning(startupConfig, shutdownConfig)) {
            // STARTING animation complete
            if (!shouldRender) {
                // Entity still sleeping/invisible → skip NORMAL, mark as NULL for reactivation
                instance.setHiddenByState(true);
                instance.setTransitionState(HaloTransitionState.NULL);
                instance.deactivate();
                prevSleepHidden.remove(uuid);
                prevInvisHidden.remove(uuid);
                return false;
            }
            instance.setTransitionState(HaloTransitionState.NORMAL);
            state = HaloTransitionState.NORMAL;
        }

        // ---- Rendering gate ----
        if (state == HaloTransitionState.NULL) return false;
        if (state == HaloTransitionState.NORMAL && !shouldRender) return false;
        // STARTING and ENDING always render (animation must play regardless of shouldRender)

        // Determine if transition is currently active
        boolean transitionActive = (state == HaloTransitionState.STARTING || state == HaloTransitionState.ENDING)
            && instance.getTransitionStartTime() > 0;
        double transitionElapsed = instance.getTransitionElapsed();
        boolean isStartup = state == HaloTransitionState.STARTING;

        // ---- compute anchor frame ----
        AnchorFrame frame = frameCalculator.calculate(instance, entity, def, cameraPos, tickDelta, dt);

        // ---- camera-relative position ----
        Vec3 crp = frame.cameraRelativePos();
        if (Math.abs(crp.x) > 1000 || Math.abs(crp.y) > 1000 || Math.abs(crp.z) > 1000) {
            return false;
        }

        // ---- elapsed time since halo creation ----
        final double rawAnimTime = (System.currentTimeMillis() - instance.getCreatedAtTime()) / 1000.0;

        // ---- adjust animTime so the periodic animation freezes during a
        // transition and resumes at its actual phase afterwards ----
        final double animTime;
        long transitionStart = instance.getTransitionStartTime();
        if (transitionStart > 0
                && (state == HaloTransitionState.STARTING || state == HaloTransitionState.ENDING)) {
            // Transition in progress — freeze the periodic animation at the
            // phase captured when the transition started.
            animTime = instance.getTransitionFreezeAnimTime();
        } else if (transitionStart > 0) {
            // NORMAL right after a completed startup — resume the periodic
            // animation from where it would have been, skipping the transition.
            double transitionDur = startupConfig != null ? startupConfig.maxDuration() : 0.0;
            animTime = Math.max(0.0, rawAnimTime - transitionDur);
        } else {
            animTime = rawAnimTime;
        }
        idlePhaseTracker.record(instance.getEntityUuid(), animTime, transitionActive);

        // ---- compute light at halo position for non-glowing layers ----
        BlockPos lightPos = BlockPos.containing(frame.worldPosition());
        int blockLight = client.level.getLightEngine().getLayerListener(LightLayer.BLOCK).getLightValue(lightPos);
        int skyLight = client.level.getLightEngine().getLayerListener(LightLayer.SKY).getLightValue(lightPos);
        // Brightness multiplier: use the stronger of block/sky light, with a floor so
        // the layer never vanishes completely.  This approximates the lightmap without
        // depending on its texture unit being bound — works with both vanilla and Iris.
        float brightness = Math.max(blockLight, skyLight) / 15.0f;
        brightness = Math.max(brightness, 0.04f);

        // ---- render model groups ----
        var model = def.model();
        if (model.groups().isEmpty()) {
            return true; // empty model, nothing to draw
        }

        matrices.pushPose();
        try {
            // Step 1: Anchor frame → world (pose stack already camera-relative
            // via the -camera translate in renderHalos, so use the world-space
            // anchor position directly).
            Vec3 wp = frame.worldPosition();
            matrices.translate(wp.x, wp.y, wp.z);
            applyQuaternionRotation(matrices, frame.worldOrientation());
            matrices.scale(frame.scale(), frame.scale(), frame.scale());

            // Step 2: Definition-level animation (whole-halo-body, visual only).
            // The definition animation acts as an implicit root group: its
            // alpha/glow become the initial inherited values for every top-level
            // group, so animation.alpha at the definition root fades the whole
            // halo (offset/rotation/scale still apply to the whole body).
            float defAlpha = 1.0f;
            float defGlow = 1.0f;
            var defAnimOpt = def.animation();
            if (defAnimOpt.isPresent()) {
                var defAnim = defAnimOpt.get();
                if (!defAnim.isEmpty()) {
                    Vec3 defOffset = defAnim.evaluateOffset(animTime);
                    Quaternionf defRot = defAnim.evaluateRotation(animTime);
                    float[] defScale = defAnim.evaluateScale(animTime);
                    matrices.translate(defOffset.x, defOffset.y, defOffset.z);
                    applyQuaternionRotation(matrices, defRot);
                    matrices.scale(defScale[0], defScale[1], defScale[2]);
                    defAlpha = defAnim.evaluateAlpha(animTime);
                    defGlow = defAnim.evaluateGlow(animTime);
                }
            }

            // Step 3: Recursive group rendering
            for (HaloGroup group : model.groups()) {
                // Root groups inherit the definition root's alpha/glow
                renderGroup(group, matrices, camera, cameraPos, animTime, brightness, defAlpha, defGlow,
                    transitionActive, transitionElapsed, isStartup, instance,
                    startupConfig, shutdownConfig, draws);
            }
        } finally {
            matrices.popPose();
        }

        return true;
    }

    // ------------------------------------------------------------------
    // Recursive group rendering (scene-graph traversal)
    // ------------------------------------------------------------------

    /**
     * Recursively render a {@link HaloGroup}: apply group transform,
     * draw all primitives, then recurse into child groups.
     *
     * <p>When a transition is active, the group's pre-built animation
     * is evaluated and applied as additional offset, rotation, scale, and
     * alpha (rotation in YXZ order, matching the idle animation).</p>
     */
    private void renderGroup(HaloGroup group, PoseStack matrices, Camera camera, Vec3 cameraPos,
                              double animTime, float brightness,
                              float inheritedAlpha, float inheritedGlow,
                              boolean transitionActive, double transitionElapsed, boolean isStartup,
                              HaloInstance instance,
                              StartupAnimationConfig startupConfig, StartupAnimationConfig shutdownConfig,
                              List<SortedDraw> draws) {
        matrices.pushPose();
        try {
            // Group local transform
            matrices.translate(group.position().x, group.position().y, group.position().z);
            applyQuaternionRotation(matrices, group.rotation());
            matrices.scale(group.scale(), group.scale(), group.scale());

            // Per-group visual animation (offset + rotation + scale)
            // Blocked during transition — all startup animations must complete first
            if (!transitionActive) {
                group.animation().ifPresent(anim -> {
                    if (!anim.isEmpty()) {
                        Vec3 animOff = anim.evaluateOffset(animTime);
                        Quaternionf animRot = anim.evaluateRotation(animTime);
                        float[] animScale = anim.evaluateScale(animTime);
                        matrices.translate(animOff.x, animOff.y, animOff.z);
                        applyQuaternionRotation(matrices, animRot);
                        matrices.scale(animScale[0], animScale[1], animScale[2]);
                    }
                });
            }

            // Apply transition animation
            float transitionAlpha = 1.0f;
            boolean transitionDrivesAlpha = false;
            if (transitionActive) {
                TransitionAnimationResult anim = resolveAnimation(
                    group, instance, isStartup, startupConfig, shutdownConfig);
                transitionDrivesAlpha = anim != null;
                float[] appliedOffset = new float[]{0f, 0f, 0f};
                float[] appliedScale = new float[]{1f, 1f, 1f};
                float[] appliedRotation = new float[]{0f, 0f, 0f};
                float appliedAlpha = 1.0f;
                if (anim != null) {
                    // The transition overrides the channels it defines; a
                    // channel without a segment is held at the frozen idle
                    // value by withTail/withHead, so the handoff is seamless.
                    TransitionAnimationResult.TransitionResult tr = anim.evaluate(transitionElapsed);
                    matrices.translate(tr.offset().x, tr.offset().y, tr.offset().z);
                    appliedOffset = new float[]{
                        (float) tr.offset().x, (float) tr.offset().y, (float) tr.offset().z};
                    appliedScale = tr.scale();
                    appliedAlpha = tr.alpha();
                    transitionAlpha = tr.alpha();
                    // Rotation is driven by the transition queue; for a group
                    // whose transition does not drive rotation the idle
                    // rotation stays frozen at the trigger phase (F8).
                    appliedRotation = anim.rotationAnimated()
                        ? tr.rotationDegrees()
                        : frozenIdleVisuals(group, animTime).rotationDegrees();
                    applyQuaternionRotation(matrices, LayerAnimation.quaternionFromYxzDegrees(
                        appliedRotation[0], appliedRotation[1], appliedRotation[2]));
                    matrices.scale(appliedScale[0], appliedScale[1], appliedScale[2]);
                } else {
                    // No transition animation for this group (e.g. child groups
                    // without transition segments): freeze its idle animation
                    // at the trigger phase across every channel, so the
                    // on-screen pose stays continuous across the transition.
                    FrozenIdleVisuals frozen = frozenIdleVisuals(group, animTime);
                    matrices.translate(frozen.offset()[0], frozen.offset()[1], frozen.offset()[2]);
                    applyQuaternionRotation(matrices, LayerAnimation.quaternionFromYxzDegrees(
                        frozen.rotationDegrees()[0], frozen.rotationDegrees()[1],
                        frozen.rotationDegrees()[2]));
                    matrices.scale(frozen.scale()[0], frozen.scale()[1], frozen.scale()[2]);
                    appliedOffset = frozen.offset();
                    appliedRotation = frozen.rotationDegrees();
                    appliedScale = frozen.scale();
                    appliedAlpha = frozen.alpha();
                }
                // Record the values this frame actually drew so a hide that
                // lands mid-transition can head-patch the shutdown queues to
                // the exact on-screen state instead of the idle animation.
                idlePhaseTracker.recordGroupVisual(instance.getEntityUuid(), group.id().orElse(""),
                    appliedOffset, appliedScale, appliedAlpha, appliedRotation, System.currentTimeMillis());
            }

            // During transitions the transition's alpha overrides the layer's
            // own alpha for groups with a transition segment; groups without a
            // transition freeze their own alpha at the trigger phase.  Either
            // way the value equals the idle animation at the frozen phase
            // unless a real transition alpha segment fades it.  Glow keeps
            // following the layer animation at the frozen phase.  Outside
            // transitions the layer's own alpha drives the fade as before.
            //
            // Both channels inherit multiplicatively down the scene tree: each
            // group's effective value = inherited value × its own animated value,
            // so defining alpha on a parent group fades the whole subtree.
            float layerAlpha = 1.0f;
            float animatedGlow = 1.0f;
            if (group.animation().isPresent()) {
                var layerAnim = group.animation().get();
                if (!layerAnim.isEmpty()) {
                    layerAlpha = layerAnim.evaluateAlpha(animTime);
                    animatedGlow = layerAnim.evaluateGlow(animTime);
                }
            }
            float finalAlpha = transitionActive
                ? inheritedAlpha * (transitionDrivesAlpha ? transitionAlpha : layerAlpha)
                : inheritedAlpha * layerAlpha;
            float effectiveGlow = inheritedGlow * animatedGlow;

            // Collect all primitives in this group as deferred draws, sorted
            // later by camera distance.  The accumulated position matrix is
            // snapshotted here because the matrix stack unwinds before the
            // sorted draws execute.
            Matrix4f positionMatrix = new Matrix4f(matrices.last().pose());
            double distSq = centerDistanceSquared(positionMatrix);
            for (HaloPrimitive primitive : group.primitives()) {
                if (primitive instanceof BillboardPrimitive bp) {
                    draws.add(new SortedDraw(distSq,
                        () -> renderBillboard(bp, positionMatrix, camera, group.glowing(), brightness, effectiveGlow, finalAlpha)));
                } else if (primitive instanceof RingPrimitive rp) {
                    draws.add(new SortedDraw(distSq,
                        () -> renderRing(rp, positionMatrix, group.glowing(), brightness, effectiveGlow, finalAlpha)));
                }
            }

            // Recurse into child groups — children inherit this group's effective
            // alpha/glow multiplicatively. A group can opt out per channel via
            // inherit_alpha / inherit_glow (default true); when disabled, the
            // subtree starts fresh from 1.0 instead of inheriting.
            float childAlpha = group.inheritAlpha() ? finalAlpha : 1.0f;
            float childGlow = group.inheritGlow() ? effectiveGlow : 1.0f;
            for (HaloGroup child : group.children()) {
                renderGroup(child, matrices, camera, cameraPos, animTime, brightness, childAlpha, childGlow,
                    transitionActive, transitionElapsed, isStartup, instance,
                    startupConfig, shutdownConfig, draws);
            }
        } finally {
            matrices.popPose();
        }
    }

    /**
     * The idle visual values a group is drawn with during a transition when
     * its transition does not override a channel: the group's idle animation
     * frozen at {@code frozenAnimTime} (F8).  Identity defaults when the group
     * has no idle animation.  With this, the final transition frame equals the
     * first frame of the resumed idle animation on every channel.
     *
     * @param offset          frozen offset (3 components)
     * @param rotationDegrees frozen rotation (YXZ Euler degrees, 3 components)
     * @param scale           frozen scale (3 components)
     * @param alpha           frozen alpha multiplier
     */
    static FrozenIdleVisuals frozenIdleVisuals(HaloGroup group, double frozenAnimTime) {
        var idle = group.animation().orElse(LayerAnimation.EMPTY);
        if (idle.isEmpty()) {
            return FrozenIdleVisuals.IDENTITY;
        }
        Vec3 off = idle.evaluateOffset(frozenAnimTime);
        return new FrozenIdleVisuals(
            new float[]{(float) off.x, (float) off.y, (float) off.z},
            idle.evaluateRotationDegrees(frozenAnimTime),
            idle.evaluateScale(frozenAnimTime),
            idle.evaluateAlpha(frozenAnimTime));
    }

    /** Immutable bundle of frozen idle visual values (F8). */
    record FrozenIdleVisuals(float[] offset, float[] rotationDegrees, float[] scale, float alpha) {
        static final FrozenIdleVisuals IDENTITY = new FrozenIdleVisuals(
            new float[]{0f, 0f, 0f}, new float[]{0f, 0f, 0f}, new float[]{1f, 1f, 1f}, 1.0f);
    }

    /**
     * Resolve the endpoint-aligned transition animation for a group, caching
     * per-instance so the endpoint patch is computed only once per transition.
     *
     * <p>STARTING plays the startup config forward with derived tail values
     * aligned to the group's idle animation at the resume phase.  ENDING plays
     * an explicit shutdown config forward with derived head values aligned to
     * the idle animation at the hide phase; when no shutdown is defined the
     * startup config is reversed and aligned the same way.  A hide that lands
     * mid-transition instead head-aligns to the exact on-screen values the
     * renderer was drawing (stashed via {@link HaloInstance#getHideVisuals()}).</p>
     */
    private TransitionAnimationResult resolveAnimation(HaloGroup group, HaloInstance instance,
                                                        boolean isStartup,
                                                        StartupAnimationConfig startupConfig,
                                                        StartupAnimationConfig shutdownConfig) {
        TransitionResolver.Resolution resolution =
            TransitionResolver.resolve(isStartup, startupConfig, shutdownConfig);
        if (resolution == null) {
            return null;
        }
        String groupKey = group.id().orElse("");
        TransitionAnimationResult cached = instance.getTransitionAnimation(groupKey);
        if (cached != null) {
            return cached;
        }
        TransitionAnimationResult base = resolution.config().getAnimationForGroup(group.id());
        if (base == null) {
            return null;
        }
        LayerAnimation idle = group.animation().orElse(LayerAnimation.EMPTY);
        double freeze = instance.getTransitionFreezeAnimTime();
        // A hide that happened mid-transition must start the fade-out from
        // the exact on-screen values the renderer was drawing (recorded every
        // transition frame), not from the idle animation.
        GroupVisualSnapshot hide = instance.getHideVisuals().get(groupKey);
        TransitionAnimationResult patched;
        if (isStartup) {
            patched = base.withTail(idle, freeze);
        } else if (resolution.reversed()) {
            // Fallback shutdown = startup timeline played backwards.  The
            // reversed queues are cached at the definition level; only the
            // derived head values are patched per instance.
            TransitionAnimationResult reversedBase =
                resolution.config().getReversedAnimationForGroup(group.id());
            if (reversedBase == null) {
                return null;
            }
            patched = hide != null
                ? reversedBase.withHeadValues(hide.offset(), hide.scale(), hide.alpha(), hide.rotation())
                : reversedBase.withHead(idle, freeze);
        } else {
            patched = hide != null
                ? base.withHeadValues(hide.offset(), hide.scale(), hide.alpha(), hide.rotation())
                : base.withHead(idle, freeze);
        }
        instance.putTransitionAnimation(groupKey, patched);
        return patched;
    }

    // ------------------------------------------------------------------
    // Billboard primitive (XZ plane, normal = -Y)
    // ------------------------------------------------------------------

    /**
     * Draw a billboard quad.  By default the quad lies on the XZ plane
     * (horizontal, normal = -Y) and the layer's accumulated matrix-stack
     * transform provides the world-space placement.  When the primitive's
     * {@code face_camera} is set, the quad is instead drawn fully facing the
     * camera: the plane normal always points toward the camera and that
     * orientation cannot be overridden by any animation rotation.
     */
    private void renderBillboard(BillboardPrimitive billboard, Matrix4f positionMatrix, Camera camera,
                                 boolean glowing, float brightness, float animatedGlow, float alpha) {
        float hw = billboard.size().x / 2.0f;  // half-width (X)
        float hd = billboard.size().y / 2.0f;  // half-depth (Z) — size.y maps to Z axis

        if (DEBUG_RENDERING) {
            hw *= 5.0f;
            hd *= 5.0f;
        }

        // Camera-facing quads are rebuilt in camera-relative world space from
        // the matrix's position + scale (rotation discarded), so no animation
        // rotation can override the facing.  Other quads keep using the
        // accumulated matrix-stack transform.
        Vector3f c0, c1, c2, c3; // quad corners in the position-matrix space
        Matrix4f drawMatrix;
        if (billboard.faceCamera()) {
            // The matrix-stack position matrix is camera-relative world space
            // (the GPU applies the view rotation at draw time), and the
            // camera's vertical/diagonal planes are world-space screen-up /
            // screen-right — so the facing basis is computed directly in world
            // space from the accumulated position matrix.
            CameraFacing facing = computeCameraFacing(
                positionMatrix, hw, hd,
                new Vector3f(camera.upVector()), new Vector3f(camera.leftVector()));
            // Identity matrix — the corners are already camera-relative world
            // coordinates, so every accumulated rotation is fully discarded.
            drawMatrix = new Matrix4f();
            Vector3f rightHalf = new Vector3f(facing.right()).mul(facing.halfWidth());
            Vector3f upHalf = new Vector3f(facing.up()).mul(facing.halfDepth());
            c0 = new Vector3f(facing.center()).sub(rightHalf).sub(upHalf);
            c1 = new Vector3f(facing.center()).add(rightHalf).sub(upHalf);
            c2 = new Vector3f(facing.center()).add(rightHalf).add(upHalf);
            c3 = new Vector3f(facing.center()).sub(rightHalf).add(upHalf);
        } else {
            drawMatrix = positionMatrix;
            c0 = new Vector3f(-hw, 0.0f, -hd);
            c1 = new Vector3f( hw, 0.0f, -hd);
            c2 = new Vector3f( hw, 0.0f,  hd);
            c3 = new Vector3f(-hw, 0.0f,  hd);
        }

        // Self-illuminating primitives are lit by the animation.glow channel;
        // otherwise brightness follows the ambient light at the halo position.
        float brightnessFactor = glowing ? animatedGlow : brightness;
        float r = brightnessFactor;
        float g = brightnessFactor;
        float b = brightnessFactor;

        // 26.1 GPU pipeline: billboards are emitted as triangle soup (two
        // triangles per quad) into the shared allocator and drawn non-indexed.
        AbstractTexture texture = resolveTexture(billboard.texture());
        if (texture != null) {
            // Default: XZ plane at Y=0, normal = -Y (faces downward toward the
            // entity head).
            // Vertex winding from BELOW (-Y) is CCW → front face faces -Y:
            // (-hw, 0, -hd)  →  (+hw, 0, -hd)  →  (+hw, 0, +hd)  →  (-hw, 0, +hd)
            // face_camera keeps the same UV layout upright (V=0 at the +up side).
            // Tint texture by the effective brightness factor (fullbright at 1.0)
        BufferBuilder builder = new BufferBuilder(
            BUFFER_ALLOCATOR, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
            builder.addVertex(drawMatrix, c0.x, c0.y, c0.z).setUv(0.0f, 1.0f).setColor(r, g, b, alpha);
            builder.addVertex(drawMatrix, c1.x, c1.y, c1.z).setUv(1.0f, 1.0f).setColor(r, g, b, alpha);
            builder.addVertex(drawMatrix, c2.x, c2.y, c2.z).setUv(1.0f, 0.0f).setColor(r, g, b, alpha);
            builder.addVertex(drawMatrix, c0.x, c0.y, c0.z).setUv(0.0f, 1.0f).setColor(r, g, b, alpha);
            builder.addVertex(drawMatrix, c2.x, c2.y, c2.z).setUv(1.0f, 0.0f).setColor(r, g, b, alpha);
            builder.addVertex(drawMatrix, c3.x, c3.y, c3.z).setUv(0.0f, 0.0f).setColor(r, g, b, alpha);
            drawPrimitive(PIPELINE_TEX, builder, billboard.texture());
        } else {
        BufferBuilder builder = new BufferBuilder(
            BUFFER_ALLOCATOR, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            builder.addVertex(drawMatrix, c0.x, c0.y, c0.z).setColor(r, g, b, alpha);
            builder.addVertex(drawMatrix, c1.x, c1.y, c1.z).setColor(r, g, b, alpha);
            builder.addVertex(drawMatrix, c2.x, c2.y, c2.z).setColor(r, g, b, alpha);
            builder.addVertex(drawMatrix, c0.x, c0.y, c0.z).setColor(r, g, b, alpha);
            builder.addVertex(drawMatrix, c2.x, c2.y, c2.z).setColor(r, g, b, alpha);
            builder.addVertex(drawMatrix, c3.x, c3.y, c3.z).setColor(r, g, b, alpha);
            drawPrimitive(PIPELINE_COLOR, builder, null);
        }
    }

    /**
     * World-space (camera-relative) placement of a camera-facing billboard:
     * the quad centre, an orthonormal right/up basis, and the world-space
     * half extents.
     */
    record CameraFacing(Vector3f center, Vector3f right, Vector3f up, float halfWidth, float halfDepth) {
    }

    /**
     * Compute the placement of a camera-facing billboard from the accumulated
     * position matrix.  The matrix stack is camera-relative (its origin is the
     * camera), so the matrix translation is the quad centre and the matrix
     * column lengths give the world-space scale — group/animation scaling
     * still applies while every accumulated rotation is discarded, making the
     * facing immune to all animation rotations.
     *
     * <p>The quad normal points from the centre toward the camera.  The quad's
     * "up" is the projection of the camera's vertical plane (world up for an
     * unrolled vanilla camera).  When the view direction is (near-)parallel to
     * the camera up — looking straight down/up — the camera's diagonal (right)
     * plane provides a well-defined horizontal axis instead.</p>
     */
    static CameraFacing computeCameraFacing(Matrix4f positionMatrix, float halfWidthLocal, float halfDepthLocal,
                                            Vector3f cameraUp, Vector3f cameraRight) {
        Vector3f center = positionMatrix.getTranslation(new Vector3f());

        // World-space half extents: preserve (possibly non-uniform) scale from
        // the matrix columns; rotation does not affect their lengths.
        float scaleX = new Vector3f(positionMatrix.m00(), positionMatrix.m10(), positionMatrix.m20()).length();
        float scaleZ = new Vector3f(positionMatrix.m02(), positionMatrix.m12(), positionMatrix.m22()).length();
        float halfWidth = halfWidthLocal * scaleX;
        float halfDepth = halfDepthLocal * scaleZ;

        // Normal: quad centre → camera (the matrix origin).  Fall back to a
        // stable direction if the quad is exactly at the camera.
        Vector3f dir;
        float lenSq = center.lengthSquared();
        if (lenSq < 1e-12f) {
            dir = new Vector3f(0.0f, 0.0f, 1.0f);
        } else {
            dir = new Vector3f(center).mul(-1.0f / (float) Math.sqrt(lenSq));
        }

        Vector3f right = new Vector3f(cameraUp).cross(dir, new Vector3f());
        float rightLen = right.length();
        if (rightLen < 1e-6f) {
            // Looking (almost) straight down/up — the camera's right plane is
            // well-defined and horizontal, perpendicular to the view direction.
            right = new Vector3f(cameraRight);
        } else {
            right.mul(1.0f / rightLen);
        }

        Vector3f up = new Vector3f(dir).cross(right, new Vector3f());

        return new CameraFacing(center, right, up, halfWidth, halfDepth);
    }

    // ------------------------------------------------------------------
    // Ring primitive (cylindrical ring, XZ plane at identity rotation)
    // ------------------------------------------------------------------

    /**
     * Draw a cylindrical ring on the XZ plane.
     * At identity rotation the ring lies flat (axis = -Y), matching the
     * billboard convention.  Segment 0 starts at +X (seam on X axis).
     *
     * <p>UV mapping: U = i/segments (circumference, seam at +X),
     * V = 0 at top (+width/2), V = 1 at bottom (-width/2).</p>
     *
     * <p>Uses GL_TRIANGLES (6 vertices per segment) rather than
     * TRIANGLE_STRIP so that every triangle has a consistent,
     * independently controlled winding order.  This is required because
     * TRIANGLE_STRIP automatically flips the winding of alternating
     * triangles, which breaks face culling on a closed cylinder surface.</p>
     *
     * <p>Culling behaviour: when separate inner/outer textures are
     * provided, face culling is enabled so each texture is only visible
     * from its intended side.  When a single texture is used for both
     * sides, culling is disabled so the texture is visible from both
     * sides.</p>
     */
    private void renderRing(RingPrimitive ring, Matrix4f positionMatrix, boolean glowing, float brightness,
                            float animatedGlow, float alpha) {
        float radius = ring.size().x;
        float width  = ring.size().y;
        int segments = Math.max(3, ring.segments()); // minimum 3 for a visible shape

        if (DEBUG_RENDERING) {
            radius *= 5.0f;
            width  *= 5.0f;
        }

        float halfW = width / 2.0f;

        // Self-illuminating primitives are lit by the animation.glow channel;
        // otherwise brightness follows the ambient light at the halo position.
        float brightnessFactor = glowing ? animatedGlow : brightness;
        float r = brightnessFactor;
        float g = brightnessFactor;
        float b = brightnessFactor;

        AbstractTexture outerTexture = resolveTexture(ring.outerTexture());
        boolean twoTextures = outerTexture != null && ring.innerTexture() != null;

        if (outerTexture != null) {
            // Face culling: two textures → cull back faces (outer visible
            // only from outside); single texture → no culling (visible
            // from both sides).
            RenderPipeline pipeline = twoTextures ? PIPELINE_TEX_CULL : PIPELINE_TEX;

            // ---- Outer surface ----
            // CCW winding → front faces point outward.  Each segment emits two
            // triangles (6 vertices): tri A: top0, top1, bottom0; tri B: bottom0, top1, bottom1.
        BufferBuilder outer = new BufferBuilder(
            BUFFER_ALLOCATOR, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int i = 0; i < segments; i++) {
                int next = (i + 1) % segments;
                float u0 = (float) i / segments;
                // Seam fix: last segment uses U=1.0 instead of 0.0 so
                // GPU interpolation doesn't stretch the entire texture.
                float u1 = (i == segments - 1) ? 1.0f : (float) next / segments;
                float cos0 = (float) Math.cos(2.0 * Math.PI * i / segments);
                float sin0 = (float) Math.sin(2.0 * Math.PI * i / segments);
                float cos1 = (float) Math.cos(2.0 * Math.PI * next / segments);
                float sin1 = (float) Math.sin(2.0 * Math.PI * next / segments);
                // Triangle A
                outer.addVertex(positionMatrix, radius * cos0, halfW, radius * sin0).setUv(u0, 0.0f).setColor(r, g, b, alpha);
                outer.addVertex(positionMatrix, radius * cos1, halfW, radius * sin1).setUv(u1, 0.0f).setColor(r, g, b, alpha);
                outer.addVertex(positionMatrix, radius * cos0, -halfW, radius * sin0).setUv(u0, 1.0f).setColor(r, g, b, alpha);
                // Triangle B
                outer.addVertex(positionMatrix, radius * cos0, -halfW, radius * sin0).setUv(u0, 1.0f).setColor(r, g, b, alpha);
                outer.addVertex(positionMatrix, radius * cos1, halfW, radius * sin1).setUv(u1, 0.0f).setColor(r, g, b, alpha);
                outer.addVertex(positionMatrix, radius * cos1, -halfW, radius * sin1).setUv(u1, 1.0f).setColor(r, g, b, alpha);
            }
            drawPrimitive(pipeline, outer, ring.outerTexture());

            // ---- Inner surface ----
            // CW winding → front faces point inward (with cull enabled the
            // inner texture is only visible from inside the ring).
            Identifier innerTex = ring.innerTexture() != null ? ring.innerTexture() : ring.outerTexture();
        BufferBuilder inner = new BufferBuilder(
            BUFFER_ALLOCATOR, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int i = 0; i < segments; i++) {
                int next = (i + 1) % segments;
                float u0 = (float) i / segments;
                float u1 = (i == segments - 1) ? 1.0f : (float) next / segments;
                float cos0 = (float) Math.cos(2.0 * Math.PI * i / segments);
                float sin0 = (float) Math.sin(2.0 * Math.PI * i / segments);
                float cos1 = (float) Math.cos(2.0 * Math.PI * next / segments);
                float sin1 = (float) Math.sin(2.0 * Math.PI * next / segments);
                // Triangle A
                inner.addVertex(positionMatrix, radius * cos0, -halfW, radius * sin0).setUv(u0, 1.0f).setColor(r, g, b, alpha);
                inner.addVertex(positionMatrix, radius * cos1, -halfW, radius * sin1).setUv(u1, 1.0f).setColor(r, g, b, alpha);
                inner.addVertex(positionMatrix, radius * cos0, halfW, radius * sin0).setUv(u0, 0.0f).setColor(r, g, b, alpha);
                // Triangle B
                inner.addVertex(positionMatrix, radius * cos0, halfW, radius * sin0).setUv(u0, 0.0f).setColor(r, g, b, alpha);
                inner.addVertex(positionMatrix, radius * cos1, -halfW, radius * sin1).setUv(u1, 1.0f).setColor(r, g, b, alpha);
                inner.addVertex(positionMatrix, radius * cos1, halfW, radius * sin1).setUv(u1, 0.0f).setColor(r, g, b, alpha);
            }
            drawPrimitive(pipeline, inner, innerTex);
        } else {
            // No texture fallback — solid color ring, both sides visible
        BufferBuilder builder = new BufferBuilder(
            BUFFER_ALLOCATOR, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < segments; i++) {
                int next = (i + 1) % segments;
                float cos0 = (float) Math.cos(2.0 * Math.PI * i / segments);
                float sin0 = (float) Math.sin(2.0 * Math.PI * i / segments);
                float cos1 = (float) Math.cos(2.0 * Math.PI * next / segments);
                float sin1 = (float) Math.sin(2.0 * Math.PI * next / segments);
                // Outer surface (CCW)
                builder.addVertex(positionMatrix, radius * cos0, halfW, radius * sin0).setColor(r, g, b, alpha);
                builder.addVertex(positionMatrix, radius * cos1, halfW, radius * sin1).setColor(r, g, b, alpha);
                builder.addVertex(positionMatrix, radius * cos0, -halfW, radius * sin0).setColor(r, g, b, alpha);
                builder.addVertex(positionMatrix, radius * cos0, -halfW, radius * sin0).setColor(r, g, b, alpha);
                builder.addVertex(positionMatrix, radius * cos1, halfW, radius * sin1).setColor(r, g, b, alpha);
                builder.addVertex(positionMatrix, radius * cos1, -halfW, radius * sin1).setColor(r, g, b, alpha);
                // Inner surface (CW)
                builder.addVertex(positionMatrix, radius * cos0, -halfW, radius * sin0).setColor(r, g, b, alpha);
                builder.addVertex(positionMatrix, radius * cos1, -halfW, radius * sin1).setColor(r, g, b, alpha);
                builder.addVertex(positionMatrix, radius * cos0, halfW, radius * sin0).setColor(r, g, b, alpha);
                builder.addVertex(positionMatrix, radius * cos0, halfW, radius * sin0).setColor(r, g, b, alpha);
                builder.addVertex(positionMatrix, radius * cos1, -halfW, radius * sin1).setColor(r, g, b, alpha);
                builder.addVertex(positionMatrix, radius * cos1, halfW, radius * sin1).setColor(r, g, b, alpha);
            }
            drawPrimitive(PIPELINE_COLOR, builder, null);
        }
    }

    // ------------------------------------------------------------------
    // GPU pipeline drawing (26.1)
    // ------------------------------------------------------------------

    /**
     * Upload the built vertex data and execute a single draw with the given
     * pipeline.  Follows the Fabric 26.1.2 reference render-pipeline pattern:
     * upload the mesh through the encoder's transient memory (auto-recycled
     * per submit), then set pipeline / dynamic transforms / texture and draw
     * non-indexed (all halo primitives are emitted as triangle soup, so no
     * index buffer is needed).
     */
    private void drawPrimitive(RenderPipeline pipeline, BufferBuilder builder, Identifier textureId) {
        MeshData built = builder.buildOrThrow();
        MeshData.DrawState drawState = built.drawState();

        // Resolve/load the texture BEFORE opening the render pass — GPU texture
        // uploads (writeToTexture) are not allowed while a pass is active.
        AbstractTexture texture = textureId != null ? resolveTexture(textureId) : null;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuBufferSlice verticesSlice = encoder.transientMemory()
            .uploadStaging(built.vertexBuffer(), 0L, GpuBuffer.USAGE_VERTEX);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
            .writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> HaloMod.MOD_ID + " halo",
                Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView(), Optional.empty(),
                Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            renderPass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            if (texture != null) {
                renderPass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
            }
            renderPass.setVertexBuffer(0, verticesSlice);
            renderPass.draw(0, drawState.vertexCount(), 0, 1);
        }

        built.close();
    }

    /**
     * Resolve a texture identifier to a loaded {@link AbstractTexture}, or
     * {@code null} when the texture is missing (the caller then falls back to
     * a solid-colour quad).
     */
    private static AbstractTexture resolveTexture(Identifier textureId) {
        if (textureId == null) {
            return null;
        }
        try {
            AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(textureId);
            tex.getTextureView();
            tex.getSampler();
            return tex;
        } catch (Exception e) {
            LOG.debug("[HaloRenderer] texture not found: {} ({})", textureId, e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Rotation helpers
    // ------------------------------------------------------------------

    static void applyQuaternionRotation(PoseStack matrices, Quaternionf quat) {
        Matrix4f rotMatrix = new Matrix4f().rotation(quat);
        matrices.last().pose().mul(rotMatrix);
    }

    // ------------------------------------------------------------------
    // Entity helpers
    // ------------------------------------------------------------------

    static LivingEntity findEntityByUuid(Minecraft client, UUID uuid) {
        if (client.level == null) {
            return null;
        }
        for (net.minecraft.world.entity.Entity e : client.level.entitiesForRendering()) {
            if (e.getUUID().equals(uuid) && e instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }
}
