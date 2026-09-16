package network.azusake.halo.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.command.CommandSource;
import net.minecraft.network.packet.s2c.play.CommandTreeS2CPacket;
import network.azusake.halo.client.RendererCommandTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Repair a colliding server root on every tree refresh; safe on either side of Fabric's merge. */
@Mixin(value = ClientPlayNetworkHandler.class, priority = 900)
public abstract class ClientCommandSuggestionsMixin {
    @Shadow private CommandDispatcher<CommandSource> commandDispatcher;

    @Inject(method = "onCommandTree", at = @At("RETURN"))
    private void halo$rendererSuggestions(CommandTreeS2CPacket packet, CallbackInfo ci) {
        RendererCommandTree.addSuggestions(commandDispatcher);
    }
}
