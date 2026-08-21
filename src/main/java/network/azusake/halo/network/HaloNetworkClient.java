package network.azusake.halo.network;

import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.data.HaloTransitionState;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.manager.HaloManager;
import network.azusake.halo.render.HaloRenderer;
import network.azusake.halo.render.IdlePhaseTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Client-side handlers for the Forge SimpleChannel messages. */
public final class HaloNetworkClient {
    private HaloNetworkClient() {}
    public static void registerReceivers() { HaloNetwork.register(); }
    public static void handleSync(Map<UUID, ResourceLocation> values) {
        HaloManager.getInstance().replaceAllClientHalos(values);
        HaloRenderer.getInstance().clearIdlePhases();
    }
    public static void handleUpdate(UUID uuid, boolean attach, ResourceLocation defId) {
        if (attach) {
            HaloManager.getInstance().putClientHalo(uuid, defId, HaloTransitionState.STARTING);
            return;
        }
        HaloInstance inst = HaloManager.getInstance().getInstance(uuid);
        IdlePhaseTracker.RenderState state = HaloRenderer.getInstance().readLastRenderState(uuid);
        double freeze;
        if (inst == null) {
            HaloManager.getInstance().putClientHalo(uuid, defId);
            inst = HaloManager.getInstance().getInstance(uuid);
            freeze = state == null ? 0 : state.phase();
        } else {
            var def = HaloJsonLoader.getDefinition(inst.getDefinitionId()).orElse(null);
            freeze = inst.currentAnimTime(def == null ? null : def.startupAnimation().orElse(null));
        }
        if (inst == null) return;
        inst.setHiddenByState(false);
        inst.setTransitionState(HaloTransitionState.ENDING);
        inst.startTransition(freeze);
        if (state != null && state.transitionActive() && !state.groups().isEmpty()) inst.setHideVisuals(state.groups());
    }
    public static void handleHello() { HaloPhaseTracker.getInstance().transitionToMultiplayer(); }
    public static void sendDefsReport() {
        if (Minecraft.getInstance().getConnection() == null) return;
        if (!HaloNetwork.CHANNEL.isRemotePresent(Minecraft.getInstance().getConnection().getConnection())) return;
        Set<ResourceLocation> ids = HaloJsonLoader.getDefinitions().keySet();
        if (!ids.isEmpty()) HaloNetwork.CHANNEL.sendToServer(new HaloNetwork.DefsReportMessage(ids));
    }
}
