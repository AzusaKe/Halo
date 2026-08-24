package network.azusake.halo.compat.ysm;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.Optional;

/** Version gate shared by the optional Mixin plugin and diagnostics. */
public final class YsmVersionGate {

    private YsmVersionGate() {
    }

    public static boolean isSupportedVersion(String version) {
        return YsmV265Symbols.SUPPORTED_VERSION.equals(version);
    }

    public static Optional<String> installedVersion() {
        return FabricLoader.getInstance()
            .getModContainer(YsmV265Symbols.MOD_ID)
            .map(ModContainer::getMetadata)
            .map(metadata -> metadata.getVersion().getFriendlyString());
    }

    public static boolean isSupportedInstalledVersion() {
        return installedVersion().map(YsmVersionGate::isSupportedVersion).orElse(false);
    }
}
