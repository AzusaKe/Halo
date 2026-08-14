package network.azusake.halo.animation;

/**
 * Decides which transition config plays for a transition direction and
 * whether its queue must be reversed.
 *
 * <p>Rules (F8): a startup always plays its own config forward.  A shutdown
 * plays its own config forward when defined; otherwise the startup config is
 * reversed as the fade-out fallback.  With neither config there is no
 * transition at all.</p>
 */
public final class TransitionResolver {

    /** The chosen config plus whether the queue must be reversed. */
    public record Resolution(StartupAnimationConfig config, boolean reversed) {}

    private TransitionResolver() {}

    /**
     * @param isStartup    {@code true} for a startup transition, {@code false} for shutdown
     * @param startupConfig  the definition's startup config (may be null)
     * @param shutdownConfig the definition's shutdown config (may be null)
     * @return the resolution, or {@code null} when no transition should play
     */
    public static Resolution resolve(boolean isStartup,
                                     StartupAnimationConfig startupConfig,
                                     StartupAnimationConfig shutdownConfig) {
        if (isStartup) {
            return startupConfig != null ? new Resolution(startupConfig, false) : null;
        }
        if (shutdownConfig != null) {
            return new Resolution(shutdownConfig, false);
        }
        return startupConfig != null ? new Resolution(startupConfig, true) : null;
    }
}
