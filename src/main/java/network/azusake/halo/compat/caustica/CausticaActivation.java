package network.azusake.halo.compat.caustica;

/** Startup policy shared with the early Mixin plugin; never resolves optional mod or game classes. */
public final class CausticaActivation {
    public static final boolean ENABLED=Boolean.parseBoolean(System.getProperty("halo.caustica.experimental","true"));
    private CausticaActivation() { }
}
