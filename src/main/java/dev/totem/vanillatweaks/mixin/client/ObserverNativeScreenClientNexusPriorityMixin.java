package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/** Prevents metadata-only fallback from competing with negotiated TotemNexus semantic screens. */
@Mixin(value = ObserverNativeScreenClient.class, remap = false)
public abstract class ObserverNativeScreenClientNexusPriorityMixin {
    private static final Set<String> NEXUS_SCREENS = Set.of(
            "dev.totem.nexus.client.NexusMapScreen",
            "dev.totem.nexus.client.NexusSpaceUnitMapScreen",
            "dev.totem.nexus.client.NexusFriendsScreen",
            "dev.totem.nexus.client.NexusSpaceUnitFriendsScreen",
            "dev.totem.nexus.client.NexusRegistrationPreviewScreen",
            "dev.totem.nexus.client.NexusSpaceUnitRegistrationPreviewScreen"
    );

    @Inject(method = "isStructuredTargetScreen", at = @At("HEAD"), cancellable = true)
    private static void totem$markNexusStructured(Screen screen, CallbackInfoReturnable<Boolean> cir) {
        if (screen != null
                && NEXUS_SCREENS.contains(screen.getClass().getName())
                && ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_NEXUS)) {
            cir.setReturnValue(true);
        }
    }
}
