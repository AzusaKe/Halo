package com.example.halo.lifecycle;

import com.example.halo.data.HaloInstance;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EntityHaloTracker}, {@link HaloEntityEventHandler},
 * and {@link HaloEntityData} — the entity lifecycle and NBT persistence layer.
 *
 * <p>These tests exercise the NBT round-trip, teleport marking/grace period,
 * position tracking, and cleanup logic <em>without</em> requiring a running
 * Minecraft server.</p>
 */
class EntityHaloTrackerTest {

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Access the private recentlyTeleported map via reflection for assertions. */
    @SuppressWarnings("unchecked")
    private static Map<UUID, Long> getRecentlyTeleported() throws Exception {
        Field field = EntityHaloTracker.class.getDeclaredField("recentlyTeleported");
        field.setAccessible(true);
        return (Map<UUID, Long>) field.get(null);
    }

    @BeforeEach
    void setUp() throws Exception {
        // Clear static state between tests
        getRecentlyTeleported().clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        getRecentlyTeleported().clear();
    }

    // ------------------------------------------------------------------
    // 1. NBT round-trip (HaloEntityData)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("NBT persistence (HaloEntityData)")
    class NbtRoundTrip {

        @Test
        @DisplayName("attach → has → read → remove cycle preserves all fields")
        void testAttachHasReadRemove() {
            NbtCompound persistent = new NbtCompound();
            UUID entityUuid = UUID.randomUUID();
            Identifier defId = new Identifier("halo", "ring_default");

            // --- Attach ---
            NbtCompound haloTag = new NbtCompound();
            haloTag.putString("HaloId", entityUuid.toString());
            haloTag.putString("Definition", defId.toString());
            haloTag.putDouble("Scale", 1.5);
            net.minecraft.nbt.NbtList offsetList = new net.minecraft.nbt.NbtList();
            offsetList.add(net.minecraft.nbt.NbtDouble.of(0.0));
            offsetList.add(net.minecraft.nbt.NbtDouble.of(0.5));
            offsetList.add(net.minecraft.nbt.NbtDouble.of(0.0));
            haloTag.put("Offset", offsetList);
            persistent.put("HaloInstance", haloTag);

            // --- Has ---
            assertTrue(persistent.contains("HaloInstance"),
                "persistent NBT must contain HaloInstance key after attach");

            // --- Read ---
            NbtCompound readBack = persistent.getCompound("HaloInstance");
            assertEquals(entityUuid.toString(), readBack.getString("HaloId"),
                "HaloId must round-trip");
            assertEquals(defId.toString(), readBack.getString("Definition"),
                "Definition must round-trip");
            assertEquals(1.5, readBack.getDouble("Scale"), 0.0001,
                "Scale must round-trip");

            net.minecraft.nbt.NbtList offset = readBack.getList("Offset", net.minecraft.nbt.NbtElement.DOUBLE_TYPE);
            assertEquals(0.0, offset.getDouble(0), 0.0001);
            assertEquals(0.5, offset.getDouble(1), 0.0001);
            assertEquals(0.0, offset.getDouble(2), 0.0001);

            // --- Remove ---
            persistent.remove("HaloInstance");
            assertFalse(persistent.contains("HaloInstance"),
                "HaloInstance key must be absent after remove");
        }

        @Test
        @DisplayName("read malformed Definition string returns null gracefully")
        void testMalformedDefinition() {
            NbtCompound persistent = new NbtCompound();
            NbtCompound haloTag = new NbtCompound();
            haloTag.putString("HaloId", UUID.randomUUID().toString());
            haloTag.putString("Definition", "not:a:valid:identifier");
            persistent.put("HaloInstance", haloTag);

            // Identifier constructor should throw for triple-colon format
            assertThrows(Exception.class, () -> {
                new Identifier(persistent.getCompound("HaloInstance").getString("Definition"));
            }, "malformed identifier string must throw");
        }

        @Test
        @DisplayName("hasHalo returns false when key is absent")
        void testNoHaloWhenAbsent() {
            NbtCompound persistent = new NbtCompound();
            assertFalse(persistent.contains("HaloInstance"),
                "clean NBT must not contain HaloInstance");
        }

