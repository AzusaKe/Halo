package network.azusake.halo.platform;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.runtime.ClientRuntime;

/** Fabric's logical-client composition root; never used by server handlers. */
public final class HaloClientState {
    private static final ClientRuntime CLIENT=new ClientRuntime(
        System::currentTimeMillis, HaloClientState::warnMissingDefinition);
    private HaloClientState() {}
    public static ClientRuntime get() { return CLIENT; }

    private static void warnMissingDefinition(Identifier id) {
        var client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.translatable("message.halo.missing_definition",
                Text.literal(id.toString()).formatted(Formatting.WHITE)).formatted(Formatting.YELLOW), false);
        }
    }
}
