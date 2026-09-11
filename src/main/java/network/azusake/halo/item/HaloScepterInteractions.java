package network.azusake.halo.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** NeoForge interaction hooks for the halo scepter. */
final class HaloScepterInteractions {

    private static boolean registered;

    private HaloScepterInteractions() {
    }

    static void register(IEventBus eventBus) {
        if (registered) {
            return;
        }
        registered = true;

        eventBus.addListener(
            EventPriority.HIGHEST,
            false,
            AttackEntityEvent.class,
            HaloScepterInteractions::onAttackEntity
        );
        eventBus.addListener(
            EventPriority.HIGHEST,
            false,
            PlayerInteractEvent.EntityInteract.class,
            HaloScepterInteractions::onUseEntity
        );
        eventBus.addListener(
            EventPriority.HIGHEST,
            false,
            PlayerInteractEvent.EntityInteractSpecific.class,
            HaloScepterInteractions::onUseEntitySpecific
        );
        eventBus.addListener(
            EventPriority.HIGHEST,
            false,
            PlayerInteractEvent.LeftClickBlock.class,
            HaloScepterInteractions::onLeftClickBlock
        );
    }

    private static void onAttackEntity(AttackEntityEvent event) {
        if (!event.getEntity().getMainHandItem().is(HaloItems.HALO_SCEPTER.get())) {
            return;
        }
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            HaloScepterService.remove(player, event.getTarget(), player.isShiftKeyDown());
        }
        event.setCanceled(true);
    }

    private static void onUseEntity(PlayerInteractEvent.EntityInteract event) {
        if (!event.getItemStack().is(HaloItems.HALO_SCEPTER.get())) {
            return;
        }
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            HaloScepterService.open(player, player.isShiftKeyDown() ? player : event.getTarget());
        }
        consume(event);
    }

    private static void onUseEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.getItemStack().is(HaloItems.HALO_SCEPTER.get())) {
            return;
        }
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            HaloScepterService.open(player, player.isShiftKeyDown() ? player : event.getTarget());
        }
        consume(event);
    }

    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity().isShiftKeyDown()
            && event.getEntity().getMainHandItem().is(HaloItems.HALO_SCEPTER.get())) {
            event.setCanceled(true);
        }
    }

    private static void consume(PlayerInteractEvent.EntityInteract event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void consume(PlayerInteractEvent.EntityInteractSpecific event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