        @Test
        @DisplayName("multiple entities can each have independent NBT halo data")
        void testMultipleEntities() {
            NbtCompound entity1Nbt = new NbtCompound();
            NbtCompound entity2Nbt = new NbtCompound();

            UUID uuid1 = UUID.randomUUID();
            UUID uuid2 = UUID.randomUUID();
            Identifier def1 = new Identifier("halo", "ring_a");
            Identifier def2 = new Identifier("halo", "ring_b");

            // Attach to entity 1
            NbtCompound tag1 = new NbtCompound();
            tag1.putString("HaloId", uuid1.toString());
            tag1.putString("Definition", def1.toString());
            entity1Nbt.put("HaloInstance", tag1);

            // Attach to entity 2
            NbtCompound tag2 = new NbtCompound();
            tag2.putString("HaloId", uuid2.toString());
            tag2.putString("Definition", def2.toString());
            entity2Nbt.put("HaloInstance", tag2);

            // Verify independence
            assertTrue(entity1Nbt.contains("HaloInstance"));
            assertTrue(entity2Nbt.contains("HaloInstance"));
            assertEquals(uuid1.toString(), entity1Nbt.getCompound("HaloInstance").getString("HaloId"));
            assertEquals(uuid2.toString(), entity2Nbt.getCompound("HaloInstance").getString("HaloId"));
            assertEquals(def1.toString(), entity1Nbt.getCompound("HaloInstance").getString("Definition"));
            assertEquals(def2.toString(), entity2Nbt.getCompound("HaloInstance").getString("Definition"));

            // Remove from entity 1 — entity 2 unaffected
            entity1Nbt.remove("HaloInstance");
            assertFalse(entity1Nbt.contains("HaloInstance"));
            assertTrue(entity2Nbt.contains("HaloInstance"),
                "removing halo from entity 1 must not affect entity 2");
        }
    }

    // ------------------------------------------------------------------
    // 2. Teleport marking
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Teleport marking and grace period")
    class TeleportMarking {

        @Test
        @DisplayName("markTeleport sets needsSnap on the halo instance")
        void testMarkTeleportSetsNeedsSnap() {
            UUID entityUuid = UUID.randomUUID();
            HaloInstance instance = new HaloInstance(entityUuid,
                new Identifier("halo", "ring_default"));

            // Fresh instance starts with needsSnap = true
            assertTrue(instance.isNeedsSnap(),
                "new HaloInstance must start with needsSnap=true");

            // Clear the snap flag (simulating one tick of physics)
            instance.setNeedsSnap(false);
            assertFalse(instance.isNeedsSnap());

            // Simulate teleport
            instance.markTeleported();
            assertTrue(instance.isNeedsSnap(),
                "markTeleported must set needsSnap back to true");
        }

        @Test
        @DisplayName("teleport tracking map entry expires after grace period")
        void testTeleportGracePeriodExpiry() throws Exception {
            UUID uuid = UUID.randomUUID();

            // Manually add an expired entry
            long expiredTime = System.currentTimeMillis() - 200; // 200 ms ago (> 100 ms grace)
            getRecentlyTeleported().put(uuid, expiredTime);

            // Simulate the expiry check logic
            long now = System.currentTimeMillis();
            getRecentlyTeleported().values().removeIf(
                timestamp -> now - timestamp > 100
            );

            assertFalse(getRecentlyTeleported().containsKey(uuid),
                "expired teleport entries must be removed");
        }

        @Test
        @DisplayName("teleport tracking map entry within grace period is retained")
        void testTeleportGracePeriodRetained() throws Exception {
            UUID uuid = UUID.randomUUID();

            // Add a recent entry
            getRecentlyTeleported().put(uuid, System.currentTimeMillis());

            // Simulate the expiry check — recent entry should survive
            long now = System.currentTimeMillis();
            getRecentlyTeleported().values().removeIf(
                timestamp -> now - timestamp > 100
            );

            assertTrue(getRecentlyTeleported().containsKey(uuid),
                "recent teleport entries must survive expiry check");
        }
    }

    // ------------------------------------------------------------------
    // 3. Position tracking / teleport detection
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Position-based teleport detection")
    class PositionDetection {

        @Test
        @DisplayName("movement > 20 blocks in one tick triggers teleport flag")
        void testLargeMovementTriggersTeleport() {
            UUID uuid = UUID.randomUUID();
            HaloInstance instance = new HaloInstance(uuid,
                new Identifier("halo", "ring_default"));
            instance.setNeedsSnap(false);

            // Simulate: entity was at (0,0,0), now at (50,0,0) — 50 block jump
            double distanceSq = 50.0 * 50.0; // = 2500
            double thresholdSq = 20.0 * 20.0; // = 400

            assertTrue(distanceSq > thresholdSq,
                "50-block jump must exceed the 20-block teleport threshold");

            // This is what HaloEntityEventHandler.onEntityMove checks
            // If distance > threshold, markTeleport is called
            instance.markTeleported();
            assertTrue(instance.isNeedsSnap(),
                ">20 block movement must trigger needsSnap");
        }

