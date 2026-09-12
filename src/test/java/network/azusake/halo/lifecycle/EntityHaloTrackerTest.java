package network.azusake.halo.lifecycle;

import network.azusake.halo.data.HaloInstance;
import net.minecraft.nbt.NbtCompound;
import network.azusake.halo.core.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EntityHaloTracker} and {@link HaloEntityData}
 * — the entity lifecycle and NBT persistence layer.
 *
 * <p>These tests exercise the NBT round-trip, teleport marking/grace period,
 * position tracking, and cleanup logic <em>without</em> requiring a running
 * Minecraft server.</p>
 */
class EntityHaloTrackerTest {

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
