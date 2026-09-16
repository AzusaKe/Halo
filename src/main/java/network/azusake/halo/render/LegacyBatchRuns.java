package network.azusake.halo.render;

import java.util.List;
import java.util.Objects;
import network.azusake.halo.core.render.DrawBatch;
import network.azusake.halo.core.render.MaterialState;

/** Adjacent-only runs. No material sorting, vertex copying, or frame-spanning state cache. */
final class LegacyBatchRuns {
    private LegacyBatchRuns() {}
    static int end(List<DrawBatch> batches, int start) {
        int end = start + 1;
        while (end < batches.size() && compatible(batches.get(start), batches.get(end))) end++;
        return end;
    }
    static boolean compatible(DrawBatch a, DrawBatch b) {
        return a.material() instanceof MaterialState.Legacy && b.material() instanceof MaterialState.Legacy
            && a.topology() == b.topology() && Objects.equals(a.texture(), b.texture())
            && a.textured() == b.textured() && a.cull() == b.cull() && a.blend() == b.blend()
            && a.depthTest() == b.depthTest() && a.depthWrite() == b.depthWrite()
            && Float.compare(a.red(), b.red()) == 0 && Float.compare(a.green(), b.green()) == 0
            && Float.compare(a.blue(), b.blue()) == 0 && Float.compare(a.alpha(), b.alpha()) == 0
            && a.light().equals(b.light()) && a.directionalLighting() == b.directionalLighting();
    }
}
