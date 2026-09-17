package network.azusake.halo.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeshIndexUploadTest {
    @Test void flatAndExpandedBuffersTrackTheirOwnResidentOrderAndWinding() {
        var flat = new MeshIndexUpload(); var lit = new MeshIndexUpload();
        assertFalse(flat.matches(1, false)); assertFalse(lit.matches(1, false));
        flat.uploaded(1, false);
        assertTrue(flat.matches(1, false)); assertFalse(lit.matches(1, false));
        lit.uploaded(2, true);
        assertTrue(flat.matches(1, false)); assertFalse(flat.matches(2, true));
        assertTrue(lit.matches(2, true)); assertFalse(lit.matches(2, false));
        // A -> B -> A in a shared EBO, also representative of interleaved world/GUI draws.
        flat.uploaded(2, false);
        assertFalse(flat.matches(3, false));
        flat.uploaded(3, true);
        assertFalse(flat.matches(3, false)); assertTrue(flat.matches(3, true));
        assertFalse(new MeshIndexUpload().matches(3, true)); // reload/replacement cannot reuse residency
    }

    @Test void preparationWithoutACompletedUploadDoesNotMarkTheEboAsResident() {
        var upload = new MeshIndexUpload();
        upload.uploaded(7, false);
        assertFalse(upload.matches(8, false));
        assertFalse(upload.matches(8, false)); // failed/skipped upload must be retried
        assertTrue(upload.matches(7, false));
    }
}
