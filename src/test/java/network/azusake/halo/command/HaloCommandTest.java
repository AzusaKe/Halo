package network.azusake.halo.command;

import network.azusake.halo.config.HaloConfig;
import network.azusake.halo.data.HaloDampingConfig;
import network.azusake.halo.data.HaloPositioning;
import network.azusake.halo.manager.HaloManager;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the halo command / config / manager layer.
 *
 * <p>Tests that do not require a running Minecraft server.  Command parsing
 * and in-game behaviour are verified manually via the in-game checklist.</p>
 */
class HaloCommandTest {

    // ------------------------------------------------------------------
    // 1. HaloConfig — bounds checking
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("HaloConfig bounds checking")
    class ConfigBounds {

        @Test
        @DisplayName("linearDampingFactor clamped to [0, 1]")
        void linearDampingClamped() {
            HaloConfig config = new HaloConfig();

            config.setLinearDampingFactor(0.5);
            assertEquals(0.5, config.getLinearDampingFactor(), 1e-9);

            config.setLinearDampingFactor(-0.5);
            assertEquals(0.0, config.getLinearDampingFactor(), 1e-9);

            config.setLinearDampingFactor(1.5);
            assertEquals(1.0, config.getLinearDampingFactor(), 1e-9);

            config.setLinearDampingFactor(0.0);
            assertEquals(0.0, config.getLinearDampingFactor(), 1e-9);

            config.setLinearDampingFactor(1.0);
            assertEquals(1.0, config.getLinearDampingFactor(), 1e-9);
        }

        @Test
        @DisplayName("angularDampingFactor clamped to [0, 1]")
        void angularDampingClamped() {
            HaloConfig config = new HaloConfig();

            config.setAngularDampingFactor(0.7);
            assertEquals(0.7, config.getAngularDampingFactor(), 1e-9);

            config.setAngularDampingFactor(-1.0);
            assertEquals(0.0, config.getAngularDampingFactor(), 1e-9);

            config.setAngularDampingFactor(2.0);
            assertEquals(1.0, config.getAngularDampingFactor(), 1e-9);
        }

        @Test
        @DisplayName("maxLinearDistance clamped to >= 0.01")
        void maxLinearDistanceClamped() {
            HaloConfig config = new HaloConfig();

            config.setMaxLinearDistance(2.5);
            assertEquals(2.5, config.getMaxLinearDistance(), 1e-9);

            config.setMaxLinearDistance(0.0);
            assertEquals(0.01, config.getMaxLinearDistance(), 1e-9);

            config.setMaxLinearDistance(-5.0);
            assertEquals(0.01, config.getMaxLinearDistance(), 1e-9);
        }

        @Test
        @DisplayName("maxAngularDegrees clamped to >= 1.0")
        void maxAngularDegreesClamped() {
            HaloConfig config = new HaloConfig();

            config.setMaxAngularDegrees(90.0);
            assertEquals(90.0, config.getMaxAngularDegrees(), 1e-9);

            config.setMaxAngularDegrees(0.5);
            assertEquals(1.0, config.getMaxAngularDegrees(), 1e-9);

            config.setMaxAngularDegrees(-10.0);
            assertEquals(1.0, config.getMaxAngularDegrees(), 1e-9);
        }

        @Test
        @DisplayName("haloScale clamped to [0.1, 5.0]")
        void haloScaleClamped() {
            HaloConfig config = new HaloConfig();

            config.setHaloScale(2.0);
            assertEquals(2.0, config.getHaloScale(), 1e-9);

            config.setHaloScale(0.05);
            assertEquals(0.1, config.getHaloScale(), 1e-9);

            config.setHaloScale(10.0);
            assertEquals(5.0, config.getHaloScale(), 1e-9);

            config.setHaloScale(0.1);
            assertEquals(0.1, config.getHaloScale(), 1e-9);

            config.setHaloScale(5.0);
            assertEquals(5.0, config.getHaloScale(), 1e-9);
        }

        @Test
        @DisplayName("positionOffset and rotationOffset accept any Vec3d")
        void offsetSetters() {
            HaloConfig config = new HaloConfig();

            Vec3d pos = new Vec3d(1.0, 2.0, 3.0);
            config.setPositionOffset(pos);
            assertEquals(pos, config.getPositionOffset());

            Vec3d rot = new Vec3d(45.0, 90.0, 0.0);
            config.setRotationOffset(rot);
            assertEquals(rot, config.getRotationOffset());
        }

