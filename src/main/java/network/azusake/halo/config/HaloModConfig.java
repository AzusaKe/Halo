package network.azusake.halo.config;

/**
 * Mod-level configuration for the Halo mod, persisted to
 * {@code config/halo-azusake/halo_mod_config.json}.
 *
 * <p>This is the low-level, file-backed configuration for the halo command
 * system.  It is deliberately separate from {@link HaloConfig}, which holds the runtime rendering
 * parameters tuned in-game via {@code /halo config} and is never persisted.</p>
 *
 * <p>New mod-level settings should be added as fields here.  Unknown keys in
 * existing config files are ignored so older files keep working.</p>
 */
public class HaloModConfig {

    /** Permission level required by the {@code /halo} command tree.  Clamped to [0, 4]. */
    private int commandPermissionLevel = 2;

    /** @return the permission level required by {@code /halo} (default 2) */
    public int getCommandPermissionLevel() {
        return commandPermissionLevel;
    }

    /**
     * Set the permission level required by {@code /halo}, clamped to {@code [0, 4]}
     * (0 = every player, 4 = server owner/console only).
     */
    public void setCommandPermissionLevel(int value) {
        this.commandPermissionLevel = Math.max(0, Math.min(4, value));
    }

}
