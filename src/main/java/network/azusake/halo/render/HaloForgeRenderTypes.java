package network.azusake.halo.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import network.azusake.halo.core.Identifier;

import static network.azusake.halo.platform.PlatformTypes.game;

/**
 * Forge render-state bridge for non-emissive Halo geometry.
 *
 * <p>The entity render types establish the contract Oculus expects for a
 * material-bearing entity draw: base texture in slot 0, overlay, lightmap and
 * the {@code NEW_ENTITY} vertex layout. Halo replaces only the program after
 * setup so masks and the Iris/Oculus program variants keep working; culling,
 * blending and depth state are subsequently refined from the core command.</p>
 */
final class HaloForgeRenderTypes {
    private static final ResourceLocation WHITE =
        new ResourceLocation("minecraft", "textures/misc/white.png");

    private HaloForgeRenderTypes() {}

    static RenderType lit(Identifier texture, boolean translucent) {
        ResourceLocation base = texture == null ? WHITE : game(texture);
        return translucent ? RenderType.entityTranslucent(base) : RenderType.entitySolid(base);
    }
}