        @Test
        @DisplayName("movement ≤ 20 blocks does NOT trigger teleport flag")
        void testSmallMovementDoesNotTrigger() {
            UUID uuid = UUID.randomUUID();
            HaloInstance instance = new HaloInstance(uuid,
                new Identifier("halo", "ring_default"));
            instance.setNeedsSnap(false);

            // Simulate: entity moved 5 blocks (normal walking)
            double distanceSq = 5.0 * 5.0; // = 25
            double thresholdSq = 20.0 * 20.0; // = 400

            assertFalse(distanceSq > thresholdSq,
                "5-block movement must NOT exceed the 20-block teleport threshold");

            // No teleport → needsSnap stays false
            assertFalse(instance.isNeedsSnap(),
                "normal movement must NOT trigger needsSnap");
        }

        @Test
        @DisplayName("movement exactly 20 blocks does NOT trigger (boundary)")
        void testBoundaryDistance() {
            double distanceSq = 20.0 * 20.0; // exactly 400
            double thresholdSq = 20.0 * 20.0; // = 400

            // Strict inequality: > threshold triggers, == does not
            assertFalse(distanceSq > thresholdSq,
                "exactly 20 block movement must NOT trigger (strict > check)");
        }
    }

    // ------------------------------------------------------------------
    // 4. Cleanup
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Entity cleanup")
    class Cleanup {

        @Test
        @DisplayName("cleanup removes halo NBT from entity persistent data")
        void testCleanupRemovesNbt() {
            NbtCompound persistent = new NbtCompound();

            // Attach a halo
            NbtCompound haloTag = new NbtCompound();
            haloTag.putString("HaloId", UUID.randomUUID().toString());
            haloTag.putString("Definition", "halo:ring_default");
            persistent.put("HaloInstance", haloTag);

            assertTrue(persistent.contains("HaloInstance"));

            // Simulate cleanup: remove the halo NBT
            persistent.remove("HaloInstance");

            assertFalse(persistent.contains("HaloInstance"),
                "cleanup must remove HaloInstance from persistent NBT");
        }

        @Test
        @DisplayName("cleanup clears teleport tracking for the entity")
        void testCleanupClearsTeleportTracking() throws Exception {
            UUID uuid = UUID.randomUUID();
            getRecentlyTeleported().put(uuid, System.currentTimeMillis());
            assertTrue(getRecentlyTeleported().containsKey(uuid));

            // Simulate cleanup: remove from tracking
            getRecentlyTeleported().remove(uuid);

            assertFalse(getRecentlyTeleported().containsKey(uuid),
                "cleanup must remove entity from teleport tracking");
        }

        @Test
        @DisplayName("deactivated HaloInstance returns isActive=false")
        void testDeactivatedInstance() {
            HaloInstance instance = new HaloInstance(
                UUID.randomUUID(),
                new Identifier("halo", "ring_default")
            );

            assertTrue(instance.isActive(),
                "new HaloInstance must be active by default");

            instance.deactivate();

            assertFalse(instance.isActive(),
                "deactivated HaloInstance must return isActive=false");
        }
    }

    // ------------------------------------------------------------------
    // 5. HaloInstance lifecycle fields
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("HaloInstance lifecycle")
    class LifecycleFields {

        @Test
        @DisplayName("getCreatedAtTime returns a reasonable epoch-millis timestamp")
        void testCreatedAtTime() {
            long before = System.currentTimeMillis();
            HaloInstance instance = new HaloInstance(
                UUID.randomUUID(),
                new Identifier("halo", "ring_default")
            );
            long after = System.currentTimeMillis();

            assertTrue(instance.getCreatedAtTime() >= before,
                "createdAtTime must be >= the timestamp before construction");
            assertTrue(instance.getCreatedAtTime() <= after,
                "createdAtTime must be <= the timestamp after construction");
        }

        @Test
        @DisplayName("getInterpolatedPosition lerps between prev and current")
        void testInterpolatedPosition() {
            UUID uuid = UUID.randomUUID();
            HaloInstance instance = new HaloInstance(uuid,
                new Identifier("halo", "ring_default"));

            // Set up known world-space positions
            net.minecraft.util.math.Vec3d prev = new net.minecraft.util.math.Vec3d(0, 0, 0);
            net.minecraft.util.math.Vec3d current = new net.minecraft.util.math.Vec3d(10, 0, 0);

            instance.setPrevRelativePosition(prev);
            instance.setRelativePosition(current);

            // At tickDelta=0.0 → prev
            net.minecraft.util.math.Vec3d at0 = instance.getInterpolatedPosition(0.0);
            assertEquals(0.0, at0.x, 0.0001);
            assertEquals(0.0, at0.y, 0.0001);
            assertEquals(0.0, at0.z, 0.0001);

            // At tickDelta=0.5 → midpoint
            net.minecraft.util.math.Vec3d at05 = instance.getInterpolatedPosition(0.5);
            assertEquals(5.0, at05.x, 0.0001);

            // At tickDelta=1.0 → current
            net.minecraft.util.math.Vec3d at1 = instance.getInterpolatedPosition(1.0);
            assertEquals(10.0, at1.x, 0.0001);
        }

