package dev.totem.vanillatweaks.gametest;

import dev.totem.core.api.v1.manual.TotemManualAssembler;
import dev.totem.core.api.v1.manual.TotemManualRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Integration coverage for the VanillaTweaks section in the shared Totem Manual. */
public final class VanillaTweaksManualGameTest {
    private static final Identifier SECTION_ID =
            Identifier.fromNamespaceAndPath("totem", "vanilla_tweaks/manual");
    private static final List<String> EXPECTED_PAGE_KEYS = List.of(
            "book.totem.vanilla_tweaks_manual.page.1",
            "book.totem.vanilla_tweaks_manual.page.2",
            "book.totem.vanilla_tweaks_manual.page.3"
    );

    @GameTest(maxTicks = 20)
    public void vanillaTweaksSectionIsRegisteredAndAssemblable(GameTestHelper helper) {
        var section = TotemManualRegistry.global().section(SECTION_ID).orElse(null);
        if (section == null) {
            helper.fail("VanillaTweaks manual section was not registered");
            return;
        }
        if (section.order() != 700 || !section.pageKeys().equals(EXPECTED_PAGE_KEYS)) {
            helper.fail("VanillaTweaks manual section did not match the gameplay-only page contract");
            return;
        }
        var manual = TotemManualAssembler.create(List.of(section));
        if (!TotemManualAssembler.isCanonical(manual)
                || TotemManualAssembler.sections(manual).stream().noneMatch(value -> value.id().equals(SECTION_ID))) {
            helper.fail("VanillaTweaks manual section did not assemble into a canonical Totem Manual");
            return;
        }
        helper.succeed();
    }
}
