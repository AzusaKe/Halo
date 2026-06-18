package network.azusake.halo.data;

/**
 * Controls how the halo model's rotation around its normal axis behaves.
 *
 * <p>In both modes the halo's normal (definition -Y) always points toward
 * the entity's head.  The difference is only in the remaining degree of
 * freedom — the spin <em>around</em> the normal axis.</p>
 *
 * <ul>
 *   <li><b>LOCKED</b> — the spin is locked to the player's horizontal
 *       look direction.  An arrow drawn on the halo will maintain a
 *       fixed angle relative to where the player is looking.</li>
 *   <li><b>FREE</b> — the spin is controlled independently by damping
 *       and animation curves, not by the player's look direction.</li>
 * </ul>
 */
public enum OrientationMode {
    LOCKED,
    FREE
}
