package network.azusake.halo.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraftforge.client.ClientCommandHandler;
import network.azusake.halo.client.RendererCommandTree;
import network.azusake.halo.client.ServerCommandForwardingGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Repair a colliding server root on every tree refresh after Forge merges client commands. */
@Mixin(value = ClientPacketListener.class, priority = 900)
public abstract class ClientCommandSuggestionsMixin {
    @Shadow public CommandDispatcher<SharedSuggestionProvider> commands;

    /**
     * Forge patches this call into {@code sendCommand}. Halo uses the same
     * method to forward a client command, so skip only that recursive local
     * dispatch and let the remainder of vanilla's signed send path execute.
     */
    @Redirect(
        method = "sendCommand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/client/ClientCommandHandler;runCommand(Ljava/lang/String;)Z",
            remap = false
        )
    )
    private boolean halo$bypassForwardedClientCommand(String command) {
        if (ServerCommandForwardingGuard.shouldBypassClientDispatcher()) {
            return false;
        }
        return ClientCommandHandler.runCommand(command);
    }

    @Inject(method = "handleCommands", at = @At("RETURN"))
    private void halo$rendererSuggestions(ClientboundCommandsPacket packet, CallbackInfo ci) {
        RendererCommandTree.addSuggestions(commands);
    }
}
