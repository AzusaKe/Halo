package network.azusake.halo.api;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link EntityAnchorProviderRegistry} lookup semantics:
 * UUID priority, exact class, superclass chain, last-registration-wins and
 * fallback.  Class objects are used as keys — no Minecraft instance needed.
 */
class EntityAnchorProviderRegistryTest {

    private static final EntityAnchorProvider PROVIDER_A = (entity, tickDelta) -> null;
    private static final EntityAnchorProvider PROVIDER_B = (entity, tickDelta) -> null;

    @Nested
    @DisplayName("Class registration")
    class ClassRegistration {

        @Test
        void exactClassMatch() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            registry.register(Player.class, PROVIDER_A);
            assertSame(PROVIDER_A, registry.getProvider(Player.class));
        }

        @Test
        void superclassMatch() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            registry.register(LivingEntity.class, PROVIDER_A);
            assertSame(PROVIDER_A, registry.getProvider(Creeper.class));
        }

        @Test
        void exactClassBeatsSuperclass() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            registry.register(LivingEntity.class, PROVIDER_A);
            registry.register(Creeper.class, PROVIDER_B);
            assertSame(PROVIDER_B, registry.getProvider(Creeper.class));
            assertSame(PROVIDER_A, registry.getProvider(Player.class));
        }

        @Test
        void lastRegistrationWins() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            registry.register(Player.class, PROVIDER_A);
            registry.register(Player.class, PROVIDER_B);
            assertSame(PROVIDER_B, registry.getProvider(Player.class));
        }

        @Test
        void unregisteredFallsBackToFallbackProvider() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            assertSame(FallbackAnchorProvider.getInstance(), registry.getProvider(Creeper.class));
        }

        @Test
        void getProviderByClassNeverReturnsNull() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            assertNotNull(registry.getProvider(Object.class));
        }

        @Test
        void nullClassRegistrationRejected() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            assertThrows(IllegalArgumentException.class,
                () -> registry.register((Class<? extends LivingEntity>) null, PROVIDER_A));
        }

        @Test
        void nullProviderRejected() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            assertThrows(IllegalArgumentException.class,
                () -> registry.register(Player.class, null));
        }
    }

    @Nested
    @DisplayName("UUID (per-instance) registration")
    class InstanceRegistration {

        @Test
        void uuidBeatsClassRegistration() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            UUID uuid = UUID.randomUUID();
            registry.register(Creeper.class, PROVIDER_A);
            registry.register(uuid, PROVIDER_B);
            assertSame(PROVIDER_B, registry.getProvider(uuid, Creeper.class));
        }

        @Test
        void unknownUuidFallsThroughToClass() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            registry.register(Creeper.class, PROVIDER_A);
            assertSame(PROVIDER_A, registry.getProvider(UUID.randomUUID(), Creeper.class));
        }

        @Test
        void unknownUuidFallsThroughToFallback() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            assertSame(FallbackAnchorProvider.getInstance(),
                registry.getProvider(UUID.randomUUID(), Creeper.class));
        }

        @Test
        void lastUuidRegistrationWins() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            UUID uuid = UUID.randomUUID();
            registry.register(uuid, PROVIDER_A);
            registry.register(uuid, PROVIDER_B);
            assertSame(PROVIDER_B, registry.getProvider(uuid, Creeper.class));
        }

        @Test
        void nullUuidRegistrationRejected() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            assertThrows(IllegalArgumentException.class,
                () -> registry.register((UUID) null, PROVIDER_A));
        }

        @Test
        void nullUuidProviderRejected() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            assertThrows(IllegalArgumentException.class,
                () -> registry.register(UUID.randomUUID(), null));
        }
    }
}
