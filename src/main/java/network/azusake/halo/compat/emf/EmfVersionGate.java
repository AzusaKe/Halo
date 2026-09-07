package network.azusake.halo.compat.emf;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.Optional;

/** Minimum-version check shared by the optional EMF Mixin plugin and diagnostics. */
public final class EmfVersionGate {

    private static final int[] MIN_SUPPORTED = {3, 1, 1};

    private EmfVersionGate() {
    }

    public static boolean isSupportedVersion(String version) {
        int[] parsed = parse(version);
        return parsed != null && compare(parsed, MIN_SUPPORTED) >= 0;
    }

    public static Optional<String> installedVersion() {
        return FabricLoader.getInstance()
            .getModContainer(Emf1201Symbols.MOD_ID)
            .map(ModContainer::getMetadata)
            .map(metadata -> metadata.getVersion().getFriendlyString());
    }

    public static boolean isSupportedInstalledVersion() {
        return installedVersion().map(EmfVersionGate::isSupportedVersion).orElse(false);
    }

    private static int[] parse(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }

        String core = version.trim().split("[+-]", 2)[0];
        String[] components = core.split("\\.");
        if (components.length == 0 || components.length > 3) {
            return null;
        }

        int[] parsed = {0, 0, 0};
        try {
            for (int index = 0; index < components.length; index++) {
                if (components[index].isEmpty()) {
                    return null;
                }
                parsed[index] = Integer.parseInt(components[index]);
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int compare(int[] left, int[] right) {
        for (int index = 0; index < 3; index++) {
            int result = Integer.compare(left[index], right[index]);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }
}
