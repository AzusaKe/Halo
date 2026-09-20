package network.azusake.halo.lifecycle;

import com.mojang.serialization.DataResult;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.DataFixTypes;
import network.azusake.halo.core.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldFormatCompatibilityTest {
    @Test void legacyFileImportsOnceAndAnEmptyCurrentFilePreventsResurrection(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var uuid = new UUID(0, 1);
        var payload = TagParser.parseCompoundFully("{data:{Halos:[{UUID:\"00000000-0000-0000-0000-000000000001\",Definition:\"halo:ring_default\"}]}}");
        var legacy = directory.resolve("halo_world_data.dat");
        net.minecraft.nbt.NbtIo.writeCompressed(payload, legacy);
        var loaded = HaloWorldSaveData.readLegacyDirectory(directory);
        assertEquals(new Identifier("halo:ring_default"), loaded.get(uuid));
        assertTrue(loaded.isDirty());
        assertTrue(java.nio.file.Files.exists(legacy));
        java.nio.file.Files.createDirectories(directory.resolve("halo"));
        net.minecraft.nbt.NbtIo.writeCompressed(new CompoundTag(), directory.resolve("halo/world_data.dat"));
        assertTrue(HaloWorldSaveData.readLegacyDirectory(directory).getAll().isEmpty());
    }


    private static final UUID FIRST_UUID = new UUID(0L, 1L);
    private static final UUID SECOND_UUID = new UUID(0x0123456789ABCDEFL, 0xFEDCBA9876543210L);

    @Test
    void readsFlashIntegerArrayUuidAndPreservesAllEntries() {
        var loaded = decode(fixture(false));

        assertEquals(2, loaded.getAll().size());
        assertEquals(new Identifier("halo:ring_default"), loaded.get(FIRST_UUID));
        assertEquals(new Identifier("halo:ring_elite"), loaded.get(SECOND_UUID));
    }

    @Test
    void readsStringUuidWrittenByAffectedFabricBranches() {
        var loaded = decode(fixture(true));

        assertEquals(2, loaded.getAll().size());
        assertEquals(new Identifier("halo:ring_default"), loaded.get(FIRST_UUID));
        assertEquals(new Identifier("halo:ring_elite"), loaded.get(SECOND_UUID));
    }

    @Test
    void savesCanonicalFlashUuidAndCanBeReadByFlashCodecAfterMutation() {
        var loaded = decode(fixture(false));
        loaded.set(FIRST_UUID, new Identifier("halo:hud"));

        CompoundTag encoded = encode(loaded);
        ListTag halos = encoded.getListOrEmpty("Halos");
        assertEquals(2, halos.size());

        assertInstanceOf(IntArrayTag.class, halos.getCompoundOrEmpty(0).get("UUID"));
        CompoundTag updated = halos.getCompoundOrEmpty(1);
        assertInstanceOf(IntArrayTag.class, updated.get("UUID"));
        assertEquals(FIRST_UUID, updated.read("UUID", UUIDUtil.CODEC).orElseThrow());
        assertEquals("halo:hud", updated.getStringOr("Definition", ""));

        var restored = decode(encoded);
        assertEquals(new Identifier("halo:hud"), restored.get(FIRST_UUID));
        assertEquals(new Identifier("halo:ring_elite"), restored.get(SECOND_UUID));
    }

    @Test
    void keepsThe26xStorageDescriptorAndSupportsEmptyData() {
        assertEquals("halo:world_data", HaloWorldSaveData.TYPE.id().toString());
        assertEquals(DataFixTypes.SAVED_DATA_MAP_DATA, HaloWorldSaveData.TYPE.dataFixType());

        var empty = decode(new CompoundTag());
        assertTrue(empty.getAll().isEmpty());
        assertTrue(encode(empty).getListOrEmpty("Halos").isEmpty());
    }

    @Test
    void mixedCandidateAndFlashEntriesRewriteToFlashAndRemovalStaysRemoved() {
        var root = fixture(false);
        root.getListOrEmpty("Halos").set(0, entry(FIRST_UUID, "halo:ring_default", true));
        var loaded = decode(root);
        assertEquals(2, loaded.getAll().size());
        loaded.remove(FIRST_UUID);
        assertTrue(loaded.isDirty());
        var encoded = encode(loaded);
        var halos = encoded.getListOrEmpty("Halos");
        assertEquals(1, halos.size());
        assertEquals(SECOND_UUID, halos.getCompoundOrEmpty(0).read("UUID", UUIDUtil.CODEC).orElseThrow());
        assertEquals(1, decode(encoded).getAll().size());
        org.junit.jupiter.api.Assertions.assertNull(decode(encoded).get(FIRST_UUID));
    }

    private static CompoundTag fixture(boolean stringUuid) {
        CompoundTag root = new CompoundTag();
        ListTag halos = new ListTag();
        halos.add(entry(FIRST_UUID, "halo:ring_default", stringUuid));
        halos.add(entry(SECOND_UUID, "halo:ring_elite", stringUuid));
        root.put("Halos", halos);
        return root;
    }

    private static CompoundTag entry(UUID uuid, String definition, boolean stringUuid) {
        CompoundTag entry = new CompoundTag();
        if (stringUuid) {
            entry.putString("UUID", uuid.toString());
        } else {
            entry.putIntArray("UUID", UUIDUtil.uuidToIntArray(uuid));
        }
        entry.putString("Definition", definition);
        return entry;
    }

    private static HaloWorldSaveData decode(CompoundTag nbt) {
        DataResult<HaloWorldSaveData> result = HaloWorldSaveData.TYPE.codec().parse(NbtOps.INSTANCE, nbt);
        assertTrue(result.result().isPresent(), () -> "Codec failed: " + result.error());
        return result.result().orElseThrow();
    }

    private static CompoundTag encode(HaloWorldSaveData data) {
        DataResult<net.minecraft.nbt.Tag> result =
            HaloWorldSaveData.TYPE.codec().encodeStart(NbtOps.INSTANCE, data);
        assertTrue(result.result().isPresent(), () -> "Codec failed: " + result.error());
        return (CompoundTag) result.result().orElseThrow();
    }
}
