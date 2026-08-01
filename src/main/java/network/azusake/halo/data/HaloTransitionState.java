package network.azusake.halo.data;

/**
 * Client-side halo transition state for animation lifecycle management.
 *
 * <p>Transitions are driven exclusively by the renderer:</p>
 * <pre>
 *   STARTING ──(animation complete)──> NORMAL
 *   NORMAL   ──(explicit state change)──> STARTING or ENDING
 *   ENDING   ──(animation complete)──> NULL (instance removed)
 * </pre>
 *
 * <p>Explicit state changes that trigger transitions:</p>
 * <ul>
 *   <li>{@code /halo show} command → STARTING</li>
 *   <li>{@code /halo hide} command → ENDING</li>
 *   <li>Sleep/invisibility state change → STARTING or ENDING (detected by renderer)</li>
 * </ul>
 *
 * <p>Entering a world (initial sync) sets all halos to NORMAL — no animation plays.</p>
 */
public enum HaloTransitionState {

    /** Normal rendering, no transition animation. */
    NORMAL,

    /** Startup animation is playing. */
    STARTING,

    /** Shutdown animation is playing. */
    ENDING,

    /** No halo — reached after ENDING animation completes. Instance will be removed. */
    NULL
}
