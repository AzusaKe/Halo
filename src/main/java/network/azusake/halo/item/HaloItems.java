package network.azusake.halo.item;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import network.azusake.halo.HaloMod;

/** Item registration entry point. */
public final class HaloItems {

    private static final Identifier HALO_SCEPTER_ID =
        Identifier.fromNamespaceAndPath(HaloMod.MOD_ID, "halo_scepter");
    private static final ResourceKey<Item> HALO_SCEPTER_KEY =
        ResourceKey.create(Registries.ITEM, HALO_SCEPTER_ID);

    public static final Item HALO_SCEPTER = Registry.register(
        BuiltInRegistries.ITEM,
        HALO_SCEPTER_KEY,
        new HaloScepterItem(new Item.Properties()
            .setId(HALO_SCEPTER_KEY)
            .stacksTo(1)
            .rarity(Rarity.RARE))
    );

    private HaloItems() {
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
            .register(entries -> entries.accept(HALO_SCEPTER));
        HaloScepterInteractions.register();
        HaloMod.LOGGER.info("Registered item halo:halo_scepter");
    }
}
