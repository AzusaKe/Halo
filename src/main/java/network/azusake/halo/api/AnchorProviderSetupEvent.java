package network.azusake.halo.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fired once by Halo after every mod's client setup has run (at the end of
 * the first client tick), after the default providers have been registered, so
 * other mods can register their own {@link EntityAnchorProvider}s via
 * {@link EntityAnchorProviderRegistry}.
 *
 * <p>Because the mod loader provides no cross-mod ordering guarantee for
 * client initialisation, registering a listener during your own client setup
 * is always safe: Halo fires this event only after all client setup has
 * completed.</p>
 */
public interface AnchorProviderSetupEvent {

    /**
     * The event holder.  Listeners register via {@code EVENT.register(...)}
     * and Halo fires the event via {@code EVENT.invoker().onSetup(registry)}.
     */
    Holder EVENT = new Holder();

    /**
     * Called after Halo's default providers are registered.
     *
     * @param registry the entity anchor provider registry
     */
    void onSetup(EntityAnchorProviderRegistry registry);

    /**
     * Lightweight, thread-safe listener holder that replaces the Fabric
     * {@code Event}/{@code EventFactory} backing while keeping the same
     * {@code register}/{@code invoker} usage shape.
     */
    final class Holder {

        private final List<AnchorProviderSetupEvent> listeners = new CopyOnWriteArrayList<>();

        private Holder() {
        }

        /**
         * Register a listener to be invoked when the setup event fires.
         *
         * @param listener the listener to register
         */
        public void register(AnchorProviderSetupEvent listener) {
            listeners.add(listener);
        }

        /**
         * Return an invoker that runs every registered listener in order.
         *
         * @return an invoker for this event
         */
        public AnchorProviderSetupEvent invoker() {
            return registry -> {
                for (AnchorProviderSetupEvent listener : listeners) {
                    listener.onSetup(registry);
                }
            };
        }
    }
}
