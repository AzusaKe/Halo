package network.azusake.halo.config;

/**
 * Mod-level configuration for the Halo mod, persisted to
 * {@code config/halo-azusake/halo_mod_config.json}.
 *
 * <p>This is the low-level, file-backed configuration for the halo command
 * system and experimental compatibility switches.  It is deliberately
 * separate from {@link HaloConfig}, which holds the runtime rendering
 * parameters tuned in-game via {@code /halo config} and is never persisted.</p>
 *
 * <p>New mod-level settings should be added as fields here.  Unknown keys in
 * existing config files are ignored so older files keep working.</p>
 */
public class HaloModConfig {

    private static final double[] ZERO_YSM_HEAD_OFFSET = {0.0, 0.0, 0.0};

    /** Permission level required by the {@code /halo} command tree.  Clamped to [0, 4]. */
    private int commandPermissionLevel = 2;

    /**
     * Experimental YSM 2.6.5 render-anchor capture. Enabled by default after
     * compatibility validation; users may opt out in halo_mod_config.json.
     */
    private boolean experimentalYsmAnchorEnabled = true;

    /**
     * Head-local offset [right, up, back] in blocks, applied after YSM's Head
     * locator hierarchy.  The zero vector anchors directly at the locator.
     */
    private double[] experimentalYsmHeadLocalOffset = ZERO_YSM_HEAD_OFFSET.clone();

    /** @return the permission level required by {@code /halo} (default 2) */
    public int getCommandPermissionLevel() {
        return commandPermissionLevel;
    }

    /** @return whether the experimental YSM 2.6.5 anchor capture is enabled */
    public boolean isExperimentalYsmAnchorEnabled() {
        return experimentalYsmAnchorEnabled;
    }

    public void setExperimentalYsmAnchorEnabled(boolean value) {
        this.experimentalYsmAnchorEnabled = value;
    }

    /**
     * @return a defensive copy of the YSM Head-local [right, up, back] offset
     */
    public double[] getExperimentalYsmHeadLocalOffset() {
        if (!isValidOffset(experimentalYsmHeadLocalOffset)) {
            return ZERO_YSM_HEAD_OFFSET.clone();
        }
        return experimentalYsmHeadLocalOffset.clone();
    }

    /**
     * Set the YSM Head-local offset.  Invalid values are normalized to zero;
     * the config store emits the user-facing warning while loading a file.
     */
    public void setExperimentalYsmHeadLocalOffset(double[] value) {
        this.experimentalYsmHeadLocalOffset = isValidOffset(value)
            ? value.clone()
            : ZERO_YSM_HEAD_OFFSET.clone();
    }

    /** Validate and normalize the Gson-populated raw value. */
    boolean validateExperimentalYsmHeadLocalOffset() {
        if (isValidOffset(experimentalYsmHeadLocalOffset)) {
            return true;
        }
        experimentalYsmHeadLocalOffset = ZERO_YSM_HEAD_OFFSET.clone();
        return false;
    }

    /**
     * Set the permission level required by {@code /halo}, clamped to {@code [0, 4]}
     * (0 = every player, 4 = server owner/console only).
     */
    public void setCommandPermissionLevel(int value) {
        this.commandPermissionLevel = Math.max(0, Math.min(4, value));
    }

    private static boolean isValidOffset(double[] value) {
        return value != null
            && value.length == 3
            && Double.isFinite(value[0])
            && Double.isFinite(value[1])
            && Double.isFinite(value[2]);
    }
}
