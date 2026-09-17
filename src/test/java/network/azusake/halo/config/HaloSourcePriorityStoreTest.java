package network.azusake.halo.config;

import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HaloSourcePriorityStoreTest {
    @TempDir Path temp;
    private Path file() { return temp.resolve("halo-azusake").resolve("halo_source_priorities.json"); }

    @Test void createsMergesAndPreservesUnknownAndUnregisteredEntries() throws Exception {
        assertTrue(HaloSourcePriorityStore.load(file()).success());
        HaloSourcePriorityStore.set("old:uninstalled", -20);
        Files.writeString(file(), Files.readString(file()).replace("\"schema\": 1", "\"schema\": 1,\n  \"future\": true"));
        assertTrue(HaloSourcePriorityStore.load(file()).success());
        assertTrue(HaloSourcePriorityStore.merge(Map.of("halo:world_data", 0, "example:ring", 20)));
        var root = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
        assertTrue(root.get("future").getAsBoolean());
        assertEquals(-20, root.getAsJsonObject("priorities").get("old:uninstalled").getAsInt());
        assertEquals(20, HaloSourcePriorityStore.priorities().get("example:ring"));
        assertFalse(Files.exists(file().resolveSibling("halo_source_priorities.json.tmp")));
    }

    @Test void corruptOrOutOfRangeReloadKeepsLastValidRuntimeValues() throws Exception {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "{\"priorities\":{\"test:source\":-10}}");
        assertTrue(HaloSourcePriorityStore.load(file()).success());
        Files.writeString(file(), "{\"priorities\":{\"test:source\":2147483648}}");
        assertFalse(HaloSourcePriorityStore.load(file()).success());
        assertEquals(-10, HaloSourcePriorityStore.priorities().get("test:source"));
        Files.writeString(file(), "not-json");
        assertFalse(HaloSourcePriorityStore.load(file()).success());
        assertEquals(-10, HaloSourcePriorityStore.priorities().get("test:source"));
    }
}
