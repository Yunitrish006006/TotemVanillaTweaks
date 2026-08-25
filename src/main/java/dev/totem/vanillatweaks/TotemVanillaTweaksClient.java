package dev.totem.vanillatweaks;

import com.mojang.blaze3d.platform.InputConstants;
import dev.totem.vanillatweaks.client.ObserverAutomataCopperGolemScreenClient;
import dev.totem.vanillatweaks.client.ObserverBrewingScreenClient;
import dev.totem.vanillatweaks.client.ObserverCartographyScreenClient;
import dev.totem.vanillatweaks.client.ObserverGrindstoneScreenClient;
import dev.totem.vanillatweaks.client.ObserverLoomScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeAnvilScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeBookScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeCraftingScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeEnchantingScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeHud;
import dev.totem.vanillatweaks.client.ObserverNativeMerchantScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.client.ObserverNexusScreenClient;
import dev.totem.vanillatweaks.client.ObserverRemnantBackpackScreenClient;
import dev.totem.vanillatweaks.client.ObserverSmithingScreenClient;
import dev.totem.vanillatweaks.client.ObserverStonecutterScreenClient;
import dev.totem.vanillatweaks.client.ObserverUiClient;
import dev.totem.vanillatweaks.client.ObserverVillagersWoodcutterScreenClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class TotemVanillaTweaksClient implements ClientModInitializer {
    private static KeyMapping sortBackpackKey;

    public static KeyMapping sortBackpackKey() {
        return sortBackpackKey;
    }

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, "category")
        );
        sortBackpackKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.deadrecall.sort_backpack",
                InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                category
        ));
        ObserverUiClient.register();
        ObserverNativeClient.register();
        ObserverNativeScreenClient.register();
        ObserverNativeBookScreenClient.register();
        ObserverNativeCraftingScreenClient.register();
        ObserverNativeMerchantScreenClient.register();
        ObserverNativeAnvilScreenClient.register();
        ObserverNativeEnchantingScreenClient.register();
        ObserverRemnantBackpackScreenClient.register();
        ObserverAutomataCopperGolemScreenClient.register();
        ObserverNexusScreenClient.register();
        ObserverVillagersWoodcutterScreenClient.register();
        ObserverBrewingScreenClient.register();
        ObserverSmithingScreenClient.register();
        ObserverStonecutterScreenClient.register();
        ObserverGrindstoneScreenClient.register();
        ObserverLoomScreenClient.register();
        ObserverCartographyScreenClient.register();
        ObserverNativeHud.register();
    }
}
