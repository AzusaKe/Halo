package network.azusake.halo.network;

import net.minecraft.client.Minecraft;
import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.client.HaloScepterScreen;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.platform.HaloClientState;
import java.util.*;

/** Client-only endpoints for Forge network messages. */
public final class HaloNetworkClient {
    private HaloNetworkClient() {}
    public static void registerReceivers(){HaloNetwork.register();}
    public static void handleSync(Map<UUID,Identifier> values){HaloClientState.get().replaceAllClientHalos(values);}
    public static void handleUpdate(UUID uuid,boolean attach,Identifier id){if(attach)HaloClientState.get().attach(uuid,id,true);else HaloClientState.get().hide(uuid,id);}
    public static void handleHello(){HaloPhaseTracker.getInstance().transitionToMultiplayer();}
    public static void handleScepterOpen(int id,UUID uuid,String name){Minecraft.getInstance().setScreen(new HaloScepterScreen(id,uuid,name));}
    public static void handleScepterClose(){if(Minecraft.getInstance().screen instanceof HaloScepterScreen)Minecraft.getInstance().setScreen(null);}
    public static void sendScepterSelection(Identifier id){if(canSend())HaloNetwork.CHANNEL.sendToServer(new HaloNetwork.ScepterSelectMessage(id));}
    public static void sendScepterClose(){if(canSend())HaloNetwork.CHANNEL.sendToServer(new HaloNetwork.ScepterCloseMessage());}
    public static void sendScepterRemoveSelf(){if(canSend())HaloNetwork.CHANNEL.sendToServer(new HaloNetwork.ScepterRemoveSelfMessage());}
    public static void sendDefsReport(){if(canSend())HaloNetwork.CHANNEL.sendToServer(new HaloNetwork.DefsReportMessage(Set.copyOf(HaloJsonLoader.getDefinitions().keySet())));}
    private static boolean canSend(){var c=Minecraft.getInstance().getConnection();return c!=null&&HaloNetwork.CHANNEL.isRemotePresent(c.getConnection());}
}
