package network.azusake.halo.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** Permanent administration tool used to attach and remove entity halos. */
public final class HaloScepterItem extends Item {

    public HaloScepterItem(Properties settings) {
        super(settings);
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        TooltipDisplay display,
        Consumer<Component> tooltip,
        TooltipFlag flag
    ) {
        tooltip.accept(Component.translatable("tooltip.halo.halo_scepter.use").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.halo.halo_scepter.remove").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.halo.halo_scepter.self").withStyle(ChatFormatting.DARK_GRAY));
    }
}
