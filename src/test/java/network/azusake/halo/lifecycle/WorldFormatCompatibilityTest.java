package network.azusake.halo.lifecycle;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import network.azusake.halo.core.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldFormatCompatibilityTest {
    @Test void legacyWorldNbtRoundTripsThroughTheActualStorageAdapter() throws Exception {
        var uuid = new UUID(0, 1);
        var old = TagParser.parseTag("{Halos:[{UUID:\"00000000-0000-0000-0000-000000000001\",Definition:\"halo:ring_default\"}]}");
        var loaded = HaloWorldSaveData.fromNbt(old);
        assertEquals(new Identifier("halo:ring_default"), loaded.get(uuid));
        assertEquals(old, loaded.writeNbt(new CompoundTag()));
        loaded.set(uuid, new Identifier("halo:hud"));
        assertEquals(1, loaded.getAll().size());
        var restored = HaloWorldSaveData.fromNbt(loaded.writeNbt(new CompoundTag()));
        assertEquals(new Identifier("halo:hud"), restored.get(uuid));
        restored.remove(uuid);
        assertTrue(restored.getAll().isEmpty());
        assertTrue(restored.isDirty());
    }
}
