package network.azusake.halo.compat.ysm.mixin;

import network.azusake.halo.compat.ysm.YsmV265Symbols;
import network.azusake.halo.compat.ysm.YsmVersionGate;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Applies YSM bytecode hooks only to the verified 26.1 hotfix artifact. */
public final class YsmMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("halo");
    private boolean apply;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            var installed = YsmVersionGate.installedVersion();
            apply = installed.map(YsmVersionGate::isSupportedVersion).orElse(false);
            if (installed.isPresent() && !apply) {
                LOGGER.warn("[YSM Compat] installed version {} unsupported; expected {}; hook skipped",
                    installed.get(), YsmV265Symbols.SUPPORTED_VERSION);
            } else if (apply) {
                LOGGER.info("[YSM Compat] verified {} hotfix detected; NeoForge capture hook enabled",
                    YsmV265Symbols.SUPPORTED_VERSION);
            }
        } catch (Throwable error) {
            apply = false;
            LOGGER.warn("[YSM Compat] version inspection failed; hook skipped: {}", error.getMessage());
        }
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return apply && (mixinClassName.endsWith("YsmGeoRendererMixin")
            || mixinClassName.endsWith("YsmEntityRenderContextMixin"));
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass,
                                   String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass,
                                    String mixinClassName, IMixinInfo mixinInfo) {
        LOGGER.info("[YSM Compat] common geometry hook applied to {}", targetClassName);
    }
}
