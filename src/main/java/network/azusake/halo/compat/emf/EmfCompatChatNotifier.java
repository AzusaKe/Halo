package network.azusake.halo.compat.emf;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.text.Text;

import java.util.concurrent.atomic.AtomicBoolean;

/** Emits one actionable in-game message after the client player exists. */
public final class EmfCompatChatNotifier {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private EmfCompatChatNotifier() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && EmfCompatDiagnostics.markChatReported()) {
                client.player.sendMessage(Text.literal(EmfCompatDiagnostics.USER_ERROR_MESSAGE), false);
            }
        });
    }
}
