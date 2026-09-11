package network.azusake.halo.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HaloScepterRecipeTest {

    private static final Path RECIPE = Path.of(
        "src", "main", "resources", "data", "halo", "recipes", "halo_scepter.json"
    );

    @Test
    void earlyGameShapedRecipeMatchesTheApprovedLayout() throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(RECIPE)).getAsJsonObject();

        assertEquals("minecraft:crafting_shaped", root.get("type").getAsString());
        JsonArray pattern = root.getAsJsonArray("pattern");
        assertEquals("ABC", pattern.get(0).getAsString());
        assertEquals("DEB", pattern.get(1).getAsString());
        assertEquals("EDA", pattern.get(2).getAsString());

        JsonObject key = root.getAsJsonObject("key");
        assertIngredient(key, "A", "minecraft:redstone");
        assertIngredient(key, "B", "minecraft:glass");
        assertIngredient(key, "C", "minecraft:lapis_lazuli");
        assertIngredient(key, "D", "minecraft:gold_nugget");
        assertIngredient(key, "E", "minecraft:stick");

        JsonObject result = root.getAsJsonObject("result");
        assertEquals("halo:halo_scepter", result.get("item").getAsString());
        assertEquals(1, result.get("count").getAsInt());
    }

    private static void assertIngredient(JsonObject key, String symbol, String itemId) {
        assertEquals(itemId, key.getAsJsonObject(symbol).get("item").getAsString());
    }
}
