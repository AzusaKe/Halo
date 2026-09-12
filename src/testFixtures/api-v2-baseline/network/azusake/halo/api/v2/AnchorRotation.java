package network.azusake.halo.api.v2;

/**
 * A normalized quaternion in {@code (x, y, z, w)} order.
 *
 * <p>The constructor accepts any finite, non-zero quaternion and normalizes it.
 * Local {@code +Y} is head-up and local {@code +Z} is head-forward.</p>
 */
public record AnchorRotation(double x, double y, double z, double w) {

    public AnchorRotation {
        if (!Double.isFinite(x) || !Double.isFinite(y)
            || !Double.isFinite(z) || !Double.isFinite(w)) {
            throw new IllegalArgumentException("anchor rotation components must be finite");
        }
        double norm = Math.sqrt(x * x + y * y + z * z + w * w);
        if (!Double.isFinite(norm) || norm == 0.0) {
            throw new IllegalArgumentException("anchor rotation must be non-zero");
        }
        x /= norm;
        y /= norm;
        z /= norm;
        w /= norm;
    }
}
