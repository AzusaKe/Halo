package network.azusake.halo.item;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

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

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!player.getMainHandItem().is(HaloItems.HALO_SCEPTER)) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                HaloScepterService.remove(serverPlayer, entity, player.isShiftKeyDown());
            }
            return InteractionResult.SUCCESS;
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!player.getItemInHand(hand).is(HaloItems.HALO_SCEPTER)) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                HaloScepterService.open(serverPlayer, player.isShiftKeyDown() ? player : entity);
            }
            return InteractionResult.SUCCESS;
        });

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!player.isShiftKeyDown() || !player.getItemInHand(hand).is(HaloItems.HALO_SCEPTER)) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                HaloScepterService.open(serverPlayer, player);
            }
            return InteractionResult.SUCCESS;
        });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!player.isShiftKeyDown() || !player.getItemInHand(hand).is(HaloItems.HALO_SCEPTER)) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                HaloScepterService.open(serverPlayer, player);
            }
            return InteractionResult.SUCCESS;
        });
    }
}
