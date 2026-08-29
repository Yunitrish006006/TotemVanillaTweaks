package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.client.ObserverNativeEnchantingScreenClient;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.player.LocalPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps vanilla enchantment affordability/tooltips bound to the target's XP level. */
@Mixin(EnchantmentScreen.class)
abstract class ObserverEnchantmentLevelMixin {
    @Redirect(method = {"extractBackground", "extractRenderState"}, at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/player/LocalPlayer;experienceLevel:I", opcode = Opcodes.GETFIELD))
    private int totem$useRemoteExperienceLevel(LocalPlayer player) {
        return ObserverNativeEnchantingScreenClient.playerLevelFor((EnchantmentScreen) (Object) this,
                player.experienceLevel);
    }
}
