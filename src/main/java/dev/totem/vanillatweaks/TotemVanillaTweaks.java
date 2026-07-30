package dev.totem.vanillatweaks;

import dev.totem.vanillatweaks.bookshelf.BookshelfInventoryRule;
import dev.totem.vanillatweaks.network.VanillaTweaksPayloadRegistration;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns small vanilla gameplay behavior without a dedicated feature module. */
public final class TotemVanillaTweaks implements ModInitializer {
    public static final String MOD_ID = "totem-vanilla-tweaks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        VanillaTweaksPayloadRegistration.register();
        BookshelfInventoryRule.register();
        LOGGER.info("TotemVanillaTweaks initialized without DeadRecall implementation dependency");
    }
}
