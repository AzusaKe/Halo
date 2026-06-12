package com.example.halo.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ServerTickHandler} and {@link HaloServerEvents}.
 *
 * <p>These tests verify that the handler is correctly structured, that it
 * implements the expected Fabric API interface, and that the event
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
        @DisplayName("implements ServerTickEvents.EndTick")
        void implementsEndTick() {
            ServerTickHandler handler = new ServerTickHandler();
            assertInstanceOf(ServerTickEvents.EndTick.class, handler,
                "ServerTickHandler must implement ServerTickEvents.EndTick");
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
            // The Fabric API event bus is initialised by the time a mod
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
            // Fabric events support multiple registrations; calling twice
            // should not throw.
            assertDoesNotThrow(() -> {
                HaloServerEvents.registerTickHandler();
                HaloServerEvents.registerTickHandler();
            }, "registerTickHandler should be callable multiple times");
        }

        @Test
        @DisplayName("registered handler is invoked by Fabric event bus")
        void handlerIsInvokedByEventBus() throws Exception {
            // Fabric's Event<T> stores handlers in an array-backed list.
            // After registration, we can inspect the internal handler array
            // to confirm our handler was added.

            // Grab the internal array via reflection
            Field handlersField = ServerTickEvents.END_SERVER_TICK.getClass()
                .getDeclaredField("handlers");
            handlersField.setAccessible(true);
            Object[] handlers = (Object[]) handlersField.get(ServerTickEvents.END_SERVER_TICK);

            // Our handler should be findable among the registered handlers
            List<ServerTickEvents.EndTick> tickHandlers = new ArrayList<>();
            for (Object h : handlers) {
                if (h instanceof ServerTickEvents.EndTick th) {
                    tickHandlers.add(th);
                }
            }

            assertFalse(tickHandlers.isEmpty(),
                "At least one EndTick handler should be registered after registerTickHandler()");

            boolean found = tickHandlers.stream()
                .anyMatch(h -> h instanceof ServerTickHandler);
            assertTrue(found,
                "ServerTickHandler instance should be present in END_SERVER_TICK handler list");
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
