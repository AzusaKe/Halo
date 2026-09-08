package network.azusake.halo.compat.emf;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Checks the loaded EMF class files before the optional mixins are enabled. */
public final class EmfAbiDetector {

    private static final String STRING_DESCRIPTOR = "Ljava/lang/String;";

    private EmfAbiDetector() {
    }

    /**
     * Detect the render method from the class bytes actually visible to the
     * loader.  This intentionally does not trust only the Fabric mapping
     * namespace: the EMF class itself is optional and its Minecraft method
     * references are remapped in production.
     */
    public static Result inspect() {
        Optional<ClassNode> modelPart = readClass(Emf261Symbols.MODEL_PART);
        if (modelPart.isEmpty()) {
            return Result.incompatible("目标类不存在: " + Emf261Symbols.MODEL_PART);
        }

        boolean hasNamedRender = modelPart.get().methods.stream()
            .anyMatch(method -> Emf261Symbols.RENDER_METHOD_NAMED.equals(method.name)
                && Emf261Symbols.RENDER_DESCRIPTOR_NAMED.equals(method.desc));
        boolean hasIntermediaryRender = modelPart.get().methods.stream()
            .anyMatch(method -> Emf261Symbols.RENDER_METHOD_INTERMEDIARY.equals(method.name)
                && Emf261Symbols.RENDER_DESCRIPTOR_INTERMEDIARY.equals(method.desc));

        Optional<Namespace> namespace;
        if (hasNamedRender) {
            namespace = Optional.of(Namespace.NAMED);
        } else if (hasIntermediaryRender) {
            namespace = Optional.of(Namespace.INTERMEDIARY);
        } else {
            return Result.incompatible(
                "目标渲染方法不存在: "
                    + Emf261Symbols.RENDER_METHOD_NAMED
                    + Emf261Symbols.RENDER_DESCRIPTOR_NAMED
                    + " 或 "
                    + Emf261Symbols.RENDER_METHOD_INTERMEDIARY
                    + Emf261Symbols.RENDER_DESCRIPTOR_INTERMEDIARY);
        }

        Optional<ClassNode> vanillaPart = readClass(Emf261Symbols.VANILLA_MODEL_PART);
        if (vanillaPart.isEmpty()) {
            return Result.incompatible("目标类不存在: " + Emf261Symbols.VANILLA_MODEL_PART);
        }
        boolean hasNameField = vanillaPart.get().fields.stream()
            .anyMatch(field -> "name".equals(field.name)
                && STRING_DESCRIPTOR.equals(field.desc));
        if (!hasNameField) {
            return Result.incompatible("EMFModelPartVanilla.name 字段不存在或类型不匹配");
        }

        String signature = namespace.get() == Namespace.NAMED
            ? Emf261Symbols.RENDER_METHOD_NAMED + Emf261Symbols.RENDER_DESCRIPTOR_NAMED
            : Emf261Symbols.RENDER_METHOD_INTERMEDIARY
                + Emf261Symbols.RENDER_DESCRIPTOR_INTERMEDIARY;
        return Result.compatible(namespace.get(), signature);
    }

    private static Optional<ClassNode> readClass(String binaryName) {
        String resourceName = binaryName.replace('.', '/') + ".class";
        ClassLoader ownLoader = EmfAbiDetector.class.getClassLoader();
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        List<ClassLoader> loaders = new ArrayList<>(2);
        if (ownLoader != null) {
            loaders.add(ownLoader);
        }
        if (contextLoader != null && contextLoader != ownLoader) {
            loaders.add(contextLoader);
        }
        for (ClassLoader loader : loaders) {
            try (InputStream stream = loader.getResourceAsStream(resourceName)) {
                if (stream == null) {
                    continue;
                }
                ClassNode node = new ClassNode();
                new ClassReader(stream).accept(node, ClassReader.SKIP_CODE
                    | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return Optional.of(node);
            } catch (IOException | RuntimeException ignored) {
                // Optional compatibility must never stop startup.
            }
        }
        return Optional.empty();
    }

    public enum Namespace {
        NAMED,
        INTERMEDIARY
    }

    public record Result(boolean compatible, Namespace namespace, String detail) {

        private static Result compatible(Namespace namespace, String detail) {
            return new Result(true, namespace, detail);
        }

        private static Result incompatible(String detail) {
            return new Result(false, null, detail);
        }
    }
}

