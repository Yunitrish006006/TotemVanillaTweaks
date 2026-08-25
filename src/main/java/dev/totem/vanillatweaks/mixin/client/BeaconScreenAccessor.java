package dev.totem.vanillatweaks.mixin.client;

import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to the pending Beacon selections shown on the client screen. */
@Mixin(BeaconScreen.class)
public interface BeaconScreenAccessor {
    @Accessor("primary")
    Holder<MobEffect> totem$getPrimary();

    @Accessor("secondary")
    Holder<MobEffect> totem$getSecondary();
}
