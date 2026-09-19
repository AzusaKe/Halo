package network.azusake.halo.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import network.azusake.halo.core.Vec3d;
import network.azusake.halo.core.render.MeshDraw;
import network.azusake.halo.core.render.TriangleMesh;
import network.azusake.halo.core.render.VisualResources;
import org.joml.Matrix4fc;

/** Applies a host-owned outer view transform to transparent mesh-instance ordering. */
final class MeshDrawViewSorter {
    private MeshDrawViewSorter() {}

    static List<MeshDraw> backToFront(List<MeshDraw> draws, VisualResources resources, Matrix4fc outerView) {
        // A positive Z-only outer transform preserves the core's existing far-to-near order.
        if (outerView.m02() == 0 && outerView.m12() == 0 && outerView.m22() >= 0) return draws;
        int translucent = 0;
        for (MeshDraw draw : draws) if (draw.blend()) translucent++;
        if (translucent < 2) return draws;

        var sorted = new ArrayList<>(draws);
        // TimSort is stable: opaque command order and equal-depth transparent order remain authored.
        sorted.sort(Comparator.comparing(MeshDraw::blend)
            .thenComparingDouble(draw -> draw.blend() ? depth(draw, resources, outerView) : 0));
        return sorted;
    }

    private static float depth(MeshDraw draw, VisualResources resources, Matrix4fc outerView) {
        TriangleMesh mesh = resources.meshes().get(draw.model());
        if (mesh == null) return 0;
        Vec3d center = mesh.center();
        float x = draw.transform(0) * (float) center.x + draw.transform(4) * (float) center.y
            + draw.transform(8) * (float) center.z + draw.transform(12);
        float y = draw.transform(1) * (float) center.x + draw.transform(5) * (float) center.y
            + draw.transform(9) * (float) center.z + draw.transform(13);
        float z = draw.transform(2) * (float) center.x + draw.transform(6) * (float) center.y
            + draw.transform(10) * (float) center.z + draw.transform(14);
        return outerView.m02() * x + outerView.m12() * y + outerView.m22() * z + outerView.m32();
    }
}
