package network.azusake.halo.compat.ysm;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Entity attribution for YSM calls made inside the world entity dispatcher. */
public final class YsmEntityRenderContext {
    private static final ThreadLocal<LivingEntity> CURRENT = new ThreadLocal<>();
    private static final Map<EntityRenderState, WeakReference<LivingEntity>> ENTITY_BY_STATE =
        new WeakHashMap<>();

    private YsmEntityRenderContext() {}

    public static void register(Entity entity, EntityRenderState state) {
        if (entity instanceof LivingEntity living && state != null) {
            synchronized (ENTITY_BY_STATE) {
                ENTITY_BY_STATE.put(state, new WeakReference<>(living));
            }
        }
    }

    public static void begin(EntityRenderState state) {
        CURRENT.remove();
        if (state == null) return;
        WeakReference<LivingEntity> reference;
        synchronized (ENTITY_BY_STATE) {
            reference = ENTITY_BY_STATE.get(state);
        }
        if (reference != null) CURRENT.set(reference.get());
    }

    public static LivingEntity current() {
        return CURRENT.get();
    }

    public static void end() {
        CURRENT.remove();
    }
}
