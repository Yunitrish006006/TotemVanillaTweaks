package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives specialized crafting semantics priority over the generic container adapter.
 * This prevents Inventory/Crafting screens from transmitting two competing semantic families.
 */
@Mixin(value = ObserverNativeScreenClient.class, remap = false)
public abstract class ObserverNativeScreenClientCraftingPriorityMixin {
    @Shadow
    private static void closeTargetContainer(boolean canSend) {
        throw new AssertionError();
    }

    @Shadow
    private static void closeTargetFurnace(boolean canSend) {
        throw new AssertionError();
    }

    @Inject(method = "tickTarget", at = @At("HEAD"), cancellable = true)
    private static void totem$preferCraftingFamily(Minecraft minecraft, CallbackInfo ci) {
        Screen screen = minecraft.gui.screen();
        if (!ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_CRAFTING)
                || !(screen instanceof InventoryScreen || screen instanceof CraftingScreen)) {
            return;
        }

        closeTargetFurnace(ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_FURNACE));
        closeTargetContainer(ObserverNativeClient.targetSupportsScreen(
                ObserverNativeScreenPayloads.CAPABILITY_CONTAINER_SLOTS
        ));
        ci.cancel();
    }
}
