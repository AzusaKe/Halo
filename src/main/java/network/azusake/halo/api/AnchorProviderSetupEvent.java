package network.azusake.halo.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Fired once during Halo's client initialisation, after the default providers
 * have been registered, so other mods can register their own
 * {@link EntityAnchorProvider}s via {@link EntityAnchorProviderRegistry}.
 */
public interface AnchorProviderSetupEvent {

    Event<AnchorProviderSetupEvent> EVENT = EventFactory.createArrayBacked(
        AnchorProviderSetupEvent.class,
        callbacks -> registry -> {
            for (AnchorProviderSetupEvent callback : callbacks) {
                callback.onSetup(registry);
            }
        }
    );

    /**
     * Called after Halo's default providers are registered.
     *
     * @param registry the entity anchor provider registry
     */
    void onSetup(EntityAnchorProviderRegistry registry);
}
