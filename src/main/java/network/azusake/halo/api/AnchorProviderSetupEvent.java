package network.azusake.halo.api;

import net.neoforged.bus.api.Event;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fired once on NeoForge's game event bus after Halo's default client anchor
 * providers are registered. Consumers can call {@link #getRegistry()} and
 * register a later provider to preserve the API's last-registration-wins rule.
 *
 * <p>The {@link #EVENT} compatibility holder preserves the Fabric branch's
 * {@code EVENT.register(registry -> ...)} source shape for loader-neutral
 * integrations. Halo invokes those listeners immediately before posting this
 * event to NeoForge's game bus.</p>
 */
public final class AnchorProviderSetupEvent extends Event {
    public static final Holder EVENT = new Holder();

    private final EntityAnchorProviderRegistry registry;

    public AnchorProviderSetupEvent(EntityAnchorProviderRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public EntityAnchorProviderRegistry getRegistry() {
        return registry;
    }

    @FunctionalInterface
    public interface Listener {
        void onSetup(EntityAnchorProviderRegistry registry);
    }

    public static final class Holder {
        private final List<Listener> listeners = new CopyOnWriteArrayList<>();
        private Holder() {}

        public void register(Listener listener) {
            listeners.add(Objects.requireNonNull(listener, "listener"));
        }

        public Listener invoker() {
            return registry -> {
                for (Listener listener : listeners) listener.onSetup(registry);
            };
        }
    }
}
