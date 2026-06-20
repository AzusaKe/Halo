package network.azusake.halo.data;

import network.azusake.halo.animation.*;
import network.azusake.halo.json.HaloDefinitionDeserializer;
import network.azusake.halo.shape.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the halo data model.
 */
class HaloDataTest {

    private final HaloDefinitionDeserializer deserializer = new HaloDefinitionDeserializer();
    private final Gson gson = deserializer.getGson();

    // ------------------------------------------------------------------
    // 1. Core data records
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Core data records")
    class CoreRecords {

        @Test
        @DisplayName("HaloDampingConfig: canonical construction and accessors")
        void dampingConfig() {
            HaloDampingConfig d = new HaloDampingConfig(0.15, 0.1, 3.0, 180.0);
            assertEquals(0.15, d.linearFactor());
            assertEquals(0.1, d.angularFactor());
            assertEquals(3.0, d.maxLinearDistance());
            assertEquals(180.0, d.maxAngularDegrees());
        }

        @Test
        @DisplayName("HaloPositioning: canonical construction")
        void positioning() {
            Vec3d offset = new Vec3d(0, 1.8, 0);
            HaloPositioning p = new HaloPositioning(offset, 1.0);
            assertEquals(offset, p.offset());
            assertEquals(1.0, p.scale());
        }

        @Test
        @DisplayName("OrientationMode enum values")
        void orientationMode() {
            assertEquals(OrientationMode.LOCKED, OrientationMode.valueOf("LOCKED"));
            assertEquals(OrientationMode.FREE, OrientationMode.valueOf("FREE"));
            assertEquals(OrientationMode.SYNC, OrientationMode.valueOf("SYNC"));
        }

        @Test
        @DisplayName("HaloDefinition: all fields accessible")
        void definition() {
            Identifier id = new Identifier("halo", "test");
            BillboardPrimitive bp = new BillboardPrimitive(
                new Identifier("halo", "tex"),
                new Vector2f(1, 1),
                null
            );
            HaloLayer layer = new HaloLayer(Vec3d.ZERO, bp);
            HaloModel model = new HaloModel(OrientationMode.LOCKED, List.of(layer));
            HaloPositioning pos = new HaloPositioning(Vec3d.ZERO, 1.0);
            HaloDampingConfig damp = new HaloDampingConfig(0.2, 0.2, 2.0, 90.0);

            HaloDefinition def = new HaloDefinition(id, model, Optional.empty(), pos, damp);
            assertEquals(id, def.id());
            assertEquals(model, def.model());
            assertTrue(def.animation().isEmpty());
            assertEquals(pos, def.positioning());
            assertEquals(damp, def.damping());
        }
    }

    // ------------------------------------------------------------------
    // 2. HaloInstance
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("HaloInstance")
    class InstanceTests {

        @Test
        @DisplayName("new instance starts with needsSnap = true")
        void newInstanceNeedsSnap() {
            HaloInstance inst = new HaloInstance(
                java.util.UUID.randomUUID(),
                new Identifier("halo", "ring_default")
            );
            assertTrue(inst.isNeedsSnap());
        }

        @Test
        @DisplayName("markNeedsSnap sets flag")
        void markNeedsSnap() {
            HaloInstance inst = new HaloInstance(
                java.util.UUID.randomUUID(),
                new Identifier("halo", "ring_default")
            );
            inst.setNeedsSnap(false);
            assertFalse(inst.isNeedsSnap());
            inst.markNeedsSnap();
            assertTrue(inst.isNeedsSnap());
        }
    }

    // ------------------------------------------------------------------
    // 3. Primitive / Model hierarchy
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Primitive and Model hierarchy")
    class ShapeTests {

        @Test
        @DisplayName("BillboardPrimitive implements HaloPrimitive")
        void billboardIsHaloPrimitive() {
            BillboardPrimitive b = new BillboardPrimitive(
                new Identifier("halo", "ring"),
                new Vector2f(0.5f, 0.5f),
                null
            );
            assertInstanceOf(HaloPrimitive.class, b);
        }

