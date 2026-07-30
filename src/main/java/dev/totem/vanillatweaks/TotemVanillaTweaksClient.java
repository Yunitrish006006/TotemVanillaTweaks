package dev.totem.vanillatweaks;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
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
        sortBackpackKey = new KeyMapping(
                "key.deadrecall.sort_backpack",
                InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                category
        );
    }
}
