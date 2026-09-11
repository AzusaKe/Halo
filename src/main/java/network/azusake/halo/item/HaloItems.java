package network.azusake.halo.item;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import network.azusake.halo.HaloMod;

/** Item registration entry point. */
public final class HaloItems {

    private static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, HaloMod.MOD_ID);

    public static final RegistryObject<Item> HALO_SCEPTER = ITEMS.register(
        "halo_scepter",
        () -> new HaloScepterItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    private HaloItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(HaloItems::addCreativeTabEntry);
        HaloScepterInteractions.register(MinecraftForge.EVENT_BUS);
        HaloMod.LOGGER.info("Registered item halo:halo_scepter");
    }

    private static void addCreativeTabEntry(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(HALO_SCEPTER);
        }
    }
}