        @Test
        @DisplayName("HaloModel layers list preserves order")
        void modelLayersOrder() {
            var bp1 = new BillboardPrimitive(new Identifier("halo", "a"), new Vector2f(1, 1), null);
            var bp2 = new BillboardPrimitive(new Identifier("halo", "b"), new Vector2f(2, 2), null);
            HaloModel m = new HaloModel(OrientationMode.LOCKED, List.of(
                new HaloLayer(Vec3d.ZERO, bp1),
                new HaloLayer(new Vec3d(0, 0.2, 0), bp2)
            ));
            assertEquals(2, m.layers().size());
            assertEquals(OrientationMode.LOCKED, m.orientationMode());
            assertEquals(bp1, m.layers().get(0).primitive());
            assertEquals(bp2, m.layers().get(1).primitive());
        }

        @Test
        @DisplayName("GlowLayer and PulseConfig records")
        void glowAndPulse() {
            PulseConfig pulse = new PulseConfig(0.2f, 2.0f, 0.0f);
            assertEquals(0.2f, pulse.amplitude());
            assertEquals(2.0f, pulse.frequency());

            GlowLayer glow = new GlowLayer(
                new Identifier("halo", "glow"),
                new Vector2f(0.6f, 0.6f),
                0xFFD700,
                0.8f,
                pulse
            );
            assertEquals(0xFFD700, glow.color());
            assertEquals(0.8f, glow.alpha());
            assertEquals(pulse, glow.pulse());
        }
    }

    // ------------------------------------------------------------------
    // 4. Animation sealed hierarchy
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Animation sealed hierarchy")
    class AnimationTests {

        @Test
        @DisplayName("ConstantCurve always returns its value")
        void constantCurve() {
            ConstantCurve c = new ConstantCurve(5.0);
            assertEquals(5.0, c.evaluate(0));
            assertEquals(5.0, c.evaluate(100));
        }

        @Test
        @DisplayName("LinearCurve: value(t) = start + speed * t")
        void linearCurve() {
            LinearCurve l = new LinearCurve(10.0, 2.0);
            assertEquals(10.0, l.evaluate(0));
            assertEquals(14.0, l.evaluate(2));
            assertEquals(30.0, l.evaluate(10));
        }

        @Test
        @DisplayName("OscillateCurve: value(t) = A * sin(w*t + phi)")
        void oscillateCurve() {
            OscillateCurve o = new OscillateCurve(1.0, Math.PI / 2, 0.0);
            assertEquals(0.0, o.evaluate(0), 1e-9);
            assertEquals(1.0, o.evaluate(1), 1e-9);
            assertEquals(0.0, o.evaluate(2), 1e-9);
        }

        @Test
        @DisplayName("PositionCurve and RotationCurve enums parse correctly")
        void curveAxisEnums() {
            assertEquals(PositionCurve.PositionAxis.X, PositionCurve.PositionAxis.valueOf("X"));
            assertEquals(PositionCurve.PositionAxis.Y, PositionCurve.PositionAxis.valueOf("Y"));
            assertEquals(RotationCurve.RotationAxis.YAW, RotationCurve.RotationAxis.valueOf("YAW"));
            assertEquals(RotationCurve.RotationAxis.PITCH, RotationCurve.RotationAxis.valueOf("PITCH"));
        }

