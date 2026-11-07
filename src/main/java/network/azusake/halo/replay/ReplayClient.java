package network.azusake.halo.replay;

import net.minecraft.client.Minecraft;
import network.azusake.halo.api.v2.replay.*;
import network.azusake.halo.client.HaloScepterScreen;
import network.azusake.halo.core.runtime.ReplayHost;
import network.azusake.halo.platform.HaloClientState;
import org.slf4j.LoggerFactory;

/** Client composition and UI execution. No recorder-specific classes belong here. */
public final class ReplayClient {
    private static ReplayHost host;
    private static boolean preparing;
    private static boolean updatingUi;
    private static final ReplayUiPresentation uiPresentation = new ReplayUiPresentation();
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
    public static void disconnect() { if(host!=null)host.disconnect(); uiPresentation.reset(); }
    public static void replayUiDismissed(long session) {
        if (!updatingUi && isPlayback()) uiPresentation.dismiss(session);
    }
    public static void registerPreparation(Runnable preparation) { preparations.add(java.util.Objects.requireNonNull(preparation)); }
    public static void prepare() {
        for(var preparation:java.util.List.copyOf(preparations))preparation.run();
        if(host!=null && host.isPlayback())displayUi(host.uiState());
    }
    public static void shutdown() { if(host!=null){host.close();host=null;} uiPresentation.reset(); }
    private static void displayUi(ReplayUiState state) {
        var client = Minecraft.getInstance();
        boolean playback = isPlayback();
        boolean show = uiPresentation.shouldShow(state, playback,
            playback ? host.mode() : ReplayPlaybackSession.Mode.PLAYBACK,
            playback ? host.frameTime() : null,
            network.azusake.halo.config.HaloModConfigStore.isReplayScepterUiEnabled());
        updatingUi = true;
        try {
            if (client.gui.screen() instanceof HaloScepterScreen screen && screen.isReplayScreen()) {
                if (show && screen.isReplaySession(state.session())) return;
                client.gui.setScreen(null);
            }
            // Never replace recorder progress/error screens or ordinary gameplay selectors.
            if (show && client.gui.screen() == null) client.gui.setScreen(new HaloScepterScreen(state));
        } finally {
            updatingUi = false;
        }
    }
}
