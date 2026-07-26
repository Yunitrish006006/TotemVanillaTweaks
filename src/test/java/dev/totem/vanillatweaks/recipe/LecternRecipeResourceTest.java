package dev.totem.vanillatweaks.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LecternRecipeResourceTest {
    private static final String RECIPE_PATH = "/data/minecraft/recipe/lectern.json";

    @Test
    void overridesTheVanillaLecternRecipeId() throws IOException {
        JsonObject recipe = loadRecipe();

        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertFalse(recipe.has("conditions"), "The replacement recipe must load without optional mod conditions");
    }

    @Test
    void usesExactlyFourWoodenSlabsAndOneBook() throws IOException {
        JsonObject recipe = loadRecipe();
        JsonArray pattern = recipe.getAsJsonArray("pattern");

        assertEquals(3, pattern.size());
        assertEquals("SSS", pattern.get(0).getAsString());
        assertEquals(" B ", pattern.get(1).getAsString());
        assertEquals(" S ", pattern.get(2).getAsString());

        String flattenedPattern = pattern.get(0).getAsString()
                + pattern.get(1).getAsString()
                + pattern.get(2).getAsString();
        assertEquals(4, flattenedPattern.chars().filter(character -> character == 'S').count());
        assertEquals(1, flattenedPattern.chars().filter(character -> character == 'B').count());

        JsonObject key = recipe.getAsJsonObject("key");
        assertEquals("#minecraft:wooden_slabs", key.get("S").getAsString());
        assertEquals("minecraft:book", key.get("B").getAsString());
        assertEquals(2, key.size());
    }

    @Test
    void producesOneVanillaLectern() throws IOException {
        JsonObject result = loadRecipe().getAsJsonObject("result");

        assertNotNull(result);
        assertEquals("minecraft:lectern", result.get("id").getAsString());
        assertEquals(1, result.get("count").getAsInt());
        assertEquals(2, result.size());
    }

    @Test
    void usesOneSharedTagIngredientForEverySlabSlot() throws IOException {
        JsonObject recipe = loadRecipe();
        String slabIngredient = recipe.getAsJsonObject("key").get("S").getAsString();

        assertTrue(slabIngredient.startsWith("#"));
        assertEquals("#minecraft:wooden_slabs", slabIngredient);
    }

    private static JsonObject loadRecipe() throws IOException {
        try (InputStream stream = LecternRecipeResourceTest.class.getResourceAsStream(RECIPE_PATH)) {
            assertNotNull(stream, "Missing vanilla-ID override resource " + RECIPE_PATH);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
