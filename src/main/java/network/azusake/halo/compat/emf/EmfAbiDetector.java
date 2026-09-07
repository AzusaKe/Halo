package network.azusake.halo.compat.emf;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Checks the exact EMF class ABI before optional Mixins are enabled. */
public final class EmfAbiDetector {

    private static final String STRING_DESCRIPTOR = "Ljava/lang/String;";

    private EmfAbiDetector() {
    }

    public static Result inspect(boolean namedRuntime) {
        String methodName = namedRuntime
            ? Emf1201Symbols.RENDER_METHOD_NAMED
            : Emf1201Symbols.RENDER_METHOD_INTERMEDIARY;
        String methodDescriptor = namedRuntime
            ? Emf1201Symbols.RENDER_DESCRIPTOR_NAMED
            : Emf1201Symbols.RENDER_DESCRIPTOR_INTERMEDIARY;

        Optional<ClassNode> modelPart = readClass(Emf1201Symbols.MODEL_PART);
        if (modelPart.isEmpty()) {
            return Result.incompatible("目标类不存在: " + Emf1201Symbols.MODEL_PART);
        }
        boolean hasRenderMethod = modelPart.get().methods.stream()
            .anyMatch(method -> methodName.equals(method.name)
                && methodDescriptor.equals(method.desc));
        if (!hasRenderMethod) {
            return Result.incompatible(
                "目标渲染方法不存在: " + methodName + methodDescriptor);
        }

        Optional<ClassNode> vanillaPart = readClass(Emf1201Symbols.VANILLA_MODEL_PART);
        if (vanillaPart.isEmpty()) {
            return Result.incompatible("目标类不存在: " + Emf1201Symbols.VANILLA_MODEL_PART);
        }
        boolean hasNameField = vanillaPart.get().fields.stream()
            .anyMatch(field -> "name".equals(field.name) && STRING_DESCRIPTOR.equals(field.desc));
        if (!hasNameField) {
            return Result.incompatible("EMFModelPartVanilla.name 字段不存在或类型不匹配");
        }

        return Result.compatible(methodName + methodDescriptor);
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
            if (loader == null) {
                continue;
            }
            try (InputStream stream = loader.getResourceAsStream(resourceName)) {
                if (stream == null) {
                    continue;
                }
                ClassNode node = new ClassNode();
                new ClassReader(stream).accept(node, ClassReader.SKIP_CODE
                    | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return Optional.of(node);
            } catch (IOException | RuntimeException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public record Result(boolean compatible, String detail) {

        private static Result compatible(String detail) {
            return new Result(true, detail);
        }

        private static Result incompatible(String detail) {
            return new Result(false, detail);
        }
    }
}
