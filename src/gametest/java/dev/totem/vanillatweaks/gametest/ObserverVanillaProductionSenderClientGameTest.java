package dev.totem.vanillatweaks.gametest;

import dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen;
import dev.totem.vanillatweaks.client.ObserverBeaconScreenClient;
import dev.totem.vanillatweaks.client.ObserverAdvancementsScreenClient;
import dev.totem.vanillatweaks.client.ObserverBrewingScreenClient;
import dev.totem.vanillatweaks.client.ObserverCartographyScreenClient;
import dev.totem.vanillatweaks.client.ObserverCrafterScreenClient;
import dev.totem.vanillatweaks.client.ObserverGrindstoneScreenClient;
import dev.totem.vanillatweaks.client.ObserverHorseScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeAnvilScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeBookScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeClient;
import dev.totem.vanillatweaks.client.ObserverNativeEnchantingScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeMerchantScreenClient;
import dev.totem.vanillatweaks.client.ObserverNativeScreenClient;
import dev.totem.vanillatweaks.client.ObserverSmithingScreenClient;
import dev.totem.vanillatweaks.client.ObserverSignScreenClient;
import dev.totem.vanillatweaks.client.ObserverStatsScreenClient;
import dev.totem.vanillatweaks.network.ObserverAdvancementsScreenPayloads;
import dev.totem.vanillatweaks.client.ObserverUiClient;
import dev.totem.vanillatweaks.network.ObserverAnvilScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverBeaconScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverBookScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverBrewingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverCartographyScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverCrafterScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverEnchantingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverGrindstoneScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverHorseScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverMerchantScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import dev.totem.vanillatweaks.network.ObserverSmithingScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverSignScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverStatsScreenPayloads;
import dev.totem.vanillatweaks.observer.ObserverAdvancementsRelayManager;
import dev.totem.vanillatweaks.observer.ObserverBeaconRelayManager;
import dev.totem.vanillatweaks.observer.ObserverBrewingRelayManager;
import dev.totem.vanillatweaks.observer.ObserverCartographyRelayManager;
import dev.totem.vanillatweaks.observer.ObserverCrafterRelayManager;
import dev.totem.vanillatweaks.observer.ObserverGrindstoneRelayManager;
import dev.totem.vanillatweaks.observer.ObserverNativeSessionManager;
import dev.totem.vanillatweaks.observer.ObserverSmithingRelayManager;
import dev.totem.vanillatweaks.observer.ObserverSignRelayManager;
import dev.totem.vanillatweaks.observer.ObserverStatsRelayManager;
import dev.totem.vanillatweaks.mixin.client.AbstractSignEditScreenAccessor;
import dev.totem.vanillatweaks.mixin.client.AnvilScreenAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.HangingSignEditScreen;
import net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * Runtime evidence that vanilla Observer senders extract the visible Screen's real Menu.
 * These checks deliberately invoke the same captureTargetState helpers used by tickTarget;
 * they never manufacture relay payloads or inspect a framebuffer.
 */
public final class ObserverVanillaProductionSenderClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitTicks(2);
            singleplayer.getClientLevel().waitForChunksRender();
            context.runOnClient(minecraft -> {
                verifyContainerSlots(minecraft);
                verifyFurnace(minecraft);
                verifyAnvil(minecraft);
                verifyEnchanting(minecraft);
                verifyMerchant(minecraft);
                verifyBrewing(minecraft);
                verifySmithing(minecraft);
                verifyGrindstone(minecraft);
                verifyCartography(minecraft);
                verifyBeacon(minecraft);
                verifyCrafter(minecraft);
                verifyBook(minecraft);
                verifySign(minecraft);
                verifyAdvancements(minecraft);
                verifyStats(minecraft);
                verifyHorseInventory(minecraft);
                verifyChatPrivacy(minecraft);
                verifyNestedMirrorMetadata(minecraft);
                minecraft.setScreenAndShow(null);
            });
        }
    }

    private static void verifyContainerSlots(Minecraft minecraft) {
        ChestMenu menu = ChestMenu.threeRows(101, minecraft.player.getInventory());
        ContainerScreen screen = new ContainerScreen(menu, minecraft.player.getInventory(),
                Component.literal("Production Container"));
        showSyntheticScreen(minecraft, screen, menu, "container_slots");
        ObserverNativeScreenPayloads.ContainerState state =
                (ObserverNativeScreenPayloads.ContainerState) invoke(
                        ObserverNativeScreenClient.class, "captureContainerState",
                        new Class<?>[]{Minecraft.class, AbstractContainerScreen.class, long.class},
                        minecraft, screen, 101L);
        assertOrdinals(state.slots(), 63, "container_slots");
        assertValid(ObserverNativeSessionManager.class, "validContainer",
                ObserverNativeScreenPayloads.ContainerState.class, state, "container_slots");
        assertGenericSuppressed(minecraft, ObserverNativeScreenPayloads.CAPABILITY_CONTAINER_SLOTS,
                ContainerScreen.class.getName(), "container_slots");
    }

    private static void verifyFurnace(Minecraft minecraft) {
        FurnaceMenu menu = new FurnaceMenu(102, minecraft.player.getInventory());
        FurnaceScreen screen = new FurnaceScreen(menu, minecraft.player.getInventory(),
                Component.literal("Production Furnace"));
        showSyntheticScreen(minecraft, screen, menu, "furnace");
        ObserverNativeScreenPayloads.FurnaceState state =
                (ObserverNativeScreenPayloads.FurnaceState) invoke(
                        ObserverNativeScreenClient.class, "captureFurnaceState",
                        new Class<?>[]{Minecraft.class, AbstractFurnaceScreen.class, long.class},
                        minecraft, screen, 102L);
        assertOrdinals(state.slots(), 39, "furnace");
        assertValid(ObserverNativeSessionManager.class, "validFurnace",
                ObserverNativeScreenPayloads.FurnaceState.class, state, "furnace");
        assertGenericSuppressed(minecraft, ObserverNativeScreenPayloads.CAPABILITY_FURNACE,
                FurnaceScreen.class.getName(), "furnace");
    }

    private static void verifyAnvil(Minecraft minecraft) {
        AnvilMenu menu = new AnvilMenu(103, minecraft.player.getInventory());
        AnvilScreen screen = new AnvilScreen(menu, minecraft.player.getInventory(),
                Component.literal("Production Anvil"));
        showSyntheticScreen(minecraft, screen, menu, "anvil");
        ((AnvilScreenAccessor) screen).totem$getNameField().setValue("https://private.example/token");
        ObserverAnvilScreenPayloads.AnvilState state =
                (ObserverAnvilScreenPayloads.AnvilState) invoke(
                        ObserverNativeAnvilScreenClient.class, "captureTargetState",
                        new Class<?>[]{Minecraft.class, AnvilScreen.class, long.class},
                        minecraft, screen, 103L);
        assertOrdinals(state.slots(), 39, "anvil");
        if (!state.itemName().isEmpty()) {
            throw new AssertionError("anvil production extractor leaked an unsent rename draft");
        }
        assertValid(ObserverNativeSessionManager.class, "validAnvil",
                ObserverAnvilScreenPayloads.AnvilState.class, state, "anvil");
        assertGenericSuppressed(minecraft, ObserverNativeScreenPayloads.CAPABILITY_ANVIL,
                AnvilScreen.class.getName(), "anvil");
    }

    private static void verifyEnchanting(Minecraft minecraft) {
        EnchantmentMenu menu = new EnchantmentMenu(104, minecraft.player.getInventory());
        EnchantmentScreen screen = new EnchantmentScreen(menu, minecraft.player.getInventory(),
                Component.literal("Production Enchanting"));
        showSyntheticScreen(minecraft, screen, menu, "enchanting");
        ObserverEnchantingScreenPayloads.EnchantingState state =
                (ObserverEnchantingScreenPayloads.EnchantingState) invoke(
                        ObserverNativeEnchantingScreenClient.class, "captureTargetState",
                        new Class<?>[]{Minecraft.class, EnchantmentScreen.class, long.class},
                        minecraft, screen, 104L);
        assertOrdinals(state.slots(), 38, "enchanting");
        assertValid(ObserverNativeSessionManager.class, "validEnchanting",
                ObserverEnchantingScreenPayloads.EnchantingState.class, state, "enchanting");
        assertGenericSuppressed(minecraft, ObserverNativeScreenPayloads.CAPABILITY_ENCHANTING,
                EnchantmentScreen.class.getName(), "enchanting");
    }

    private static void verifyHorseInventory(Minecraft minecraft) {
        AbstractHorse mount = EntityTypes.LLAMA.create(minecraft.level, EntitySpawnReason.LOAD);
        if (mount == null) throw new AssertionError("horse_inventory test could not create mount");
        UUID mountId = UUID.randomUUID();
        mount.setUUID(mountId);
        // HorseInventoryScreen's vanilla renderer requires an assigned id even for
        // this detached source fixture. Keep it outside the tracked positive range.
        mount.setId(-1 - (mountId.hashCode() & Integer.MAX_VALUE));
        int columns = 3;
        HorseInventoryMenu menu = new HorseInventoryMenu(125, minecraft.player.getInventory(),
                new SimpleContainer(2 + columns * 3), mount, columns);
        HorseInventoryScreen screen = new HorseInventoryScreen(menu, minecraft.player.getInventory(), mount, columns);
        showSyntheticScreen(minecraft, screen, menu, "horse_inventory");
        ObserverHorseScreenPayloads.HorseState state = (ObserverHorseScreenPayloads.HorseState) invoke(
                ObserverHorseScreenClient.class, "captureTargetState",
                new Class<?>[]{HorseInventoryScreen.class, long.class}, screen, 125L);
        if (state == null || state.slots().size() != 47 || state.columns() != columns
                || !"minecraft:llama".equals(state.entityType()))
            throw new AssertionError("horse_inventory production extractor lost real mount/menu state");
        assertGenericSuppressed(minecraft, ObserverHorseScreenPayloads.CAPABILITY,
                HorseInventoryScreen.class.getName(), "horse_inventory");
    }

    private static void verifyMerchant(Minecraft minecraft) {
        MerchantMenu menu = new MerchantMenu(105, minecraft.player.getInventory());
        MerchantScreen screen = new MerchantScreen(menu, minecraft.player.getInventory(),
                Component.literal("Production Merchant"));
        showSyntheticScreen(minecraft, screen, menu, "merchant");
        ObserverMerchantScreenPayloads.MerchantState state =
                (ObserverMerchantScreenPayloads.MerchantState) invoke(
                        ObserverNativeMerchantScreenClient.class, "captureTargetState",
                        new Class<?>[]{MerchantScreen.class, long.class}, screen, 105L);
        if (!state.offers().isEmpty() || state.selectedOffer() != 0) {
            throw new AssertionError("merchant production extractor did not use its real empty MerchantMenu");
        }
        assertValid(ObserverNativeSessionManager.class, "validMerchant",
                ObserverMerchantScreenPayloads.MerchantState.class, state, "merchant");
        assertGenericSuppressed(minecraft, ObserverNativeScreenPayloads.CAPABILITY_MERCHANT,
                MerchantScreen.class.getName(), "merchant");
    }

    private static void verifyBrewing(Minecraft minecraft) {
        BrewingStandMenu menu = new BrewingStandMenu(106, minecraft.player.getInventory());
        BrewingStandScreen screen = new BrewingStandScreen(menu, minecraft.player.getInventory(),
                Component.literal("Production Brewing"));
        showSyntheticScreen(minecraft, screen, menu, "brewing");
        ObserverBrewingScreenPayloads.BrewingState state =
                (ObserverBrewingScreenPayloads.BrewingState) invoke(
                        ObserverBrewingScreenClient.class, "captureTargetState",
                        new Class<?>[]{BrewingStandScreen.class, long.class}, screen, 106L);
        assertOrdinals(state.slots(), 41, "brewing");
        assertValid(ObserverBrewingRelayManager.class, "valid",
                ObserverBrewingScreenPayloads.BrewingState.class, state, "brewing");
        assertGenericSuppressed(minecraft, ObserverBrewingScreenPayloads.CAPABILITY,
                BrewingStandScreen.class.getName(), "brewing");
    }

    private static void verifySmithing(Minecraft minecraft) {
        SmithingMenu menu = new SmithingMenu(107, minecraft.player.getInventory());
        SmithingScreen screen = new SmithingScreen(menu, minecraft.player.getInventory(),
                Component.literal("Production Smithing"));
        showSyntheticScreen(minecraft, screen, menu, "smithing");
        ObserverSmithingScreenPayloads.SmithingState state =
                (ObserverSmithingScreenPayloads.SmithingState) invoke(
                        ObserverSmithingScreenClient.class, "captureTargetState",
                        new Class<?>[]{SmithingScreen.class, long.class}, screen, 107L);
        assertOrdinals(state.slots(), 40, "smithing");
        assertValid(ObserverSmithingRelayManager.class, "valid",
                ObserverSmithingScreenPayloads.SmithingState.class, state, "smithing");
        assertGenericSuppressed(minecraft, ObserverSmithingScreenPayloads.CAPABILITY,
                SmithingScreen.class.getName(), "smithing");
    }

    private static void verifyGrindstone(Minecraft minecraft) {
        GrindstoneMenu menu = new GrindstoneMenu(108, minecraft.player.getInventory());
        GrindstoneScreen screen = new GrindstoneScreen(menu, minecraft.player.getInventory(),
                Component.literal("Production Grindstone"));
        showSyntheticScreen(minecraft, screen, menu, "grindstone");
        ObserverGrindstoneScreenPayloads.GrindstoneState state =
                (ObserverGrindstoneScreenPayloads.GrindstoneState) invoke(
                        ObserverGrindstoneScreenClient.class, "captureTargetState",
                        new Class<?>[]{GrindstoneScreen.class, long.class}, screen, 108L);
        assertOrdinals(state.slots(), 39, "grindstone");
        assertValid(ObserverGrindstoneRelayManager.class, "valid",
                ObserverGrindstoneScreenPayloads.GrindstoneState.class, state, "grindstone");
        assertGenericSuppressed(minecraft, ObserverGrindstoneScreenPayloads.CAPABILITY,
                GrindstoneScreen.class.getName(), "grindstone");
    }

    private static void verifyCartography(Minecraft minecraft) {
        CartographyTableMenu menu = new CartographyTableMenu(109, minecraft.player.getInventory());
        CartographyTableScreen screen = new CartographyTableScreen(menu, minecraft.player.getInventory(),
                Component.literal("Production Cartography"));
        showSyntheticScreen(minecraft, screen, menu, "cartography");
        ObserverCartographyScreenPayloads.CartographyState state =
                (ObserverCartographyScreenPayloads.CartographyState) invoke(
                        ObserverCartographyScreenClient.class, "captureTargetState",
                        new Class<?>[]{CartographyTableScreen.class, long.class}, screen, 109L);
        assertOrdinals(state.slots(), 39, "cartography");
        assertValid(ObserverCartographyRelayManager.class, "valid",
                ObserverCartographyScreenPayloads.CartographyState.class, state, "cartography");
        assertGenericSuppressed(minecraft, ObserverCartographyScreenPayloads.CAPABILITY,
                CartographyTableScreen.class.getName(), "cartography");
    }

    private static void verifyBeacon(Minecraft minecraft) {
        BeaconMenu menu = new BeaconMenu(110, minecraft.player.getInventory());
        BeaconScreen screen = new BeaconScreen(menu, minecraft.player.getInventory(),
                Component.literal("Production Beacon"));
        showSyntheticScreen(minecraft, screen, menu, "beacon");
        ObserverBeaconScreenPayloads.BeaconState state =
                (ObserverBeaconScreenPayloads.BeaconState) invoke(
                        ObserverBeaconScreenClient.class, "captureTargetState",
                        new Class<?>[]{BeaconScreen.class, long.class}, screen, 110L);
        assertOrdinals(state.slots(), 37, "beacon");
        assertValid(ObserverBeaconRelayManager.class, "valid",
                ObserverBeaconScreenPayloads.BeaconState.class, state, "beacon");
        assertGenericSuppressed(minecraft, ObserverBeaconScreenPayloads.CAPABILITY,
                BeaconScreen.class.getName(), "beacon");
    }

    private static void verifyCrafter(Minecraft minecraft) {
        CrafterMenu menu = new CrafterMenu(111, minecraft.player.getInventory());
        CrafterScreen screen = new CrafterScreen(menu, minecraft.player.getInventory(),
                Component.literal("Production Crafter"));
        showSyntheticScreen(minecraft, screen, menu, "crafter");
        ObserverCrafterScreenPayloads.CrafterState state =
                (ObserverCrafterScreenPayloads.CrafterState) invoke(
                        ObserverCrafterScreenClient.class, "captureTargetState",
                        new Class<?>[]{CrafterScreen.class, long.class}, screen, 111L);
        assertOrdinals(state.slots(), 46, "crafter");
        assertValid(ObserverCrafterRelayManager.class, "valid",
                ObserverCrafterScreenPayloads.CrafterState.class, state, "crafter");
        assertGenericSuppressed(minecraft, ObserverCrafterScreenPayloads.CAPABILITY,
                CrafterScreen.class.getName(), "crafter");
    }

    private static void verifyBook(Minecraft minecraft) {
        String pageText = "Production book semantic page";
        BookViewScreen view = new BookViewScreen(
                new BookViewScreen.BookAccess(List.of(Component.literal(pageText))));
        ObserverBookScreenPayloads.BookState viewState = captureBook(minecraft, view, 112L, "book/view");
        if (!ObserverBookScreenPayloads.VARIANT_WRITTEN.equals(viewState.variant())
                || viewState.pageCount() != 1 || viewState.pageIndex() != 0
                || !pageText.equals(viewState.pageText())) {
            throw new AssertionError("book/view production extractor captured the wrong branch");
        }

        WritableBookContent privateDraft = new WritableBookContent(List.of(
                Filterable.passThrough("private prompt and API token")));
        BookEditScreen edit = new BookEditScreen(
                minecraft.player, new ItemStack(Items.WRITABLE_BOOK), InteractionHand.MAIN_HAND,
                privateDraft);
        ObserverBookScreenPayloads.BookState editState = captureBook(minecraft, edit, 113L, "book/edit");
        if (!ObserverBookScreenPayloads.VARIANT_WRITABLE.equals(editState.variant())
                || editState.pageCount() != 1 || !editState.pageText().isEmpty()) {
            throw new AssertionError("book/edit production extractor leaked a writable-book draft");
        }

        BookSignScreen sign = new BookSignScreen(
                edit, minecraft.player, InteractionHand.MAIN_HAND, List.of("Unsubmitted book page"));
        ObserverBookScreenPayloads.BookState signState = captureBook(minecraft, sign, 114L, "book/sign");
        if (!ObserverBookScreenPayloads.VARIANT_SIGNING.equals(signState.variant())
                || signState.pageCount() != 1 || !signState.pageText().isEmpty()
                || !signState.bookTitle().isEmpty() || !signState.author().isEmpty()) {
            throw new AssertionError("book/sign production extractor leaked an unsent signing draft");
        }

        LecternScreen lectern = new LecternScreen(
                new LecternMenu(115), minecraft.player.getInventory(), Component.literal("Production Lectern"));
        ObserverBookScreenPayloads.BookState lecternState = captureBook(
                minecraft, lectern, 115L, "book/lectern");
        if (!ObserverBookScreenPayloads.VARIANT_LECTERN.equals(lecternState.variant())) {
            throw new AssertionError("book/lectern production extractor captured the wrong branch");
        }

        assertValid(ObserverNativeSessionManager.class, "validBook",
                ObserverBookScreenPayloads.BookState.class,
                ObserverBookScreenPayloads.closed(116L), "book close");
    }

    private static ObserverBookScreenPayloads.BookState captureBook(
            Minecraft minecraft, Screen screen, long sequence, String variant) {
        showScreen(minecraft, screen, variant);
        ObserverBookScreenPayloads.BookState state =
                (ObserverBookScreenPayloads.BookState) invoke(
                        ObserverNativeBookScreenClient.class, "captureTargetState",
                        new Class<?>[]{Screen.class, long.class}, screen, sequence);
        if (state == null) throw new AssertionError(variant + " production extractor returned null");
        assertValid(ObserverNativeSessionManager.class, "validBook",
                ObserverBookScreenPayloads.BookState.class, state, variant);
        assertGenericSuppressed(minecraft, ObserverNativeScreenPayloads.CAPABILITY_BOOK,
                screen.getClass().getName(), variant);
        return state;
    }

    private static void verifySign(Minecraft minecraft) {
        SignBlockEntity sign = new SignBlockEntity(BlockPos.ZERO, Blocks.OAK_SIGN.defaultBlockState());
        SignEditScreen screen = new SignEditScreen(sign, true, false);
        showScreen(minecraft, screen, "sign");
        String[] privateLines = ((AbstractSignEditScreenAccessor) screen).totem$getMessages();
        privateLines[0] = "https://private.example/token";
        privateLines[1] = "unsent command /login secret";
        ObserverSignScreenPayloads.SignState state =
                (ObserverSignScreenPayloads.SignState) invoke(
                        ObserverSignScreenClient.class, "captureTargetState",
                        new Class<?>[]{net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen.class,
                                long.class}, screen, 114L);
        if (!ObserverSignScreenPayloads.SIGN_SCREEN_CLASS.equals(state.screenClass())
                || !"sign".equals(state.variant()) || !state.frontText()
                || state.lines().size() != ObserverSignScreenPayloads.LINE_COUNT
                || state.lines().stream().anyMatch(line -> !line.isEmpty())) {
            throw new AssertionError("sign production extractor leaked unsent editor text");
        }
        assertValid(ObserverSignRelayManager.class, "valid",
                ObserverSignScreenPayloads.SignState.class, state, "sign");
        assertValid(ObserverSignRelayManager.class, "valid",
                ObserverSignScreenPayloads.SignState.class,
                ObserverSignScreenPayloads.closed(115L), "sign close");
        assertGenericSuppressed(minecraft, ObserverSignScreenPayloads.CAPABILITY,
                SignEditScreen.class.getName(), "sign");

        HangingSignBlockEntity hangingSign = new HangingSignBlockEntity(
                BlockPos.ZERO, Blocks.OAK_HANGING_SIGN.defaultBlockState());
        HangingSignEditScreen hangingScreen = new HangingSignEditScreen(hangingSign, false, false);
        showScreen(minecraft, hangingScreen, "hanging sign");
        ObserverSignScreenPayloads.SignState hangingState =
                (ObserverSignScreenPayloads.SignState) invoke(
                        ObserverSignScreenClient.class, "captureTargetState",
                        new Class<?>[]{net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen.class,
                                long.class}, hangingScreen, 116L);
        if (!ObserverSignScreenPayloads.HANGING_SIGN_SCREEN_CLASS.equals(hangingState.screenClass())
                || !"hanging_sign".equals(hangingState.variant()) || hangingState.frontText()) {
            throw new AssertionError("sign production extractor did not capture the real HangingSignEditScreen");
        }
        assertValid(ObserverSignRelayManager.class, "valid",
                ObserverSignScreenPayloads.SignState.class, hangingState, "hanging sign");
        assertGenericSuppressed(minecraft, ObserverSignScreenPayloads.CAPABILITY,
                HangingSignEditScreen.class.getName(), "hanging sign");
    }

    private static void verifyAdvancements(Minecraft minecraft) {
        AdvancementsScreen screen = new AdvancementsScreen(minecraft.getConnection().getAdvancements());
        showScreen(minecraft, screen, "advancements");
        ObserverAdvancementsScreenPayloads.AdvancementsState state =
                (ObserverAdvancementsScreenPayloads.AdvancementsState) invoke(
                        ObserverAdvancementsScreenClient.class, "captureTargetState",
                        new Class<?>[]{Minecraft.class, AdvancementsScreen.class, long.class},
                        minecraft, screen, 116L);
        if (!ObserverAdvancementsScreenPayloads.SCREEN_CLASS.equals(state.screenClass())) {
            throw new AssertionError("advancements production extractor did not capture the real AdvancementsScreen");
        }
        assertValid(ObserverAdvancementsRelayManager.class, "valid",
                ObserverAdvancementsScreenPayloads.AdvancementsState.class, state, "advancements");
        assertValid(ObserverAdvancementsRelayManager.class, "valid",
                ObserverAdvancementsScreenPayloads.AdvancementsState.class,
                ObserverAdvancementsScreenPayloads.closed(117L), "advancements close");
        assertGenericSuppressed(minecraft, ObserverAdvancementsScreenPayloads.CAPABILITY,
                AdvancementsScreen.class.getName(), "advancements");
    }

    private static void verifyStats(Minecraft minecraft) {
        StatsScreen screen = new StatsScreen(null, minecraft.player.getStats());
        showScreen(minecraft, screen, "stats");
        ObserverStatsScreenPayloads.StatsState state =
                (ObserverStatsScreenPayloads.StatsState) invoke(
                        ObserverStatsScreenClient.class, "captureTargetState",
                        new Class<?>[]{StatsScreen.class, long.class}, screen, 118L);
        if (!ObserverStatsScreenPayloads.SCREEN_CLASS.equals(state.screenClass())) {
            throw new AssertionError("stats production extractor did not capture the real StatsScreen");
        }
        assertValid(ObserverStatsRelayManager.class, "valid",
                ObserverStatsScreenPayloads.StatsState.class, state, "stats");
        assertValid(ObserverStatsRelayManager.class, "valid",
                ObserverStatsScreenPayloads.StatsState.class,
                ObserverStatsScreenPayloads.closed(119L), "stats close");
        assertGenericSuppressed(minecraft, ObserverStatsScreenPayloads.CAPABILITY,
                StatsScreen.class.getName(), "stats");
    }

    private static void verifyChatPrivacy(Minecraft minecraft) {
        String unsent = "/login client-only-secret";
        ChatScreen screen = new ChatScreen(unsent, true);
        showScreen(minecraft, screen, "chat privacy");
        ObserverPayloads.ScreenState state = (ObserverPayloads.ScreenState) invoke(
                ObserverUiClient.class, "captureScreenMetadata",
                new Class<?>[]{Screen.class}, screen);
        if (!state.open() || !ChatScreen.class.getName().equals(state.screenClass())
                || state.title().contains(unsent) || state.toString().contains(unsent)) {
            throw new AssertionError("unsent ChatScreen text escaped into Observer metadata");
        }
        minecraft.setScreenAndShow(null);
    }

    private static void verifyNestedMirrorMetadata(Minecraft minecraft) {
        UUID firstTarget = UUID.randomUUID();
        applySession(firstTarget, ObserverNativeScreenPayloads.CAPABILITY_BOOK);
        invoke(ObserverNativeBookScreenClient.class, "acceptRelay",
                new Class<?>[]{ObserverBookScreenPayloads.BookRelay.class},
                new ObserverBookScreenPayloads.BookRelay(
                        firstTarget, ObserverBookScreenPayloads.PROTOCOL_VERSION, 1L, true,
                        ObserverNativeScreenPayloads.FAMILY_BOOK, ObserverBookScreenPayloads.VARIANT_WRITTEN,
                        BookViewScreen.class.getName(), "Nested mirror", 0, 1, "semantic", "", ""));
        Screen mirror = minecraft.gui.screen();
        if (!(mirror instanceof BookViewScreen) || !(mirror instanceof ObserverReadOnlyScreen)) {
            throw new AssertionError("nested Observer setup did not open a read-only Mojang BookViewScreen");
        }
        assertClosedMetadata(mirror, "first observer hop");

        UUID secondTarget = UUID.randomUUID();
        applySession(secondTarget, ObserverNativeScreenPayloads.CAPABILITY_BOOK);
        assertClosedMetadata(mirror, "second observer hop");
        applySession(new UUID(0L, 0L), 0L, false);
        minecraft.setScreenAndShow(null);
    }

    private static void assertClosedMetadata(Screen screen, String hop) {
        ObserverPayloads.ScreenState state = (ObserverPayloads.ScreenState) invoke(
                ObserverUiClient.class, "captureScreenMetadata", new Class<?>[]{Screen.class}, screen);
        if (state.open() || !state.screenClass().isEmpty() || !state.title().isEmpty()) {
            throw new AssertionError(hop + " retransmitted a local Observer mirror");
        }
    }

    private static void applySession(UUID targetId, long capabilities) {
        applySession(targetId, capabilities, true);
    }

    private static void applySession(UUID targetId, long capabilities, boolean active) {
        invoke(ObserverNativeClient.class, "applySession",
                new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(active, targetId,
                        active ? "NestedObserverTarget" : "",
                        ObserverNativePayloads.PROTOCOL_VERSION, capabilities));
    }

    private static void showSyntheticScreen(
            Minecraft minecraft,
            AbstractContainerScreen<?> screen,
            AbstractContainerMenu menu,
            String family
    ) {
        if (minecraft.player.containerMenu == menu) {
            throw new AssertionError(family + " test setup accidentally reused player.containerMenu");
        }
        minecraft.setScreenAndShow(screen);
        if (minecraft.gui.screen() != screen || screen.getMenu() != menu) {
            throw new AssertionError(family + " did not expose the synthetic Menu through the visible Screen");
        }
    }

    private static void showScreen(Minecraft minecraft, Screen screen, String family) {
        minecraft.setScreenAndShow(screen);
        if (minecraft.gui.screen() != screen) {
            throw new AssertionError(family + " did not become the visible production Screen");
        }
    }

    private static void assertOrdinals(
            List<ObserverNativeScreenPayloads.SlotState> slots,
            int expectedCount,
            String family
    ) {
        if (slots.size() != expectedCount) {
            throw new AssertionError(family + " production slot count " + slots.size()
                    + " (expected " + expectedCount + ")");
        }
        for (int ordinal = 0; ordinal < slots.size(); ordinal++) {
            if (slots.get(ordinal).index() != ordinal) {
                throw new AssertionError(family + " slot " + ordinal + " used container-local id "
                        + slots.get(ordinal).index());
            }
        }
    }

    private static void assertValid(
            Class<?> validatorOwner,
            String validatorName,
            Class<?> stateType,
            Object state,
            String family
    ) {
        Object result = invoke(validatorOwner, validatorName, new Class<?>[]{stateType}, state);
        if (!(result instanceof Boolean valid) || !valid) {
            throw new AssertionError(family + " production state failed its server validator");
        }
    }

    private static void assertGenericSuppressed(
            Minecraft minecraft,
            long capability,
            String screenClass,
            String family
    ) {
        UUID targetId = UUID.randomUUID();
        invoke(ObserverNativeClient.class, "applySession",
                new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(true, targetId, "ProductionTarget",
                        ObserverNativePayloads.PROTOCOL_VERSION, capability));
        invoke(ObserverUiClient.class, "applyScreenRelay",
                new Class<?>[]{ObserverPayloads.ScreenRelay.class},
                new ObserverPayloads.ScreenRelay(targetId, true, screenClass, family));
        if (staticBoolean(ObserverNativeScreenClient.class, "remoteGenericOpen")) {
            throw new AssertionError(family + " negotiated semantic screen competed with generic metadata");
        }
        invoke(ObserverNativeClient.class, "applySession",
                new Class<?>[]{ObserverNativePayloads.NativeSession.class},
                new ObserverNativePayloads.NativeSession(false, new UUID(0L, 0L), "",
                        ObserverNativePayloads.PROTOCOL_VERSION, 0L));
        minecraft.setScreenAndShow(null);
    }

    private static boolean staticBoolean(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.getBoolean(null);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Failed to read " + owner.getSimpleName() + "." + name, error);
        }
    }

    private static Object invoke(Class<?> owner, String name, Class<?>[] types, Object... arguments) {
        try {
            Method method = owner.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(null, arguments);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Failed to invoke " + owner.getSimpleName() + "." + name, error);
        }
    }
}
