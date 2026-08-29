package dev.totem.vanillatweaks.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.totem.vanillatweaks.client.ObserverReadOnlyPacketFirewall;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Gui.class)
public abstract class ObserverReadOnlyScreenTransitionMixin {
    @Shadow public abstract Screen screen();

    @WrapMethod(method = "setScreen")
    private void totem$guardObserverScreenTransition(Screen next, Operation<Void> original) {
        Screen previous = screen();
        ObserverReadOnlyPacketFirewall.beginScreenTransition(previous, next);
        try {
            original.call(next);
        } finally {
            ObserverReadOnlyPacketFirewall.endScreenTransition(previous, next);
        }
    }
}
