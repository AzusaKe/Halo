package network.azusake.halo.platform;

import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.Vec3d;

/** Minecraft types stop at this conversion boundary. */
public final class PlatformTypes {
    private PlatformTypes() {}
    public static Identifier core(net.minecraft.util.Identifier id) { return new Identifier(id.getNamespace(),id.getPath()); }
    public static net.minecraft.util.Identifier game(Identifier id) { return new net.minecraft.util.Identifier(id.getNamespace(),id.getPath()); }
    public static Vec3d core(net.minecraft.util.math.Vec3d v) { return new Vec3d(v.x,v.y,v.z); }
    public static net.minecraft.util.math.Vec3d game(Vec3d v) { return new net.minecraft.util.math.Vec3d(v.x,v.y,v.z); }
}
