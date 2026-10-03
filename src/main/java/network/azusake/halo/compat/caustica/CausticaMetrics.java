package network.azusake.halo.compat.caustica;

import java.util.concurrent.atomic.LongAdder;
import jdk.jfr.*;

final class CausticaMetrics {
    static final LongAdder geometryBytes=new LongAdder(),textureBytes=new LongAdder(),blasBuilds=new LongAdder();
    static final LongAdder liveBuffers=new LongAdder(),liveImages=new LongAdder();
    @Name("halo.CausticaFrame") @Label("Halo retained scene update") @Category("Halo")
    static final class Frame extends Event {
        @Label("Instances") int instances;
        @Label("Scene edits") int edits;
        @Label("Geometry upload bytes total") long geometryUploadBytes;
        @Label("Texture upload bytes total") long textureUploadBytes;
        @Label("BLAS preparations total") long blasPreparations;
        @Label("Live buffers") long buffers;
        @Label("Live texture images") long images;
    }
    static Frame begin() { var e=new Frame();e.begin();return e; }
    static void end(Frame e,int instances,int edits) {
        e.instances=instances;e.edits=edits;e.geometryUploadBytes=geometryBytes.sum();
        e.textureUploadBytes=textureBytes.sum();e.blasPreparations=blasBuilds.sum();
        e.buffers=liveBuffers.sum();e.images=liveImages.sum();e.commit();
    }
}
