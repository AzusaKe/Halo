package network.azusake.halo.compat.emf;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.Optional;

/** Applies the 3.1.1 lower bound without imposing an upper EMF version limit. */
public final class EmfVersionGate {

    private EmfVersionGate() {
    }

    public static boolean isSupportedVersion(String version) {
        int[] actual = parseCore(version);
        int[] minimum = parseCore(Emf1211Symbols.MIN_SUPPORTED_VERSION);
        if (actual == null || minimum == null) {
            return false;
        }
        for (int index = 0; index < minimum.length; index++) {
            if (actual[index] != minimum[index]) {
                return actual[index] > minimum[index];
            }
        }
        return true;
    }

    public static Optional<String> installedVersion() {
        return FabricLoader.getInstance()
            .getModContainer(Emf1211Symbols.MOD_ID)
            .map(ModContainer::getMetadata)
            .map(metadata -> metadata.getVersion().getFriendlyString());
    }

    private static int[] parseCore(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        String core = version.trim();
        int suffix = core.indexOf('-');
        if (suffix >= 0) {
            core = core.substring(0, suffix);
        }
        suffix = core.indexOf('+');
        if (suffix >= 0) {
            core = core.substring(0, suffix);
        }
        String[] parts = core.split("\\.");
        if (parts.length < 2 || parts.length > 3) {
            return null;
        }
        int[] result = new int[]{0, 0, 0};
        try {
            for (int index = 0; index < parts.length; index++) {
                if (parts[index].isBlank()) {
                    return null;
                }
                result[index] = Integer.parseInt(parts[index]);
            }
            return result;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
