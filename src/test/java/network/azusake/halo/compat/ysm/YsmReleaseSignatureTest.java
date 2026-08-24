package network.azusake.halo.compat.ysm;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** Bytecode contract test for the official 26.1 hotfix fixture. */
class YsmReleaseSignatureTest {
    private static final String HOTFIX_SHA256 =
        "4e3dc02f4a0a3719422e7760036aee942755379e68c4e1a0a465fe7a820fc9b8";

    @Test
    void officialHotfixRetainsPinnedContract() throws Exception {
        String configured = System.getenv("HALO_YSM_TEST_JAR");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(),
            "set HALO_YSM_TEST_JAR to the official hotfix jar");
        Path jar = Path.of(configured);
        assertTrue(Files.isRegularFile(jar));
        assertEquals(HOTFIX_SHA256, HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));

        try (ZipFile zip = new ZipFile(jar.toFile())) {
            String metadata = new String(zip.getInputStream(
                zip.getEntry("META-INF/neoforge.mods.toml")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(metadata.contains("version=\"" + YsmV265Symbols.SUPPORTED_VERSION + "\""));

            ClassNode renderer = node(zip, YsmV265Symbols.GEO_RENDERER);
            assertTrue(method(renderer, YsmV265Symbols.RENDER_METHOD, YsmV265Symbols.RENDER_DESCRIPTOR));

            ClassNode data = node(zip, YsmV265Symbols.RENDER_DATA);
            assertTrue(field(data, YsmV265Symbols.DATA_MODEL_FIELD,
                "L" + YsmV265Symbols.RAW_MODEL.replace('.', '/') + ";"));
            assertTrue(field(data, YsmV265Symbols.DATA_ATTRIBUTES_BUFFER_FIELD,
                "Ljava/nio/ByteBuffer;"));

            ClassNode model = node(zip, YsmV265Symbols.RAW_MODEL);
            assertTrue(method(model, YsmV265Symbols.MODEL_BONES_GETTER, "()Ljava/util/List;"));
            assertTrue(field(model, YsmV265Symbols.HEAD_LOCATOR_FIELD,
                "Lit/unimi/dsi/fastutil/ints/IntList;"), YsmV265Symbols.HEAD_LOCATOR_FIELD);

            ClassNode bone = node(zip, YsmV265Symbols.RAW_BONE);
            assertTrue(method(bone, YsmV265Symbols.BONE_NAME_GETTER, "()Ljava/lang/String;"));
            assertTrue(method(bone, YsmV265Symbols.BONE_ID_GETTER, "()I"));
            for (String getter : new String[]{YsmV265Symbols.PIVOT_X,
                    YsmV265Symbols.PIVOT_Y, YsmV265Symbols.PIVOT_Z}) {
                assertTrue(method(bone, getter, "()F"), getter);
            }

            // Constructor data flow verified from the pinned artifact: pivot is read first;
            // initial rotation is then copied into the animated 12-float attribute array.
            ClassNode animatedBone = nodeWithCode(zip, YsmV265Symbols.ANIMATED_BONE);
            String rawBoneOwner = YsmV265Symbols.RAW_BONE.replace('.', '/');
            MethodNode constructor = animatedBone.methods.stream()
                .filter(m -> "<init>".equals(m.name) && m.desc.startsWith("(L" + rawBoneOwner + ";"))
                .findFirst().orElseThrow();
            List<String> rawFloatReads = constructor.instructions.iterator().hasNext()
                ? java.util.stream.StreamSupport.stream(
                    java.util.Spliterators.spliteratorUnknownSize(
                        constructor.instructions.iterator(), 0), false)
                    .filter(MethodInsnNode.class::isInstance)
                    .map(MethodInsnNode.class::cast)
                    .filter(i -> rawBoneOwner.equals(i.owner) && "()F".equals(i.desc))
                    .map(i -> i.name).toList()
                : List.of();
            assertEquals(List.of(
                YsmV265Symbols.PIVOT_X, YsmV265Symbols.PIVOT_Y, YsmV265Symbols.PIVOT_Z,
                YsmV265Symbols.INITIAL_ROTATION_X, YsmV265Symbols.INITIAL_ROTATION_Y,
                YsmV265Symbols.INITIAL_ROTATION_Z, YsmV265Symbols.INITIAL_ROTATION_X,
                YsmV265Symbols.INITIAL_ROTATION_Y, YsmV265Symbols.INITIAL_ROTATION_Z
            ), rawFloatReads);
        }
    }

    private static ClassNode node(ZipFile zip, String binaryName) throws Exception {
        String entry = binaryName.replace('.', '/') + ".class";
        assertNotNull(zip.getEntry(entry), entry);
        ClassNode node = new ClassNode();
        new ClassReader(zip.getInputStream(zip.getEntry(entry))).accept(node,
            ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static ClassNode nodeWithCode(ZipFile zip, String binaryName) throws Exception {
        String entry = binaryName.replace('.', '/') + ".class";
        assertNotNull(zip.getEntry(entry), entry);
        ClassNode node = new ClassNode();
        new ClassReader(zip.getInputStream(zip.getEntry(entry))).accept(node,
            ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static boolean method(ClassNode node, String name, String desc) {
        return node.methods.stream().anyMatch(m -> name.equals(m.name) && desc.equals(m.desc));
    }

    private static boolean field(ClassNode node, String name, String desc) {
        return node.fields.stream().anyMatch(f -> name.equals(f.name) && desc.equals(f.desc));
    }
}
