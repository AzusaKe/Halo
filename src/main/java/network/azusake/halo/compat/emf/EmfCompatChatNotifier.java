package network.azusake.halo.compat.emf;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.concurrent.atomic.AtomicBoolean;

/** Emits one actionable in-game message after the Forge client player exists. */
public final class EmfCompatChatNotifier {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private EmfCompatChatNotifier() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && EmfCompatDiagnostics.markChatReported()) {
                client.player.sendSystemMessage(Component.literal(
                    EmfCompatDiagnostics.USER_ERROR_MESSAGE));
            }
        });
    }
}
