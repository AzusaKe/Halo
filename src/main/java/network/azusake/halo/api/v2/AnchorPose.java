package network.azusake.halo.api.v2;

import java.util.Objects;

/** The final world-space visual centre and orientation of an entity head. */
public record AnchorPose(AnchorVec3 position, AnchorRotation rotation) {

    public AnchorPose {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");
    }
}
