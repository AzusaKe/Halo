package network.azusake.halo.render;

/** Claims the main world stages once per GameRenderer frame, excluding shadow passes. */
final class WorldFrameGate {
    private boolean captured;
    private boolean rendered;

    void beginFrame() { captured = false; rendered = false; }

    boolean capture(boolean mainPass) {
        if (!mainPass || captured) return false;
        captured = true;
        return true;
    }

    boolean render(boolean mainPass) {
        if (!mainPass || !captured || rendered) return false;
        rendered = true;
        return true;
    }
}
