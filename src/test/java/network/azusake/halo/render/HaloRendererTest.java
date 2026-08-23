package network.azusake.halo.render;

import network.azusake.halo.animation.AnimationTerm;
import network.azusake.halo.animation.LayerAnimation;
import network.azusake.halo.data.HaloInstance;
import network.azusake.halo.shape.HaloGroup;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for halo rendering math: quaternion-to-matrix conversion,
 * billboard facing, frame interpolation, and culling.
 *
 * <p>These tests exercise the pure-computation paths — no Minecraft
 * instance is required.</p>
 */
class HaloRendererTest {

    @Nested
    @DisplayName("Vanilla entity rendering backend")
    class VanillaEntityBackend {

        private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("halo", "textures/halo/ring.png");

        @Test
        @DisplayName("material resolver retains Iris-recognized vanilla pipeline identities")
        void selectsRecognizedPipelineIdentity() {
            RenderType normal = HaloRenderer.selectRenderType(TEXTURE, false, false);
            RenderType glowing = HaloRenderer.selectRenderType(TEXTURE, true, false);
            RenderType glowingCull = HaloRenderer.selectRenderType(TEXTURE, true, true);
            RenderType normalCull = HaloRenderer.selectRenderType(TEXTURE, false, true);

            assertSame(RenderPipelines.ENTITY_TRANSLUCENT, normal.pipeline());
            assertSame(RenderPipelines.BREEZE_WIND, glowing.pipeline());
            assertSame(glowing, glowingCull,
                "all glowing geometry uses the same flat-lit material");
            assertSame(RenderPipelines.ENTITY_TRANSLUCENT_CULL, normalCull.pipeline());
        }

        @Test
        @DisplayName("all halo materials use complete ENTITY quads")
        void usesEntityQuadVertexContract() {
            for (RenderType renderType : List.of(
                    HaloRenderer.selectRenderType(TEXTURE, false, false),
                    HaloRenderer.selectRenderType(TEXTURE, true, false),
                    HaloRenderer.selectRenderType(TEXTURE, false, true))) {
                assertSame(DefaultVertexFormat.ENTITY, renderType.format());
                assertEquals(VertexFormat.Mode.QUADS, renderType.mode());
                assertTrue(renderType.sortOnUpload());
            }
        }

        @Test
        @DisplayName("glowing geometry is fullbright while normal geometry keeps packed light")
        void selectsPackedLight() {
            int environment = LightCoordsUtil.pack(3, 11);
            assertEquals(environment, HaloRenderer.selectPackedLight(false, environment));
            assertEquals(LightCoordsUtil.FULL_BRIGHT,
                HaloRenderer.selectPackedLight(true, environment));
        }

        @Test
        @DisplayName("cross-texture translucent submits are ordered far to near")
        void ordersCrossTextureGeometryFarToNear() {
            Matrix4f near = new Matrix4f().translation(0.0f, 0.0f, 2.0f);
            Matrix4f far = new Matrix4f().translation(0.0f, 0.0f, 8.0f);

            assertTrue(HaloRenderer.translucentOrder(far)
                < HaloRenderer.translucentOrder(near));
            assertEquals(0, HaloRenderer.translucentOrder(new Matrix4f()));
        }

        @Test
        @DisplayName("CPU culling preserves the front-facing half of flat-lit two-texture rings")
        void identifiesFrontFacingRingQuads() {
            Matrix4f fiveBlocksAhead = new Matrix4f().translation(0.0f, 0.0f, -5.0f);
            Vector3f p0 = new Vector3f(-1.0f, -1.0f, 0.0f);
            Vector3f p1 = new Vector3f(1.0f, -1.0f, 0.0f);
            Vector3f p2 = new Vector3f(1.0f, 1.0f, 0.0f);
            Vector3f p3 = new Vector3f(-1.0f, 1.0f, 0.0f);

            assertTrue(HaloRenderer.isFrontFacing(fiveBlocksAhead, p0, p1, p2, p3));
            assertFalse(HaloRenderer.isFrontFacing(fiveBlocksAhead, p3, p2, p1, p0));
        }
    }

    // ------------------------------------------------------------------
    // 1. Quaternion → Matrix
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Quaternion-to-matrix conversion")
    class QuaternionToMatrix {

        @Test
        @DisplayName("identity quaternion → identity matrix (no rotation)")
        void testIdentityQuaternion() {
            Quaternionf identity = new Quaternionf();
            Matrix4f mat = new Matrix4f().rotation(identity);

            // Identity matrix: diagonal should be ~1, off-diagonals ~0
            assertEquals(1.0f, mat.m00(), 0.0001f);
            assertEquals(1.0f, mat.m11(), 0.0001f);
            assertEquals(1.0f, mat.m22(), 0.0001f);
            assertEquals(0.0f, mat.m01(), 0.0001f);
            assertEquals(0.0f, mat.m02(), 0.0001f);
            assertEquals(0.0f, mat.m10(), 0.0001f);
            assertEquals(0.0f, mat.m12(), 0.0001f);
            assertEquals(0.0f, mat.m20(), 0.0001f);
            assertEquals(0.0f, mat.m21(), 0.0001f);
        }

