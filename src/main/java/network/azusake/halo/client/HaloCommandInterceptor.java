package network.azusake.halo.client;

/**
 * Abstraction layer for intercepting {@code /halo} commands before they reach
 * the server.
 *
 * <h3>Purpose</h3>
 * <p>The core logic ({@link HaloPhaseTracker}, {@link HaloLocalCommandHandler},
 * {@link HaloLocalManager}) is loader-agnostic.  This interface isolates the
 * loader-specific mechanism that actually hooks into the command pipeline.</p>
 *
 * <h3>Contract</h3>
 * <p>Implementations <em>must</em> follow this flow in every command executor:</p>
 * <ol>
 *   <li>If {@link HaloPhaseTracker#shouldIntercept()} is {@code true}
 *       → call {@link HaloLocalCommandHandler#handle(String)} and display the
 *       result locally.  Do <strong>not</strong> forward to the server.</li>
 *   <li>Otherwise (singleplayer or MULTIPLAYER phase)
 *       → forward the raw command string to the server via the loader's
 *       command-send mechanism (e.g. {@code client.getNetworkHandler().sendCommand(cmd)}).</li>
 * </ol>
 *
 * <h3>Multi-loader support</h3>
 * <p>To port to a new loader, implement this interface using that loader's
 * client-command API (or Mixin-based interception).  No core logic changes
 * are required.</p>
 *
 * @see FabricHaloCommandInterceptor
 */
public interface HaloCommandInterceptor {

    /**
     * Register the client-side {@code /halo} command tree.
     *
     * <p>Must be called during client initialisation.  Safe to call multiple
     * times — implementations should guard against double registration.</p>
     */
    void register();

    /**
     * Whether {@link #register()} has been called (and not unregistered).
     */
    boolean isRegistered();
}
