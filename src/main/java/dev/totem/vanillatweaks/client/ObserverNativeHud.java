package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Locally reconstructed Observer HUD. No pixels or textures are received from the Target client. */
public final class ObserverNativeHud {
    private static final Identifier ELEMENT_ID = Identifier.fromNamespaceAndPath(
            TotemVanillaTweaks.MOD_ID,
            "observer_native_hud"
    );
    private static final int BACKGROUND = 0xB0000000;
    private static final int HEALTH = 0xFFE53935;
    private static final int FOOD = 0xFFFFB300;
    private static final int SATURATION = 0xFFFFE082;
    private static final int EXPERIENCE = 0xFF7CB342;
    private static final int SELECTED = 0xFFFFFFFF;
    private static final int INACTIVE_SLOT = 0x80606060;
    private static final int TEXT = 0xFFFFFFFF;

    private static long extractedFrames;

    private ObserverNativeHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                ELEMENT_ID,
                ObserverNativeHud::extract
        );
    }

    static long extractedFrames() {
        return extractedFrames;
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!ObserverNativeClient.observerSessionActive()
                || ObserverNativeClient.lastNativeStateSequence() < 0L) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.gui.screen() != null) {
            return;
        }

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int center = width / 2;
        int barY = height - 45;
        int barWidth = 82;
        int barHeight = 5;

        float healthRatio = ratio(ObserverNativeClient.remoteHealth(), ObserverNativeClient.remoteMaxHealth());
        float foodRatio = Mth.clamp(ObserverNativeClient.remoteFood() / 20.0F, 0.0F, 1.0F);
        float saturationRatio = Mth.clamp(ObserverNativeClient.remoteSaturation() / 20.0F, 0.0F, 1.0F);
        float experienceRatio = Mth.clamp(ObserverNativeClient.remoteExperienceProgress(), 0.0F, 1.0F);

        int healthX = center - 91;
        int foodX = center + 9;
        fillBar(graphics, healthX, barY, barWidth, barHeight, healthRatio, HEALTH);
        fillBar(graphics, foodX, barY, barWidth, barHeight, foodRatio, FOOD);

        int saturationWidth = Math.round(barWidth * saturationRatio);
        if (saturationWidth > 0) {
            graphics.fill(foodX, barY + barHeight - 1, foodX + saturationWidth, barY + barHeight, SATURATION);
        }

        int xpX = center - 91;
        int xpY = height - 36;
        int xpWidth = 182;
        fillBar(graphics, xpX, xpY, xpWidth, 3, experienceRatio, EXPERIENCE);

        int slotY = height - 29;
        int selectedSlot = Mth.clamp(ObserverNativeClient.remoteSelectedHotbarSlot(), 0, 8);
        for (int slot = 0; slot < 9; slot++) {
            int x = center - 90 + slot * 20;
            int color = slot == selectedSlot ? SELECTED : INACTIVE_SLOT;
            graphics.fill(x, slotY, x + 18, slotY + 2, color);
        }

        String targetName = ObserverNativeClient.observerTargetName();
        String status = targetName
                + "  HP " + Math.round(ObserverNativeClient.remoteHealth())
                + "/" + Math.round(ObserverNativeClient.remoteMaxHealth())
                + "  Food " + ObserverNativeClient.remoteFood()
                + "  Lv " + ObserverNativeClient.remoteExperienceLevel()
                + "  Slot " + (selectedSlot + 1);
        int textX = center - minecraft.font.width(status) / 2;
        graphics.text(minecraft.font, status, textX, barY - 11, TEXT, true);
        extractedFrames++;
    }

    private static void fillBar(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            float ratio,
            int foreground
    ) {
        graphics.fill(x, y, x + width, y + height, BACKGROUND);
        int filled = Math.round(width * Mth.clamp(ratio, 0.0F, 1.0F));
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + height, foreground);
        }
    }

    private static float ratio(float value, float maximum) {
        if (!(maximum > 0.0F) || !Float.isFinite(value) || !Float.isFinite(maximum)) {
            return 0.0F;
        }
        return Mth.clamp(value / maximum, 0.0F, 1.0F);
    }
}
