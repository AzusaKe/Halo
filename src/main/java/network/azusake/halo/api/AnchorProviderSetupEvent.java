package network.azusake.halo.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Fired once by Halo after every client mod entrypoint has run (at the end of
 * the first client tick), after the default providers have been registered, so
 * other mods can register their own {@link EntityAnchorProvider}s via
 * {@link EntityAnchorProviderRegistry}.
 *
 * <p>Because Fabric provides no cross-mod ordering guarantee for
 * {@code ClientModInitializer} entrypoints, registering a listener during your
 * own {@code onInitializeClient} is always safe: Halo fires this event only
 * after all entrypoints have completed.</p>
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
