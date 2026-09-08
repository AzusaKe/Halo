package network.azusake.halo.compat.emf;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.concurrent.atomic.AtomicBoolean;

/** Emits the requested actionable message once after the client player exists. */
public final class EmfCompatChatNotifier {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private EmfCompatChatNotifier() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && EmfCompatDiagnostics.markChatReported()) {
                client.player.sendSystemMessage(
                    Component.literal(EmfCompatDiagnostics.USER_ERROR_MESSAGE));
            }
        });
    }
}

