package compat;

import java.util.UUID;
import network.azusake.halo.api.v2.*;

/** Compiled ONLY against the pre-refactor v2 sources. Executed against the remapped distribution. */
public final class LegacyAnchorConsumer {
    public static void main(String[] args) {
        var position = new AnchorVec3(1.0, 2.0, 3.0);
        var rotation = new AnchorRotation(0, 0, 0, 2);
        var pose = new AnchorPose(position, rotation);
        if (pose.position().x() != 1 || pose.position().y() != 2 || pose.position().z() != 3
                || pose.rotation().x() != 0 || pose.rotation().y() != 0
                || pose.rotation().z() != 0 || pose.rotation().w() != 1) throw new AssertionError("Value ABI changed");
        if (!pose.equals(new AnchorPose(position, rotation)) || pose.hashCode() == 0 || pose.toString().isEmpty()) {
            throw new AssertionError("Record methods unavailable");
        }
        try (AnchorSource source = HaloAnchorApi.register("compat:old_binary")) {
            if (source.submit(new UUID(0, 1), pose)) throw new AssertionError("Out-of-scope submit accepted");
        }
        System.out.println("Pre-refactor anchor API v2 consumer passed against the final Halo jar.");
    }
}
