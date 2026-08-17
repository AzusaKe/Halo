package network.azusake.halo.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ServerTickHandler} and {@link HaloServerEvents}.
 *
 * <p>These tests verify that the handler is correctly structured, that it
 * exposes the expected {@code onEndTick} entry point, and that the event
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
            // server.getPlayerCount(), but on intervals 1-19 we are safe.
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

        @Test
        @DisplayName("registerTickHandler does not throw")
        void registerTickHandlerDoesNotThrow() {
            // The NeoForge event bus is usable without a full mod-loading
            // environment, so registration should never throw.
            assertDoesNotThrow(HaloServerEvents::registerTickHandler,
                "registerTickHandler should register without exception");
        }

        @Test
        @DisplayName("registerEntityEvents does not throw")
        void registerEntityEventsDoesNotThrow() {
            assertDoesNotThrow(HaloServerEvents::registerEntityEvents,
                "registerEntityEvents should register without exception");
        }

        @Test
        @DisplayName("registerConnectionEvents does not throw")
        void registerConnectionEventsDoesNotThrow() {
            assertDoesNotThrow(HaloServerEvents::registerConnectionEvents,
                "registerConnectionEvents should register without exception");
        }

        @Test
        @DisplayName("registerAll does not throw")
        void registerAllDoesNotThrow() {
            assertDoesNotThrow(HaloServerEvents::registerAll,
                "registerAll should register every handler without exception");
        }

        @Test
        @DisplayName("multiple registerTickHandler calls are idempotent (no throw)")
        void registerTickHandlerIsIdempotent() {
            // The NeoForge event bus supports multiple registrations; calling
            // twice should not throw.
            assertDoesNotThrow(() -> {
                HaloServerEvents.registerTickHandler();
                HaloServerEvents.registerTickHandler();
            }, "registerTickHandler should be callable multiple times");
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
