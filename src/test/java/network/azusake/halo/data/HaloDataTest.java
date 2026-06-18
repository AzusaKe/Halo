package network.azusake.halo.data;

import network.azusake.halo.animation.*;
import network.azusake.halo.json.HaloDefinitionDeserializer;
import network.azusake.halo.shape.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

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
            HaloAnimation anim = HaloAnimation.EMPTY;
            HaloPositioning pos = new HaloPositioning(Vec3d.ZERO, 1.0);
            HaloDampingConfig damp = new HaloDampingConfig(0.2, 0.2, 2.0, 90.0);

            HaloDefinition def = new HaloDefinition(id, model, anim, pos, damp);
            assertEquals(id, def.id());
            assertEquals(model, def.model());
            assertEquals(anim, def.animation());
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
                    "positionCurves": [
                      {
                        "axis": "y",
                        "curve": {
                          "type": "oscillate",
                          "amplitude": 0.08,
                          "frequency": 1.5,
                          "phase": 0.0
                        }
                      }
                    ],
                    "rotationCurves": [
                      {
                        "axis": "yaw",
                        "curve": {
                          "type": "linear",
                          "start": 0.0,
                          "speed": 30.0
                        }
                      }
                    ]
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
            assertEquals(1, def.animation().positionCurves().size());
            assertEquals(1, def.animation().rotationCurves().size());

            // Positioning
            assertEquals(0.0, def.positioning().offset().x, 0.001);
            assertEquals(1.8, def.positioning().offset().y, 0.001);

            // Damping
            assertEquals(0.15, def.damping().linearFactor(), 0.001);
            assertEquals(3.0, def.damping().maxLinearDistance(), 0.001);
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
                  "animation": {
                    "positionCurves": [],
                    "rotationCurves": []
                  },
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
                  "animation": {
                    "positionCurves": [],
                    "rotationCurves": []
                  },
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
        @DisplayName("ConstantCurve JSON parse")
        void parseConstantCurve() {
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
                    "positionCurves": [
                      {
                        "axis": "y",
                        "curve": {
                          "type": "constant",
                          "value": 0.2
                        }
                      }
                    ],
                    "rotationCurves": []
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

            assertEquals(1, def.animation().positionCurves().size());
            assertInstanceOf(ConstantCurve.class, def.animation().positionCurves().get(0).curve());
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
                  "animation": {
                    "positionCurves": [],
                    "rotationCurves": []
                  },
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
