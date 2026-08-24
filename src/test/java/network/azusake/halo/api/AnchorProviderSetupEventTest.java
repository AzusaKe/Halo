package network.azusake.halo.api;

import net.neoforged.bus.api.Event;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnchorProviderSetupEventTest {
    @Test
    void isANeoForgeEventWithThePublicRegistry() {
        EntityAnchorProviderRegistry registry = EntityAnchorProviderRegistry.getInstance();
        AnchorProviderSetupEvent event = new AnchorProviderSetupEvent(registry);
        assertTrue(event instanceof Event);
        assertSame(registry, event.getRegistry());
    }

    @Test
    void compatibilityHolderRetainsFabricRegistrationShape() {
        EntityAnchorProviderRegistry registry = EntityAnchorProviderRegistry.getInstance();
        AtomicReference<EntityAnchorProviderRegistry> observed = new AtomicReference<>();
        AnchorProviderSetupEvent.EVENT.register(observed::set);
        AnchorProviderSetupEvent.EVENT.invoker().onSetup(registry);
        assertSame(registry, observed.get());
    }
}