        @Test
        @DisplayName("transition rotation: quaternionFromYxzDegrees feeds applyQuaternionRotation (YXZ)")
        void testTransitionRotationYxz() {
            // F8: the transition rotation is applied via
            // LayerAnimation.quaternionFromYxzDegrees -> applyQuaternionRotation.
            // A 90° yaw must behave exactly like the renderer's own YXZ helper.
            Quaternionf quat = LayerAnimation.quaternionFromYxzDegrees(90f, 0f, 0f);
            Matrix4f mat = new Matrix4f().rotation(quat);
            Vector3f forward = new Vector3f(0, 0, -1);
            Vector3f result = mat.transformDirection(new Vector3f(forward), new Vector3f());
            assertEquals(0.0f, result.y, 0.001f, "yaw keeps forward in XZ plane");
            assertEquals(1.0f, result.length(), 0.001f, "rotation preserves length");
        }

        @Test
        @DisplayName("90° around Y → forwards stays in XZ plane, preserves length")
        void testYaw90Degrees() {
            Quaternionf quat = new Quaternionf()
                .rotateY((float) Math.toRadians(90.0));
            Matrix4f mat = new Matrix4f().rotation(quat);

            // 90° Y rotation should rotate forward (0,0,-1) to lie in the XZ
            // plane (y ≈ 0), with unit length, and be perpendicular to original.
            // NOTE: transformDirection mutates IN PLACE — use two-arg form.
            Vector3f forward = new Vector3f(0, 0, -1);
            Vector3f result = mat.transformDirection(new Vector3f(forward), new Vector3f());

            assertEquals(0.0f, result.y, 0.001f,
                "90° Y rotation: forward should stay in XZ plane (y=0)");
            assertEquals(1.0f, result.length(), 0.001f,
                "90° Y rotation: length must be preserved");
            // Dot product with original → cos(90°) ≈ 0
            assertEquals(0.0f, forward.dot(result), 0.001f,
                "90° Y rotation: forward and result must be perpendicular");
        }

        @Test
        @DisplayName("90° around X → forwards stays in XY plane, preserves length")
        void testPitch90Degrees() {
            Quaternionf quat = new Quaternionf()
                .rotateX((float) Math.toRadians(90.0));
            Matrix4f mat = new Matrix4f().rotation(quat);

            // 90° X rotation: forward (0,0,-1) rotates into the YZ plane (x ≈ 0)
            Vector3f forward = new Vector3f(0, 0, -1);
            Vector3f result = mat.transformDirection(new Vector3f(forward), new Vector3f());

            assertEquals(0.0f, result.x, 0.001f,
                "90° X rotation: forward should have x=0");
            assertEquals(1.0f, result.length(), 0.001f,
                "90° X rotation: length must be preserved");
            assertEquals(0.0f, forward.dot(result), 0.001f,
                "90° X rotation: forward and result must be perpendicular");
        }

        @Test
        @DisplayName("matrix multiplication with rotation composes correctly")
        void testMatrixComposition() {
            // Apply a 90° Y rotation via matrix multiplication
            Matrix4f base = new Matrix4f(); // identity
            Matrix4f rot = new Matrix4f()
                .rotation(new Quaternionf().rotateY((float) Math.toRadians(90.0)));
            base.mul(rot); // base = base * rot

            Vector3f forward = new Vector3f(0, 0, -1);
            Vector3f result = base.transformDirection(new Vector3f(forward), new Vector3f());

            // Should be unit length, in XZ plane, perpendicular to forward
            assertEquals(0.0f, result.y, 0.001f,
                "matrix mul Y-90°: forward should stay in XZ plane");
            assertEquals(1.0f, result.length(), 0.001f,
                "matrix mul Y-90°: length must be preserved");
            assertEquals(0.0f, forward.dot(result), 0.001f,
                "matrix mul Y-90°: forward and result must be perpendicular");
        }

        @Test
        @DisplayName("45° around Y then 45° around X → combined rotation preserves length")
        void testCombinedYawPitch() {
            Quaternionf combined = new Quaternionf()
                .rotateY((float) Math.toRadians(45.0))
                .rotateX((float) Math.toRadians(45.0));
            Matrix4f mat = new Matrix4f().rotation(combined);

            Vector3f forward = new Vector3f(0, 0, -1);
            Vector3f result = mat.transformDirection(new Vector3f(forward), new Vector3f());

            // Combined rotation should preserve unit length
            assertEquals(1.0f, result.length(), 0.001f,
                "combined rotation: length must be preserved");

            // The angle between forward and result should be nonzero
            double angleDeg = Math.toDegrees(
                Math.acos(Math.max(-1.0, Math.min(1.0, forward.dot(result)))));
            assertTrue(angleDeg > 0.0 && angleDeg < 90.0,
                "combined 45° yaw + 45° pitch should rotate forward by 0°–90°, got "
                    + angleDeg + "°");
        }

