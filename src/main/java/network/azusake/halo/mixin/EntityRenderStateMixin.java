package network.azusake.halo.mixin;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import network.azusake.halo.physics.EntityRenderSnapshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements EntityRenderSnapshot.Holder {
    @Unique private EntityRenderSnapshot halo$snapshot;
    public EntityRenderSnapshot halo$snapshot() { return halo$snapshot; }
    public void halo$snapshot(EntityRenderSnapshot value) { halo$snapshot = value; }
}
