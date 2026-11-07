package network.azusake.halo.compat.flashback;

/** Successful optional transformations, separate from ordinary Halo runtime state. */
public final class FlashbackHooks {
    private static final java.util.Set<String> APPLIED=java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static void applied(String target){APPLIED.add(target);}
    public static boolean has(String target){return APPLIED.contains(target);}
    private FlashbackHooks(){}
}
