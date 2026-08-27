package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.network.ObserverLoomScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverStonecutterScreenPayloads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObserverStructuredScreenPolicyTest {
    @Test
    void negotiatedDedicatedFamiliesSuppressCompatibilityMetadata() {
        assertTrue(ObserverStructuredScreenPolicy.suppressGenericMetadata(
                ObserverLoomScreenPayloads.SCREEN_CLASS, ObserverLoomScreenPayloads.CAPABILITY));
        assertTrue(ObserverStructuredScreenPolicy.suppressGenericMetadata(
                ObserverStonecutterScreenPayloads.SCREEN_CLASS, ObserverStonecutterScreenPayloads.CAPABILITY));
        assertTrue(ObserverStructuredScreenPolicy.suppressGenericMetadata(
                "net.minecraft.client.gui.screens.inventory.InventoryScreen",
                ObserverNativeScreenPayloads.CAPABILITY_CRAFTING));
    }

    @Test
    void genericContainersAreSuppressedOnlyWhenTheirAdapterWasNegotiated() {
        String inventory = "net.minecraft.client.gui.screens.inventory.InventoryScreen";
        assertTrue(ObserverStructuredScreenPolicy.suppressGenericMetadata(
                inventory, ObserverNativeScreenPayloads.CAPABILITY_CONTAINER_SLOTS));
        assertFalse(ObserverStructuredScreenPolicy.suppressGenericMetadata(inventory, 0L));
        assertFalse(ObserverStructuredScreenPolicy.suppressGenericMetadata(
                "example.missing.UnknownScreen", Long.MAX_VALUE));
    }
}
