package dev.totem.vanillatweaks.e2e;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** Assertions against the actual detached Mojang menu backing an observer screen. */
final class ObserverE2eMenuAssertions {
    private ObserverE2eMenuAssertions() { }

    static boolean hasNonEmptySlots(AbstractContainerScreen<?> screen, int... slotIndexes) {
        for (int slotIndex : slotIndexes) {
            if (slotIndex < 0 || slotIndex >= screen.getMenu().slots.size()
                    || screen.getMenu().getSlot(slotIndex).getItem().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
