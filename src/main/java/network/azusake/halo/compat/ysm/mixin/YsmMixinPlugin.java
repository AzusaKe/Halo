package network.azusake.halo.compat.ysm.mixin;

import network.azusake.halo.compat.ysm.YsmV265Symbols;
import network.azusake.halo.compat.ysm.YsmVersionGate;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Applies the optional YSM Mixin only to the verified release. */
public final class YsmMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("halo");
    private boolean apply;

    @Override public void onLoad(String mixinPackage) {
        try {
            var installed = YsmVersionGate.installedVersion();
            apply = installed.map(YsmVersionGate::isSupportedVersion).orElse(false);
            if (installed.isPresent() && !apply) {
                LOGGER.warn("[YSM Compat] installed YSM version {} is unsupported; expected {}; capture disabled",
                    installed.get(), YsmV265Symbols.SUPPORTED_VERSION);
            }
        } catch (Throwable error) {
            apply = false;
            LOGGER.warn("[YSM Compat] failed to inspect YSM; capture disabled", error);
        }
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return apply && mixinClassName.endsWith("YsmGeoRendererNamedMixin");
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass,
        String mixinClassName, IMixinInfo mixinInfo) {}

    @Override public void postApply(String targetClassName, ClassNode targetClass,
        String mixinClassName, IMixinInfo mixinInfo) {
        if (!YsmV265Symbols.GEO_RENDERER.equals(targetClassName)) return;
        boolean present = false;
        for (var method : targetClass.methods) {
            if (!YsmV265Symbols.RENDER_METHOD.equals(method.name)
                || !YsmV265Symbols.RENDER_DESCRIPTOR_NEOFORGE.equals(method.desc)) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call
                    && targetClass.name.equals(call.owner)
                    && call.name.contains("$halo$captureYsmHead")) {
                    present = true;
                    break;
                }
            }
        }
        if (present) LOGGER.info("[YSM Compat] capture hook injected into verified YSM renderer");
        else LOGGER.warn("[YSM Compat] no capture hook was injected; using Halo fallback");
    }
}
