package network.azusake.halo.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameSubmissionTest {
    @Test void repeatedWorldPassesShareOneResultWithoutAdvancingOrDrawingTwice() {
        var frames = new FrameSubmission<Object>();
        Object output = new Object();
        assertTrue(frames.begin(1));
        frames.publish(output);
        assertFalse(frames.begin(1));
        assertSame(output, frames.claimEarly());
        assertNull(frames.claimEarly());
        assertSame(output, frames.claimLate());
        assertNull(frames.claimLate());
        assertFalse(frames.begin(1));
    }

    @Test void interruptedFramesAndWorldChangesCannotReplayOldGeometry() {
        var frames = new FrameSubmission<Object>();
        frames.begin(1);
        frames.publish(new Object());
        assertTrue(frames.begin(2));
        assertNull(frames.claimLate());
        frames.publish(new Object());
        frames.clear();
        assertNull(frames.claimEarly());
        assertNull(frames.claimLate());
        assertTrue(frames.begin(2));
    }
}
