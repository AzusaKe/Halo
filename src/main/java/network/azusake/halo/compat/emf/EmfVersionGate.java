package network.azusake.halo.compat.emf;

import net.minecraftforge.fml.loading.LoadingModList;

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
        LoadingModList loadingMods = LoadingModList.get();
        if (loadingMods == null) return Optional.empty();
        var modFile = loadingMods.getModFileById(Emf1201Symbols.MOD_ID);
        if (modFile == null) return Optional.empty();
        return modFile.getMods().stream()
            .filter(mod -> Emf1201Symbols.MOD_ID.equals(mod.getModId()))
            .map(mod -> mod.getVersion().toString())
            .findFirst();
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
