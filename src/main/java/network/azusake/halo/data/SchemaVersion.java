package network.azusake.halo.data;

/**
 * Semantic version (major.minor.patch) for halo definition schema versioning.
 * Supports comparison via {@link Comparable} for version-range dispatch.
 *
 * <h3>Versioning Policy</h3>
 * <ul>
 *   <li><b>patch</b>: backward-compatible fixes (field default changes, bug fixes)</li>
 *   <li><b>minor</b>: backward-compatible additions (new optional fields, new primitive types)</li>
 *   <li><b>major</b>: breaking changes (removed fields, restructured formats)</li>
 * </ul>
 * <p>When a definition's version exceeds {@link #CURRENT}, the parser logs a warning
 * and attempts best-effort parsing — unrecognized fields/structures are silently
 * skipped by Gson, but rendering may fail if the format changed incompatibly.</p>
 *
 * <p>See {@link network.azusake.halo.json.HaloDefinitionDeserializer} for the
 * version-branching implementation strategy.</p>
 *
 * @param major major version — incremented for breaking changes
 * @param minor minor version — incremented for backward-compatible additions
 * @param patch patch version — incremented for fixes
 */
public record SchemaVersion(int major, int minor, int patch) implements Comparable<SchemaVersion> {

    /** Current schema version supported by this mod build. */
    public static final SchemaVersion CURRENT = new SchemaVersion(1, 0, 10);

    /** Parse a version string like "1.0.10" into a SchemaVersion. */
    public static SchemaVersion parse(String raw) {
        String[] parts = raw.trim().split("\\.");
        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException("Invalid schema version format: " + raw + " (expected major.minor or major.minor.patch)");
        }
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        return new SchemaVersion(major, minor, patch);
    }

    @Override
    public int compareTo(SchemaVersion other) {
        int cmp = Integer.compare(this.major, other.major);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(this.minor, other.minor);
        if (cmp != 0) return cmp;
        return Integer.compare(this.patch, other.patch);
    }

    /** Returns true if this version is strictly older than the other. */
    public boolean isBefore(SchemaVersion other) {
        return this.compareTo(other) < 0;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
