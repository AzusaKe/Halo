package network.azusake.halo.item;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import network.azusake.halo.HaloMod;

/** Item registration entry point. */
public final class HaloItems {

    private static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(HaloMod.MOD_ID);

    public static final DeferredHolder<Item, Item> HALO_SCEPTER = ITEMS.register(
        "halo_scepter",
        () -> new HaloScepterItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    private HaloItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(HaloItems::addCreativeTabEntry);
        HaloScepterInteractions.register(NeoForge.EVENT_BUS);
        HaloMod.LOGGER.info("Registered item halo:halo_scepter");
    }

    private static void addCreativeTabEntry(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(HALO_SCEPTER.get());
        }
    }
}
