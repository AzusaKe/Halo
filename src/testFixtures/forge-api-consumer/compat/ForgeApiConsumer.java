package compat;

import java.util.UUID;
import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorRotation;
import network.azusake.halo.api.v2.AnchorSource;
import network.azusake.halo.api.v2.AnchorVec3;
import network.azusake.halo.api.v2.HaloAnchorApi;
import network.azusake.halo.api.v2.HaloApi;
import network.azusake.halo.api.v2.HaloSource;

/** Compiles and runs as a separately packaged Forge-side API caller against only the final Halo JAR. */
public final class ForgeApiConsumer {
    public static void main(String[] args) {
        UUID entity = new UUID(0, 42);
        try (HaloSource source = HaloApi.registerSource("compat:forge_consumer", 7)) {
            if (!"compat:forge_consumer".equals(source.sourceId()) || source.defaultPriority() != 7) {
                throw new AssertionError("HaloSource public contract changed");
            }
            if (source.set(entity, "halo:ring_default") || source.clear(entity) || source.clearAll() != 0) {
                throw new AssertionError("source mutation was accepted without an active server host");
            }
        }
        try (AnchorSource source = HaloAnchorApi.register("compat:forge_anchor")) {
            AnchorPose pose = new AnchorPose(new AnchorVec3(1, 2, 3), new AnchorRotation(0, 0, 0, 1));
            if (source.submit(entity, pose)) {
                throw new AssertionError("world anchor was accepted outside a render scope");
            }
        }
        System.out.println("Forge API v2 consumer passed against the final Halo jar.");
    }
}
