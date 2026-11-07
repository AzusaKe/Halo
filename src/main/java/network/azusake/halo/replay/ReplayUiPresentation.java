package network.azusake.halo.replay;

import network.azusake.halo.api.v2.replay.ReplayFrameTime;
import network.azusake.halo.api.v2.replay.ReplayPlaybackSession;
import network.azusake.halo.api.v2.replay.ReplayUiState;

/** Local screen visibility, independent of the authoritative UI track in core. */
final class ReplayUiPresentation {
    private long epoch = -1;
    private long dismissedSession = -1;

    boolean shouldShow(ReplayUiState state, boolean playback, ReplayPlaybackSession.Mode mode,
                       ReplayFrameTime frame, boolean enabled) {
        if (!playback) {
            reset();
            return false;
        }
        // Restore callbacks precede the completed frame; do not open intermediate seek UI.
        if (frame == null) return false;
        if (epoch != frame.epoch()) {
            epoch = frame.epoch();
            dismissedSession = -1;
        }
        if (!state.open()) dismissedSession = -1;
        return enabled && mode == ReplayPlaybackSession.Mode.PLAYBACK
            && state.open() && state.session() != dismissedSession;
    }

    void dismiss(long session) {
        dismissedSession = session;
    }

    void reset() {
        epoch = -1;
        dismissedSession = -1;
    }
}
