package network.azusake.halo.platform;

import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.Vec3d;

/** Minecraft types stop at this conversion boundary. */
public final class PlatformTypes {
    public static net.minecraft.server.MinecraftServer server(net.minecraft.world.entity.Entity entity) {
        return entity.level() instanceof net.minecraft.server.level.ServerLevel level ? level.getServer() : null;
    }
    private PlatformTypes() {}
    public static Identifier core(net.minecraft.resources.Identifier id) { return new Identifier(id.getNamespace(),id.getPath()); }
    public static net.minecraft.resources.Identifier game(Identifier id) { return net.minecraft.resources.Identifier.fromNamespaceAndPath(id.getNamespace(),id.getPath()); }
    public static Vec3d core(net.minecraft.world.phys.Vec3 v) { return new Vec3d(v.x,v.y,v.z); }
    public static net.minecraft.world.phys.Vec3 game(Vec3d v) { return new net.minecraft.world.phys.Vec3(v.x,v.y,v.z); }
}
