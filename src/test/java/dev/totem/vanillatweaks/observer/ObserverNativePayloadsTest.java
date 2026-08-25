package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverAnvilScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverAutomataCopperGolemPayloads;
import dev.totem.vanillatweaks.network.ObserverBookScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverCraftingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverEnchantingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverMerchantScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import dev.totem.vanillatweaks.network.ObserverRemnantBackpackPayloads;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverNativePayloadsTest {
    @Test
    void protocolV4UsesVersionedPacketIdentifiers() {
        assertEquals(4, ObserverNativePayloads.PROTOCOL_VERSION);
        assertNativeVersioned(ObserverNativePayloads.NativeControl.TYPE.id().getPath());
        assertNativeVersioned(ObserverNativePayloads.NativeSession.TYPE.id().getPath());
        assertNativeVersioned(ObserverNativePayloads.NativeViewState.TYPE.id().getPath());
        assertNativeVersioned(ObserverNativePayloads.NativeViewRelay.TYPE.id().getPath());
    }

    @Test
    void semanticScreenCapabilitiesIncludeSupportedFamilies() {
        assertEquals(2, ObserverNativeScreenPayloads.SCREEN_PROTOCOL_VERSION);
        assertEquals(1, ObserverNativeScreenPayloads.FURNACE_PROTOCOL_VERSION);
        assertEquals(1, ObserverBookScreenPayloads.PROTOCOL_VERSION);
        assertEquals(1, ObserverCraftingScreenPayloads.PROTOCOL_VERSION);
        assertEquals(1, ObserverMerchantScreenPayloads.PROTOCOL_VERSION);
        assertEquals(1, ObserverAnvilScreenPayloads.PROTOCOL_VERSION);
        assertEquals(1, ObserverEnchantingScreenPayloads.PROTOCOL_VERSION);
        assertEquals(1, ObserverRemnantBackpackPayloads.PROTOCOL_VERSION);
        assertEquals(1, ObserverAutomataCopperGolemPayloads.PROTOCOL_VERSION);
        assertTrue(ObserverNativeScreenPayloads.ContainerState.TYPE.id().getPath().endsWith("_v2"));
        assertTrue(ObserverNativeScreenPayloads.ContainerRelay.TYPE.id().getPath().endsWith("_v2"));
        assertTrue(ObserverNativeScreenPayloads.FurnaceState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverNativeScreenPayloads.FurnaceRelay.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverBookScreenPayloads.BookState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverBookScreenPayloads.BookRelay.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverCraftingScreenPayloads.CraftingState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverCraftingScreenPayloads.CraftingRelay.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverMerchantScreenPayloads.MerchantState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverMerchantScreenPayloads.MerchantRelay.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverAnvilScreenPayloads.AnvilState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverAnvilScreenPayloads.AnvilRelay.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverEnchantingScreenPayloads.EnchantingState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverEnchantingScreenPayloads.EnchantingRelay.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverRemnantBackpackPayloads.BackpackState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverRemnantBackpackPayloads.BackpackRelay.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverAutomataCopperGolemPayloads.CopperGolemState.TYPE.id().getPath().endsWith("_v1"));
        assertTrue(ObserverAutomataCopperGolemPayloads.CopperGolemRelay.TYPE.id().getPath().endsWith("_v1"));

        long container = ObserverNativeScreenPayloads.CAPABILITY_CONTAINER_SLOTS;
        long furnace = ObserverNativeScreenPayloads.CAPABILITY_FURNACE;
        long book = ObserverNativeScreenPayloads.CAPABILITY_BOOK;
        long crafting = ObserverNativeScreenPayloads.CAPABILITY_CRAFTING;
        long merchant = ObserverNativeScreenPayloads.CAPABILITY_MERCHANT;
        long anvil = ObserverNativeScreenPayloads.CAPABILITY_ANVIL;
        long enchanting = ObserverNativeScreenPayloads.CAPABILITY_ENCHANTING;
        long remnantBackpack = ObserverNativeScreenPayloads.CAPABILITY_REMNANT_BACKPACK;
        long automata = ObserverNativeScreenPayloads.CAPABILITY_AUTOMATA_COPPER_GOLEM;
        long known = container | furnace | book | crafting | merchant | anvil | enchanting | remnantBackpack | automata;
        assertEquals(known, ObserverNativeScreenPayloads.KNOWN_CAPABILITIES);
        assertEquals(container, ObserverNativeScreenPayloads.capabilityForFamily(ObserverNativeScreenPayloads.FAMILY_CONTAINER_SLOTS));
        assertEquals(furnace, ObserverNativeScreenPayloads.capabilityForFamily(ObserverNativeScreenPayloads.FAMILY_FURNACE));
        assertEquals(book, ObserverNativeScreenPayloads.capabilityForFamily(ObserverNativeScreenPayloads.FAMILY_BOOK));
        assertEquals(crafting, ObserverNativeScreenPayloads.capabilityForFamily(ObserverNativeScreenPayloads.FAMILY_CRAFTING));
        assertEquals(merchant, ObserverNativeScreenPayloads.capabilityForFamily(ObserverNativeScreenPayloads.FAMILY_MERCHANT));
        assertEquals(anvil, ObserverNativeScreenPayloads.capabilityForFamily(ObserverNativeScreenPayloads.FAMILY_ANVIL));
        assertEquals(enchanting, ObserverNativeScreenPayloads.capabilityForFamily(ObserverNativeScreenPayloads.FAMILY_ENCHANTING));
        assertEquals(remnantBackpack, ObserverNativeScreenPayloads.capabilityForFamily(ObserverNativeScreenPayloads.FAMILY_REMNANT_BACKPACK));
        assertEquals(automata, ObserverNativeScreenPayloads.capabilityForFamily(ObserverNativeScreenPayloads.FAMILY_AUTOMATA_COPPER_GOLEM));
        assertEquals(0L, ObserverNativeScreenPayloads.capabilityForFamily("unknown"));
        assertEquals(known, ObserverNativeScreenPayloads.sanitizeCapabilities(known | (1L << 40)));
        assertTrue(ObserverNativeScreenPayloads.supports(known, container));
        assertTrue(ObserverNativeScreenPayloads.supports(known, furnace));
        assertTrue(ObserverNativeScreenPayloads.supports(known, book));
        assertTrue(ObserverNativeScreenPayloads.supports(known, crafting));
        assertTrue(ObserverNativeScreenPayloads.supports(known, merchant));
        assertTrue(ObserverNativeScreenPayloads.supports(known, anvil));
        assertTrue(ObserverNativeScreenPayloads.supports(known, enchanting));
        assertTrue(ObserverNativeScreenPayloads.supports(known, remnantBackpack));
        assertTrue(ObserverNativeScreenPayloads.supports(known, automata));
        assertFalse(ObserverNativeScreenPayloads.supports(container, automata));
        assertFalse(ObserverNativeScreenPayloads.supports(0L, container));
    }

    @Test
    void automataObserverPayloadNeverCarriesApiKeyText() {
        for (Class<?> type : Set.of(
                ObserverAutomataCopperGolemPayloads.CopperGolemState.class,
                ObserverAutomataCopperGolemPayloads.CopperGolemRelay.class)) {
            for (RecordComponent component : type.getRecordComponents()) {
                if (component.getName().toLowerCase().contains("apikey")) {
                    assertEquals(boolean.class, component.getType(),
                            () -> "Observer Automata payload leaked API key text through " + component.getName());
                }
            }
        }
    }

    @Test
    void compatibilityPayloadSurfaceContainsNoFramebufferMessages() {
        Set<String> nestedTypes = Arrays.stream(ObserverPayloads.class.getDeclaredClasses())
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("ScreenState", "ScreenRelay", "Stop"), nestedTypes);
        assertThrows(ClassNotFoundException.class, () ->
                Class.forName("dev.totem.vanillatweaks.observer.ObserverFrameRules"));
    }

    private static void assertNativeVersioned(String path) {
        assertTrue(path.endsWith("_v4"), () -> "Native Observer v4 packet id is not versioned: " + path);
    }
}
