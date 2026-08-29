package dev.totem.vanillatweaks.mixin.client;

import dev.totem.vanillatweaks.client.ObserverOwnedScreenCoordinator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds the remote logical cursor into the real Screen's vanilla hover/tooltip renderer. */
@Mixin(Screen.class)
public abstract class ObserverReadOnlyScreenCursorMixin {
    @ModifyVariable(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int totem$remoteMouseX(int local) { return ObserverOwnedScreenCoordinator.renderMouseX(local); }

    @ModifyVariable(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int totem$remoteMouseY(int local) { return ObserverOwnedScreenCoordinator.renderMouseY(local); }

    /**
     * The native OS pointer is local-only, so render a tiny hard-edged Minecraft-style
     * arrow above the owning Screen after its normal tooltip/carried-stack extraction.
     */
    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
    private void totem$remoteCursorOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                           float partialTick, CallbackInfo ci) {
        ObserverOwnedScreenCoordinator.recordRenderedFrame(this);
        if (!ObserverOwnedScreenCoordinator.hasRemoteCursor()) return;
        int x = ObserverOwnedScreenCoordinator.renderMouseX(mouseX);
        int y = ObserverOwnedScreenCoordinator.renderMouseY(mouseY);
        // One-pixel black outline/shadow followed by a compact vanilla-white arrow.
        graphics.fill(x, y, x + 2, y + 10, 0xFF000000);
        graphics.fill(x + 2, y + 2, x + 4, y + 8, 0xFF000000);
        graphics.fill(x + 4, y + 4, x + 6, y + 7, 0xFF000000);
        graphics.fill(x + 2, y + 7, x + 5, y + 10, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + 2, y + 8, 0xFFFFFFFF);
        graphics.fill(x + 2, y + 2, x + 3, y + 7, 0xFFFFFFFF);
        graphics.fill(x + 3, y + 3, x + 4, y + 6, 0xFFFFFFFF);
        graphics.fill(x + 4, y + 5, x + 5, y + 6, 0xFFFFFFFF);
    }
}
