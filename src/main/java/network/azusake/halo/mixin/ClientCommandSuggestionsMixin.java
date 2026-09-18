package network.azusake.halo.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import network.azusake.halo.client.RendererCommandTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Repair a colliding server root on every tree refresh after Forge merges client commands. */
@Mixin(value = ClientPacketListener.class, priority = 900)
public abstract class ClientCommandSuggestionsMixin {
    @Shadow public CommandDispatcher<SharedSuggestionProvider> commands;

    @Inject(method = "handleCommands", at = @At("RETURN"))
    private void halo$rendererSuggestions(ClientboundCommandsPacket packet, CallbackInfo ci) {
        RendererCommandTree.addSuggestions(commands);
    }
}