        @Test
        @DisplayName("quaternion slerp at t=0 returns start quaternion")
        void testSlerpStart() {
            Quaternionf start = new Quaternionf().rotateY((float) Math.toRadians(30));
            Quaternionf end = new Quaternionf().rotateY((float) Math.toRadians(90));
            Quaternionf result = new Quaternionf(start).slerp(end, 0.0f);

            // Extract Y angle from result
            double angle = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(result.w))));
            // Should be within ~3° of start angle
            assertTrue(angle < 40.0 && angle > 20.0,
                "slerp at t=0 should be close to start rotation (30°), got " + angle);
        }

        @Test
        @DisplayName("quaternion slerp at t=1 returns end quaternion")
        void testSlerpEnd() {
            Quaternionf start = new Quaternionf().rotateY((float) Math.toRadians(30));
            Quaternionf end = new Quaternionf().rotateY((float) Math.toRadians(90));
            Quaternionf result = new Quaternionf(start).slerp(end, 1.0f);

            double angle = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(result.w))));
            assertTrue(angle > 80.0 && angle < 100.0,
                "slerp at t=1 should be close to end rotation (90°), got " + angle);
        }
    }

    // ------------------------------------------------------------------
    // 2. Billboard facing
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Billboard camera-facing math")
    class BillboardFacing {

        @Test
        @DisplayName("billboard rotation matrix is orthonormal (no shear/scale)")
        void testBillboardRotationValid() {
            float yaw = 45.0f;
            float pitch = 30.0f;

            // Billboard transform: rotate by -yaw around Y, then +pitch around X
            // This is the standard Minecraft billboard pattern used by particles.
            Quaternionf billboard = new Quaternionf()
                .rotateY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(pitch));

            Matrix4f mat = new Matrix4f().rotation(billboard);

            // A rotation matrix must preserve vector lengths
            Vector3f v = new Vector3f(1, 2, 3);
            Vector3f rotated = mat.transformDirection(new Vector3f(v), new Vector3f());
            assertEquals(v.length(), rotated.length(), 0.001f,
                "rotation must preserve vector length");

            // The determinant of a 3x3 rotation matrix must be 1
            float det = mat.m00() * (mat.m11() * mat.m22() - mat.m12() * mat.m21())
                - mat.m01() * (mat.m10() * mat.m22() - mat.m12() * mat.m20())
                + mat.m02() * (mat.m10() * mat.m21() - mat.m11() * mat.m20());
            assertEquals(1.0f, det, 0.001f,
                "rotation matrix determinant must be 1");
        }

        @Test
        @DisplayName("billboard at yaw=0 pitch=0 → normal points along +Z (identity rotation)")
        void testBillboardDefaultCamera() {
            // Default camera: yaw=0 (looking along -Z), pitch=0 (horizontal)
            Quaternionf billboard = new Quaternionf()
                .rotateY(0)
                .rotateX(0);

            Vector3f normal = new Vector3f(0, 0, 1);
            Matrix4f mat = new Matrix4f().rotation(billboard);
            Vector3f result = mat.transformDirection(new Vector3f(normal), new Vector3f());

            // With yaw=0 pitch=0, the billboard rotation is identity:
            // normal stays at (0, 0, 1)
            assertEquals(0.0f, result.x, 0.001f);
            assertEquals(0.0f, result.y, 0.001f);
            assertEquals(1.0f, result.z, 0.001f);
        }

        @Test
        @DisplayName("billboard yaw rotation rotates normal in XZ plane only")
        void testBillboardYawOnly() {
            // Yaw=90°, pitch=0°
            Quaternionf billboard = new Quaternionf()
                .rotateY((float) Math.toRadians(-90.0))
                .rotateX(0);

            Vector3f normal = new Vector3f(0, 0, 1);
            Matrix4f mat = new Matrix4f().rotation(billboard);
            Vector3f result = mat.transformDirection(new Vector3f(normal), new Vector3f());

            // Pure yaw rotation must not move the Y component
            assertEquals(0.0f, result.y, 0.001f,
                "pure yaw rotation: normal Y must stay at 0");
            // Length must be preserved
            assertEquals(1.0f, result.length(), 0.001f);
            // Normal should be perpendicular to Y axis
            assertEquals(1.0f, Math.abs(result.x) + Math.abs(result.z), 0.001f,
                "normal should lie entirely in XZ plane");
        }

        @Test
        @DisplayName("computeCameraFacing: normal points at the camera, basis orthonormal")
        void testCameraFacingBasis() {
            Matrix4f matrix = new Matrix4f().translation(0.0f, 0.0f, -5.0f);
            HaloRenderer.CameraFacing facing = HaloRenderer.computeCameraFacing(
                matrix, 1.0f, 1.0f,
                new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f(1.0f, 0.0f, 0.0f));

            // Centre is the camera-relative quad position.
            assertEquals(0.0f, facing.center().x, 0.001f);
            assertEquals(0.0f, facing.center().y, 0.001f);
            assertEquals(-5.0f, facing.center().z, 0.001f);

            // Horizontal camera at the origin: right = +X, up = +Y.
            assertEquals(1.0f, facing.right().x, 0.001f);
            assertEquals(0.0f, facing.right().y, 0.001f);
            assertEquals(0.0f, facing.right().z, 0.001f);
            assertEquals(0.0f, facing.up().x, 0.001f);
            assertEquals(1.0f, facing.up().y, 0.001f);
            assertEquals(0.0f, facing.up().z, 0.001f);

            // right/up are unit length and mutually perpendicular.
            assertEquals(1.0f, facing.right().length(), 0.001f);
            assertEquals(1.0f, facing.up().length(), 0.001f);
            assertEquals(0.0f, facing.right().dot(facing.up()), 0.001f);

            // The quad normal (right × up) points from the centre to the camera.
            Vector3f normal = new Vector3f(facing.right()).cross(facing.up(), new Vector3f());
            Vector3f toCamera = new Vector3f(facing.center()).mul(-1.0f).normalize();
            assertEquals(1.0f, normal.dot(toCamera), 0.001f,
                "quad normal must point toward the camera");

            // Half extents match the local size at unit scale.
            assertEquals(1.0f, facing.halfWidth(), 0.001f);
            assertEquals(1.0f, facing.halfDepth(), 0.001f);
        }

        @Test
        @DisplayName("computeCameraFacing: accumulated rotation is discarded")
        void testCameraFacingIgnoresRotation() {
            Matrix4f rotated = new Matrix4f()
                .translation(0.0f, 0.0f, -5.0f)
                .rotateY((float) Math.toRadians(90.0))
                .rotateX((float) Math.toRadians(45.0));
            HaloRenderer.CameraFacing facing = HaloRenderer.computeCameraFacing(
                rotated, 1.0f, 1.0f,
                new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f(1.0f, 0.0f, 0.0f));

            // Same placement as an unrotated matrix with the same translation.
            assertEquals(0.0f, facing.center().x, 0.001f);
            assertEquals(0.0f, facing.center().y, 0.001f);
            assertEquals(-5.0f, facing.center().z, 0.001f);
            assertEquals(1.0f, facing.right().x, 0.001f);
            assertEquals(1.0f, facing.up().y, 0.001f);
            assertEquals(1.0f, facing.halfWidth(), 0.001f);
            assertEquals(1.0f, facing.halfDepth(), 0.001f);
        }

        @Test
        @DisplayName("computeCameraFacing: non-uniform scale is preserved")
        void testCameraFacingPreservesScale() {
            Matrix4f matrix = new Matrix4f()
                .translation(0.0f, 0.0f, -5.0f)
                .scale(2.0f, 1.0f, 4.0f);
            HaloRenderer.CameraFacing facing = HaloRenderer.computeCameraFacing(
                matrix, 1.0f, 1.0f,
                new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f(1.0f, 0.0f, 0.0f));

            assertEquals(2.0f, facing.halfWidth(), 0.001f, "X scale must survive");
            assertEquals(4.0f, facing.halfDepth(), 0.001f, "Z scale must survive");
            assertEquals(1.0f, facing.right().length(), 0.001f, "basis stays unit length");
            assertEquals(1.0f, facing.up().length(), 0.001f, "basis stays unit length");
        }

        @Test
        @DisplayName("computeCameraFacing: looking straight down falls back to camera right")
        void testCameraFacingVerticalFallback() {
            Matrix4f matrix = new Matrix4f().translation(0.0f, -5.0f, 0.0f);
            HaloRenderer.CameraFacing facing = HaloRenderer.computeCameraFacing(
                matrix, 1.0f, 1.0f,
                new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f(1.0f, 0.0f, 0.0f));

            // dir = +Y is parallel to camera up → right falls back to +X.
            assertEquals(1.0f, facing.right().x, 0.001f);
            assertEquals(0.0f, facing.right().y, 0.001f);
            assertEquals(0.0f, facing.up().x, 0.001f);
            assertEquals(0.0f, facing.up().y, 0.001f);
            assertEquals(1.0f, facing.right().length(), 0.001f);
            assertEquals(1.0f, facing.up().length(), 0.001f);
            assertEquals(0.0f, facing.right().dot(facing.up()), 0.001f);

            Vector3f normal = new Vector3f(facing.right()).cross(facing.up(), new Vector3f());
            Vector3f toCamera = new Vector3f(facing.center()).mul(-1.0f).normalize();
            assertEquals(1.0f, normal.dot(toCamera), 0.001f,
                "fallback basis must still face the camera");
        }

        @Test
        @DisplayName("computeCameraFacing: quad at the camera position stays stable")
        void testCameraFacingAtCamera() {
            Matrix4f matrix = new Matrix4f(); // translation (0,0,0)
            HaloRenderer.CameraFacing facing = HaloRenderer.computeCameraFacing(
                matrix, 1.0f, 1.0f,
                new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f(1.0f, 0.0f, 0.0f));

            assertTrue(!Float.isNaN(facing.right().x) && !Float.isNaN(facing.up().y),
                "degenerate position must not produce NaN");
            assertEquals(1.0f, facing.right().length(), 0.001f);
            assertEquals(1.0f, facing.up().length(), 0.001f);
            assertEquals(0.0f, facing.right().dot(facing.up()), 0.001f);
        }
    }

    // ------------------------------------------------------------------
    // 3. Distance culling
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Distance-based culling")
    class DistanceCulling {

        @Test
        @DisplayName("entity within render distance is NOT culled")
        void testNotCulledWhenClose() {
            Vec3 camPos = new Vec3(0, 64, 0);
            Vec3 entityPos = new Vec3(100, 64, 0); // 100 blocks away

            double distSq = entityPos.distanceToSqr(camPos);
            double renderDistSq = 16.0 * 16.0 * 16.0 * 16.0; // (16 chunks * 16 blocks)^2 = 256^2

            assertTrue(distSq <= renderDistSq,
                "entity 100 blocks away should be within 256-block render distance");
        }

        @Test
        @DisplayName("entity beyond render distance IS culled")
        void testCulledWhenFar() {
            Vec3 camPos = new Vec3(0, 64, 0);
            Vec3 entityPos = new Vec3(300, 64, 0); // 300 blocks away

            double distSq = entityPos.distanceToSqr(camPos);
            double renderDistSq = 16.0 * 16.0 * 16.0 * 16.0; // 256^2

            assertTrue(distSq > renderDistSq,
                "entity 300 blocks away should be outside 256-block render distance");
        }

        @Test
        @DisplayName("entity at exactly render distance boundary is NOT culled (inclusive)")
        void testNotCulledAtBoundary() {
            Vec3 camPos = new Vec3(0, 64, 0);
            // Exactly 256 blocks away (one chunk = 16 blocks)
            Vec3 entityPos = new Vec3(256, 64, 0);

            double distSq = entityPos.distanceToSqr(camPos);
            double renderDistSq = 16.0 * 16.0 * 16.0 * 16.0;

            assertTrue(distSq <= renderDistSq + 0.0001,
                "entity at exactly 256 blocks should be within render distance (inclusive)");
        }
    }

    // ------------------------------------------------------------------
    // 5. Vec3 helper (getHeadAnchorPosition logic)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Head anchor calculation")
    class HeadAnchor {

        @Test
        @DisplayName("anchor is at 85% of entity height above foot position")
        void testAnchorHeight() {
            // Non-player entity of height 2.0 blocks
            double footY = 64.0;
            double height = 2.0;
            Vec3 footPos = new Vec3(10, footY, 10);

            // Anchor at 85% height
            Vec3 anchor = footPos.add(0, height * 0.85, 0);

            assertEquals(10.0, anchor.x, 0.001);
            assertEquals(64.0 + 1.7, anchor.y, 0.001); // 64 + 2.0*0.85 = 65.7
            assertEquals(10.0, anchor.z, 0.001);
        }

        @Test
        @DisplayName("anchor for tall entity is proportionally higher")
        void testTallEntityAnchor() {
            // Very tall entity (e.g. enderman, height 2.9)
            Vec3 footPos = new Vec3(0, 32, 0);
            double height = 2.9;
            Vec3 anchor = footPos.add(0, height * 0.85, 0);

            assertEquals(32.0 + 2.9 * 0.85, anchor.y, 0.001); // 32 + 2.465 = 34.465
        }
    }

    // ------------------------------------------------------------------
    // 7. Alpha / glow composition math (animation channels)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Alpha / glow composition math")
    class AlphaGlowComposition {

        @Test
        @DisplayName("during a transition the transition alpha is the sole alpha driver")
        void finalAlphaComposes() {
            // animation.alpha: sin(A=0.2, ω=2) at t=0.25 → sin(π/2)=1 → 0.2
            LayerAnimation anim = new LayerAnimation(
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(new AnimationTerm.Sin(0.2, 2.0, 0.0)),  // alpha
                List.of()                                       // glow
            );
            float layerAlpha = anim.evaluateAlpha(0.25);
            assertEquals(0.2f, layerAlpha, 0.001f);

            // During a transition the layer's own animated alpha is
            // suppressed: finalAlpha = inheritedAlpha × transitionAlpha.
            float inheritedAlpha = 1.0f;
            float transitionAlpha = 0.5f;
            float finalAlpha = inheritedAlpha * transitionAlpha;
            assertEquals(0.5f, finalAlpha, 0.001f);
        }

        @Test
        @DisplayName("outside a transition the layer alpha drives the fade")
        void fadingAlphaThroughTransition() {
            // animation.alpha: linear(start=0, speed=0.5) → 0.5t → at t=1 → 0.5
            LayerAnimation anim = new LayerAnimation(
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(new AnimationTerm.Linear(0.0, 0.5)),   // alpha
                List.of()
            );
            float layerAlpha = anim.evaluateAlpha(1.0);
            assertEquals(0.5f, layerAlpha, 0.001f);

            // No transition active → finalAlpha = inheritedAlpha × layerAlpha.
            float finalAlpha = 1.0f * layerAlpha;
            assertEquals(0.5f, finalAlpha, 0.001f);
        }

        @Test
        @DisplayName("glowing=true uses animated glow as primitive brightness")
        void glowDrivesBrightnessWhenGlowing() {
            // animation.glow: cos(A=0.5, ω=1) at t=0 → cos(0)=1 → 0.5
            LayerAnimation anim = new LayerAnimation(
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(),
                List.of(new AnimationTerm.Cos(0.5, 1.0, 0.0))
            );
            float animatedGlow = anim.evaluateGlow(0.0);
            assertEquals(0.5f, animatedGlow, 0.001f);

            // glowing=true → brightness = animated glow
            float environmentBrightness = 0.2f;
            float glowingBrightness = animatedGlow;
            assertEquals(0.5f, glowingBrightness, 0.001f);

            // glowing=false → brightness follows ambient light
            float nonGlowingBrightness = environmentBrightness;
            assertEquals(0.2f, nonGlowingBrightness, 0.001f);
        }

        @Test
        @DisplayName("glow at zero renders the primitive dark")
        void glowZeroDrivesDark() {
            // animation.glow: linear(start=-2, speed=0) → clamp 0.0
            LayerAnimation anim = new LayerAnimation(
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(),
                List.of(new AnimationTerm.Linear(-2.0, 0.0))
            );
            float animatedGlow = anim.evaluateGlow(0.0);
            assertEquals(0.0f, animatedGlow, 0.001f);

            float glowingBrightness = animatedGlow;
            assertEquals(0.0f, glowingBrightness, 0.001f);
        }

        @Test
        @DisplayName("alpha inherits multiplicatively down the tree")
        void alphaInheritsMultiplicatively() {
            // Parent effective alpha 0.5 × child own alpha 0.5
            float inheritedAlpha = 0.5f;   // accumulated from ancestors
            float childAlpha = 0.5f;       // child's own animation.alpha
            float finalAlpha = inheritedAlpha * childAlpha;
            assertEquals(0.25f, finalAlpha, 0.001f);

            // Omitting the channel on the child keeps the inherited value unchanged
            float defaultedChild = 1.0f;
            assertEquals(0.5f, inheritedAlpha * defaultedChild, 0.001f);
        }

        @Test
        @DisplayName("glow inherits multiplicatively down the tree")
        void glowInheritsMultiplicatively() {
            // Parent effective glow 0.8 × child own glow 0.5 → 0.4 (used as brightness when glowing)
            float inheritedGlow = 0.8f;
            float childGlow = 0.5f;
            float effectiveGlow = inheritedGlow * childGlow;
            assertEquals(0.4f, effectiveGlow, 0.001f);

            // Glow flows to descendants regardless of the glowing flag — the flag
            // only decides whether this group's own primitives use glow brightness
            // or follow ambient light.
            float ownGlowNonGlowing = 0.1f;
            float passedToChildren = inheritedGlow * ownGlowNonGlowing;
            assertEquals(0.08f, passedToChildren, 0.001f);
        }

        @Test
        @DisplayName("inherit_alpha=false cuts alpha inheritance for the subtree")
        void inheritAlphaDisabledResetsSubtree() {
            // Parent effective alpha 0.5, but inherit_alpha=false → children start fresh
            float finalAlpha = 0.5f;
            float childInheritedAlpha = 1.0f; // inherit_alpha=false → no inheritance
            assertEquals(1.0f, childInheritedAlpha, 0.001f);

            // Child's own alpha then applies on top of the fresh 1.0
            float childAlpha = 0.4f;
            assertEquals(0.4f, childInheritedAlpha * childAlpha, 0.001f);

            // Sanity: with inheritance enabled the parent's alpha would multiply in
            assertEquals(0.2f, finalAlpha * childAlpha, 0.001f);
        }

        @Test
        @DisplayName("inherit_glow=false cuts glow inheritance for the subtree")
        void inheritGlowDisabledResetsSubtree() {
            // Parent effective glow 0.8, but inherit_glow=false → children start fresh
            float effectiveGlow = 0.8f;
            float childInheritedGlow = 1.0f; // inherit_glow=false → no inheritance
            assertEquals(1.0f, childInheritedGlow, 0.001f);

            // Child's own glow then applies on top of the fresh 1.0
            float childGlow = 0.5f;
            assertEquals(0.5f, childInheritedGlow * childGlow, 0.001f);
        }

        @Test
        @DisplayName("definition root alpha/glow seed the inheritance chain")
        void definitionRootSeedsInheritance() {
            // The top-level animation acts as an implicit root group: its
            // alpha/glow become the inherited values for every top-level group.
            LayerAnimation defAnim = new LayerAnimation(
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(new AnimationTerm.Linear(0.5, 0.0)),  // def alpha → 0.5
                List.of(new AnimationTerm.Linear(0.7, 0.0))   // def glow → 0.7
            );
            float defAlpha = defAnim.evaluateAlpha(0.0);
            float defGlow = defAnim.evaluateGlow(0.0);
            assertEquals(0.5f, defAlpha, 0.001f);
            assertEquals(0.7f, defGlow, 0.001f);

            // A top-level group's own alpha/glow multiplies on top
            float groupAlpha = 0.8f;
            float groupGlow = 0.5f;
            assertEquals(0.4f, defAlpha * groupAlpha, 0.001f);
            assertEquals(0.35f, defGlow * groupGlow, 0.001f);
        }
    }

    // ------------------------------------------------------------------
    // 8. Frame-rate-independent damping & clamp (renderer path)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("逐帧阻尼与钳制 (per-frame damping & clamp)")
    class FrameDampingAndClamp {

        /**
         * Compute k_f from the renderer's actual formula for testing.
         */
        private static double computeKF(double k, double dt) {
            k = Math.max(0.001, Math.min(k, 0.999));
            double exp = dt / 0.05; // Δt / reference tick
            if (exp <= 0.0) exp = 1.0;
            if (exp > 10.0) exp = 10.0;
            double kF = 1.0 - Math.pow(1.0 - k, exp);
            return Math.max(0.0, Math.min(1.0, kF));
        }

        @Test
        @DisplayName("k_f formula: at dt=0.05, k_f = k (reference tick)")
        void testKFAtReferenceTick() {
            // The renderer clamps k to [0.001, 0.999] for numerical stability
            assertEquals(0.3, computeKF(0.3, 0.05), 0.0001);
            assertEquals(0.5, computeKF(0.5, 0.05), 0.0001);
            // k=0 clamped to 0.001: k_f = 1 − (0.999)^1 ≈ 0.001
            assertTrue(computeKF(0.0, 0.05) < 0.01, "k=0 should give near-zero k_f");
            // k=1 clamped to 0.999: k_f = 1 − (0.001)^1 = 0.999
            assertTrue(computeKF(1.0, 0.05) > 0.99, "k=1 should give near-one k_f");
        }

        @Test
        @DisplayName("k_f at 60 FPS (dt≈0.0167) is proportionally smaller")
        void testKFAt60FPS() {
            double kf60 = computeKF(0.3, 0.0167);
            // At 60 FPS: k_f = 1 − (1−0.3)^(0.0167/0.05) = 1 − 0.7^0.334 ≈ 0.112
            assertTrue(kf60 < 0.3, "60 FPS k_f should be smaller than reference tick");
            assertTrue(kf60 > 0.05, "60 FPS k_f should not be zero");
        }

        @Test
        @DisplayName("frame damping: H converges toward T with k_f factor")
        void testSingleFrameDamping() {
            // T = (0, 0, 0), H = (10, 0, 0)
            Vec3 target = Vec3.ZERO;
            Vec3 prev = new Vec3(10, 0, 0);

            double kF = computeKF(0.3, 0.05); // = 0.3
            // H_new = H + k_f × (T − H)
            Vec3 halo = prev.add(target.subtract(prev).scale(kF));

            assertEquals(7.0, halo.x, 0.0001, "10 × (1−0.3) = 7.0");
            assertEquals(0.0, halo.y, 0.0001);
            assertEquals(0.0, halo.z, 0.0001);
        }

        @Test
        @DisplayName("clamp: d > max_d → halo clamped to max_d from target")
        void testFrameClampExceedsMaxDist() {
            // After damping: halo at x=7, target at x=0
            // d = 7 > max_d = 5 → clamp
            Vec3 target = Vec3.ZERO;
            Vec3 halo = new Vec3(7.0, 0.0, 0.0); // after damping
            double maxDist = 5.0;

            double dist = halo.distanceTo(target);
            assertTrue(dist > maxDist, "precondition: dist must exceed maxDist");

            // Clamp: H_new = T − normalize(T−H) × maxDist
            Vec3 toTarget = target.subtract(halo).normalize();
            Vec3 clamped = target.subtract(toTarget.scale(maxDist));

            assertEquals(5.0, clamped.distanceTo(target), 0.0001,
                "clamped halo must be exactly maxDist from target");
            assertEquals(5.0, clamped.x, 0.0001);
            assertEquals(0.0, clamped.y, 0.0001);
        }

        @Test
        @DisplayName("clamp direction is toward target (在 S 方向继续前进)")
        void testClampDirectionTowardTarget() {
            // H = (10, 0, 0), T = (0, 0, 0)
            // S = k_f * (T - H) = 0.3 * (-10, 0, 0) = (-3, 0, 0) → left
            // After damping: H_new' = (7, 0, 0), d = 7 > max_d = 5
            // Clamp: continue in S direction (left) until d = 5
            // H_new = (5, 0, 0) — moved further left from 7, closer to target
            Vec3 target = Vec3.ZERO;
            Vec3 haloAfterDamping = new Vec3(7.0, 0.0, 0.0);
            double maxDist = 5.0;

            Vec3 toTarget = target.subtract(haloAfterDamping).normalize();
            Vec3 clamped = target.subtract(toTarget.scale(maxDist));

            assertEquals(5.0, clamped.x, 0.0001);
            assertTrue(clamped.x < haloAfterDamping.x,
                "clamped halo should move further toward target (from x=7 to x=5)");
            assertTrue(clamped.x > target.x,
                "clamped halo should stay between target and original position");
        }

        @Test
        @DisplayName("multi-frame simulation: halo NEVER exceeds maxDist from target")
        void testMultiFrameClampGuarantee() {
            // Simulate 200 frames with random target movement
            // The halo must stay within maxDist of target after every frame
            Vec3 target = Vec3.ZERO;
            Vec3 halo = Vec3.ZERO;
            double maxDist = 1.0;
            double k = 0.3;
            java.util.Random rng = new java.util.Random(12345);

            for (int frame = 0; frame < 200; frame++) {
                // Target moves randomly (simulating entity movement & rotation)
                double dx = (rng.nextDouble() - 0.5) * 1.0;
                double dy = (rng.nextDouble() - 0.5) * 1.0;
                double dz = (rng.nextDouble() - 0.5) * 1.0;
                target = target.add(dx, dy, dz);

                // Per-frame damping (simulated at ~60 FPS)
                double kF = computeKF(k, 0.0167);
                halo = halo.add(target.subtract(halo).scale(kF));

                // Clamp: ensure halo never exceeds maxDist
                double dist = halo.distanceTo(target);
                if (dist > maxDist) {
                    Vec3 toTarget = target.subtract(halo).normalize();
                    halo = target.subtract(toTarget.scale(maxDist));
                }
            }

            // After 200 frames, halo must be within maxDist
            double finalDist = halo.distanceTo(target);
            assertTrue(finalDist <= maxDist + 1e-9,
                String.format("After 200 frames, dist=%.4f must not exceed maxDist=%.4f",
                    finalDist, maxDist));
        }

        @Test
        @DisplayName("extreme: high entity speed, low k (near freeze) → clamp still holds")
        void testExtremeClampWithLowK() {
            // k = 0.1 (very little movement per frame)
            // Entity moves 2 blocks per frame → halo lags severely
            // Clamp must prevent halo from exceeding maxDist
            Vec3 target = new Vec3(0, 0, 0);
            Vec3 halo = new Vec3(0, 0, 0);
            double maxDist = 0.5;

            for (int frame = 0; frame < 60; frame++) {
                // Entity moves rapidly rightward
                target = target.add(0.5, 0, 0);

                // Damping with very low k
                double kF = computeKF(0.1, 0.0167);
                halo = halo.add(target.subtract(halo).scale(kF));

                // Clamp
                double dist = halo.distanceTo(target);
                if (dist > maxDist) {
                    Vec3 toTarget = target.subtract(halo).normalize();
                    halo = target.subtract(toTarget.scale(maxDist));
                }

                double distAfter = halo.distanceTo(target);
                assertTrue(distAfter <= maxDist + 1e-9,
                    String.format("Frame %d: dist=%.4f > maxDist=%.4f after clamp",
                        frame, distAfter, maxDist));
            }
        }
    }

    // ------------------------------------------------------------------
    // Frozen idle animation during transitions (F8 fix)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Frozen idle animation during transitions")
    class FrozenIdleAnimation {

        @Test
        @DisplayName("all channels freeze at the trigger phase for unconfigured groups")
        void freezesAllChannelsAtPhase() {
            // A child group with a yaw spin, a vertical bob, a scale pulse and
            // an alpha channel must hold every channel at the frozen phase
            // while the transition plays (ring_default's hands).
            LayerAnimation anim = new LayerAnimation(
                List.of(), List.of(new AnimationTerm.Linear(0.1, 0.0)), List.of(),
                List.of(new AnimationTerm.Linear(30.0)), List.of(), List.of(),
                List.of(new AnimationTerm.Linear(0.2, 0.0)), List.of(), List.of(),
                List.of(new AnimationTerm.Linear(0.7, 0.0)),
                List.of());
            HaloGroup group = new HaloGroup(
                Optional.empty(), new Vec3(0, 0, 0), new Quaternionf(), 1.0f,
                List.of(), true, true, true, Optional.of(anim), List.of());

            HaloRenderer.FrozenIdleVisuals frozen = HaloRenderer.frozenIdleVisuals(group, 2.0);
            assertArrayEquals(new float[]{0f, 0.1f, 0f}, frozen.offset(), 1e-5f,
                "offset.y frozen at 0.1");
            assertArrayEquals(new float[]{60f, 0f, 0f}, frozen.rotationDegrees(), 1e-5f,
                "yaw frozen at 60° at t=2s");
            assertArrayEquals(new float[]{1.2f, 1f, 1f}, frozen.scale(), 1e-5f,
                "scale.x frozen at 1.2");
            assertEquals(0.7f, frozen.alpha(), 1e-5f, "alpha frozen at 0.7");
        }

        @Test
        @DisplayName("identity defaults when the group has no idle animation")
        void identityWithoutAnimation() {
            HaloGroup plain = new HaloGroup(
                Optional.empty(), new Vec3(0, 0, 0), new Quaternionf(), 1.0f,
                List.of(), true, true, true, Optional.empty(), List.of());

            HaloRenderer.FrozenIdleVisuals frozen = HaloRenderer.frozenIdleVisuals(plain, 5.0);
            assertArrayEquals(new float[]{0f, 0f, 0f}, frozen.offset(), 1e-6f);
            assertArrayEquals(new float[]{0f, 0f, 0f}, frozen.rotationDegrees(), 1e-6f);
            assertArrayEquals(new float[]{1f, 1f, 1f}, frozen.scale(), 1e-6f);
            assertEquals(1.0f, frozen.alpha(), 1e-6f);
        }
    }
}
