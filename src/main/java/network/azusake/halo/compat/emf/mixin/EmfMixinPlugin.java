package network.azusake.halo.compat.emf.mixin;

import network.azusake.halo.compat.emf.Emf262Symbols;
import network.azusake.halo.compat.emf.EmfAbiDetector;
import network.azusake.halo.compat.emf.EmfCompatDiagnostics;
import network.azusake.halo.compat.emf.EmfVersionGate;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;
import java.util.Optional;

/** Enables EMF hooks only after version and actual ABI checks pass. */
public final class EmfMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("halo");

    private boolean apply;
    private EmfAbiDetector.Namespace namespace;

    @Override
    public void onLoad(String mixinPackage) {
        Optional<String> installed = Optional.empty();
        try {
            installed = EmfVersionGate.installedVersion();
            if (installed.isEmpty()) {
                apply = false;
                return;
            }
            if (!EmfVersionGate.isSupportedVersion(installed.get())) {
                apply = false;
                String reason = "EMF " + installed.get()
                    + " 低于最低支持版本 " + Emf262Symbols.MIN_SUPPORTED_VERSION;
                EmfCompatDiagnostics.reportIncompatible(reason);
                LOGGER.warn("[EMF Compat] installed EMF version {} is below minimum {}; capture disabled",
                    installed.get(), Emf262Symbols.MIN_SUPPORTED_VERSION);
                return;
            }

            EmfAbiDetector.Result abi = EmfAbiDetector.inspect();
            apply = abi.compatible();
            namespace = abi.namespace();
            if (!apply) {
                EmfCompatDiagnostics.reportIncompatible(abi.detail());
                LOGGER.warn("[EMF Compat] EMF ABI is incompatible; capture disabled: {}", abi.detail());
            } else {
                LOGGER.info("[EMF Compat] verified EMF {} detected; head capture hook enabled for {} ABI",
                    installed.get(), namespace.name().toLowerCase());
            }
        } catch (Throwable error) {
            apply = false;
            namespace = null;
            if (installed.isPresent()) {
                EmfCompatDiagnostics.reportIncompatible(error.getClass().getSimpleName()
                    + ": " + error.getMessage());
            }
            LOGGER.warn("[EMF Compat] failed to inspect installed EMF ABI; capture disabled: {}",
                error.getMessage());
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!apply || namespace == null) {
            return false;
        }
        if (mixinClassName.endsWith("EmfModelPartVanillaNameMixin")) {
            return true;
        }
        if (mixinClassName.endsWith("EmfModelPartNamedMixin")) {
            return namespace == EmfAbiDetector.Namespace.NAMED;
        }
        if (mixinClassName.endsWith("EmfModelPartIntermediaryMixin")) {
            return namespace == EmfAbiDetector.Namespace.INTERMEDIARY;
        }
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
        if (!Emf262Symbols.MODEL_PART.equals(targetClassName)
            || namespace == null
            || (!mixinClassName.endsWith("EmfModelPartNamedMixin")
                && !mixinClassName.endsWith("EmfModelPartIntermediaryMixin"))) {
            return;
        }

        String renderMethod = namespace == EmfAbiDetector.Namespace.NAMED
            ? Emf262Symbols.RENDER_METHOD_NAMED
            : Emf262Symbols.RENDER_METHOD_INTERMEDIARY;
        boolean captureHookPresent = false;
        search:
        for (var method : targetClass.methods) {
            if (!renderMethod.equals(method.name)) {
                continue;
            }
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode invocation
                    && targetClass.name.equals(invocation.owner)
                    && invocation.name.contains("$halo$captureEmfHead")) {
                    captureHookPresent = true;
                    break search;
                }
            }
        }

        if (captureHookPresent) {
            LOGGER.info("[EMF Compat] capture hook injected into EMFModelPart.{}", renderMethod);
        } else {
            EmfCompatDiagnostics.reportIncompatible(
                "Mixin 未能注入 EMFModelPart." + renderMethod);
            LOGGER.warn("[EMF Compat] EMFModelPart loaded, but no capture hook was injected; "
                + "using Halo's normal entity anchors");
        }
    }
}

