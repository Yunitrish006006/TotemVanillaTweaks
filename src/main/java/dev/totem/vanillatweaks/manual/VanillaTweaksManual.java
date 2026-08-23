package dev.totem.vanillatweaks.manual;

import dev.totem.core.api.v1.manual.TotemManualSection;
import dev.totem.core.api.v1.manual.TotemModuleManualSource;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/** Small vanilla-behavior guide recorded from a lectern. */
public final class VanillaTweaksManual {
    private static final TotemManualSection SECTION = new TotemManualSection(
            Identifier.fromNamespaceAndPath("totem", "vanilla_tweaks/manual"),
            700,
            "book.deadrecall.vanilla_tweaks_manual.title",
            List.of(
                    "book.deadrecall.vanilla_tweaks_manual.page.1",
                    "book.deadrecall.vanilla_tweaks_manual.page.2",
                    "book.deadrecall.vanilla_tweaks_manual.page.3"
            )
    );

    private VanillaTweaksManual() {
    }

    public static void register() {
        TotemModuleManualSource.register(
                SECTION,
                Identifier.fromNamespaceAndPath("deadrecall", "vanilla_tweaks_manual"),
                state -> state.is(Blocks.LECTERN)
        );
    }
}
