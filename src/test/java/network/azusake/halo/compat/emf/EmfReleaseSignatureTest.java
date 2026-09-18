package network.azusake.halo.compat.emf;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** ABI gate for the separately distributed Forge EMF release used by smoke tests. */
class EmfReleaseSignatureTest {

    @Test
    @DisplayName("official Forge EMF 3.1.1 jar retains the pinned model-part ABI")
    void officialJarSignatures() throws Exception {
        String configured = System.getenv("HALO_EMF_TEST_JAR");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(),
            "set HALO_EMF_TEST_JAR to run release signature verification");
        Path jar = Path.of(configured);
        assertTrue(Files.isRegularFile(jar), "EMF test jar does not exist: " + jar);

        try (ZipFile zip = new ZipFile(jar.toFile())) {
            String metadata = text(zip, "META-INF/mods.toml");
            String compactMetadata = metadata.replaceAll("\\s+", "");
            assertTrue(compactMetadata.contains("modId=\"" + Emf1201Symbols.MOD_ID + "\""));
            assertTrue(compactMetadata.contains("version=\"3.1.1\""));

            ClassNode modelPart = classNode(zip, Emf1201Symbols.MODEL_PART);
            assertTrue(modelPart.methods.stream().anyMatch(method ->
                (Emf1201Symbols.RENDER_METHOD_NAMED.equals(method.name)
                    || Emf1201Symbols.RENDER_METHOD_FORGE.equals(method.name))
                    && (Emf1201Symbols.RENDER_DESCRIPTOR_NAMED.equals(method.desc)
                    || Emf1201Symbols.RENDER_DESCRIPTOR_FORGE.equals(method.desc))),
                "missing supported EMFModelPart render signature");

            ClassNode vanillaPart = classNode(zip, Emf1201Symbols.VANILLA_MODEL_PART);
            assertTrue(vanillaPart.fields.stream().anyMatch(field ->
                "name".equals(field.name) && "Ljava/lang/String;".equals(field.desc)),
                "missing EMFModelPartVanilla.name");
        }
    }

    private static ClassNode classNode(ZipFile zip, String binaryName) throws Exception {
        String name = binaryName.replace('.', '/') + ".class";
        ZipEntry entry = zip.getEntry(name);
        assertTrue(entry != null, "missing jar entry " + name);
        ClassNode node = new ClassNode();
        new ClassReader(zip.getInputStream(entry)).accept(node,
            ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static String text(ZipFile zip, String name) throws Exception {
        ZipEntry entry = zip.getEntry(name);
        assertTrue(entry != null, "missing jar entry " + name);
        return new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
    }
}
