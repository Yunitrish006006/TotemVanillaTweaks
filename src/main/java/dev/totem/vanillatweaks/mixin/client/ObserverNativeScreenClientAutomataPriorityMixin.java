package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives TotemAutomata Copper Golem semantics priority over the generic container adapter. */
@Mixin(value = ObserverNativeScreenClient.class, remap = false)
public abstract class ObserverNativeScreenClientAutomataPriorityMixin {
    private static final String SCREEN_CLASS = "dev.totem.automata.client.CopperGolemMenuScreen";

    @Shadow
    private static void closeTargetContainer(boolean canSend) {
        throw new AssertionError();
    }

    @Shadow
    private static void closeTargetFurnace(boolean canSend) {
        throw new AssertionError();
    }

    @Inject(method = "tickTarget", at = @At("HEAD"), cancellable = true)
    private static void totem$preferAutomataFamily(Minecraft minecraft, CallbackInfo ci) {
        if (!ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_AUTOMATA_COPPER_GOLEM)
                || minecraft.gui.screen() == null
                || !SCREEN_CLASS.equals(minecraft.gui.screen().getClass().getName())) {
            return;
        }
        closeTargetFurnace(ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_FURNACE));
        closeTargetContainer(ObserverNativeClient.targetSupportsScreen(
                ObserverNativeScreenPayloads.CAPABILITY_CONTAINER_SLOTS));
        ci.cancel();
    }
}
