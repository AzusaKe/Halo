package network.azusake.halo.physics;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import network.azusake.halo.api.HeadAnchor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-frame capture of the player head's rendered transform.
 *
 * <p>Hooks installed by the client mixins register the base player body model
 * per entity at submit time ({@link #registerPlayer}), then — because 26.1
 * defers model drawing until after all entities have submitted — set the
 * draw-time capture context from the entity render state right before each
 * model is drawn ({@link #beginDraw}, invoked from {@code PlayerModel.setupAnim}).
 * The {@link ModelPart#render} hook snapshots the head part's final pose when
 * the base player body's head is actually rendered.  Raw captures are cleared
 * once per frame ({@link #clearFrame}).  Valid main-camera captures are retained
 * for one following frame because 26.1 draws translucent player models after
 * Halo's {@code AFTER_SOLID_FEATURES} lookup.  Iris shadow-pass captures are
 * diagnostic-only and never become an anchor candidate.</p>
 *
 * <p>Unlike a submission-order FIFO, this does not depend on the deferred draw
 * phase executing models in the same order they were submitted — entity submit
 * nodes are bucketed by render type and translucent models are sorted by
 * distance, so the draw order is not guaranteed.  The render state carried by
 * each model submit identifies the entity being drawn.</p>
 */
public final class RenderHeadCapture {

    private static final Logger LOGGER = LoggerFactory.getLogger("halo");
    /**
     * Temporarily disabled after the four-view Iris validation matrix passed.
     * Set to true when anchor capture timing/space needs to be traced again.
     */
    public static final boolean ANCHOR_DIAGNOSTICS_ENABLED = false;
    private static final long DIAGNOSTIC_INTERVAL_NANOS = 1_000_000_000L;
    private static final int MAX_CAPTURE_LOGS_PER_TRACE_FRAME = 8;

    private static final ThreadLocal<PlayerModel> CURRENT_MODEL = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_UUID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> CURRENT_ENTITY_ID = new ThreadLocal<>();
    private static final Map<UUID, CapturedHead> CAPTURES = new ConcurrentHashMap<>();
    /** Base player body model per entity id, registered at submit time. */
    private static final Map<Integer, PlayerModel> BASE_MODEL_BY_ENTITY_ID = new ConcurrentHashMap<>();
    /** Entity UUID per entity id, registered at submit time. */
    private static final Map<Integer, UUID> UUID_BY_ENTITY_ID = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> REGISTER_FRAME_BY_ENTITY_ID = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> BEGIN_DRAW_FRAME_BY_ENTITY_ID = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> CAPTURE_FRAME_BY_UUID = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LOOKUP_FRAME_BY_UUID = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> CAPTURE_ORDINAL_BY_UUID = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LOOKUP_ORDINAL_BY_UUID = new ConcurrentHashMap<>();
    private static final Map<UUID, CaptureDiagnostic> CAPTURE_DIAGNOSTIC_BY_UUID = new ConcurrentHashMap<>();
    private static final Map<UUID, MainPassAnchor> MAIN_PASS_ANCHORS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_LATE_CAPTURE_LOG_NANOS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_CAPTURE_TRACE_NANOS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SPACE_REJECTION_LOG_NANOS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> CAPTURE_TRACE_FRAME_BY_UUID = new ConcurrentHashMap<>();
    private static final AtomicBoolean CAPTURE_STACK_LOGGED = new AtomicBoolean();
    private static volatile long frameId;
    private static volatile float frameTickDelta;
    private static volatile Object frameLevelIdentity;
    /**
     * The frame's view matrix (world → camera space), captured once per frame
     * before entities render.  On 1.21.1+ the world-render matrix stack has an
     * identity root, so captured head matrices are already camera-relative
     * world space and this is set to the identity matrix — consumers must NOT
     * un-rotate them (doing so double-rotates the anchor).  Kept for the
     * conversion API contract; pass identity when the capture is already in
     * world space.
     */
    private static volatile Matrix4f viewMatrix;

    private RenderHeadCapture() { /* utility class */ }

    /**
     * Called at the HEAD of {@code AvatarRenderer.submit}.  Records the base
     * player body model and entity UUID for the entity id so the deferred draw
     * phase can attribute the head render to the right player.
     */
    public static void registerPlayer(int entityId, UUID entityUuid, PlayerModel model) {
        BASE_MODEL_BY_ENTITY_ID.put(entityId, model);
        UUID_BY_ENTITY_ID.put(entityId, entityUuid);
        REGISTER_FRAME_BY_ENTITY_ID.put(entityId, frameId);
    }

    /**
     * Called at the HEAD of {@code PlayerModel.setupAnim} during the deferred
     * draw phase, right before the model is rendered.  Sets the capture context
     * only for the base player body model of the entity being drawn; layer and
     * armour models clear the context so their head parts are not captured.
     */
    public static void beginDraw(int entityId, PlayerModel model) {
        if (BASE_MODEL_BY_ENTITY_ID.get(entityId) == model) {
            CURRENT_MODEL.set(model);
            CURRENT_UUID.set(UUID_BY_ENTITY_ID.get(entityId));
            CURRENT_ENTITY_ID.set(entityId);
            BEGIN_DRAW_FRAME_BY_ENTITY_ID.put(entityId, frameId);
        } else {
            CURRENT_MODEL.remove();
            CURRENT_UUID.remove();
            CURRENT_ENTITY_ID.remove();
        }
    }

    /** Drop all captures from the previous frame; call before entity rendering. */
    public static void clearFrame() {
        frameId++;
        CAPTURES.clear();
        BASE_MODEL_BY_ENTITY_ID.clear();
        UUID_BY_ENTITY_ID.clear();
        REGISTER_FRAME_BY_ENTITY_ID.clear();
        BEGIN_DRAW_FRAME_BY_ENTITY_ID.clear();
        CAPTURE_FRAME_BY_UUID.clear();
        LOOKUP_FRAME_BY_UUID.clear();
        CAPTURE_ORDINAL_BY_UUID.clear();
        LOOKUP_ORDINAL_BY_UUID.clear();
        CAPTURE_DIAGNOSTIC_BY_UUID.clear();
        CURRENT_MODEL.remove();
        CURRENT_UUID.remove();
        CURRENT_ENTITY_ID.remove();

        Minecraft client = Minecraft.getInstance();
        Object currentLevel = client != null ? client.level : null;
        if (currentLevel != frameLevelIdentity) {
            MAIN_PASS_ANCHORS.clear();
            frameLevelIdentity = currentLevel;
        } else {
            MAIN_PASS_ANCHORS.entrySet().removeIf(entry -> frameId - entry.getValue().frameId() > 1L);
        }
    }

    /** Record the frame's view matrix (world → camera). */
    public static void setViewMatrix(Matrix4f value) {
        viewMatrix = value;
    }

    /** The frame's view matrix, or {@code null} before the first frame. */
    public static Matrix4f getViewMatrix() {
        return viewMatrix;
    }

    /** Tick delta used for entity interpolation in this render frame. */
    public static void setFrameTickDelta(float value) {
        frameTickDelta = value;
    }

    /** Current diagnostic render-frame id, incremented at end of extraction. */
    public static long getFrameId() {
        return frameId;
    }

    /**
     * Called at the HEAD of {@link ModelPart#render} for every part while a
     * player is rendering.  Only the head part of the current player model is
     * snapshotted.
     */
    public static void capture(PoseStack matrices, ModelPart part) {
        PlayerModel model = CURRENT_MODEL.get();
        if (model == null || part != model.getHead()) {
            return;
        }
        UUID entityUuid = CURRENT_UUID.get();
        if (entityUuid == null) {
            return;
        }
        int entityId = CURRENT_ENTITY_ID.get();
        int ordinal = CAPTURE_ORDINAL_BY_UUID.merge(entityUuid, 1, Integer::sum);
        boolean afterLookup = LOOKUP_FRAME_BY_UUID.getOrDefault(entityUuid, -1L) == frameId;
        OptionalIrisDiagnostics.Snapshot iris = OptionalIrisDiagnostics.snapshot();
        boolean acceptedMainPass = isMainPass(iris);
        CapturedHead captured = new CapturedHead(
            new Matrix4f(matrices.last().pose()),
            part.x, part.y, part.z,
            part.xRot, part.yRot, part.zRot,
            part.xScale, part.yScale, part.zScale
        );
        CapturedHead replaced = CAPTURES.put(entityUuid, captured);
        CaptureDiagnostic currentDiagnostic = new CaptureDiagnostic(
            frameId, ordinal, afterLookup, identity(model), identity(part),
            iris, acceptedMainPass);
        CaptureDiagnostic replacedDiagnostic = CAPTURE_DIAGNOSTIC_BY_UUID.put(
            entityUuid, currentDiagnostic);
        CAPTURE_FRAME_BY_UUID.put(entityUuid, frameId);

        if (acceptedMainPass) {
            cacheMainPassAnchor(entityId, entityUuid, captured, currentDiagnostic);
        }

        if (ANCHOR_DIAGNOSTICS_ENABLED && shouldTraceCapture(entityUuid, ordinal)) {
            logCapture(entityId, entityUuid, captured, ordinal, afterLookup,
                replaced, replacedDiagnostic, currentDiagnostic, model, part);
        }

        if (ANCHOR_DIAGNOSTICS_ENABLED && afterLookup
                && shouldLog(LAST_LATE_CAPTURE_LOG_NANOS, entityUuid)) {
            LOGGER.info("[HaloAnchorDiag] stage=capture-after-lookup frame={} entityId={} uuid={} "
                    + "ordinal={} acceptedMainPass={} registered={} beganDraw={} capturedPlayers={}",
                frameId, entityId, entityUuid, ordinal, acceptedMainPass,
                REGISTER_FRAME_BY_ENTITY_ID.getOrDefault(entityId, -1L) == frameId,
                BEGIN_DRAW_FRAME_BY_ENTITY_ID.getOrDefault(entityId, -1L) == frameId,
                CAPTURES.size());
        }
    }

    /**
     * Resolve the most recent valid main-camera head capture against the
     * entity's current interpolated position.
     *
     * <p>The cached centre is entity-relative, not camera-relative or an old
     * absolute world position.  This makes the one-frame deferred capture
     * follow both player movement and camera movement without reusing either
     * frame's stale camera translation.</p>
     */
    public static HeadAnchor resolveMainPassAnchor(LivingEntity entity, float tickDelta) {
        MainPassAnchor cached = MAIN_PASS_ANCHORS.get(entity.getUUID());
        Minecraft client = Minecraft.getInstance();
        Object currentLevel = client != null ? client.level : null;
        if (cached == null || !cached.isUsable(frameId, entity.getId(), currentLevel)) {
            return null;
        }
        return cached.resolve(interpolatedPosition(entity, tickDelta));
    }

    /** Snapshot the capture pipeline at the exact provider lookup point. */
    public static DiagnosticSnapshot noteLookup(int entityId, UUID uuid) {
        LOOKUP_FRAME_BY_UUID.put(uuid, frameId);
        int lookupOrdinal = LOOKUP_ORDINAL_BY_UUID.merge(uuid, 1, Integer::sum);
        CaptureDiagnostic selected = CAPTURE_DIAGNOSTIC_BY_UUID.get(uuid);
        MainPassAnchor main = MAIN_PASS_ANCHORS.get(uuid);
        long mainAge = main != null ? frameId - main.frameId() : -1L;
        return new DiagnosticSnapshot(
            frameId,
            lookupOrdinal,
            UUID_BY_ENTITY_ID.get(entityId) != null,
            uuid.equals(UUID_BY_ENTITY_ID.get(entityId)),
            REGISTER_FRAME_BY_ENTITY_ID.getOrDefault(entityId, -1L) == frameId,
            BEGIN_DRAW_FRAME_BY_ENTITY_ID.getOrDefault(entityId, -1L) == frameId,
            CAPTURE_FRAME_BY_UUID.getOrDefault(uuid, -1L) == frameId,
            BASE_MODEL_BY_ENTITY_ID.size(),
            CAPTURES.size(),
            selected != null ? selected.ordinal() : 0,
            selected != null ? timing(selected.afterLookup()) : "none",
            selected != null ? selected.modelIdentity() : "none",
            selected != null ? selected.iris().shadowPass() : OptionalIrisDiagnostics.Status.UNKNOWN,
            selected != null && selected.acceptedMainPass(),
            main != null ? main.frameId() : -1L,
            mainAge,
            main != null && main.isUsable(frameId, entityId, currentLevelIdentity())
        );
    }

    private static void cacheMainPassAnchor(int entityId, UUID uuid, CapturedHead captured,
                                            CaptureDiagnostic diagnostic) {
        Minecraft client = Minecraft.getInstance();
        Camera camera = client != null && client.gameRenderer != null
            ? client.gameRenderer.getMainCamera()
            : null;
        Entity rawEntity = client != null && client.level != null
            ? client.level.getEntity(entityId)
            : null;
        Matrix4f currentViewMatrix = viewMatrix;
        if (!(rawEntity instanceof LivingEntity entity) || !uuid.equals(entity.getUUID())
                || camera == null || currentViewMatrix == null) {
            return;
        }

        HeadAnchor worldAnchor = RenderHeadMath.toHeadAnchor(
            captured, camera.position(), currentViewMatrix);
        if (!isFinite(worldAnchor)) {
            return;
        }
        Vec3 entityPosition = interpolatedPosition(entity, frameTickDelta);
        Vec3 entityRelativeCenter = worldAnchor.headCenter().subtract(entityPosition);
        if (!isPlausibleEntityRelativeCenter(entityRelativeCenter, entity.getBbHeight())) {
            if (ANCHOR_DIAGNOSTICS_ENABLED
                    && shouldLog(LAST_SPACE_REJECTION_LOG_NANOS, uuid)) {
                Matrix4f root = captured.rootMatrix();
                LOGGER.info("[HaloAnchorDiag] stage=cache-rejected-space frame={} ordinal={} "
                        + "entityId={} uuid={} cameraType={} entityRelativeCenter=({},{},{}) "
                        + "distance={} entityHeight={} rootTranslation=({},{},{}) "
                        + "rootBasisLengths=({},{},{}) stack={}",
                    frameId, diagnostic.ordinal(), entityId, uuid,
                    client.options.getCameraType(),
                    entityRelativeCenter.x, entityRelativeCenter.y, entityRelativeCenter.z,
                    entityRelativeCenter.length(), entity.getBbHeight(),
                    root.m30(), root.m31(), root.m32(),
                    basisLength(root.m00(), root.m01(), root.m02()),
                    basisLength(root.m10(), root.m11(), root.m12()),
                    basisLength(root.m20(), root.m21(), root.m22()),
                    compactStack());
            }
            return;
        }

        MainPassAnchor candidate = new MainPassAnchor(
            frameId,
            entityId,
            client.level,
            entityRelativeCenter,
            worldAnchor.yaw(), worldAnchor.pitch(), worldAnchor.roll(),
            diagnostic.ordinal(), diagnostic.modelIdentity()
        );
        MAIN_PASS_ANCHORS.compute(uuid,
            (ignored, existing) -> selectMainPassAnchor(existing, candidate));
    }

    static boolean isMainPass(OptionalIrisDiagnostics.Snapshot iris) {
        return iris.shadowPass() == OptionalIrisDiagnostics.Status.FALSE;
    }

    /**
     * Reject captures produced by screen-space/UI player renders.  A genuine
     * world player head stays close to the entity's interpolated position;
     * NeoForge can render the same {@link PlayerModel} later with a GUI matrix
     * (typically a basis scale around 150), which must never become a world
     * anchor even though Iris correctly reports it as a non-shadow pass.
     */
    static boolean isPlausibleEntityRelativeCenter(Vec3 relativeCenter, double entityHeight) {
        if (relativeCenter == null
                || !Double.isFinite(relativeCenter.x)
                || !Double.isFinite(relativeCenter.y)
                || !Double.isFinite(relativeCenter.z)
                || !Double.isFinite(entityHeight)) {
            return false;
        }
        double maximumDistance = Math.max(4.0, Math.abs(entityHeight) * 2.0 + 1.0);
        return relativeCenter.lengthSqr() <= maximumDistance * maximumDistance;
    }

    /** Keep the first trustworthy main-world capture for each player/frame. */
    static MainPassAnchor selectMainPassAnchor(MainPassAnchor existing, MainPassAnchor candidate) {
        if (existing != null && existing.frameId() == candidate.frameId()) {
            return existing;
        }
        return candidate;
    }

    private static Object currentLevelIdentity() {
        Minecraft client = Minecraft.getInstance();
        return client != null ? client.level : null;
    }

    private static Vec3 interpolatedPosition(LivingEntity entity, float tickDelta) {
        return new Vec3(
            entity.xo + (entity.getX() - entity.xo) * tickDelta,
            entity.yo + (entity.getY() - entity.yo) * tickDelta,
            entity.zo + (entity.getZ() - entity.zo) * tickDelta
        );
    }

    private static boolean isFinite(HeadAnchor anchor) {
        Vec3 center = anchor.headCenter();
        return Double.isFinite(center.x) && Double.isFinite(center.y) && Double.isFinite(center.z)
            && Float.isFinite(anchor.yaw()) && Float.isFinite(anchor.pitch()) && Float.isFinite(anchor.roll());
    }

    private static float basisLength(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static boolean shouldTraceCapture(UUID uuid, int ordinal) {
        if (!isLocalPlayer(uuid)) {
            return false;
        }
        if (CAPTURE_TRACE_FRAME_BY_UUID.getOrDefault(uuid, -1L) == frameId) {
            return ordinal <= MAX_CAPTURE_LOGS_PER_TRACE_FRAME;
        }
        long now = System.nanoTime();
        Long previous = LAST_CAPTURE_TRACE_NANOS.get(uuid);
        if (previous != null && now - previous < DIAGNOSTIC_INTERVAL_NANOS) {
            return false;
        }
        LAST_CAPTURE_TRACE_NANOS.put(uuid, now);
        CAPTURE_TRACE_FRAME_BY_UUID.put(uuid, frameId);
        return true;
    }

    private static boolean isLocalPlayer(UUID uuid) {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.player != null && uuid.equals(client.player.getUUID());
    }

    private static void logCapture(int entityId, UUID uuid, CapturedHead captured,
                                   int ordinal, boolean afterLookup,
                                   CapturedHead replaced, CaptureDiagnostic replacedDiagnostic,
                                   CaptureDiagnostic currentDiagnostic,
                                   PlayerModel model, ModelPart part) {
        Matrix4f root = captured.rootMatrix();
        Matrix4f head = RenderHeadMath.composeHeadMatrix(captured);
        Vector4f beforeHeadOrigin = transform(root, 0f, 0f, 0f);
        Vector4f afterHeadOrigin = transform(head, 0f, 0f, 0f);
        Vector4f afterHeadCenter = transform(head, 0f, RenderHeadMath.HEAD_BOX_CENTER_Y, 0f);

        Minecraft client = Minecraft.getInstance();
        Camera camera = client != null && client.gameRenderer != null
            ? client.gameRenderer.getMainCamera()
            : null;
        Vec3 cameraPos = camera != null ? camera.position() : Vec3.ZERO;
        Object cameraType = client != null ? client.options.getCameraType() : "unknown";
        OptionalIrisDiagnostics.Snapshot iris = currentDiagnostic.iris();
        String replaces = replaced == null || replacedDiagnostic == null
            ? "none"
            : "frame=" + replacedDiagnostic.frameId()
                + ":ordinal=" + replacedDiagnostic.ordinal()
                + ":timing=" + timing(replacedDiagnostic.afterLookup())
                + ":shadow=" + replacedDiagnostic.iris().shadowPass()
                + ":model=" + replacedDiagnostic.modelIdentity()
                + ":headPart=" + replacedDiagnostic.headPartIdentity();

        LOGGER.info("[HaloAnchorDiag] stage=capture frame={} ordinal={} timing={} acceptedMainPass={} replaces={} "
                + "entityId={} uuid={} cameraType={} model={} headPart={} "
                + "camera=({},{},{}) cameraYawPitch=({},{}) "
                + "irisLoaded={} shaders={} irisShadowPass={} irisProbe={} "
                + "rootTranslation=({},{},{}) rootBasisX=({},{},{}) rootBasisY=({},{},{}) rootBasisZ=({},{},{}) "
                + "beforeHeadOrigin=({},{},{}) afterHeadOrigin=({},{},{}) afterHeadCenter=({},{},{}) "
                + "headPivot=({},{},{}) headPitchYawRoll=({},{},{}) headScale=({},{},{})",
            frameId, ordinal, timing(afterLookup), currentDiagnostic.acceptedMainPass(), replaces,
            entityId, uuid, cameraType, identity(model), identity(part),
            cameraPos.x, cameraPos.y, cameraPos.z,
            camera != null ? camera.yRot() : Float.NaN, camera != null ? camera.xRot() : Float.NaN,
            iris.irisLoaded(), iris.shaderPackInUse(), iris.shadowPass(), iris.probe(),
            root.m30(), root.m31(), root.m32(),
            root.m00(), root.m01(), root.m02(),
            root.m10(), root.m11(), root.m12(),
            root.m20(), root.m21(), root.m22(),
            beforeHeadOrigin.x, beforeHeadOrigin.y, beforeHeadOrigin.z,
            afterHeadOrigin.x, afterHeadOrigin.y, afterHeadOrigin.z,
            afterHeadCenter.x, afterHeadCenter.y, afterHeadCenter.z,
            captured.pivotX(), captured.pivotY(), captured.pivotZ(),
            captured.pitch(), captured.yaw(), captured.roll(),
            captured.xScale(), captured.yScale(), captured.zScale());

        if (CAPTURE_STACK_LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[HaloAnchorDiag] stage=capture-stack frame={} entityId={} uuid={} stack={}",
                frameId, entityId, uuid, compactStack());
        }
    }

    private static Vector4f transform(Matrix4f matrix, float x, float y, float z) {
        return matrix.transform(new Vector4f(x, y, z, 1f));
    }

    private static String identity(Object object) {
        return object.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(object));
    }

    private static String timing(boolean afterLookup) {
        return afterLookup ? "AFTER_LOOKUP" : "BEFORE_LOOKUP";
    }

    private static String compactStack() {
        return Arrays.stream(Thread.currentThread().getStackTrace())
            .filter(frame -> {
                String name = frame.getClassName();
                return name.startsWith("net.minecraft.client.renderer")
                    || name.startsWith("net.minecraft.client.model")
                    || name.startsWith("net.irisshaders.iris")
                    || name.startsWith("network.azusake.halo.mixin");
            })
            .limit(12)
            .map(frame -> frame.getClassName() + "#" + frame.getMethodName()
                + ":" + frame.getLineNumber())
            .collect(Collectors.joining(" <- "));
    }

    private static boolean shouldLog(Map<UUID, Long> lastLogNanos, UUID uuid) {
        long now = System.nanoTime();
        Long previous = lastLogNanos.get(uuid);
        if (previous != null && now - previous < DIAGNOSTIC_INTERVAL_NANOS) {
            return false;
        }
        lastLogNanos.put(uuid, now);
        return true;
    }

    /**
     * The model root matrix (camera-relative, including entity position, body
     * rotation, the {@code scale(-1,-1,1)} flip and player scale) at the moment
     * the head part starts rendering, plus the head part's own pose
     * (pivot in 1/16 model units, angles in radians, per-part scale).
     */
    public record CapturedHead(
        Matrix4f rootMatrix,
        float pivotX, float pivotY, float pivotZ,
        float pitch, float yaw, float roll,
        float xScale, float yScale, float zScale
    ) {}

    public record DiagnosticSnapshot(
        long frameId,
        int lookupOrdinal,
        boolean entityIdKnown,
        boolean uuidMatchesEntityId,
        boolean registeredThisFrame,
        boolean beganDrawThisFrame,
        boolean capturedThisFrame,
        int registeredPlayers,
        int capturedPlayers,
        int selectedCaptureOrdinal,
        String selectedCaptureTiming,
        String selectedModelIdentity,
        OptionalIrisDiagnostics.Status selectedShadowPass,
        boolean currentCaptureAcceptedMainPass,
        long mainCacheFrame,
        long mainCacheAge,
        boolean mainCacheUsable
    ) {}

    static record MainPassAnchor(
        long frameId,
        int entityId,
        Object levelIdentity,
        Vec3 entityRelativeCenter,
        float yaw,
        float pitch,
        float roll,
        int captureOrdinal,
        String modelIdentity
    ) {
        boolean isUsable(long currentFrameId, int currentEntityId, Object currentLevelIdentity) {
            long age = currentFrameId - frameId;
            return age >= 0L && age <= 1L
                && entityId == currentEntityId
                && levelIdentity == currentLevelIdentity;
        }

        HeadAnchor resolve(Vec3 currentEntityPosition) {
            return new HeadAnchor(currentEntityPosition.add(entityRelativeCenter), yaw, pitch, roll);
        }
    }

    private record CaptureDiagnostic(
        long frameId,
        int ordinal,
        boolean afterLookup,
        String modelIdentity,
        String headPartIdentity,
        OptionalIrisDiagnostics.Snapshot iris,
        boolean acceptedMainPass
    ) {}
}
