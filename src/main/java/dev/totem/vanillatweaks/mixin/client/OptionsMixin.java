package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.TotemVanillaTweaksClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.Arrays;

@Mixin(Options.class)
public abstract class OptionsMixin {
    @Mutable
    @Final
    @Shadow
    public KeyMapping[] keyMappings;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void totemVanillaTweaks$registerSortKey(
            net.minecraft.client.Minecraft minecraft,
            File file,
            CallbackInfo callback
    ) {
        KeyMapping key = TotemVanillaTweaksClient.sortBackpackKey();
        if (key == null) {
            return;
        }
        for (KeyMapping existing : keyMappings) {
            if (existing == key) {
                return;
            }
        }
        keyMappings = Arrays.copyOf(keyMappings, keyMappings.length + 1);
        keyMappings[keyMappings.length - 1] = key;
    }
}
