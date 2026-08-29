package dev.totem.vanillatweaks.observer;

import dev.totem.vanillatweaks.network.ObserverHorseScreenPayloads;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ObserverHorsePayloadsTest {
    @Test void ownsCapabilityBit25AndValidatesMountLayouts() {
        assertEquals(1L << 25, ObserverHorseScreenPayloads.CAPABILITY);
        assertTrue(ObserverHorseRelayManager.valid(open("minecraft:llama", 3, 47)));
        assertTrue(ObserverHorseRelayManager.valid(open("minecraft:donkey", 5, 53)));
        assertFalse(ObserverHorseRelayManager.valid(open("minecraft:horse", 5, 53)));
        assertFalse(ObserverHorseRelayManager.valid(open("minecraft:llama", 3, 46)));
        assertTrue(ObserverHorseRelayManager.valid(ObserverHorseScreenPayloads.closed(9)));
    }

    private static ObserverHorseScreenPayloads.HorseState open(String type, int columns, int slotCount) {
        List<ObserverHorseScreenPayloads.HorseSlotState> slots = new ArrayList<>();
        for (int i = 0; i < slotCount; i++)
            slots.add(new ObserverHorseScreenPayloads.HorseSlotState(i, 0, 0, ItemStack.EMPTY));
        return new ObserverHorseScreenPayloads.HorseState(1, 8, true, "horse_inventory",
                ObserverHorseScreenPayloads.SCREEN_CLASS, "Mount", 42, UUID.randomUUID(), type, columns, slots);
    }
}
