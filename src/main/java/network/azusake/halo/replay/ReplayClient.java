package network.azusake.halo.replay;

import net.minecraft.client.MinecraftClient;
import network.azusake.halo.api.v2.replay.*;
import network.azusake.halo.client.HaloScepterScreen;
import network.azusake.halo.core.runtime.ReplayHost;
import network.azusake.halo.platform.HaloClientState;
import org.slf4j.LoggerFactory;

/** Client composition and UI execution. No recorder-specific classes belong here. */
public final class ReplayClient {
    private static ReplayHost host;
    private static boolean preparing;
    private static final java.util.List<Runnable> preparations=new java.util.ArrayList<>();
    private ReplayClient() {}
    public static void initialize() {
        if(host==null) host=new ReplayHost(HaloClientState.get(),ReplayClient::displayUi,
            message->LoggerFactory.getLogger("halo").warn("[Halo replay] {}",message));
    }
    public static boolean isPlayback() { return host!=null && host.isPlayback(); }
    public static boolean ownsState() { return host!=null && host.ownsState(); }
    public static void setPreparing(boolean value){preparing=value;}
    public static boolean isReady(){return !preparing;}
    public static long uiOpened(java.util.UUID target,String name) { return host==null?0:host.uiOpened(target,name); }
    public static void uiClosed(long session) { if(host!=null)host.uiClosed(session); }
    public static void disconnect() { if(host!=null)host.disconnect(); }
    public static void registerPreparation(Runnable preparation) { preparations.add(java.util.Objects.requireNonNull(preparation)); }
    public static void prepare() {
        for(var preparation:java.util.List.copyOf(preparations))preparation.run();
        if(host!=null && host.isPlayback())displayUi(host.uiState());
    }
    public static void shutdown() { if(host!=null){host.close();host=null;} }
    private static void displayUi(ReplayUiState state) {
        var client=MinecraftClient.getInstance();
        boolean show=state.open() && host!=null && host.isPlayback() && host.mode()==ReplayPlaybackSession.Mode.PLAYBACK;
        if(client.currentScreen instanceof HaloScepterScreen screen) {
            if(show && screen.isReplaySession(state.session()))return;
            client.setScreen(null);
        }
        // Do not replace a recorder's loading, progress or error screen.
        if(show && client.currentScreen==null)client.setScreen(new HaloScepterScreen(state));
    }
}
