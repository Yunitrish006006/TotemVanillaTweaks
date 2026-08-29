package dev.totem.automata.client;

import dev.totem.automata.menu.CopperGolemMenu;
import dev.totem.automata.menu.CopperGolemMenuOpenData;
import dev.totem.automata.menu.CopperGolemMenuRegistration;
import dev.totem.automata.network.CopperWrenchBindingsPayload;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

/** Test-only bridge into Automata's package-private visual snapshot seam. */
public final class ObserverAutomataIntegrationFixture {
    public static final String PRIVATE_API_URL = "https://private.invalid/v1";
    public static final String PRIVATE_API_KEY = "sk-private-observer-test-key";
    public static final String PRIVATE_MODEL = "private-observer-model";
    public static final String PRIVATE_GATHERING_PROMPT = "private gathering prompt";
    public static final String PRIVATE_BINDING_PROMPT = "private binding prompt";
    public static final String PRIVATE_CACHE_VALUE = "private-cache-value";

    private ObserverAutomataIntegrationFixture() {}

    public static CopperGolemMenuScreen create(Inventory inventory, UUID golemId) {
        return create(inventory, golemId, 7);
    }

    public static CopperGolemMenuScreen create(Inventory inventory, UUID golemId, int revision) {
        CopperGolemMenu menu = new CopperGolemMenu(
                CopperGolemMenuRegistration.TYPE, 42, inventory, new CopperGolemMenuOpenData(golemId));
        CopperGolemMenuScreen screen = new CopperGolemMenuScreen(menu, inventory, Component.literal("Copper Golem"));
        screen.acceptSnapshotForVisualTest(snapshot(golemId, revision));
        return screen;
    }

    public static int revision(CopperGolemMenuScreen screen) {
        return screen.observerCaptureSource().orElseThrow().revision();
    }

    public static void enterPrivateEditorValues(CopperGolemMenuScreen screen) {
        set(screen, "apiUrlField", PRIVATE_API_URL);
        set(screen, "apiKeyField", PRIVATE_API_KEY);
        set(screen, "modelField", PRIVATE_MODEL);
        set(screen, "gatheringPromptField", PRIVATE_GATHERING_PROMPT);
        set(screen, "bindingPromptField", PRIVATE_BINDING_PROMPT);
        set(screen, "cacheValueField", PRIVATE_CACHE_VALUE);
    }

    private static CopperWrenchBindingsPayload snapshot(UUID golemId, int revision) {
        var source = new CopperWrenchBindingsPayload.BindingEntry(
                "minecraft:overworld", 10, 64, 10, "minecraft:chest", "minecraft:chest",
                true, true, true, PRIVATE_BINDING_PROMPT, 1, 1,
                List.of("minecraft:iron_ingot"), List.of("minecraft:dirt"), List.of("c:ingots"), List.of());
        return new CopperWrenchBindingsPayload(
                golemId, revision, true, "sorting", "searching",
                "minecraft:nether_star", 1, 800, true,
                "minecraft:iron_pickaxe", 1, 12, 250,
                "minecraft:chest", 1,
                PRIVATE_API_URL, PRIVATE_API_KEY, PRIVATE_MODEL, 1,
                source, null, List.of("minecraft:stone"), true, PRIVATE_GATHERING_PROMPT, 1, 1,
                List.of("minecraft:stone"), List.of("minecraft:bedrock"),
                List.of("c:ores"), List.of(), List.of(source));
    }

    private static void set(CopperGolemMenuScreen screen, String fieldName, String value) {
        try {
            Field field = CopperGolemMenuScreen.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object editor = field.get(screen);
            if (!(editor instanceof EditBox editBox)) {
                throw new AssertionError("Automata editor not initialized: " + fieldName);
            }
            editBox.setValue(value);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Unable to set Automata integration editor " + fieldName, error);
        }
    }
}
