package network.azusake.halo.data;

/**
 * Immutable snapshot of the per-group visual values the renderer last drew
 * during a transition (offset, scale, alpha, rotation).
 *
 * <p>When a halo is hidden mid-transition (e.g. during its startup), the
 * shutdown head must start from these on-screen values — not from the idle
 * animation — or the fade-out would visibly jump.  The renderer records them
 * every transition frame; the network layer stashes them on the fresh ENDING
 * instance so the shutdown queues can be head-patched to match.</p>
 *
 * <p>{@code rotation} holds the transition's own applied rotation as YXZ Euler
 * degrees (the group's base rotation is applied identically before both
 * startup and shutdown, so it is not part of the snapshot).</p>
 *
 * @param offset         3-component offset as applied by the transition (x, y, z)
 * @param scale          3-component scale as applied by the transition
 * @param alpha          alpha multiplier as applied by the transition
 * @param rotation       YXZ Euler degrees applied by the transition (yaw, pitch, roll)
 */
public record GroupVisualSnapshot(float[] offset, float[] scale, float alpha,
                                  float[] rotation) {}
