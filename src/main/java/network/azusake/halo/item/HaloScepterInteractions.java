package network.azusake.halo.item;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;

/** Fabric interaction hooks for the halo scepter. */
final class HaloScepterInteractions {

    private static boolean registered;

    private HaloScepterInteractions() {
    }

    static void register() {
        if (registered) {
            return;
        }
        registered = true;

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!player.getMainHandStack().isOf(HaloItems.HALO_SCEPTER)) {
                return ActionResult.PASS;
            }
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                HaloScepterService.remove(serverPlayer, entity, player.isSneaking());
            }
            return ActionResult.SUCCESS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!player.getStackInHand(hand).isOf(HaloItems.HALO_SCEPTER)) {
                return ActionResult.PASS;
            }
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                HaloScepterService.open(serverPlayer, player.isSneaking() ? player : entity);
            }
            return ActionResult.SUCCESS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!player.isSneaking() || !player.getStackInHand(hand).isOf(HaloItems.HALO_SCEPTER)) {
                return ActionResult.PASS;
            }
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                HaloScepterService.open(serverPlayer, player);
            }
            return ActionResult.SUCCESS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!player.isSneaking() || !player.getStackInHand(hand).isOf(HaloItems.HALO_SCEPTER)) {
                return TypedActionResult.pass(player.getStackInHand(hand));
            }
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                HaloScepterService.open(serverPlayer, player);
            }
            return TypedActionResult.success(player.getStackInHand(hand), world.isClient);
        });
    }
}
