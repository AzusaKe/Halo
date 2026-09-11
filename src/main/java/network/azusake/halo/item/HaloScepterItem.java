package network.azusake.halo.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** Permanent administration tool used to attach and remove entity halos. */
public final class HaloScepterItem extends Item {

    public HaloScepterItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.halo.halo_scepter.use").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.halo.halo_scepter.remove").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.halo.halo_scepter.self").formatted(Formatting.DARK_GRAY));
    }
}
