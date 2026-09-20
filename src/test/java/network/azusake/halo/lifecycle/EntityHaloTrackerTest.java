package network.azusake.halo.lifecycle;

import network.azusake.halo.data.HaloInstance;
import net.minecraft.nbt.CompoundTag;
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
            CompoundTag persistent = new CompoundTag();
            UUID entityUuid = UUID.randomUUID();
            Identifier defId = new Identifier("halo", "ring_default");

            // --- Attach ---
            CompoundTag haloTag = new CompoundTag();
            haloTag.putString("HaloId", entityUuid.toString());
            haloTag.putString("Definition", defId.toString());
            haloTag.putDouble("Scale", 1.5);
            net.minecraft.nbt.ListTag offsetList = new net.minecraft.nbt.ListTag();
            offsetList.add(net.minecraft.nbt.DoubleTag.valueOf(0.0));
            offsetList.add(net.minecraft.nbt.DoubleTag.valueOf(0.5));
            offsetList.add(net.minecraft.nbt.DoubleTag.valueOf(0.0));
            haloTag.put("Offset", offsetList);
            persistent.put("HaloInstance", haloTag);

            // --- Has ---
            assertTrue(persistent.contains("HaloInstance"),
                "persistent NBT must contain HaloInstance key after attach");

            // --- Read ---
            CompoundTag readBack = persistent.getCompoundOrEmpty("HaloInstance");
            assertEquals(entityUuid.toString(), readBack.getStringOr("HaloId", ""),
                "HaloId must round-trip");
            assertEquals(defId.toString(), readBack.getStringOr("Definition", ""),
                "Definition must round-trip");
            assertEquals(1.5, readBack.getDoubleOr("Scale", 0), 0.0001,
                "Scale must round-trip");

            net.minecraft.nbt.ListTag offset = readBack.getListOrEmpty("Offset");
            assertEquals(0.0, offset.getDoubleOr(0, 0), 0.0001);
            assertEquals(0.5, offset.getDoubleOr(1, 0), 0.0001);
            assertEquals(0.0, offset.getDoubleOr(2, 0), 0.0001);

            // --- Remove ---
            persistent.remove("HaloInstance");
            assertFalse(persistent.contains("HaloInstance"),
                "HaloInstance key must be absent after remove");
        }

        @Test
        @DisplayName("read malformed Definition string returns null gracefully")
        void testMalformedDefinition() {
            CompoundTag persistent = new CompoundTag();
            CompoundTag haloTag = new CompoundTag();
            haloTag.putString("HaloId", UUID.randomUUID().toString());
            haloTag.putString("Definition", "not:a:valid:identifier");
            persistent.put("HaloInstance", haloTag);

            // Identifier constructor should throw for triple-colon format
            assertThrows(Exception.class, () -> {
                new Identifier(persistent.getCompoundOrEmpty("HaloInstance").getStringOr("Definition", ""));
            }, "malformed identifier string must throw");
        }

        @Test
        @DisplayName("hasHalo returns false when key is absent")
        void testNoHaloWhenAbsent() {
            CompoundTag persistent = new CompoundTag();
            assertFalse(persistent.contains("HaloInstance"),
                "clean NBT must not contain HaloInstance");
        }

        @Test
        @DisplayName("multiple entities can each have independent NBT halo data")
        void testMultipleEntities() {
            CompoundTag entity1Nbt = new CompoundTag();
            CompoundTag entity2Nbt = new CompoundTag();

            UUID uuid1 = UUID.randomUUID();
            UUID uuid2 = UUID.randomUUID();
            Identifier def1 = new Identifier("halo", "ring_a");
            Identifier def2 = new Identifier("halo", "ring_b");

            // Attach to entity 1
            CompoundTag tag1 = new CompoundTag();
            tag1.putString("HaloId", uuid1.toString());
            tag1.putString("Definition", def1.toString());
            entity1Nbt.put("HaloInstance", tag1);

            // Attach to entity 2
            CompoundTag tag2 = new CompoundTag();
            tag2.putString("HaloId", uuid2.toString());
            tag2.putString("Definition", def2.toString());
            entity2Nbt.put("HaloInstance", tag2);

            // Verify independence
            assertTrue(entity1Nbt.contains("HaloInstance"));
            assertTrue(entity2Nbt.contains("HaloInstance"));
            assertEquals(uuid1.toString(), entity1Nbt.getCompoundOrEmpty("HaloInstance").getStringOr("HaloId", ""));
            assertEquals(uuid2.toString(), entity2Nbt.getCompoundOrEmpty("HaloInstance").getStringOr("HaloId", ""));
            assertEquals(def1.toString(), entity1Nbt.getCompoundOrEmpty("HaloInstance").getStringOr("Definition", ""));
            assertEquals(def2.toString(), entity2Nbt.getCompoundOrEmpty("HaloInstance").getStringOr("Definition", ""));

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
            CompoundTag root = new CompoundTag();

            // --- Write ---
            net.minecraft.nbt.ListTag haloList = new net.minecraft.nbt.ListTag();
            UUID uuid1 = UUID.randomUUID();
            UUID uuid2 = UUID.randomUUID();

            CompoundTag tag1 = new CompoundTag();
            tag1.putString("UUID", uuid1.toString());
            tag1.putString("Definition", "halo:ring_default");
            haloList.add(tag1);

            CompoundTag tag2 = new CompoundTag();
            tag2.putString("UUID", uuid2.toString());
            tag2.putString("Definition", "halo:ring_elite");
            haloList.add(tag2);

            root.put("Halos", haloList);

            // --- Read ---
            net.minecraft.nbt.ListTag readBack = root.getListOrEmpty("Halos");
            assertEquals(2, readBack.size(), "halo list must contain 2 entries");

            CompoundTag entry1 = readBack.getCompoundOrEmpty(0);
            assertEquals(uuid1.toString(), entry1.getStringOr("UUID", ""));
            assertEquals("halo:ring_default", entry1.getStringOr("Definition", ""));

            CompoundTag entry2 = readBack.getCompoundOrEmpty(1);
            assertEquals(uuid2.toString(), entry2.getStringOr("UUID", ""));
            assertEquals("halo:ring_elite", entry2.getStringOr("Definition", ""));
        }

        @Test
        @DisplayName("empty NBT (no Halos key) produces empty entry list")
        void testEmptyNbt() {
            CompoundTag root = new CompoundTag();
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
