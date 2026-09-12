package network.azusake.halo.anchor;

import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorRotation;
import network.azusake.halo.api.v2.AnchorSource;
import network.azusake.halo.api.v2.AnchorVec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Internal, loader-neutral storage and arbitration for render-time anchors. */
public final class AnchorCaptureCoordinator {

    private static final Pattern SOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Map<String, SourceHandle> SOURCES = new HashMap<>();
    private static final Map<UUID, CapturedPose> CAPTURES = new HashMap<>();
    private static final ThreadLocal<Deque<EntityScope>> SCOPES =
        ThreadLocal.withInitial(ArrayDeque::new);

    private static long frameId;
    private static long submissionSequence;
    private static Object worldIdentity;

    private AnchorCaptureCoordinator() {
    }

    public static synchronized AnchorSource register(String sourceId) {
        if (sourceId == null || !SOURCE_ID.matcher(sourceId).matches()) {
            throw new IllegalArgumentException("sourceId must be a lowercase namespace:path identifier");
        }
        SourceHandle existing = SOURCES.get(sourceId);
        if (existing != null && !existing.closed) {
            throw new IllegalStateException("anchor source is already registered: " + sourceId);
        }
        SourceHandle handle = new SourceHandle(sourceId);
        SOURCES.put(sourceId, handle);
        return handle;
    }

    /** Advance exactly once for one main-camera world frame. */
    public static synchronized void beginFrame(Object currentWorldIdentity) {
        frameId++;
        submissionSequence = 0L;
        SCOPES.remove();
        if (worldIdentity != currentWorldIdentity) {
            CAPTURES.clear();
            worldIdentity = currentWorldIdentity;
            return;
        }
        CAPTURES.entrySet().removeIf(entry -> frameId - entry.getValue().frameId > 1L);
    }

    /** Open a render attempt. Only scopes classified as main pass accept submissions. */
    public static void beginEntityRender(
        UUID entityUuid,
        int runtimeEntityId,
        Object currentWorldIdentity,
        AnchorVec3 interpolatedEntityPosition,
        boolean mainPass
    ) {
        Objects.requireNonNull(entityUuid, "entityUuid");
        Objects.requireNonNull(interpolatedEntityPosition, "interpolatedEntityPosition");
        SCOPES.get().push(new EntityScope(
            entityUuid, runtimeEntityId, currentWorldIdentity, interpolatedEntityPosition, mainPass));
    }

    public static void endEntityRender() {
        Deque<EntityScope> scopes = SCOPES.get();
        if (!scopes.isEmpty()) {
            scopes.pop();
        }
        if (scopes.isEmpty()) {
            SCOPES.remove();
        }
    }

    public static synchronized AnchorPose resolve(
        UUID entityUuid,
        int runtimeEntityId,
        Object currentWorldIdentity,
        AnchorVec3 currentInterpolatedPosition
    ) {
        CapturedPose captured = CAPTURES.get(entityUuid);
        if (captured == null || captured.runtimeEntityId != runtimeEntityId
            || captured.worldIdentity != currentWorldIdentity) {
            return null;
        }
        long age = frameId - captured.frameId;
        if (age < 0L || age > 1L) {
            CAPTURES.remove(entityUuid);
            return null;
        }
        AnchorVec3 relative = captured.entityRelativePosition;
        return new AnchorPose(new AnchorVec3(
            currentInterpolatedPosition.x() + relative.x(),
            currentInterpolatedPosition.y() + relative.y(),
            currentInterpolatedPosition.z() + relative.z()
        ), captured.rotation);
    }

    public static synchronized void clearEntity(UUID entityUuid) {
        CAPTURES.remove(entityUuid);
    }

    public static synchronized void clearCaptures() {
        CAPTURES.clear();
        SCOPES.remove();
    }

    static synchronized void resetForTests() {
        SOURCES.clear();
        CAPTURES.clear();
        SCOPES.remove();
        frameId = 0L;
        submissionSequence = 0L;
        worldIdentity = null;
    }

    private static synchronized boolean submit(SourceHandle source, UUID entityUuid, AnchorPose pose) {
        if (source.closed || entityUuid == null || pose == null) {
            return false;
        }
        Deque<EntityScope> scopes = SCOPES.get();
        EntityScope scope = scopes.peek();
        if (scope == null || !scope.mainPass || !scope.entityUuid.equals(entityUuid)
            || scope.worldIdentity != worldIdentity) {
            return false;
        }
        AnchorVec3 position = pose.position();
        AnchorVec3 entityPosition = scope.interpolatedEntityPosition;
        CAPTURES.put(entityUuid, new CapturedPose(
            source.sourceId,
            frameId,
            ++submissionSequence,
            scope.runtimeEntityId,
            scope.worldIdentity,
            new AnchorVec3(
                position.x() - entityPosition.x(),
                position.y() - entityPosition.y(),
                position.z() - entityPosition.z()
            ),
            pose.rotation()
        ));
        return true;
    }

    private static synchronized void close(SourceHandle source) {
        if (source.closed) {
            return;
        }
        source.closed = true;
        SOURCES.remove(source.sourceId, source);
        Iterator<CapturedPose> iterator = CAPTURES.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().sourceId.equals(source.sourceId)) {
                iterator.remove();
            }
        }
    }

    private static final class SourceHandle implements AnchorSource {
        private final String sourceId;
        private volatile boolean closed;

        private SourceHandle(String sourceId) {
            this.sourceId = sourceId;
        }

        @Override
        public boolean submit(UUID entityUuid, AnchorPose pose) {
            return AnchorCaptureCoordinator.submit(this, entityUuid, pose);
        }

        @Override
        public void close() {
            AnchorCaptureCoordinator.close(this);
        }
    }

    private record EntityScope(
        UUID entityUuid,
        int runtimeEntityId,
        Object worldIdentity,
        AnchorVec3 interpolatedEntityPosition,
        boolean mainPass
    ) {
    }

    private record CapturedPose(
        String sourceId,
        long frameId,
        long submissionSequence,
        int runtimeEntityId,
        Object worldIdentity,
        AnchorVec3 entityRelativePosition,
        AnchorRotation rotation
    ) {
    }
}
