package network.azusake.halo.shape;

import network.azusake.halo.animation.LayerAnimation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.List;
import java.util.Optional;

/**
 * A transform group within a {@link HaloModel}.
 *
 * <p>Each group has its own local transform (position / rotation / scale)
 * relative to its parent group (or the halo definition origin for top-level
 * groups).  A group contains zero or more {@link HaloPrimitive}s that share
 * this transform, and zero or more child groups that inherit it.</p>
 *
 * <p>This replaces the old flat {@code HaloLayer} with a hierarchical
 * scene-graph structure, enabling transform inheritance and grouping of
 * related primitives.</p>
 *
 * @param id         optional name for animation-group binding
 * @param position   offset from the parent origin in local space
 * @param rotation   orientation relative to the parent
 *                   (stored as a quaternion; converted from Euler at load time)
 * @param scale      uniform scale multiplier for this group (default 1.0)
 * @param primitives the renderable primitives within this group
 * @param glowing    whether glow layers render for primitives in this group (default true)
 * @param inheritAlpha whether descendants inherit this group's animated alpha (default true)
 * @param inheritGlow  whether descendants inherit this group's animated glow (default true)
 * @param animation  optional per-group visual animation (offset + rotation over time)
 * @param children   child groups that inherit this group's transform
 */
public record HaloGroup(
    Optional<String> id,
    Vec3 position,
    Quaternionf rotation,
    float scale,
    List<HaloPrimitive> primitives,
    boolean glowing,
    boolean inheritAlpha,
    boolean inheritGlow,
    Optional<LayerAnimation> animation,
    List<HaloGroup> children
) {
    /** Convenience constructor for a group with a single primitive and no children. */
    public HaloGroup(Vec3 position, HaloPrimitive primitive) {
        this(Optional.empty(), position, new Quaternionf(), 1.0f, List.of(primitive), true, true, true, Optional.empty(), List.of());
    }

    /** Convenience constructor with explicit rotation and a single primitive. */
    public HaloGroup(Vec3 position, Quaternionf rotation, HaloPrimitive primitive) {
        this(Optional.empty(), position, rotation, 1.0f, List.of(primitive), true, true, true, Optional.empty(), List.of());
    }

    /** Total number of primitives in this group and all descendant groups. */
    public int totalPrimitiveCount() {
        int count = primitives.size();
        for (HaloGroup child : children) {
            count += child.totalPrimitiveCount();
        }
        return count;
    }
}
