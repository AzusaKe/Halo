package network.azusake.halo.shape;

import net.minecraft.util.Identifier;
import org.joml.Vector2f;

/**
 * A cylindrical ring primitive (like a halo or buckle) drawn with
 * inner and outer surfaces inside a {@link HaloLayer}.
 *
 * <p>When rotation is identity, the ring lies on the XZ plane with its
 * axis of symmetry along -Y (same orientation as a billboard).  The
 * texture seam (where U=0 meets U=1) runs along the +X axis.</p>
 *
 * <p>Texture mapping: U wraps around the circumference (0→1 = one full
 * revolution, seam at +X), V spans from top (+width/2, V=0) to bottom
 * (-width/2, V=1).</p>
 *
 * @param outerTexture  outer surface texture (required)
 * @param innerTexture  inner surface texture ({@code null} → use outerTexture)
 * @param size          x = radius, y = cylinder width (radial thickness)
 * @param segments      polygon segment count (default 32)
 * @param glow          reserved for future glow support ({@code null} for now)
 */
public record RingPrimitive(
    Identifier outerTexture,
    Identifier innerTexture,
    Vector2f size,
    int segments,
    GlowLayer glow
) implements HaloPrimitive {
}
