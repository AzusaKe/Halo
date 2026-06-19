package network.azusake.halo.data;

/**
 * Controls how the halo model's rotation around its normal axis behaves.
 *
 * <p>In all modes the halo's normal (definition -Y) always points toward
 * the entity's head.  The difference is only in the remaining degree of
 * freedom — the spin <em>around</em> the normal axis.</p>
 *
 * <ul>
 *   <li><b>LOCKED</b> — the spin is locked to a compass-like pole derived
 *       from the head's orientation.  An arrow on the halo maintains a
 *       consistent angle relative to the player's head-up direction.</li>
 *   <li><b>FREE</b> — the spin is controlled independently by damping
 *       and animation curves, not by the player's look direction.</li>
 *   <li><b>SYNC</b> — on the first frame, the halo captures its relative
 *       orientation w.r.t. the head (same as LOCKED).  From then on, the
 *       halo's full 3-D orientation is slaved to the head: whenever the
 *       head turns, the halo turns by the same amount.  A configurable
 *       angular offset can be applied on top.</li>
 * </ul>
 */
public enum OrientationMode {
    LOCKED,
    FREE,
    SYNC
}
