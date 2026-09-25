package network.azusake.halo.compat.ysm.mixin;

import network.azusake.halo.compat.ysm.YsmV265Symbols;
import network.azusake.halo.compat.ysm.YsmVersionGate;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Applies the optional YSM Mixin only to the one verified release. */
public final class YsmMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("halo");
    private boolean apply;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            var installed = YsmVersionGate.installedVersion();
            apply = installed.map(YsmVersionGate::isSupportedVersion).orElse(false);
            if (installed.isPresent() && !apply) {
                LOGGER.warn(
                    "[YSM Compat] installed YSM version {} is unsupported; expected {}; experimental capture disabled",
                    installed.get(), YsmV265Symbols.SUPPORTED_VERSION);
            } else if (apply) {
                LOGGER.info("[YSM Compat] verified YSM {} detected; optional Forge capture hook enabled",
                    YsmV265Symbols.SUPPORTED_VERSION);
            }
        } catch (Throwable error) {
            apply = false;
            LOGGER.warn("[YSM Compat] failed to inspect installed YSM version; optional capture hook disabled: {}",
                error.getMessage());
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!apply) {
            return false;
        }
        return mixinClassName.endsWith("YsmGeoEntityRendererMixin")
            || mixinClassName.endsWith("YsmGeoReplacedEntityRendererMixin");
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        String suffix;
        if (YsmV265Symbols.LIVING_GEO_RENDERER.equals(targetClassName)) {
            suffix = "$halo$captureYsmHead";
        } else if (YsmV265Symbols.ENTITY_GEO_RENDERER.equals(targetClassName)) {
            suffix = "$halo$captureYsmEntityHead";
        } else {
            return;
        }
        boolean captureHookPresent = false;
        search:
        for (MethodNode method : targetClass.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode invocation
                    && invocation.name.contains(suffix)) {
                    captureHookPresent = true;
                    break search;
                }
            }
            if (method.name.contains(suffix)) {
                captureHookPresent = true;
                break search;
            }
        }

        if (captureHookPresent) {
            LOGGER.info("[YSM Compat] Forge capture hook injected into verified YSM renderer");
        } else {
            LOGGER.warn("[YSM Compat] verified YSM renderer loaded, but no capture hook was injected; "
                + "falling back to Halo's normal entity anchors");
        }
    }
}
