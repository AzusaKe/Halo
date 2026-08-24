package network.azusake.halo.config;

/**
 * Mod-level configuration for the Halo mod, persisted to
 * {@code config/halo-azusake/halo_mod_config.json}.
 *
 * <p>This is the low-level, file-backed configuration for the halo command
 * system (currently the permission level required by {@code /halo}).  It is
 * deliberately separate from {@link HaloConfig}, which holds the runtime
 * rendering parameters tuned in-game via {@code /halo config} and is never
 * persisted.</p>
 *
 * <p>New mod-level settings should be added as fields here.  Unknown keys in
 * existing config files are ignored so older files keep working.</p>
 */
public class HaloModConfig {

    private static final double[] ZERO_YSM_HEAD_OFFSET = {0.0, 0.0, 0.0};

    /** Permission level required by the {@code /halo} command tree.  Clamped to [0, 4]. */
    private int commandPermissionLevel = 2;

    /** Experimental, version-pinned YSM render-anchor capture. */
    private boolean experimentalYsmAnchorEnabled = false;

    /** Head-local [right, up, back] offset in blocks. */
    private double[] experimentalYsmHeadLocalOffset = ZERO_YSM_HEAD_OFFSET.clone();

    /** @return the permission level required by {@code /halo} (default 2) */
    public int getCommandPermissionLevel() {
        return commandPermissionLevel;
    }

    public boolean isExperimentalYsmAnchorEnabled() {
        return experimentalYsmAnchorEnabled;
    }

    public void setExperimentalYsmAnchorEnabled(boolean value) {
        experimentalYsmAnchorEnabled = value;
    }

    public double[] getExperimentalYsmHeadLocalOffset() {
        return isValidOffset(experimentalYsmHeadLocalOffset)
            ? experimentalYsmHeadLocalOffset.clone()
            : ZERO_YSM_HEAD_OFFSET.clone();
    }

    public void setExperimentalYsmHeadLocalOffset(double[] value) {
        experimentalYsmHeadLocalOffset = isValidOffset(value)
            ? value.clone()
            : ZERO_YSM_HEAD_OFFSET.clone();
    }

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
        return value != null && value.length == 3
            && Double.isFinite(value[0])
            && Double.isFinite(value[1])
            && Double.isFinite(value[2]);
    }
}
