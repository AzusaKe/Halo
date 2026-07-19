package network.azusake.halo.data;

/**
 * Damping parameters controlling how smoothly a halo follows its anchor entity.
 *
 * @param linearFactor               k: 0 = instant snap, 1 = never move (linear interpolation weight)
 * @param angularFactor              angular interpolation weight (same semantics)
 * @param maxLinearDistance           S: maximum linear offset in blocks before hard-clamping
 * @param maxAngularDegrees           maximum angular offset in degrees before hard-clamping
 * @param allowAngularMomentum       when true, the orientation from LOCKED/FREE is treated as a
 *                                   target and damped with rotational inertia (SYNC mode unaffected)
 * @param angularMomentumFactor      interpolation weight for angular momentum damping (0 = frozen, 1 = instant)
 * @param maxAngularMomentumDegrees  maximum angular momentum deviation in degrees before hard-clamping
 */
public record HaloDampingConfig(
    double linearFactor,
    double angularFactor,
    double maxLinearDistance,
    double maxAngularDegrees,
    boolean allowAngularMomentum,
    double angularMomentumFactor,
    double maxAngularMomentumDegrees
) {}
