package network.azusake.halo.compat.replaymod;

/** Successful optional transformations, populated by the Mixin plugin without loading target classes. */
public final class ReplayModHooks {
    private static final java.util.Set<String> APPLIED=java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static void applied(String target){APPLIED.add(target);}
    public static boolean has(String target){return APPLIED.contains(target);}
    private ReplayModHooks(){}
}
