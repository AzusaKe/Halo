package network.azusake.halo.shape;

import net.minecraft.util.Identifier;
import org.joml.Vector2f;

/**
 * A single textured billboard quad drawn on the XZ plane (horizontal,
 * normal = -Y in definition-local space) inside a {@link HaloLayer}.
 *
 * <p>This replaces the old {@code BillboardShape} which was part of the
 * {@code HaloShape} sealed hierarchy.  Billboard positioning, rotation,
 * and scale are now handled by the enclosing {@link HaloLayer} rather
 * than being hard-coded at the shape level.</p>
 *
 * @param texture  the texture resource identifier
 * @param size     width (X) and depth (Z) in definition-local units
 * @param glow     optional additive glow layer, or {@code null}
 */
public record BillboardPrimitive(
    Identifier texture,
    Vector2f size,
    GlowLayer glow
) implements HaloPrimitive {
}