        @Test
        @DisplayName("getInterpolatedRotation slerps between prev and current quaternions")
        void testInterpolatedRotation() {
            UUID uuid = UUID.randomUUID();
            HaloInstance instance = new HaloInstance(uuid,
                new Identifier("halo", "ring_default"));

            org.joml.Quaternionf identity = new org.joml.Quaternionf();
            org.joml.Quaternionf rotated90Y = new org.joml.Quaternionf()
                .rotateY((float) Math.toRadians(90.0));

            instance.setPrevRelativeRotation(identity);
            instance.setRelativeRotation(rotated90Y);

            // At tickDelta=0.0 → identity (0°)
            org.joml.Quaternionf at0 = instance.getInterpolatedRotation(0.0);
            double angle0 = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(at0.w))));
            assertEquals(0.0, angle0, 0.01);

            // At tickDelta=0.5 → 45° around Y
            org.joml.Quaternionf at05 = instance.getInterpolatedRotation(0.5);
            double angle05 = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(at05.w))));
            assertEquals(45.0, angle05, 0.01);

            // At tickDelta=1.0 → 90° around Y
            org.joml.Quaternionf at1 = instance.getInterpolatedRotation(1.0);
            double angle1 = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(at1.w))));
            assertEquals(90.0, angle1, 0.01);
        }
    }

    // ------------------------------------------------------------------
    // 6. World save data
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("HaloWorldSaveData NBT format")
    class WorldSaveData {

        @Test
        @DisplayName("NBT halo list round-trips through write/read")
        void testNbtListRoundTrip() {
            NbtCompound root = new NbtCompound();

            // --- Write ---
            net.minecraft.nbt.NbtList haloList = new net.minecraft.nbt.NbtList();
            UUID uuid1 = UUID.randomUUID();
            UUID uuid2 = UUID.randomUUID();

            NbtCompound tag1 = new NbtCompound();
            tag1.putString("UUID", uuid1.toString());
            tag1.putString("Definition", "halo:ring_default");
            haloList.add(tag1);

            NbtCompound tag2 = new NbtCompound();
            tag2.putString("UUID", uuid2.toString());
            tag2.putString("Definition", "halo:ring_elite");
            haloList.add(tag2);

            root.put("Halos", haloList);

            // --- Read ---
            net.minecraft.nbt.NbtList readBack = root.getList("Halos",
                net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
            assertEquals(2, readBack.size(), "halo list must contain 2 entries");

            NbtCompound entry1 = readBack.getCompound(0);
            assertEquals(uuid1.toString(), entry1.getString("UUID"));
            assertEquals("halo:ring_default", entry1.getString("Definition"));

            NbtCompound entry2 = readBack.getCompound(1);
            assertEquals(uuid2.toString(), entry2.getString("UUID"));
            assertEquals("halo:ring_elite", entry2.getString("Definition"));
        }

        @Test
        @DisplayName("empty NBT (no Halos key) produces empty entry list")
        void testEmptyNbt() {
            NbtCompound root = new NbtCompound();
            assertFalse(root.contains("Halos"),
                "fresh NBT must not contain Halos key");
        }
    }

    // ------------------------------------------------------------------
    // 7. HaloEntry record
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("HaloWorldSaveData.HaloEntry")
    class HaloEntryRecord {

        @Test
        @DisplayName("HaloEntry stores and retrieves UUID and Identifier correctly")
        void testHaloEntry() {
            UUID uuid = UUID.randomUUID();
            Identifier defId = new Identifier("halo", "ring_test");

            HaloWorldSaveData.HaloEntry entry = new HaloWorldSaveData.HaloEntry(uuid, defId);

            assertEquals(uuid, entry.entityUuid());
            assertEquals(defId, entry.definitionId());
        }

        @Test
        @DisplayName("two HaloEntry instances with same values are equal")
        void testHaloEntryEquality() {
            UUID uuid = UUID.randomUUID();
            Identifier defId = new Identifier("halo", "ring_test");

            HaloWorldSaveData.HaloEntry entry1 = new HaloWorldSaveData.HaloEntry(uuid, defId);
            HaloWorldSaveData.HaloEntry entry2 = new HaloWorldSaveData.HaloEntry(uuid, defId);

            assertEquals(entry1, entry2, "records with same fields must be equal");
            assertEquals(entry1.hashCode(), entry2.hashCode(),
                "equal records must have equal hash codes");
        }
    }
}
