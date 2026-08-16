package network.azusake.halo.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.LivingEntity;

/**
 * Registry of {@link EntityAnchorProvider}s, consulted by the halo anchor
 * pipeline once per render frame.
 *
 * <h3>Lookup order for {@link #getProvider(LivingEntity)}</h3>
 * <ol>
 *   <li>Per-instance registration keyed by the entity's {@link UUID}.</li>
 *   <li>Exact entity class match.</li>
 *   <li>Superclass chain (walking {@link Class#getSuperclass()} upward).</li>
 *   <li>{@link FallbackAnchorProvider} — never null.</li>
 * </ol>
 *
 * <p>Class and UUID registrations are independent; the last registration for
 * a given key wins, and UUID registrations always take priority over class
 * registrations.  To adapt only specific entities by any predicate (NBT,
 * team, dynamic state, …), capture the current provider with
 * {@link #getProvider(Class)} before registering, then delegate inside the
 * new provider for entities that should not be customised.</p>
 */
public final class EntityAnchorProviderRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityAnchorProviderRegistry.class);

    private static final EntityAnchorProviderRegistry INSTANCE = new EntityAnchorProviderRegistry();

    private final Map<UUID, EntityAnchorProvider> instanceProviders = new ConcurrentHashMap<>();
    private final Map<Class<?>, EntityAnchorProvider> classProviders = new ConcurrentHashMap<>();

    /** Package-private so unit tests can create isolated instances. */
    EntityAnchorProviderRegistry() { /* singleton by default */ }

    public static EntityAnchorProviderRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Register a provider for every entity of the given class (and its
     * subclasses).  Later registrations for the same class override earlier
     * ones.
     */
    public void register(Class<? extends LivingEntity> entityClass, EntityAnchorProvider provider) {
        if (entityClass == null || provider == null) {
            throw new IllegalArgumentException("entityClass and provider must not be null");
        }
        classProviders.put(entityClass, provider);
        LOGGER.info("Registered EntityAnchorProvider {} for {}", provider.getClass().getSimpleName(), entityClass.getName());
    }

    /**
     * Register a provider for one specific entity (by UUID).  Takes priority
     * over all class-based registrations.  Later registrations for the same
     * UUID override earlier ones.
     */
    public void register(UUID entityUuid, EntityAnchorProvider provider) {
        if (entityUuid == null || provider == null) {
            throw new IllegalArgumentException("entityUuid and provider must not be null");
        }
        instanceProviders.put(entityUuid, provider);
        LOGGER.info("Registered EntityAnchorProvider {} for entity {}", provider.getClass().getSimpleName(), entityUuid);
    }

    /**
     * Resolve the provider for a concrete entity (UUID → exact class →
     * superclass chain → fallback).
     */
    public EntityAnchorProvider getProvider(LivingEntity entity) {
        return getProvider(entity.getUUID(), entity.getClass());
    }

    /**
     * Resolve the provider for an entity class (exact match → superclass
     * chain → {@link FallbackAnchorProvider}).  Never returns null.
     */
    public EntityAnchorProvider getProvider(Class<?> entityClass) {
        for (Class<?> c = entityClass; c != null; c = c.getSuperclass()) {
            EntityAnchorProvider provider = classProviders.get(c);
            if (provider != null) {
                return provider;
            }
        }
        return FallbackAnchorProvider.getInstance();
    }

    /**
     * Package-private: UUID lookup split out so the priority semantics can be
     * unit-tested without a Minecraft entity instance.
     */
    EntityAnchorProvider getProvider(UUID entityUuid, Class<?> entityClass) {
        EntityAnchorProvider instanceProvider = instanceProviders.get(entityUuid);
        if (instanceProvider != null) {
            return instanceProvider;
        }
        return getProvider(entityClass);
    }
}
