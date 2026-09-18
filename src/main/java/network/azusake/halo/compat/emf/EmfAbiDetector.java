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

    public static Result inspect() {
        Optional<ClassNode> modelPart = readClass(Emf1201Symbols.MODEL_PART);
        if (modelPart.isEmpty()) {
            return Result.incompatible("目标类不存在: " + Emf1201Symbols.MODEL_PART);
        }
        RenderSignature signature = findRenderSignature(modelPart.get());
        if (signature == null) return Result.incompatible("目标渲染方法不存在: Forge/Mojmap render ABI");

        Optional<ClassNode> vanillaPart = readClass(Emf1201Symbols.VANILLA_MODEL_PART);
        if (vanillaPart.isEmpty()) {
            return Result.incompatible("目标类不存在: " + Emf1201Symbols.VANILLA_MODEL_PART);
        }
        boolean hasNameField = vanillaPart.get().fields.stream()
            .anyMatch(field -> "name".equals(field.name) && STRING_DESCRIPTOR.equals(field.desc));
        if (!hasNameField) {
            return Result.incompatible("EMFModelPartVanilla.name 字段不存在或类型不匹配");
        }

        return Result.compatible(signature);
    }

    private static RenderSignature findRenderSignature(ClassNode modelPart) {
        List<RenderSignature> candidates = List.of(
            new RenderSignature(Emf1201Symbols.RENDER_METHOD_NAMED, Emf1201Symbols.RENDER_DESCRIPTOR_NAMED),
            new RenderSignature(Emf1201Symbols.RENDER_METHOD_NAMED, Emf1201Symbols.RENDER_DESCRIPTOR_FORGE),
            new RenderSignature(Emf1201Symbols.RENDER_METHOD_FORGE, Emf1201Symbols.RENDER_DESCRIPTOR_FORGE),
            new RenderSignature(Emf1201Symbols.RENDER_METHOD_FORGE, Emf1201Symbols.RENDER_DESCRIPTOR_NAMED));
        return candidates.stream().filter(c -> modelPart.methods.stream().anyMatch(m -> c.name.equals(m.name) && c.descriptor.equals(m.desc))).findFirst().orElse(null);
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

    public record Result(boolean compatible, String detail, String renderMethod, String renderDescriptor) {

        private static Result compatible(RenderSignature signature) {
            return new Result(true, signature.name + signature.descriptor, signature.name, signature.descriptor);
        }

        private static Result incompatible(String detail) {
            return new Result(false, detail, null, null);
        }
    }
    private record RenderSignature(String name, String descriptor) {}
}
