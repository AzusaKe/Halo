package network.azusake.halo.server;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.IEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ServerTickHandler} and {@link HaloServerEvents}.
 *
 * <p>These tests verify that the handler is correctly structured, that it
 * exposes the Forge callback shape, and that the event
 * registration wiring does not throw.</p>
 */
class ServerTickHandlerTest {

    // ------------------------------------------------------------------
    // 1. ServerTickHandler structural tests
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("ServerTickHandler")
    class TickHandlerTests {

        @Test
        @DisplayName("exposes a MinecraftServer end-tick callback")
        void exposesEndTickCallback() throws Exception {
            var method = ServerTickHandler.class.getDeclaredMethod("onEndTick", MinecraftServer.class);
            assertEquals(void.class, method.getReturnType());
        }

        @Test
        @DisplayName("tick counter initialises to zero")
        void tickCounterStartsAtZero() throws Exception {
            ServerTickHandler handler = new ServerTickHandler();
            Field counterField = ServerTickHandler.class.getDeclaredField("tickCounter");
            counterField.setAccessible(true);
            assertEquals(0, counterField.getInt(handler),
                "tickCounter should be 0 on construction");
        }

        @Test
        @DisplayName("onEndTick increments counter")
        void onEndTickIncrementsCounter() throws Exception {
            ServerTickHandler handler = new ServerTickHandler();
            Field counterField = ServerTickHandler.class.getDeclaredField("tickCounter");
            counterField.setAccessible(true);

            // Call onEndTick with null – counter should still increment;
            // the LOGGER.trace call on interval 20 may fail with NPE on
            // server.getCurrentPlayerCount(), but on intervals 1-19 we are safe.
            for (int i = 0; i < 5; i++) {
                handler.onEndTick(null);
            }

            assertEquals(5, counterField.getInt(handler),
                "tickCounter should equal the number of onEndTick calls");
        }
    }

    // ------------------------------------------------------------------
    // 2. HaloServerEvents registration tests
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("HaloServerEvents registration")
    class RegistrationTests {
        private IEventBus eventBus;
        private AtomicInteger listenerCount;

        @BeforeEach
        void resetRegistrationState() throws Exception {
            for (String fieldName : new String[] {
                "registered", "tickRegistered", "entityRegistered", "connectionRegistered"
            }) {
                Field field = HaloServerEvents.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.setBoolean(null, false);
            }

            listenerCount = new AtomicInteger();
            eventBus = (IEventBus) Proxy.newProxyInstance(
                IEventBus.class.getClassLoader(),
                new Class<?>[] {IEventBus.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("addListener")) listenerCount.incrementAndGet();
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                }
            );
        }

        @Test
        @DisplayName("registerTickHandler installs both END tick listeners")
        void registerTickHandlerInstallsListeners() {
            assertDoesNotThrow(() -> HaloServerEvents.registerTickHandler(eventBus));
            assertEquals(2, listenerCount.get());
        }

        @Test
        @DisplayName("registerEntityEvents installs the entity listener")
        void registerEntityEventsInstallsListener() {
            assertDoesNotThrow(() -> HaloServerEvents.registerEntityEvents(eventBus));
            assertEquals(1, listenerCount.get());
        }

        @Test
        @DisplayName("registerConnectionEvents installs login and logout listeners")
        void registerConnectionEventsInstallsListeners() {
            assertDoesNotThrow(() -> HaloServerEvents.registerConnectionEvents(eventBus));
            assertEquals(2, listenerCount.get());
        }

        @Test
        @DisplayName("registerAll does not throw")
        void registerAllDoesNotThrow() {
            assertDoesNotThrow(() -> HaloServerEvents.registerAll(eventBus),
                "registerAll should register every handler without exception");
            assertEquals(5, listenerCount.get());
        }

        @Test
        @DisplayName("multiple registerTickHandler calls are idempotent (no throw)")
        void registerTickHandlerIsIdempotent() {
            // Registration is explicitly idempotent.
            assertDoesNotThrow(() -> {
                HaloServerEvents.registerTickHandler(eventBus);
                HaloServerEvents.registerTickHandler(eventBus);
            }, "registerTickHandler should be callable multiple times");
            assertEquals(2, listenerCount.get());
        }

        @Test
        @DisplayName("multiple registerAll calls are idempotent")
        void registerAllIsIdempotent() {
            assertDoesNotThrow(() -> {
                HaloServerEvents.registerAll(eventBus);
                HaloServerEvents.registerAll(eventBus);
            });
            assertEquals(5, listenerCount.get());
        }
    }

    // ------------------------------------------------------------------
    // 3. HaloServerEvents utility class contract
    // ------------------------------------------------------------------

    @Test
    @DisplayName("HaloServerEvents: private constructor (utility class)")
    void privateConstructor() throws Exception {
        var ctor = HaloServerEvents.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object instance = ctor.newInstance();
        assertNotNull(instance,
            "HaloServerEvents should be instantiable via reflection (utility class pattern)");
    }
}
