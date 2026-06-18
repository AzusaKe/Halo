package network.azusake.halo.shape;

/**
 * Optional pulse animation applied to a glow layer.
 *
 * @param amplitude peak deviation from the base alpha (0–1 range recommended)
 * @param frequency cycles per second
 * @param phase     initial phase offset in radians
 */
public record PulseConfig(
    float amplitude,
    float frequency,
    float phase
) {}
