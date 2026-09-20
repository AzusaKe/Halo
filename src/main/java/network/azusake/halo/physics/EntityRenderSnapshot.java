package network.azusake.halo.physics;

import java.util.UUID;
import network.azusake.halo.api.v2.AnchorVec3;
import net.minecraft.client.model.player.PlayerModel;

/** Facts copied during extraction; delayed model draws never read a moving entity. */
public record EntityRenderSnapshot(UUID uuid, int runtimeId, Object world, AnchorVec3 position,
                                   boolean living, boolean player, PlayerModel playerModel) {
    public EntityRenderSnapshot(UUID uuid, int runtimeId, Object world, AnchorVec3 position,
                                boolean living, boolean player) {
        this(uuid, runtimeId, world, position, living, player, null);
    }

    /** Bind the renderer's base model before its body and feature submissions are collected. */
    public EntityRenderSnapshot withPlayerModel(PlayerModel model) {
        return new EntityRenderSnapshot(uuid, runtimeId, world, position, living, player, model);
    }

    public interface Holder {
        EntityRenderSnapshot halo$snapshot();
        void halo$snapshot(EntityRenderSnapshot value);
    }
}
