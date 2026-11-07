package network.azusake.halo.replay;

import java.util.Map;
import java.util.UUID;
import network.azusake.halo.api.v2.replay.*;
import network.azusake.halo.core.runtime.ClientRuntime;
import network.azusake.halo.core.runtime.ReplayHost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayUiPresentationTest {
    private final ReplayUiPresentation view = new ReplayUiPresentation();
    private final ReplayUiState open = new ReplayUiState(7, true, UUID.randomUUID(), "wearer");

    private boolean show(ReplayUiState ui, long epoch, long sample, long source) {
        return view.shouldShow(ui, true, ReplayPlaybackSession.Mode.PLAYBACK,
            new ReplayFrameTime(epoch, sample, source, source, false), true);
    }

    @Test void manuallyClosedUiStaysClosedWhilePausedAndAfterPlaybackResumes() {
        assertTrue(show(open, 1, 1, 100));
        view.dismiss(open.session());
        assertFalse(show(open, 1, 1, 100));
        assertFalse(show(open, 1, 2, 100));
        assertFalse(show(open, 1, 3, 200));
        assertFalse(show(open, 1, 4, 300));
    }

    @Test void anotherRecordedOpenCanDisplayAndItsRecordedCloseHidesIt() {
        assertTrue(show(open, 1, 1, 100));
        view.dismiss(open.session());
        var next = new ReplayUiState(8, true, open.target(), open.targetName());
        assertTrue(show(next, 1, 2, 200));
        assertFalse(show(new ReplayUiState(8, false, open.target(), open.targetName()), 1, 3, 300));
    }

    @Test void seekReevaluatesDismissalEvenAtTheSameSourceTime() {
        assertTrue(show(open, 1, 1, 100));
        view.dismiss(open.session());
        assertFalse(view.shouldShow(open, true, ReplayPlaybackSession.Mode.PLAYBACK, null, true));
        assertTrue(show(open, 2, 2, 100));
    }

    @Test void sameEpochSnapshotRecoveryDoesNotUndoManualDismissal() {
        assertTrue(show(open, 1, 1, 100));
        view.dismiss(open.session());
        assertFalse(view.shouldShow(open, true, ReplayPlaybackSession.Mode.PLAYBACK, null, true));
        assertFalse(show(open, 1, 2, 200));
    }

    @Test void exportNeverShowsTheScreenOrImplicitlyDismissesThePlaybackSession() {
        var frame = new ReplayFrameTime(1, 1, 100, 0, false);
        for (var mode : new ReplayPlaybackSession.Mode[]{ReplayPlaybackSession.Mode.EXPORT_PREPARING,
            ReplayPlaybackSession.Mode.EXPORTING}) {
            assertFalse(view.shouldShow(open, true, mode, frame, true));
        }
        assertTrue(show(open, 1, 1, 100));
        view.dismiss(open.session());
        assertFalse(view.shouldShow(open, true, ReplayPlaybackSession.Mode.EXPORTING, frame, true));
        assertFalse(show(open, 1, 1, 100));
    }

    @Test void leavingPlaybackClearsLocalSuppressionForANewPlayback() {
        assertTrue(show(open, 1, 1, 100));
        view.dismiss(open.session());
        assertFalse(view.shouldShow(open, false, ReplayPlaybackSession.Mode.PLAYBACK, null, true));
        assertTrue(show(open, 1, 1, 100));
    }

    @Test void hidingOrDismissingTheScreenNeverChangesTheRecordedUiTrack() {
        try (var host = new ReplayHost(new ClientRuntime(() -> 0L, ignored -> {}), ignored -> {}, ignored -> {})) {
            try (var playback = HaloReplayApi.openPlayback("test:ui").orElseThrow()) {
                playback.restore(new ReplaySnapshot(0, Map.of(), Map.of(), open), 100, 1);
                var frame = new ReplayFrameTime(1, 1, 100, 100, false);
                playback.frame(frame);
                assertFalse(view.shouldShow(host.uiState(), true, host.mode(), host.frameTime(), false));
                assertEquals(open, host.uiState());
                assertTrue(show(host.uiState(), 1, 1, 100));
                view.dismiss(open.session());
                assertFalse(show(host.uiState(), 1, 1, 100));
                assertEquals(open, host.uiState());
                var closed = new ReplayUiState(7, false, open.target(), open.targetName());
                assertTrue(playback.apply(new ReplayEvent.UiChanged(1, closed), 200, 1));
                playback.frame(new ReplayFrameTime(1, 2, 200, 200, false));
                assertEquals(closed, host.uiState());
                assertFalse(show(host.uiState(), 1, 2, 200));
            }
        }
    }
}
