package network.azusake.halo.compat.ysm;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import network.azusake.halo.anchor.AnchorCaptureCoordinator;
import network.azusake.halo.api.v2.AnchorVec3;
import network.azusake.halo.physics.RenderHeadCapture;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Entity attribution for YSM calls made inside the world entity dispatcher. */
public final class YsmEntityRenderContext {
    private static final ThreadLocal<LivingEntity> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SCOPE_OPEN = ThreadLocal.withInitial(() -> false);
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
        closeScope();
        CURRENT.remove();
        if (state == null) return;
        WeakReference<LivingEntity> reference;
        synchronized (ENTITY_BY_STATE) {
            reference = ENTITY_BY_STATE.get(state);
        }
        LivingEntity living = reference == null ? null : reference.get();
        if (living != null) {
            CURRENT.set(living);
            float tickDelta = RenderHeadCapture.getFrameTickDelta();
            Vec3 position = new Vec3(
                living.xOld + (living.getX() - living.xOld) * tickDelta,
                living.yOld + (living.getY() - living.yOld) * tickDelta,
                living.zOld + (living.getZ() - living.zOld) * tickDelta);
            AnchorCaptureCoordinator.beginEntityRender(
                living.getUUID(), living.getId(), living.level(),
                new AnchorVec3(position.x, position.y, position.z),
                RenderHeadCapture.isCurrentMainPass());
            SCOPE_OPEN.set(true);
        }
    }

    public static LivingEntity current() {
        return CURRENT.get();
    }

    public static void end() {
        CURRENT.remove();
        closeScope();
    }

    private static void closeScope() {
        if (SCOPE_OPEN.get()) AnchorCaptureCoordinator.endEntityRender();
        SCOPE_OPEN.remove();
    }
}
