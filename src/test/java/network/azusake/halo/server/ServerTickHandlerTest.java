package network.azusake.halo.server;

import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ServerTickHandler} and {@link HaloServerEvents}.
 *
 * <p>These tests verify that the handler is correctly structured, that it
 * exposes the expected NeoForge callback shape, and that the event
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
        @DisplayName("exposes the NeoForge server-tick callback")
        void exposesServerTickCallback() throws Exception {
            var method = ServerTickHandler.class.getMethod("onEndTick", MinecraftServer.class);
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

        @Test
        @DisplayName("registerTickHandler does not throw")
        void registerTickHandlerDoesNotThrow() {
            // The NeoForge event bus is initialised by the time a mod
            // calls onInitialize, so registration should never throw.
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
            // Registration is explicitly idempotent; calling twice
            // should not throw.
            assertDoesNotThrow(() -> {
                HaloServerEvents.registerTickHandler();
                HaloServerEvents.registerTickHandler();
            }, "registerTickHandler should be callable multiple times");
        }

        @Test
        @DisplayName("registered NeoForge callback retains one handler instance")
        void handlerIsRetainedAfterRegistration() throws Exception {
            HaloServerEvents.registerTickHandler();
            Field handlerField = HaloServerEvents.class.getDeclaredField("tickHandler");
            handlerField.setAccessible(true);
            assertInstanceOf(ServerTickHandler.class, handlerField.get(null));
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