        @Test
        @DisplayName("HaloAnimation.EMPTY has no curves")
        void emptyAnimation() {
            assertTrue(HaloAnimation.EMPTY.positionCurves().isEmpty());
            assertTrue(HaloAnimation.EMPTY.rotationCurves().isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // 4b. AnimationTerm hierarchy (per-layer trig/linear terms)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("AnimationTerm hierarchy")
    class AnimationTermTests {

        @Test
        @DisplayName("Sin: evaluate(0) = 0")
        void sinTermAtZeroReturnsZero() {
            AnimationTerm.Sin s = new AnimationTerm.Sin(1.0, 1.0, 0.0);
            assertEquals(0.0, s.evaluate(0.0), 1e-9);
        }

        @Test
        @DisplayName("Sin: evaluate(0.5) = A with omega=1 (quarter-period)")
        void sinTermQuarterPeriod() {
            AnimationTerm.Sin s = new AnimationTerm.Sin(1.0, 1.0, 0.0);
            // omega=1 → angular freq = π rad/s → at t=0.5, arg = π/2, sin = 1
            assertEquals(1.0, s.evaluate(0.5), 1e-9);
        }

        @Test
        @DisplayName("Sin: omega=2 has period 1 s")
        void sinTermOmegaTwoPeriod() {
            AnimationTerm.Sin s = new AnimationTerm.Sin(1.0, 2.0, 0.0);
            assertEquals(0.0, s.evaluate(0.0), 1e-9);
            assertEquals(0.0, s.evaluate(0.5), 1e-9);   // sin(π)=0
            assertEquals(0.0, s.evaluate(1.0), 1e-9);   // sin(2π)=0
        }

        @Test
        @DisplayName("Sin: with phi=π/2 acts like cos")
        void sinTermPhaseShift() {
            AnimationTerm.Sin s = new AnimationTerm.Sin(1.0, 1.0, Math.PI / 2);
            assertEquals(1.0, s.evaluate(0.0), 1e-9);   // sin(π/2)=1 = cos(0)
        }

        @Test
        @DisplayName("Sin: amplitude scales output")
        void sinTermAmplitude() {
            AnimationTerm.Sin s = new AnimationTerm.Sin(3.0, 1.0, 0.0);
            assertEquals(3.0, s.evaluate(0.5), 1e-9);   // 3 * sin(π/2)
        }

        @Test
        @DisplayName("Cos: evaluate(0) = A")
        void cosTermAtZeroReturnsAmplitude() {
            AnimationTerm.Cos c = new AnimationTerm.Cos(1.0, 1.0, 0.0);
            assertEquals(1.0, c.evaluate(0.0), 1e-9);
        }

        @Test
        @DisplayName("Cos: evaluate(0.5) = 0 with omega=1")
        void cosTermQuarterPeriod() {
            AnimationTerm.Cos c = new AnimationTerm.Cos(1.0, 1.0, 0.0);
            assertEquals(0.0, c.evaluate(0.5), 1e-9);   // cos(π/2)=0
        }

        @Test
        @DisplayName("Cos: convenience constructor defaults phi=0")
        void cosTermDefaultPhi() {
            AnimationTerm.Cos c = new AnimationTerm.Cos(1.0, 1.0);
            assertEquals(1.0, c.evaluate(0.0), 1e-9);
        }

        @Test
        @DisplayName("Linear: value(t) = speed * t")
        void linearTerm() {
            AnimationTerm.Linear l = new AnimationTerm.Linear(30.0);
            assertEquals(0.0, l.evaluate(0.0), 1e-9);
            assertEquals(30.0, l.evaluate(1.0), 1e-9);
            assertEquals(150.0, l.evaluate(5.0), 1e-9);
        }

        @Test
        @DisplayName("Linear: negative speed for reverse rotation")
        void linearTermNegativeSpeed() {
            AnimationTerm.Linear l = new AnimationTerm.Linear(-15.0);
            assertEquals(-15.0, l.evaluate(1.0), 1e-9);
        }

        @Test
        @DisplayName("AnimationTerm is sealed and permits only Sin, Cos, Linear")
        void sealedHierarchy() {
            assertInstanceOf(AnimationTerm.class, new AnimationTerm.Sin(1.0, 1.0));
            assertInstanceOf(AnimationTerm.class, new AnimationTerm.Cos(1.0, 1.0));
            assertInstanceOf(AnimationTerm.class, new AnimationTerm.Linear(1.0));
        }
    }

    // ------------------------------------------------------------------
    // 4c. LayerAnimation evaluation
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("LayerAnimation evaluation")
    class LayerAnimationTests {

        @Test
        @DisplayName("EMPTY evaluates to Vec3d.ZERO offset")
        void emptyEvaluatesToZeroOffset() {
            Vec3d off = LayerAnimation.EMPTY.evaluateOffset(0.0);
            assertEquals(0.0, off.x, 1e-9);
            assertEquals(0.0, off.y, 1e-9);
            assertEquals(0.0, off.z, 1e-9);
        }

        @Test
        @DisplayName("EMPTY evaluates to identity quaternion")
        void emptyEvaluatesToIdentityRotation() {
            Quaternionf q = LayerAnimation.EMPTY.evaluateRotation(0.0);
            assertEquals(0.0f, q.x(), 1e-6f);
            assertEquals(0.0f, q.y(), 1e-6f);
            assertEquals(0.0f, q.z(), 1e-6f);
            assertEquals(1.0f, q.w(), 1e-6f);
        }

        @Test
        @DisplayName("isEmpty returns true for EMPTY")
        void emptyIsEmpty() {
            assertTrue(LayerAnimation.EMPTY.isEmpty());
        }

        @Test
        @DisplayName("Single Y offset sin term evaluates correctly")
        void singleYOffset() {
            LayerAnimation anim = new LayerAnimation(
                List.of(),                                        // offsetX
                List.of(new AnimationTerm.Sin(0.08, 1.5, 0.0)),  // offsetY
                List.of(),                                        // offsetZ
                List.of(), List.of(), List.of()                   // rotations
            );
            assertFalse(anim.isEmpty());
            Vec3d off = anim.evaluateOffset(1.0 / 3.0);
            // omega=1.5 → ωπ = 1.5π → at t=1/3: arg = 1.5π/3 = π/2, sin=1
            assertEquals(0.08, off.y, 1e-9);
            assertEquals(0.0, off.x, 1e-9);
            assertEquals(0.0, off.z, 1e-9);
        }

        @Test
        @DisplayName("Superposition: two terms on same axis are summed")
        void superpositionOnSameAxis() {
            // sin(A=1, ω=1) at t=0.5 → sin(π/2)=1
            // cos(A=0.5, ω=2) at t=0 → cos(0)=0.5
            // Together at t=0: 0 + 0.5 = 0.5
            LayerAnimation anim = new LayerAnimation(
                List.of(new AnimationTerm.Sin(1.0, 1.0, 0.0),
                        new AnimationTerm.Cos(0.5, 2.0, 0.0)),
                List.of(), List.of(),
                List.of(), List.of(), List.of()
            );
            Vec3d off = anim.evaluateOffset(0.0);
            assertEquals(0.5, off.x, 1e-9);
        }

        @Test
        @DisplayName("Rotation yaw linear term: degrees converted to radians")
        void rotationYawLinear() {
            // 30 deg/s at t=2 → 60 degrees → Math.toRadians(60)
            LayerAnimation anim = new LayerAnimation(
                List.of(), List.of(), List.of(),
                List.of(new AnimationTerm.Linear(30.0)),  // yaw
                List.of(),                                // pitch
                List.of()                                 // roll
            );
            Quaternionf q = anim.evaluateRotation(2.0);
            // A pure yaw rotation around Y: should produce non-identity quaternion
            assertNotEquals(0.0f, q.y(), 1e-6f, "Y component should be non-zero for yaw rotation");
            assertEquals(0.0f, q.x(), 1e-6f);
            assertEquals(0.0f, q.z(), 1e-6f);
            assertTrue(q.w() > 0.0f, "W should be positive for 60-degree rotation");
        }

        @Test
        @DisplayName("Rotation pitch sin term: oscillation in degrees")
        void rotationPitchSin() {
            // sin(A=5°, ω=1) at t=0.5 → 5 * sin(π/2) = 5 degrees → radians
            LayerAnimation anim = new LayerAnimation(
                List.of(), List.of(), List.of(),
                List.of(),                                          // yaw
                List.of(new AnimationTerm.Sin(5.0, 1.0, 0.0)),     // pitch
                List.of()                                           // roll
            );
            Quaternionf q = anim.evaluateRotation(0.5);
            // A pure pitch rotation around X: should produce non-identity quaternion
            assertTrue(Math.abs(q.x()) > 0.0f || Math.abs(q.y()) > 0.0f || Math.abs(q.z()) > 0.0f,
                "Quaternion should be non-identity for 5-degree pitch");
        }

        @Test
        @DisplayName("Combined offset and rotation evaluate independently")
        void combinedOffsetAndRotation() {
            LayerAnimation anim = new LayerAnimation(
                List.of(new AnimationTerm.Linear(0.1)),  // offsetX: drift 0.1 blocks/s
                List.of(new AnimationTerm.Sin(0.08, 1.0)),
                List.of(),
                List.of(new AnimationTerm.Linear(30.0)), // yaw: spin 30 deg/s
                List.of(),
                List.of()
            );
            assertFalse(anim.isEmpty());

            Vec3d off = anim.evaluateOffset(1.0);
            assertEquals(0.1, off.x, 1e-9);
            assertEquals(0.0, off.y, 1e-9);  // sin(π)=0

            Quaternionf q = anim.evaluateRotation(1.0);
            assertNotEquals(0.0f, q.y(), 1e-6f, "30-degree yaw should produce non-zero Y quat component");
        }
    }

    // ------------------------------------------------------------------
    // 5. JSON round-trip
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("JSON serialization / deserialization round-trip")
    class JsonRoundTrip {

        @Test
        @DisplayName("Parse new layers format JSON → HaloDefinition")
        void parseNewLayersFormat() {
            String json = """
                {
                  "id": "halo:ring_default",
                  "orientation_mode": "locked",
                  "layers": [
                    {
                      "position": [0.0, 0.0, 0.0],
                      "rotation": [0.0, 0.0, 0.0],
                      "scale": 1.0,
                      "primitive": {
                        "type": "billboard",
                        "texture": "halo:textures/halo/ring.png",
                        "size": [0.5, 0.5],
                        "glow": {
                          "texture": "halo:textures/halo/ring_glow.png",
                          "size": [0.6, 0.6],
                          "color": 16777215,
                          "alpha": 0.8,
                          "pulse": {
                            "amplitude": 0.15,
                            "frequency": 2.0,
                            "phase": 0.0
                          }
                        }
                      }
                    }
                  ],
                  "animation": {
                    "offset": {
                      "y": [
                        {"function": "sin", "A": 0.08, "omega": 0.5}
                      ]
                    },
                    "rotation": {
                      "yaw": [
                        {"function": "linear", "speed": 30.0}
                      ]
                    }
                  },
                  "positioning": {
                    "offset": [0.0, 1.8, 0.0],
                    "scale": 1.0
                  },
                  "damping": {
                    "linearFactor": 0.15,
                    "angularFactor": 0.1,
                    "maxLinearDistance": 3.0,
                    "maxAngularDegrees": 180.0
                  }
                }
                """;

            HaloDefinition def = deserializer.deserialize(
                gson.fromJson(json, JsonObject.class),
                HaloDefinition.class,
                null
            );

            assertEquals("halo:ring_default", def.id().toString());
            assertEquals(OrientationMode.LOCKED, def.model().orientationMode());
            assertEquals(1, def.model().layers().size());

            HaloLayer layer = def.model().layers().get(0);
            assertInstanceOf(BillboardPrimitive.class, layer.primitive());
            BillboardPrimitive bp = (BillboardPrimitive) layer.primitive();
            assertEquals("halo:textures/halo/ring.png", bp.texture().toString());
            assertEquals(0.5f, bp.size().x, 0.001f);
            assertEquals(0.5f, bp.size().y, 0.001f);
            assertNotNull(bp.glow());
            assertEquals(0xFF_FFFF, bp.glow().color());
            assertEquals(0.8f, bp.glow().alpha(), 0.001f);
            assertNotNull(bp.glow().pulse());
            assertEquals(0.15f, bp.glow().pulse().amplitude(), 0.001f);

            // Animation
            assertTrue(def.animation().isPresent());
            LayerAnimation anim = def.animation().get();
            assertEquals(1, anim.offsetY().size());
            assertEquals(1, anim.rotationYaw().size());
            assertFalse(anim.isEmpty());

            // Positioning
            assertEquals(0.0, def.positioning().offset().x, 0.001);
            assertEquals(1.8, def.positioning().offset().y, 0.001);

            // Damping
            assertEquals(0.15, def.damping().linearFactor(), 0.001);
            assertEquals(3.0, def.damping().maxLinearDistance(), 0.001);
        }

        @Test
        @DisplayName("Parse SYNC mode with sync_offset")
        void parseSyncMode() {
            String json = """
                {
                  "id": "halo:sync_test",
                  "orientation_mode": "sync",
                  "sync_offset": [15.0, -5.0, 0.0],
                  "layers": [
                    {
                      "position": [0.0, 0.0, 0.0],
                      "rotation": [0.0, 0.0, 0.0],
                      "scale": 1.0,
                      "primitive": {
                        "type": "billboard",
                        "texture": "halo:textures/halo/ring.png",
                        "size": [0.5, 0.5]
                      }
                    }
                  ],
                  "positioning": {
                    "offset": [0.0, 0.4, 0.35],
                    "scale": 1.0
                  },
                  "damping": {
                    "linearFactor": 0.15,
                    "angularFactor": 0.1,
                    "maxLinearDistance": 1.0,
                    "maxAngularDegrees": 180.0
                  }
                }
                """;

            HaloDefinition def = deserializer.deserialize(
                gson.fromJson(json, JsonObject.class),
                HaloDefinition.class,
                null
            );

            assertEquals(OrientationMode.SYNC, def.model().orientationMode());
            assertEquals(1, def.model().layers().size());

            // sync_offset should be a non-identity quaternion
            Quaternionf off = def.model().syncOffset();
            assertNotNull(off);
            // It should NOT be identity (15° yaw, -5° pitch)
            float angle = off.angle();
            assertTrue(angle > 0.001f, "SYNC offset quaternion should be non-identity");
        }

        @Test
        @DisplayName("Legacy 'shape' format is backward-compatible")
        void parseLegacyShapeFormat() {
            String json = """
                {
                  "id": "halo:legacy",
                  "shape": {
                    "type": "billboard",
                    "texture": "halo:textures/halo/ring.png",
                    "size": [0.5, 0.5]
                  },
                  "animation": {},
                  "positioning": {
                    "offset": [0.0, 1.5, 0.0]
                  },
                  "damping": {
                    "linearFactor": 0.1,
                    "angularFactor": 0.1,
                    "maxLinearDistance": 2.0,
                    "maxAngularDegrees": 90.0
                  }
                }
                """;

            HaloDefinition def = deserializer.deserialize(
                gson.fromJson(json, JsonObject.class),
                HaloDefinition.class,
                null
            );

            // Legacy shape → one layer at origin with billboard primitive
            assertEquals(1, def.model().layers().size());
            assertEquals(OrientationMode.LOCKED, def.model().orientationMode()); // default
            assertInstanceOf(BillboardPrimitive.class, def.model().layers().get(0).primitive());
        }

        @Test
        @DisplayName("Legacy multi_billboard format is backward-compatible")
        void parseLegacyMultiBillboard() {
            String json = """
                {
                  "id": "halo:multi_test",
                  "shape": {
                    "type": "multi_billboard",
                    "layers": [
                      {
                        "type": "billboard",
                        "texture": "halo:textures/halo/back.png",
                        "size": [1.0, 1.0],
                        "glow": null
                      },
                      {
                        "type": "billboard",
                        "texture": "halo:textures/halo/front.png",
                        "size": [0.8, 0.8],
                        "glow": null
                      }
                    ]
                  },
                  "animation": {},
                  "positioning": {
                    "offset": [0.0, 1.5, 0.0],
                    "scale": 1.0
                  },
                  "damping": {
                    "linearFactor": 0.1,
                    "angularFactor": 0.1,
                    "maxLinearDistance": 2.0,
                    "maxAngularDegrees": 90.0
                  }
                }
                """;

            HaloDefinition def = deserializer.deserialize(
                gson.fromJson(json, JsonObject.class),
                HaloDefinition.class,
                null
            );

            // Legacy multi_billboard → multiple layers at origin
            assertEquals(2, def.model().layers().size());
            assertInstanceOf(BillboardPrimitive.class, def.model().layers().get(0).primitive());
            assertEquals("halo:textures/halo/back.png",
                ((BillboardPrimitive) def.model().layers().get(0).primitive()).texture().toString());
        }

        @Test
        @DisplayName("Animation JSON parse with new format")
        void parseAnimationNewFormat() {
            String json = """
                {
                  "id": "halo:static_test",
                  "layers": [
                    {
                      "position": [0.0, 0.0, 0.0],
                      "primitive": {
                        "type": "billboard",
                        "texture": "halo:textures/halo/ring.png",
                        "size": [0.5, 0.5]
                      }
                    }
                  ],
                  "animation": {
                    "offset": {
                      "y": [
                        {"function": "sin", "A": 0.2, "omega": 1.0}
                      ]
                    }
                  },
                  "positioning": {
                    "offset": [0.0, 2.0, 0.0]
                  },
                  "damping": {
                    "linearFactor": 0.1,
                    "angularFactor": 0.1,
                    "maxLinearDistance": 2.0,
                    "maxAngularDegrees": 90.0
                  }
                }
                """;

            HaloDefinition def = deserializer.deserialize(
                gson.fromJson(json, JsonObject.class),
                HaloDefinition.class,
                null
            );

            assertTrue(def.animation().isPresent());
            assertEquals(1, def.animation().get().offsetY().size());
            assertInstanceOf(AnimationTerm.Sin.class, def.animation().get().offsetY().get(0));
        }

        @Test
        @DisplayName("Missing optional fields use defaults")
        void missingOptionalFieldsFallback() {
            String json = """
                {
                  "id": "halo:minimal",
                  "layers": [
                    {
                      "primitive": {
                        "type": "billboard",
                        "texture": "halo:textures/halo/ring.png",
                        "size": [0.5, 0.5]
                      }
                    }
                  ],
                  "animation": {},
                  "positioning": {
                    "offset": [0.0, 1.8, 0.0]
                  },
                  "damping": {
                    "linearFactor": 0.15,
                    "angularFactor": 0.1,
                    "maxLinearDistance": 3.0,
                    "maxAngularDegrees": 180.0
                  }
                }
                """;

            HaloDefinition def = deserializer.deserialize(
                gson.fromJson(json, JsonObject.class),
                HaloDefinition.class,
                null
            );

            HaloLayer layer = def.model().layers().get(0);
            assertEquals(Vec3d.ZERO, layer.position());             // default position
            assertEquals(1.0f, layer.scale(), 0.001f);              // default scale
            assertInstanceOf(BillboardPrimitive.class, layer.primitive());
            assertNull(((BillboardPrimitive) layer.primitive()).glow());  // no glow
            assertEquals(1.0, def.positioning().scale(), 0.001);    // scale default
        }

        @Test
        @DisplayName("Per-layer animation block parsed correctly")
        void parseLayerAnimation() {
            String json = """
                {
                  "id": "halo:animated_test",
                  "layers": [
                    {
                      "position": [0.0, 0.03, 0.0],
                      "rotation": [0.0, 0.0, 0.0],
                      "scale": 1.0,
                      "animation": {
                        "offset": {
                          "x": [
                            {"function": "sin", "A": 1.0, "omega": 1.0, "phi": 0.0},
                            {"function": "cos", "A": 0.5, "omega": 2.0}
                          ],
                          "y": [
                            {"function": "sin", "A": 0.08, "omega": 1.5}
                          ]
                        },
                        "rotation": {
                          "yaw": [
                            {"function": "linear", "speed": 30.0},
                            {"function": "sin", "A": 5.0, "omega": 0.5}
                          ]
                        }
                      },
                      "primitive": {
                        "type": "billboard",
                        "texture": "halo:textures/halo/ring.png",
                        "size": [0.5, 0.5]
                      }
                    }
                  ],
                  "animation": {},
                  "positioning": {
                    "offset": [0.0, 0.4, 0.35],
                    "scale": 1.0
                  },
                  "damping": {
                    "linearFactor": 0.15,
                    "angularFactor": 0.1,
                    "maxLinearDistance": 3.0,
                    "maxAngularDegrees": 180.0
                  }
                }
                """;

            HaloDefinition def = deserializer.deserialize(
                gson.fromJson(json, JsonObject.class),
                HaloDefinition.class,
                null
            );

            HaloLayer layer = def.model().layers().get(0);
            assertTrue(layer.animation().isPresent(), "Layer should have animation");
            LayerAnimation anim = layer.animation().get();

            // offsetX: 2 terms (sin + cos)
            assertEquals(2, anim.offsetX().size());
            assertInstanceOf(AnimationTerm.Sin.class, anim.offsetX().get(0));
            assertEquals(1.0, ((AnimationTerm.Sin) anim.offsetX().get(0)).A(), 1e-9);
            assertEquals(1.0, ((AnimationTerm.Sin) anim.offsetX().get(0)).omega(), 1e-9);
            assertInstanceOf(AnimationTerm.Cos.class, anim.offsetX().get(1));
            assertEquals(0.5, ((AnimationTerm.Cos) anim.offsetX().get(1)).A(), 1e-9);

            // offsetY: 1 term
            assertEquals(1, anim.offsetY().size());

            // offsetZ: empty
            assertEquals(0, anim.offsetZ().size());

            // rotationYaw: 2 terms (linear + sin)
            assertEquals(2, anim.rotationYaw().size());
            assertInstanceOf(AnimationTerm.Linear.class, anim.rotationYaw().get(0));
            assertEquals(30.0, ((AnimationTerm.Linear) anim.rotationYaw().get(0)).speed(), 1e-9);
            assertInstanceOf(AnimationTerm.Sin.class, anim.rotationYaw().get(1));

            // rotationPitch and roll: empty
            assertEquals(0, anim.rotationPitch().size());
            assertEquals(0, anim.rotationRoll().size());

            assertFalse(anim.isEmpty(), "Animation should not be empty");
        }

        @Test
        @DisplayName("Per-layer animation missing block returns Optional.empty()")
        void missingLayerAnimationIsEmpty() {
            String json = """
                {
                  "id": "halo:no_anim",
                  "layers": [
                    {
                      "position": [0.0, 0.0, 0.0],
                      "primitive": {
                        "type": "billboard",
                        "texture": "halo:textures/halo/ring.png",
                        "size": [0.5, 0.5]
                      }
                    }
                  ],
                  "animation": {},
                  "positioning": { "offset": [0.0, 0.4, 0.35], "scale": 1.0 },
                  "damping": { "linearFactor": 0.15, "angularFactor": 0.1, "maxLinearDistance": 3.0, "maxAngularDegrees": 180.0 }
                }
                """;

            HaloDefinition def = deserializer.deserialize(
                gson.fromJson(json, JsonObject.class),
                HaloDefinition.class,
                null
            );

            assertTrue(def.model().layers().get(0).animation().isEmpty(),
                "Layer without animation block should have empty Optional");
        }

        @Test
        @DisplayName("Per-layer animation with empty offset/rotation is Optional.empty()")
        void emptyAnimationBlockIsEmpty() {
            String json = """
                {
                  "id": "halo:empty_anim",
                  "layers": [
                    {
                      "position": [0.0, 0.0, 0.0],
                      "animation": {},
                      "primitive": {
                        "type": "billboard",
                        "texture": "halo:textures/halo/ring.png",
                        "size": [0.5, 0.5]
                      }
                    }
                  ],
                  "animation": {},
                  "positioning": { "offset": [0.0, 0.4, 0.35], "scale": 1.0 },
                  "damping": { "linearFactor": 0.15, "angularFactor": 0.1, "maxLinearDistance": 3.0, "maxAngularDegrees": 180.0 }
                }
                """;

            HaloDefinition def = deserializer.deserialize(
                gson.fromJson(json, JsonObject.class),
                HaloDefinition.class,
                null
            );

            assertTrue(def.model().layers().get(0).animation().isEmpty(),
                "Layer with empty animation object should have empty Optional");
        }

        @Test
        @DisplayName("Vec3d adapter: array ↔ Vec3d round-trip")
        void vec3dAdapterRoundTrip() {
            Vec3d original = new Vec3d(1.5, -2.0, 3.25);
            String serialized = gson.toJson(original);
            Vec3d deserialized = gson.fromJson(serialized, Vec3d.class);
            assertEquals(original.x, deserialized.x, 1e-9);
            assertEquals(original.y, deserialized.y, 1e-9);
            assertEquals(original.z, deserialized.z, 1e-9);
        }

        @Test
        @DisplayName("Vector2f adapter: array ↔ Vector2f round-trip")
        void vec2fAdapterRoundTrip() {
            Vector2f original = new Vector2f(0.5f, 0.75f);
            String serialized = gson.toJson(original);
            Vector2f deserialized = gson.fromJson(serialized, Vector2f.class);
            assertEquals(original.x, deserialized.x, 1e-6);
            assertEquals(original.y, deserialized.y, 1e-6);
        }

        @Test
        @DisplayName("Identifier adapter: string ↔ Identifier round-trip")
        void identifierAdapterRoundTrip() {
            Identifier original = new Identifier("halo", "textures/halo/ring");
            String serialized = gson.toJson(original);
            assertTrue(serialized.contains("halo:textures/halo/ring"));
            Identifier deserialized = gson.fromJson(serialized, Identifier.class);
            assertEquals(original.toString(), deserialized.toString());
        }
    }
}
