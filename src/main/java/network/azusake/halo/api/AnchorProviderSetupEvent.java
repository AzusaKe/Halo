package network.azusake.halo.api;

import net.minecraftforge.eventbus.api.Event;

/**
 * Fired once by Halo after every client mod entrypoint has run (at the end of
 * the first client tick), after the default providers have been registered, so
 * other mods can register their own {@link EntityAnchorProvider}s via
 * {@link EntityAnchorProviderRegistry}.
 *
 * <p>Subscribe on the Forge event bus during client setup. Halo posts this
 * event after all mod constructors and client setup hooks have completed.</p>
 */
public class AnchorProviderSetupEvent extends Event {

    private final EntityAnchorProviderRegistry registry;

    public AnchorProviderSetupEvent(EntityAnchorProviderRegistry registry) {
        this.registry = registry;
    }

    public EntityAnchorProviderRegistry getRegistry() {
        return registry;
    }

}
