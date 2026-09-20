package network.azusake.halo.compat.ysm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.util.Optional;

import static network.azusake.halo.compat.ysm.YsmV265Symbols.*;

/** Inspect class resources without initializing optional YSM classes during Mixin bootstrap. */
public final class YsmAbiDetector {
    private YsmAbiDetector() {}

    @FunctionalInterface
    interface Classes { ClassNode read(String name) throws IOException; }

    public static Optional<String> incompatibility() {
        return incompatibility(name -> {
            String resource = name.replace('.', '/') + ".class";
            ClassLoader loader = YsmAbiDetector.class.getClassLoader();
            try (var stream = loader.getResourceAsStream(resource)) {
                if (stream == null) return null;
                var node = new ClassNode();
                new ClassReader(stream).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return node;
            }
        });
    }

    static Optional<String> incompatibility(Classes classes) {
        try {
            var renderer = require(classes, GEO_RENDERER);
            method(renderer, RENDER_METHOD, RENDER_DESCRIPTOR);
            var data = require(classes, RENDER_DATA);
            field(data, DATA_MODEL_FIELD, "L" + RAW_MODEL.replace('.', '/') + ";");
            field(data, DATA_ATTRIBUTES_BUFFER_FIELD, "Ljava/nio/ByteBuffer;");
            var model = require(classes, RAW_MODEL);
            method(model, MODEL_BONES_GETTER, "()Ljava/util/List;");
            field(model, HEAD_LOCATOR_FIELD, "Lit/unimi/dsi/fastutil/ints/IntList;");
            var bone = require(classes, RAW_BONE);
            method(bone, BONE_NAME_GETTER, "()Ljava/lang/String;");
            method(bone, BONE_ID_GETTER, "()I");
            for (String pivot : new String[]{PIVOT_X, PIVOT_Y, PIVOT_Z}) method(bone, pivot, "()F");
            return Optional.empty();
        } catch (IOException | RuntimeException error) {
            return Optional.of(error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private static ClassNode require(Classes classes, String name) throws IOException {
        var node = classes.read(name);
        if (node == null) throw new IllegalArgumentException("missing class " + name);
        if ((node.access & Opcodes.ACC_PUBLIC) == 0) throw new IllegalArgumentException("non-public class " + name);
        return node;
    }

    private static boolean publicInstance(int access) {
        return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == Opcodes.ACC_PUBLIC;
    }

    private static void method(ClassNode node, String name, String descriptor) {
        if (node.methods.stream().noneMatch(m -> m.name.equals(name) && m.desc.equals(descriptor) && publicInstance(m.access)))
            throw new IllegalArgumentException("missing public instance method " + node.name + "." + name + descriptor);
    }

    private static void field(ClassNode node, String name, String descriptor) {
        if (node.fields.stream().noneMatch(f -> f.name.equals(name) && f.desc.equals(descriptor) && publicInstance(f.access)))
            throw new IllegalArgumentException("missing public instance field " + node.name + "." + name + ":" + descriptor);
    }
}
