package network.azusake.halo.client;

import net.minecraft.client.MinecraftClient;
import network.azusake.halo.item.HaloItems;
import network.azusake.halo.network.HaloNetworkClient;

/** Edge-triggered client handling for crouching left-click on any target. */
public final class HaloScepterClientInput {

    private static boolean attackHeld;

    private HaloScepterClientInput() {
    }

    /**
     * @return true when vanilla attack/mining must be cancelled
     */
    public static boolean interceptSneakingAttack(MinecraftClient client) {
        if (client.player == null
            || !client.player.isSneaking()
            || !client.player.getMainHandStack().isOf(HaloItems.HALO_SCEPTER)) {
            return false;
        }
        if (!attackHeld) {
            attackHeld = true;
            HaloNetworkClient.sendScepterRemoveSelf();
            client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        }
        return true;
    }

    public static void tick(MinecraftClient client) {
        if (!client.options.attackKey.isPressed()) {
            attackHeld = false;
        }
    }
}
