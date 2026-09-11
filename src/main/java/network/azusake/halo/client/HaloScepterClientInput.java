package network.azusake.halo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
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
    public static boolean interceptSneakingAttack(Minecraft client) {
        if (client.player == null
            || !client.player.isShiftKeyDown()
            || !client.player.getMainHandItem().is(HaloItems.HALO_SCEPTER)) {
            return false;
        }
        if (!attackHeld) {
            attackHeld = true;
            HaloNetworkClient.sendScepterRemoveSelf();
            client.player.swing(InteractionHand.MAIN_HAND);
        }
        return true;
    }

    public static void tick(Minecraft client) {
        if (!client.options.keyAttack.isDown()) {
            attackHeld = false;
        }
    }
}
