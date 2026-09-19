package network.azusake.halo.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import network.azusake.halo.HaloMod;

/** Item registration entry point. */
public final class HaloItems {

    public static final Item HALO_SCEPTER = Registry.register(
        Registries.ITEM,
        Identifier.of(HaloMod.MOD_ID, "halo_scepter"),
        new HaloScepterItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE))
    );

    private HaloItems() {
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
            .register(entries -> entries.add(HALO_SCEPTER));
        HaloScepterInteractions.register();
        HaloMod.LOGGER.info("Registered item halo:halo_scepter");
    }
}
