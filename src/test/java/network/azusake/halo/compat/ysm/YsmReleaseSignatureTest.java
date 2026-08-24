package network.azusake.halo.compat.ysm;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the optional adapter against an official release jar when
 * {@code HALO_YSM_TEST_JAR} points to it.  The ordinary test suite skips this
 * large external fixture rather than checking a 60 MB binary into Halo.
 */
class YsmReleaseSignatureTest {

    @Test
    void captureArgumentSlotsMatchPinnedBaseRenderDescriptor() {
        Type[] arguments = Type.getArgumentTypes(YsmV265Symbols.RENDER_DESCRIPTOR_FORGE);

        assertEquals(
            "L" + YsmV265Symbols.ANIMATED_GEO_MODEL.replace('.', '/') + ";",
            arguments[YsmV265Symbols.RENDER_MODEL_ARGUMENT].getDescriptor()
        );
        assertEquals(
            "Lcom/mojang/blaze3d/vertex/PoseStack;",
            arguments[YsmV265Symbols.RENDER_POSE_STACK_ARGUMENT].getDescriptor()
        );
    }

    @Test
    @DisplayName("official YSM 2.6.5 jar retains every pinned adapter signature")
    void officialJarSignatures() throws Exception {
        String configured = System.getenv("HALO_YSM_TEST_JAR");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(),
            "set HALO_YSM_TEST_JAR to run release signature verification");
        Path jar = Path.of(configured);
        assertTrue(Files.isRegularFile(jar), "YSM test jar does not exist: " + jar);

        try (ZipFile zip = new ZipFile(jar.toFile())) {
            String metadata = text(zip, "META-INF/mods.toml");
            assertTrue(metadata.contains("version=\"" + YsmV265Symbols.SUPPORTED_VERSION + "\""));

            ClassNode renderer = classNode(zip, YsmV265Symbols.GEO_RENDERER);
            assertTrue(hasMethod(renderer, YsmV265Symbols.RENDER_METHOD,
                YsmV265Symbols.RENDER_DESCRIPTOR_FORGE),
                "missing pinned YSM render method");

            ClassNode livingRenderer = classNode(zip, YsmV265Symbols.LIVING_GEO_RENDERER);
            assertTrue(hasMethod(livingRenderer, YsmV265Symbols.RENDER_METHOD,
                YsmV265Symbols.LIVING_RENDER_DESCRIPTOR),
                "missing pinned YSM living renderer entrypoint");
            assertTrue(hasBaseRenderInvocation(livingRenderer),
                "living renderer no longer invokes the pinned final base-model render method");

            ClassNode entityRenderer = classNode(zip, YsmV265Symbols.ENTITY_GEO_RENDERER);
            assertTrue(hasMethod(entityRenderer, YsmV265Symbols.RENDER_METHOD,
                YsmV265Symbols.ENTITY_RENDER_DESCRIPTOR),
                "missing pinned YSM generic entity renderer entrypoint");
            assertTrue(hasBaseRenderInvocation(
                    entityRenderer,
                    YsmV265Symbols.ENTITY_GEO_RENDERER,
                    YsmV265Symbols.ENTITY_RENDER_DESCRIPTOR),
                "generic entity renderer no longer invokes the pinned final base-model render method");

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
            ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static boolean hasMethod(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream()
            .anyMatch(method -> name.equals(method.name) && descriptor.equals(method.desc));
    }

    private static boolean hasBaseRenderInvocation(ClassNode owner) {
        return hasBaseRenderInvocation(
            owner,
            YsmV265Symbols.LIVING_GEO_RENDERER,
            YsmV265Symbols.LIVING_RENDER_DESCRIPTOR
        );
    }

    private static boolean hasBaseRenderInvocation(
        ClassNode owner,
        String expectedOwnerName,
        String entrypointDescriptor
    ) {
        String expectedOwner = expectedOwnerName.replace('.', '/');
        for (var method : owner.methods) {
            if (!YsmV265Symbols.RENDER_METHOD.equals(method.name)
                || !entrypointDescriptor.equals(method.desc)) {
                continue;
            }
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode invocation
                    && expectedOwner.equals(invocation.owner)
                    && YsmV265Symbols.RENDER_METHOD.equals(invocation.name)
                    && YsmV265Symbols.RENDER_DESCRIPTOR_FORGE.equals(invocation.desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String text(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        assertTrue(entry != null, "missing jar entry " + name);
        return new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.ISO_8859_1);
    }
}
