package network.azusake.halo.platform;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderFrameClockTest {
    @Test void videoTimeIgnoresSlowRenderingAndRepeatedSamples() {
        for (int fps : new int[]{24, 30, 60, 120}) {
            var clock = new RenderFrameClock();
            Object export = new Object();
            var start = clock.sample(10_000, 1_000_000_000L, export, 1, fps);
            for (int frame = 2; frame <= fps + 1; frame++) {
                long wall = frame * 5_000_000_000L;
                var time = clock.sample(frame * 5000L, wall, export, frame, fps);
                assertEquals(time, clock.sample(frame * 5000L + 2000, wall + 2_000_000_000L,
                    export, frame, fps));
            }
            var end = clock.sample(999_999, 999_999_000_000L, export, fps + 1, fps);
            assertEquals(1_000_000_000L, end.nanos() - start.nanos());
            assertEquals(1000L, end.millis() - start.millis());
        }
    }

    @Test void exportExitAndRestartKeepAnimationTimeContinuous() {
        var clock = new RenderFrameClock();
        Object export = new Object();
        var normal = clock.sample(10_000, 1_000_000_000L, null, 0, 0);
        var first = clock.sample(20_000, 11_000_000_000L, export, 10, 30);
        assertEquals(normal, first);
        var second = clock.sample(30_000, 21_000_000_000L, export, 40, 30);
        assertEquals(first.millis() + 1000, second.millis());
        assertEquals(second, clock.sample(40_000, 31_000_000_000L, null, 0, 0));
        var resumed = clock.sample(40_050, 31_050_000_000L, null, 0, 0);
        assertEquals(second.millis() + 50, resumed.millis());
        assertEquals(resumed, clock.sample(50_000, 41_000_000_000L, export, 1, 60));
        var restarted = clock.sample(60_000, 51_000_000_000L, export, 61, 60);
        assertEquals(resumed.millis() + 1000, restarted.millis());
        assertEquals(restarted, clock.sample(70_000, 61_000_000_000L, export, 0, 60));
    }

    @Test void withoutVideoExportTimeFollowsNormalElapsedTime() {
        var clock = new RenderFrameClock();
        var start = clock.sample(10_000, 1_000_000_000L, null, 0, 0);
        var next = clock.sample(10_025, 1_025_000_000L, null, 0, 0);
        assertEquals(25, next.millis() - start.millis());
        assertEquals(25_000_000L, next.nanos() - start.nanos());
    }
}
