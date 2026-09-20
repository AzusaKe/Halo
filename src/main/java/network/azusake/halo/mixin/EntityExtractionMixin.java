package network.azusake.halo.mixin;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import network.azusake.halo.physics.EntityRenderSnapshot;
import network.azusake.halo.api.v2.AnchorVec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EntityRenderer.class)
public abstract class EntityExtractionMixin {
    @Inject(method="extractRenderState", at=@At("RETURN"))
    private void halo$copyIdentity(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        ((EntityRenderSnapshot.Holder) state).halo$snapshot(new EntityRenderSnapshot(entity.getUUID(), entity.getId(), entity.level(),
            new AnchorVec3(state.x, state.y, state.z), entity instanceof LivingEntity, entity instanceof Player));
    }
}
