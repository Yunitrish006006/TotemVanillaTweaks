package dev.totem.vanillatweaks.recipe;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GravelOreLootResourceTest {
    @Test
    void preservesConditionalSilkTouchAndOrderedDropModifiers() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/data/totem/loot_table/blocks/vanilla_tweaks/gravel_iron_ore.json")) {
            assertNotNull(stream);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var children = root.getAsJsonArray("pools").get(0).getAsJsonObject()
                    .getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonArray("children");
            var silk = children.get(0).getAsJsonObject();
            assertEquals("minecraft:tool/can_silk_touch", silk.get("condition").getAsString());
            assertFalse(silk.has("conditions"));
            var ordinary = children.get(1).getAsJsonObject();
            assertFalse(ordinary.has("functions"));
            var modifiers = ordinary.getAsJsonArray("modifier");
            assertEquals(3, modifiers.size());
            var count = modifiers.get(0).getAsJsonObject();
            assertEquals("minecraft:set_count", count.get("type").getAsString());
            assertEquals(1, count.getAsJsonObject("count").get("min").getAsInt());
            assertEquals(3, count.getAsJsonObject("count").get("max").getAsInt());
            assertFalse(count.get("add").getAsBoolean());
            var fortune = modifiers.get(1).getAsJsonObject();
            assertEquals("minecraft:apply_bonus", fortune.get("type").getAsString());
            assertEquals("minecraft:fortune", fortune.get("enchantment").getAsString());
            assertEquals("minecraft:ore_drops", fortune.get("formula").getAsString());
            assertEquals("minecraft:explosion_decay", modifiers.get(2).getAsJsonObject().get("type").getAsString());
        }
    }
}
