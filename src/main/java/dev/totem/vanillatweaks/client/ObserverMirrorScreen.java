package dev.totem.vanillatweaks.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base type for every local Observer reconstruction screen.
 *
 * <p>Target metadata capture excludes this exact type so an Observer that is
 * itself being observed cannot relay a reconstructed screen into another
 * session.</p>
 */
abstract class ObserverMirrorScreen extends Screen {
    protected ObserverMirrorScreen(Component title) {
        super(title);
    }

    static boolean isMirror(Screen screen) {
        return screen instanceof ObserverMirrorScreen;
    }
}
