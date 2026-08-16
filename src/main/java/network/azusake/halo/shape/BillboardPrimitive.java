package network.azusake.halo.shape;

import net.minecraft.resources.ResourceLocation;
import org.joml.Vector2f;

/**
 * A single textured billboard quad drawn on the XZ plane (horizontal,
 * normal = -Y in definition-local space) inside a {@link HaloGroup}.
 *
 * <p>This replaces the old {@code BillboardShape} which was part of the
 * {@code HaloShape} sealed hierarchy.  Billboard positioning, rotation,
 * and scale are now handled by the enclosing {@link HaloGroup} rather
 * than being hard-coded at the shape level.</p>
 *
 * @param texture  the texture resource identifier
 * @param size     width (X) and depth (Z) in definition-local units
 * @param faceCamera when {@code true} the quad always faces the camera
 *                   (full camera-facing billboard); no animation rotation
 *                   can override this orientation
 */
public record BillboardPrimitive(
    ResourceLocation texture,
    Vector2f size,
    boolean faceCamera
) implements HaloPrimitive {

    /** Convenience constructor for a billboard that does not face the camera. */
    public BillboardPrimitive(ResourceLocation texture, Vector2f size) {
        this(texture, size, false);
    }
}
