package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.TotemVanillaTweaksClient;
import dev.totem.vanillatweaks.client.ContainerSortClient;
import dev.totem.vanillatweaks.network.SortBackpackPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow
    protected Slot hoveredSlot;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void totemVanillaTweaks$handleContainerKey(
            KeyEvent event,
            CallbackInfoReturnable<Boolean> callback
    ) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        boolean editingText = screen.getFocused() instanceof EditBox editBox && editBox.isFocused();

        if (editingText) {
            if (Minecraft.getInstance().options.keyInventory.matches(event)) {
                callback.setReturnValue(true);
            }
            return;
        }

        if (TotemVanillaTweaksClient.sortBackpackKey().matches(event)) {
            SortBackpackPayload.Target target = ContainerSortClient.resolveTarget(
                    screen,
                    hoveredSlot,
                    Minecraft.getInstance()
            );
            if (target != null) {
                ContainerSortClient.requestSort(Minecraft.getInstance(), target);
                callback.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void totemVanillaTweaks$handleSortMouse(
            MouseButtonEvent event,
            boolean over,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (TotemVanillaTweaksClient.sortBackpackKey().matchesMouse(event)) {
            SortBackpackPayload.Target target = ContainerSortClient.resolveTarget(
                    (AbstractContainerScreen<?>) (Object) this,
                    hoveredSlot,
                    Minecraft.getInstance()
            );
            if (target != null) {
                ContainerSortClient.requestSort(Minecraft.getInstance(), target);
                callback.setReturnValue(true);
            }
        }
    }
}