        @Test
        @DisplayName("default values match specification")
        void defaultValues() {
            HaloConfig config = new HaloConfig();

            assertEquals(0.3, config.getLinearDampingFactor(), 1e-9);
            assertEquals(0.3, config.getAngularDampingFactor(), 1e-9);
            assertEquals(1.0, config.getMaxLinearDistance(), 1e-9);
            assertEquals(45.0, config.getMaxAngularDegrees(), 1e-9);
            assertEquals(1.0, config.getHaloScale(), 1e-9);
            assertEquals(new Vec3d(0, 0.2, 0), config.getPositionOffset());
            assertEquals(new Vec3d(0, 0, 0), config.getRotationOffset());
        }
    }

    // ------------------------------------------------------------------
    // 2. HaloConfig — conversion methods
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("HaloConfig conversion methods")
    class ConfigConversion {

        @Test
        @DisplayName("toDampingConfig converts current damping values")
        void toDampingConfig() {
            HaloConfig config = new HaloConfig();
            config.setLinearDampingFactor(0.5);
            config.setAngularDampingFactor(0.7);
            config.setMaxLinearDistance(2.0);
            config.setMaxAngularDegrees(60.0);

            HaloDampingConfig damping = config.toDampingConfig();

            assertEquals(0.5, damping.linearFactor(), 1e-9);
            assertEquals(0.7, damping.angularFactor(), 1e-9);
            assertEquals(2.0, damping.maxLinearDistance(), 1e-9);
            assertEquals(60.0, damping.maxAngularDegrees(), 1e-9);
        }

        @Test
        @DisplayName("toDampingConfig uses default values when unchanged")
        void toDampingConfigDefaults() {
            HaloConfig config = new HaloConfig();
            HaloDampingConfig damping = config.toDampingConfig();

            assertEquals(0.3, damping.linearFactor(), 1e-9);
            assertEquals(0.3, damping.angularFactor(), 1e-9);
            assertEquals(1.0, damping.maxLinearDistance(), 1e-9);
            assertEquals(45.0, damping.maxAngularDegrees(), 1e-9);
        }

        @Test
        @DisplayName("toPositioning converts current positioning values")
        void toPositioning() {
            HaloConfig config = new HaloConfig();
            Vec3d offset = new Vec3d(0, 0.5, 0);
            config.setPositionOffset(offset);
            config.setHaloScale(2.0);

            HaloPositioning positioning = config.toPositioning();

            assertEquals(offset, positioning.offset());
            assertEquals(2.0, positioning.scale(), 1e-9);
        }

        @Test
        @DisplayName("config changes are reflected in subsequent conversions")
        void configChangesAreLive() {
            HaloConfig config = new HaloConfig();

            HaloDampingConfig d1 = config.toDampingConfig();
            assertEquals(0.3, d1.linearFactor());

            config.setLinearDampingFactor(0.8);
            HaloDampingConfig d2 = config.toDampingConfig();
            assertEquals(0.8, d2.linearFactor());

            // First snapshot is unchanged
            assertEquals(0.3, d1.linearFactor());
        }
    }

    // ------------------------------------------------------------------
    // 3. HaloManager — singleton and lifecycle
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("HaloManager singleton and lifecycle")
    class ManagerLifecycle {

        @Test
        @DisplayName("getInstance returns the same instance")
        void singletonReturnsSameInstance() {
            HaloManager m1 = HaloManager.getInstance();
            HaloManager m2 = HaloManager.getInstance();
            assertSame(m1, m2);
        }

        @Test
        @DisplayName("getConfig returns non-null HaloConfig")
        void getConfigReturnsConfig() {
            HaloConfig config = HaloManager.getInstance().getConfig();
            assertNotNull(config);
        }

        @Test
        @DisplayName("active halos start empty")
        void startsEmpty() {
            assertEquals(0, HaloManager.getInstance().getActiveCount());
            assertTrue(HaloManager.getInstance().getActiveHalos().isEmpty());
        }

        @Test
        @DisplayName("getHaloInstance returns null for unknown UUID")
        void nullForUnknownUuid() {
            assertNull(HaloManager.getInstance().getHaloInstance(java.util.UUID.randomUUID()));
        }
    }
}
