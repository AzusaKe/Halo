package network.azusake.halo.compat.ysm;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the optional adapter against an official release jar when
 * {@code HALO_YSM_TEST_JAR} points to it.  The ordinary test suite skips this
 * large external fixture rather than checking a 60 MB binary into Halo.
 */
class YsmReleaseSignatureTest {

    @Test
    @DisplayName("official YSM 2.6.5 jar retains every pinned adapter signature")
    void officialJarSignatures() throws Exception {
        String configured = System.getenv("HALO_YSM_TEST_JAR");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(),
            "set HALO_YSM_TEST_JAR to run release signature verification");
        Path jar = Path.of(configured);
        assertTrue(Files.isRegularFile(jar), "YSM test jar does not exist: " + jar);

        try (ZipFile zip = new ZipFile(jar.toFile())) {
            String metadata = text(zip, "fabric.mod.json");
            assertTrue(metadata.contains("\"version\": \"" + YsmV265Symbols.SUPPORTED_VERSION + "\""));

            ClassNode renderer = classNode(zip, YsmV265Symbols.GEO_RENDERER);
            assertTrue(hasMethod(renderer, YsmV265Symbols.RENDER_METHOD,
                YsmV265Symbols.RENDER_DESCRIPTOR_INTERMEDIARY),
                "missing pinned YSM render method");

            ClassNode model = classNode(zip, YsmV265Symbols.ANIMATED_GEO_MODEL);
            assertTrue(hasMethod(model, YsmV265Symbols.HEAD_BONES_GETTER,
                YsmV265Symbols.HEAD_BONES_DESCRIPTOR),
                "missing pinned Head hierarchy getter");

            ClassNode bone = classNode(zip, YsmV265Symbols.BONE);
            for (String getter : new String[]{
                YsmV265Symbols.ROTATION_X, YsmV265Symbols.ROTATION_Y, YsmV265Symbols.ROTATION_Z,
                YsmV265Symbols.POSITION_X, YsmV265Symbols.POSITION_Y, YsmV265Symbols.POSITION_Z,
                YsmV265Symbols.SCALE_X, YsmV265Symbols.SCALE_Y, YsmV265Symbols.SCALE_Z,
                YsmV265Symbols.PIVOT_X, YsmV265Symbols.PIVOT_Y, YsmV265Symbols.PIVOT_Z
            }) {
                assertTrue(hasMethod(bone, getter, YsmV265Symbols.BONE_FLOAT_GETTER_DESCRIPTOR),
                    "missing pinned bone getter " + getter);
            }
        }
    }

    private static ClassNode classNode(ZipFile zip, String binaryName) throws IOException {
        String name = binaryName.replace('.', '/') + ".class";
        ZipEntry entry = zip.getEntry(name);
        assertTrue(entry != null, "missing jar entry " + name);
        ClassNode node = new ClassNode();
        new ClassReader(zip.getInputStream(entry)).accept(node,
            ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static boolean hasMethod(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream()
            .anyMatch(method -> name.equals(method.name) && descriptor.equals(method.desc));
    }

    private static String text(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        assertTrue(entry != null, "missing jar entry " + name);
        return new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.ISO_8859_1);
    }
}
