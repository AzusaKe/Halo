package network.azusake.halo.compat.ysm;

import net.minecraftforge.fml.loading.LoadingModList;

import java.util.Optional;

/** Version gate shared by the optional Mixin plugin and diagnostics. */
public final class YsmVersionGate {

    private YsmVersionGate() {
    }

    public static boolean isSupportedVersion(String version) {
        return YsmV265Symbols.SUPPORTED_VERSION.equals(version);
    }

    public static Optional<String> installedVersion() {
        LoadingModList loadingMods = LoadingModList.get();
        if (loadingMods == null) {
            return Optional.empty();
        }
        var modFile = loadingMods.getModFileById(YsmV265Symbols.MOD_ID);
        if (modFile == null) {
            return Optional.empty();
        }
        return modFile.getMods().stream()
            .filter(mod -> YsmV265Symbols.MOD_ID.equals(mod.getModId()))
            .map(mod -> mod.getVersion().toString())
            .findFirst();
    }

    public static boolean isSupportedInstalledVersion() {
        return installedVersion().map(YsmVersionGate::isSupportedVersion).orElse(false);
    }
}
