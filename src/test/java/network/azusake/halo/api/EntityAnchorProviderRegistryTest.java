package network.azusake.halo.api;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
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
            registry.register(PlayerEntity.class, PROVIDER_A);
            assertSame(PROVIDER_A, registry.getProvider(PlayerEntity.class));
        }

        @Test
        void superclassMatch() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            registry.register(LivingEntity.class, PROVIDER_A);
            assertSame(PROVIDER_A, registry.getProvider(ZombieEntity.class));
        }

        @Test
        void exactClassBeatsSuperclass() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            registry.register(LivingEntity.class, PROVIDER_A);
            registry.register(ZombieEntity.class, PROVIDER_B);
            assertSame(PROVIDER_B, registry.getProvider(ZombieEntity.class));
            assertSame(PROVIDER_A, registry.getProvider(PlayerEntity.class));
        }

        @Test
        void lastRegistrationWins() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            registry.register(PlayerEntity.class, PROVIDER_A);
            registry.register(PlayerEntity.class, PROVIDER_B);
            assertSame(PROVIDER_B, registry.getProvider(PlayerEntity.class));
        }

        @Test
        void unregisteredFallsBackToFallbackProvider() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            assertSame(FallbackAnchorProvider.getInstance(), registry.getProvider(ZombieEntity.class));
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
                () -> registry.register(PlayerEntity.class, null));
        }
    }

    @Nested
    @DisplayName("UUID (per-instance) registration")
    class InstanceRegistration {

        @Test
        void uuidBeatsClassRegistration() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            UUID uuid = UUID.randomUUID();
            registry.register(ZombieEntity.class, PROVIDER_A);
            registry.register(uuid, PROVIDER_B);
            assertSame(PROVIDER_B, registry.getProvider(uuid, ZombieEntity.class));
        }

        @Test
        void unknownUuidFallsThroughToClass() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            registry.register(ZombieEntity.class, PROVIDER_A);
            assertSame(PROVIDER_A, registry.getProvider(UUID.randomUUID(), ZombieEntity.class));
        }

        @Test
        void unknownUuidFallsThroughToFallback() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            assertSame(FallbackAnchorProvider.getInstance(),
                registry.getProvider(UUID.randomUUID(), ZombieEntity.class));
        }

        @Test
        void lastUuidRegistrationWins() {
            EntityAnchorProviderRegistry registry = new EntityAnchorProviderRegistry();
            UUID uuid = UUID.randomUUID();
            registry.register(uuid, PROVIDER_A);
            registry.register(uuid, PROVIDER_B);
            assertSame(PROVIDER_B, registry.getProvider(uuid, ZombieEntity.class));
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
