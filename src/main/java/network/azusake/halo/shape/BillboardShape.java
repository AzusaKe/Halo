package network.azusake.halo.shape;

import net.minecraft.util.Identifier;
import org.joml.Vector2f;

/**
 * A single textured quad facing the camera.
 *
 * @param texture the texture resource identifier
 * @param size    width and height of the billboard in game units
 * @param glow    optional glow layer rendered behind / around the billboard
 */
public record BillboardShape(
    Identifier texture,
    Vector2f size,
    GlowLayer glow
) implements HaloShape {
}
