package network.azusake.halo.api.v2;

/** A finite world-space vector measured in Minecraft blocks. */
public record AnchorVec3(double x, double y, double z) {

    public AnchorVec3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("anchor vector components must be finite");
        }
    }
}
