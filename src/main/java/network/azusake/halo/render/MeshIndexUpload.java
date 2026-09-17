package network.azusake.halo.render;

/** Resident contents of one dynamic EBO, belonging to one mesh writer and vertex layout. */
final class MeshIndexUpload {
    private boolean uploaded;
    private long revision;
    private boolean mirrored;

    boolean matches(long nextRevision, boolean nextMirrored) {
        return uploaded && revision == nextRevision && mirrored == nextMirrored;
    }

    /** Record only after the upload succeeded. A replacement EBO starts with a new tracker. */
    void uploaded(long nextRevision, boolean nextMirrored) {
        revision = nextRevision;
        mirrored = nextMirrored;
        uploaded = true;
    }
}
