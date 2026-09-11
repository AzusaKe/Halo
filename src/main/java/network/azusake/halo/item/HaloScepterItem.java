package network.azusake.halo.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/** Permanent administration tool used to attach and remove entity halos. */
public final class HaloScepterItem extends Item {

    public HaloScepterItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.halo.halo_scepter.use").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.halo.halo_scepter.remove").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.halo.halo_scepter.self").formatted(Formatting.DARK_GRAY));
    }
}
