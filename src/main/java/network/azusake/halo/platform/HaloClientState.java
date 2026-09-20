package network.azusake.halo.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.runtime.ClientRuntime;

/** Fabric's logical-client composition root; never used by server handlers. */
public final class HaloClientState {
    private static final ClientRuntime CLIENT=new ClientRuntime(
        System::currentTimeMillis, HaloClientState::warnMissingDefinition);
    private HaloClientState() {}
    public static ClientRuntime get() { return CLIENT; }

    private static void warnMissingDefinition(Identifier id) {
        var client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.translatable("message.halo.missing_definition",
                Component.literal(id.toString()).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.YELLOW));
        }
    }
}
