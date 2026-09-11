package network.azusake.halo.api.v2;

import java.util.UUID;

/**
 * A render-time head-anchor source.
 *
 * <p>Call {@link #submit} only where the source has completed the effective
 * head transform for the entity currently being rendered. Halo rejects
 * submissions outside an accepted main-camera entity render scope.</p>
 */
public interface AnchorSource extends AutoCloseable {

    boolean submit(UUID entityUuid, AnchorPose pose);

    @Override
    void close();
}
