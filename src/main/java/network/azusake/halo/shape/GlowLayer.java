package network.azusake.halo.shape;

import net.minecraft.util.Identifier;
import org.joml.Vector2f;

/**
 * An additive glow layer rendered with the billboard.
 *
 * @param texture texture resource identifier for the glow map
 * @param size    width and height of the glow quad
 * @param color   packed RGB color (0xRRGGBB), e.g. 0xFFD700 for gold
 * @param alpha   base opacity (0–1)
 * @param pulse   optional pulsing animation on alpha / scale
 */
public record GlowLayer(
    Identifier texture,
    Vector2f size,
    int color,
    float alpha,
    PulseConfig pulse
) {}
