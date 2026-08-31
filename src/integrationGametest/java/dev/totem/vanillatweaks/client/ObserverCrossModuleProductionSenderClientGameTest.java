package dev.totem.vanillatweaks.client;

import dev.totem.automata.client.AutomataObserverScreenProvider;
import dev.totem.automata.client.ObserverAutomataIntegrationFixture;
import dev.totem.core.api.v1.client.observer.*;
import dev.totem.locksmith.client.LocksmithManagementScreen;
import dev.totem.locksmith.client.LocksmithObserverScreenProvider;
import dev.totem.locksmith.menu.LocksmithManagementMenu;
import dev.totem.locksmith.menu.LocksmithManagementOpenData;
import dev.totem.locksmith.menu.LocksmithMenus;
import dev.totem.nexus.client.*;
import dev.totem.nexus.network.*;
import dev.totem.nexus.space.TeleportInterfaceType;
import dev.totem.remnant.client.screen.BackpackScreen;
import dev.totem.remnant.client.screen.RemnantBackpackObserverScreenProvider;
import dev.totem.remnant.inventory.BackpackMenu;
import dev.totem.vanillatweaks.network.ObserverNativePayloads;
import dev.totem.villagers.client.WoodcutterObserverScreenProvider;
import dev.totem.villagers.client.WoodcutterScreen;
import dev.totem.villagers.woodcutter.WoodcutterMenu;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Module-present proof for owner capture, production Screen creation/update and read-only input. */
public final class ObserverCrossModuleProductionSenderClientGameTest implements FabricClientGameTest {
    private static final int NEXUS_MAP_ID = 8801;

    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            context.getInput().resizeWindow(1280, 720);
            remnant(context);
            automata(context);
            nexus(context);
            villagers(context);
            deathAdmin(context);
            locksmith(context);
            malformedThenRecover(context);
            supportsFailureThenRecover(context);
        }
    }

    private static void remnant(ClientGameTestContext context) {
        var provider = new RemnantBackpackObserverScreenProvider();
        Screen source = context.computeOnClient(client -> new BackpackScreen(
                BackpackMenu.clientSide(1, client.player.getInventory(), 8, 4),
                client.player.getInventory(), Component.literal("Backpack")));
        exercise(context, provider, source, "dev.totem.remnant.client.screen.BackpackScreen",
                "owner-present-remnant-backpack-production-screen");
    }

    private static void automata(ClientGameTestContext context) {
        Screen source = context.computeOnClient(client -> ObserverAutomataIntegrationFixture.create(
                client.player.getInventory(), UUID.fromString("00000000-0000-0000-0000-000000000042")));
        exercise(context, new AutomataObserverScreenProvider(), source,
                "dev.totem.automata.client.CopperGolemMenuScreen",
                "owner-present-automata-copper-golem-production-screen");
    }

    private static void nexus(ClientGameTestContext context) {
        var provider = new NexusObserverScreenProvider();
        for (TeleportInterfaceType interfaceType : List.of(
                TeleportInterfaceType.COMPASS,
                TeleportInterfaceType.RECOVERY_COMPASS,
                TeleportInterfaceType.BOOK)) {
            verifyNexusManagementOnly(context, provider, interfaceType);
        }
        Screen map = context.computeOnClient(client -> new NexusSpaceUnitMapScreen(new SpaceUnitMapPayload(
                UUID.randomUUID(), "local", "Home", "minecraft:overworld", 1, 64, 1,
                TeleportInterfaceType.FILLED_MAP, NEXUS_MAP_ID, List.of())));
        exercise(context, provider, map, "dev.totem.nexus.client.NexusSpaceUnitMapScreen",
                "owner-present-nexus-map-production-screen");
        Screen friends = context.computeOnClient(client -> ObserverNexusIntegrationFixture.friends(
                new SpaceUnitFriendsPayload(List.of(
                        new SpaceUnitFriendsPayload.Entry(UUID.randomUUID(), "Friend", true, "friend")))));
        exercise(context, provider, friends, "dev.totem.nexus.client.NexusSpaceUnitFriendsScreen",
                "owner-present-nexus-friends-production-screen");
        Screen registration = context.computeOnClient(client -> ObserverNexusIntegrationFixture.registration(
                new SpaceUnitRegistrationPreviewPayload(
                        "minecraft:overworld", 2, 70, 2, 3, 84, 92, 7, 20)));
        exercise(context, provider, registration,
                "dev.totem.nexus.client.NexusSpaceUnitRegistrationPreviewScreen",
                "owner-present-nexus-registration-production-screen");
    }

    private static void verifyNexusManagementOnly(ClientGameTestContext context,
                                                  ObserverScreenProvider provider,
                                                  TeleportInterfaceType interfaceType) {
        Screen source = context.computeOnClient(client -> new NexusSpaceUnitMapScreen(new SpaceUnitMapPayload(
                UUID.randomUUID(), "local", "Management", "minecraft:overworld", 1, 64, 1,
                interfaceType, SpaceUnitMapPayload.NO_MAP_ID, List.of())));
        ObserverScreenSnapshot snapshot = captureOnClient(context, provider, source, 1);
        AtomicBoolean stopped = new AtomicBoolean();
        ObserverScreenHandle handle = context.computeOnClient(client -> provider.create(new ObserverScreenContext(
                UUID.randomUUID(), "Target", () -> stopped.set(true)), snapshot));
        context.runOnClient(client -> client.setScreenAndShow(handle.screen()));
        context.waitFor(client -> client.gui.screen() == handle.screen()
                && ObserverNexusIntegrationFixture.isManagementOnly(client.gui.screen(), interfaceType), 100);
        context.runOnClient(client -> {
            if (!handle.screen().keyPressed(new KeyEvent(256, 0, 0)) || !stopped.get()) {
                throw new AssertionError("Nexus " + interfaceType.id()
                        + " management-only Observer screen did not close read-only");
            }
            client.setScreenAndShow(null);
        });
        context.waitForScreen(null);
    }

    private static void villagers(ClientGameTestContext context) {
        Screen source = context.computeOnClient(client -> new WoodcutterScreen(
                new WoodcutterMenu(2, client.player.getInventory()), client.player.getInventory(),
                Component.literal("Woodcutter")));
        exercise(context, new WoodcutterObserverScreenProvider(), source,
                "dev.totem.villagers.client.WoodcutterScreen",
                "owner-present-villagers-woodcutter-production-screen");
    }

    private static void deathAdmin(ClientGameTestContext context) {
        UUID node = UUID.randomUUID(), owner = UUID.randomUUID();
        Screen source = context.computeOnClient(client -> new NexusDeathNodeAdminScreen(new DeathNodeAdminPayload(
                List.of(new DeathNodeAdminPayload.Entry(node, owner, "Owner", "Node", "active",
                        "minecraft:overworld", 1, 64, 1, 1, 2, List.of())), false)));
        exercise(context, new NexusDeathAdminObserverScreenProvider(), source,
                "dev.totem.nexus.client.NexusDeathNodeAdminScreen",
                "owner-present-nexus-death-node-admin-production-screen");
    }

    private static void locksmith(ClientGameTestContext context) {
        UUID lock = UUID.randomUUID();
        var data = new LocksmithManagementOpenData(lock, 7, "Owner", true, true, true, 1, 1, 2, 3,
                List.of(), List.of(), List.of());
        Screen source = context.computeOnClient(client -> new LocksmithManagementScreen(
                new LocksmithManagementMenu(LocksmithMenus.MANAGEMENT, 3, client.player.getInventory(), data),
                client.player.getInventory(), Component.literal("Locksmith")));
        exercise(context, new LocksmithObserverScreenProvider(), source,
                "dev.totem.locksmith.client.LocksmithManagementScreen",
                "owner-present-locksmith-management-production-screen");
    }

    private static void exercise(ClientGameTestContext context, ObserverScreenProvider provider, Screen source,
                                 String expectedClass, String screenshotName) {
        if (!(source instanceof ObserverReadOnlyScreen productionMarker)
                || productionMarker.totem$isObserverReadOnly()
                || ObserverOwnedScreenCoordinator.isReadOnlyObserverScreen(source)) {
            throw new AssertionError(expectedClass + " production mode was misclassified as Observer read-only");
        }
        ObserverScreenSnapshot initial = captureOnClient(context, provider, source, 1);
        ObserverScreenSnapshot update = context.computeOnClient(client -> semanticUpdate(client, provider, initial));
        if (java.util.Arrays.equals(initial.ownerPayload(), update.ownerPayload())
                && java.util.Arrays.equals(initial.data(), update.data())
                && initial.slots().equals(update.slots()) && initial.metadata().equals(update.metadata())) {
            throw new AssertionError(expectedClass + " second snapshot has no semantic state change");
        }
        AtomicBoolean stopped = new AtomicBoolean();
        AtomicReference<List<ItemStack>> localInventoryBefore = new AtomicReference<>();
        ObserverScreenHandle handle = context.computeOnClient(client -> {
            localInventoryBefore.set(copyInventory(client.player.getInventory()));
            return provider.create(new ObserverScreenContext(
                    UUID.randomUUID(), "Target", () -> stopped.set(true)), initial);
        });
        if (!ObserverOwnedScreenCoordinator.isReadOnlyObserverScreen(handle.screen())) {
            throw new AssertionError(expectedClass + " Observer mode did not enable read-only ownership");
        }
        context.runOnClient(client -> {
            client.setScreenAndShow(handle.screen());
        });
        context.waitFor(client -> client.gui.screen() != null
                && expectedClass.equals(client.gui.screen().getClass().getName())
                && client.gui.screen() instanceof ObserverReadOnlyScreen, 100);
        AtomicLong renderBaseline = new AtomicLong();
        AtomicReference<ItemStack> expectedCarried = new AtomicReference<>();
        context.runOnClient(client -> {
            renderBaseline.set(ObserverOwnedScreenCoordinator.renderGeneration());
            handle.applySnapshot(update);
            ItemStack carried = new ItemStack(Items.DIAMOND, 2);
            carried.set(DataComponents.CUSTOM_NAME, Component.literal("Remote carried"));
            expectedCarried.set(carried.copy());
            handle.applyCursor(new ObserverRemoteCursor(1, 88, 83, 176, 166, carried));
        });
        context.waitFor(client -> semanticApplied(client.gui.screen(), update)
                && ObserverOwnedScreenCoordinator.renderGeneration() > renderBaseline.get(), 100);
        AtomicLong packetBaseline = new AtomicLong();
        context.runOnClient(client -> {
            Screen screen = client.gui.screen();
            assertNativeScaleBounds(client, screen, expectedClass);
            packetBaseline.set(ObserverReadOnlyPacketFirewall.suppressedMutationPacketTotal());
            assertInventoryUnchanged(localInventoryBefore.get(), client.player.getInventory(), expectedClass);
            if (!screen.mouseClicked(new MouseButtonEvent(1, 1, new MouseButtonInfo(0, 0)), false)
                    || !screen.keyPressed(new KeyEvent(65, 0, 0))) {
                throw new AssertionError(expectedClass + " accepted local Observer input");
            }
            if (screen instanceof AbstractContainerScreen<?> container) {
                ItemStack carried = container.getMenu().getCarried();
                if (!ItemStack.matches(expectedCarried.get(), carried))
                    throw new AssertionError(expectedClass + " lost remote carried stack components");
            }
        });
        persist(context.takeScreenshot(screenshotName), screenshotName + ".png");
        context.runOnClient(client -> {
            Screen screen = client.gui.screen();
            if (!screen.keyPressed(new KeyEvent(256, 0, 0)) || !stopped.get())
                throw new AssertionError(expectedClass + " Escape did not stop observing");
            client.setScreenAndShow(null);
            if (ObserverReadOnlyPacketFirewall.suppressedMutationPacketTotal() != packetBaseline.get())
                throw new AssertionError(expectedClass + " attempted a mutation packet in read-only mode");
            assertInventoryUnchanged(localInventoryBefore.get(), client.player.getInventory(), expectedClass);
        });
        context.waitForScreen(null);
    }

    private static void assertNativeScaleBounds(net.minecraft.client.Minecraft client, Screen screen,
                                                String expectedClass) {
        if (client.getWindow().getWidth() != 1280 || client.getWindow().getHeight() != 720) {
            throw new AssertionError(expectedClass + " was not exercised at the required 1280x720 native window size");
        }
        for (var child : screen.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible) continue;
            if (widget.getX() < 0 || widget.getY() < 0
                    || widget.getX() + widget.getWidth() > screen.width
                    || widget.getY() + widget.getHeight() > screen.height) {
                throw new AssertionError(expectedClass + " placed a visible widget outside native GUI bounds: "
                        + widget.getX() + "," + widget.getY() + " "
                        + widget.getWidth() + "x" + widget.getHeight() + " in "
                        + screen.width + "x" + screen.height);
            }
        }
    }

    private static ObserverScreenSnapshot semanticUpdate(net.minecraft.client.Minecraft client,
                                                         ObserverScreenProvider provider,
                                                         ObserverScreenSnapshot initial) {
        Screen source = switch (initial.familyId()) {
            case "remnant_backpack" -> {
                int[] data = initial.data();
                BackpackMenu menu = BackpackMenu.clientSide(90, client.player.getInventory(), data[0], data[1]);
                menu.getSlot(0).set(new ItemStack(Items.GOLD_INGOT, 3));
                yield new BackpackScreen(menu, client.player.getInventory(), Component.literal("Backpack"));
            }
            case "automata_copper_golem" -> ObserverAutomataIntegrationFixture.create(
                    client.player.getInventory(), UUID.fromString(initial.metadata().get("golem_id")), 8);
            case "nexus" -> switch (initial.variant()) {
                case "map" -> new NexusSpaceUnitMapScreen(new SpaceUnitMapPayload(UUID.randomUUID(), "local",
                        "Remote Home", "minecraft:overworld", 9, 70, 9,
                        TeleportInterfaceType.FILLED_MAP, NEXUS_MAP_ID, List.of()));
                case "friends" -> ObserverNexusIntegrationFixture.friends(new SpaceUnitFriendsPayload(List.of(
                        new SpaceUnitFriendsPayload.Entry(UUID.randomUUID(), "Friend", true, "friend"),
                        new SpaceUnitFriendsPayload.Entry(UUID.randomUUID(), "Remote Friend", false, "shared"))));
                case "registration" -> ObserverNexusIntegrationFixture.registration(
                        new SpaceUnitRegistrationPreviewPayload("minecraft:overworld", 2, 70, 2,
                                4, 96, 99, 9, 18));
                default -> throw new AssertionError("unexpected Nexus integration variant " + initial.variant());
            };
            case "villagers_woodcutter" -> {
                WoodcutterMenu menu = new WoodcutterMenu(91, client.player.getInventory());
                menu.getSlot(0).set(new ItemStack(Items.OAK_LOG, 8));
                menu.setData(0, 1); menu.setData(1, 4); menu.setData(2, 3);
                yield new WoodcutterScreen(menu, client.player.getInventory(), Component.literal("Woodcutter"));
            }
            case "nexus_death_node_admin" -> {
                UUID owner = UUID.randomUUID();
                yield new NexusDeathNodeAdminScreen(new DeathNodeAdminPayload(List.of(
                        deathEntry("Remote Node A", owner), deathEntry("Remote Node B", owner)), false));
            }
            case "locksmith_management" -> {
                UUID lock = UUID.fromString(initial.metadata().get("lock_id"));
                var data = new LocksmithManagementOpenData(lock, 8, "Remote Owner", true, true, false,
                        2, 1, 4, 6, List.of(), List.of(), List.of());
                yield new LocksmithManagementScreen(new LocksmithManagementMenu(LocksmithMenus.MANAGEMENT, 92,
                        client.player.getInventory(), data), client.player.getInventory(), Component.literal("Locksmith"));
            }
            default -> throw new AssertionError("missing semantic update fixture for " + initial.familyId());
        };
        ObserverScreenSnapshot captured = provider.capture(source, 2).orElseThrow();
        if (captured.slots().isEmpty()) return captured;
        List<ItemStack> slots = new java.util.ArrayList<>(captured.slots());
        ItemStack remoteOnly = new ItemStack(Items.DIAMOND, 5);
        remoteOnly.set(DataComponents.CUSTOM_NAME, Component.literal("Remote-only inventory sentinel"));
        slots.set(slots.size() - 1, remoteOnly);
        return new ObserverScreenSnapshot(captured.familyId(), captured.variant(), captured.protocolVersion(),
                captured.sequence(), captured.title(), slots, captured.data(), captured.metadata(), captured.ownerPayload());
    }

    private static ObserverScreenSnapshot captureOnClient(ClientGameTestContext context,
                                                          ObserverScreenProvider provider,
                                                          Screen source,
                                                          long sequence) {
        return context.computeOnClient(client -> provider.capture(source, sequence).orElseThrow());
    }

    private static DeathNodeAdminPayload.Entry deathEntry(String name, UUID owner) {
        return new DeathNodeAdminPayload.Entry(UUID.randomUUID(), owner, "Owner", name, "active",
                "minecraft:overworld", 1, 64, 1, 1, 2, List.of());
    }

    private static boolean semanticApplied(Screen screen, ObserverScreenSnapshot update) {
        if (screen == null) return false;
        if (screen instanceof AbstractContainerScreen<?> container && !update.slots().isEmpty()) {
            ItemStack last = container.getMenu().getSlot(container.getMenu().slots.size() - 1).getItem();
            if (last.getCount() != 5 || last.get(DataComponents.CUSTOM_NAME) == null) return false;
        }
        return switch (update.familyId()) {
            case "automata_copper_golem" -> ObserverAutomataIntegrationFixture.revision(
                    (dev.totem.automata.client.CopperGolemMenuScreen) screen) == 8;
            case "nexus" -> switch (update.variant()) {
                case "map" -> "Remote Home".equals(ObserverNexusIntegrationFixture.mapName(screen))
                        && ObserverNexusIntegrationFixture.isFilledMap(screen, NEXUS_MAP_ID);
                case "friends" -> ObserverNexusIntegrationFixture.friendCount(screen) == 2;
                case "registration" -> ObserverNexusIntegrationFixture.registrationTier(screen) == 4;
                default -> false;
            };
            case "villagers_woodcutter" -> ((WoodcutterScreen) screen).getMenu().requiredInputCount() == 3;
            case "nexus_death_node_admin" -> ObserverNexusIntegrationFixture.deathEntryCount(screen) == 2;
            case "locksmith_management" -> ((LocksmithManagementScreen) screen).observerSnapshot().revision() == 8;
            default -> true;
        };
    }

    private static List<ItemStack> copyInventory(net.minecraft.world.entity.player.Inventory inventory) {
        List<ItemStack> copy = new java.util.ArrayList<>(inventory.getContainerSize());
        for (int index = 0; index < inventory.getContainerSize(); index++) copy.add(inventory.getItem(index).copy());
        return List.copyOf(copy);
    }

    private static void assertInventoryUnchanged(List<ItemStack> before,
                                                 net.minecraft.world.entity.player.Inventory inventory,
                                                 String expectedClass) {
        for (int index = 0; index < before.size(); index++) {
            if (!ItemStack.matches(before.get(index), inventory.getItem(index)))
                throw new AssertionError(expectedClass + " polluted Observer local inventory slot " + index);
        }
    }

    private static void malformedThenRecover(ClientGameTestContext context) {
        UUID target = UUID.randomUUID();
        applySession(true, target);
        var invalid = new ObserverScreenSnapshot("automata_copper_golem", "", 1, 100,
                Component.literal("Invalid"), List.of(), new int[0], java.util.Map.of(), new byte[]{1, 2, 3});
        context.runOnClient(client -> ObserverOwnedScreenCoordinator.open(invalid));
        context.waitFor(client -> client.gui.screen() != null
                && client.gui.screen().getClass().getSimpleName().equals("ObserverMetadataScreen"), 100);
        Screen source = context.computeOnClient(client -> ObserverAutomataIntegrationFixture.create(
                client.player.getInventory(), UUID.randomUUID()));
        ObserverScreenSnapshot captured = captureOnClient(
                context, new AutomataObserverScreenProvider(), source, 101);
        context.runOnClient(client -> ObserverOwnedScreenCoordinator.open(captured));
        context.waitFor(client -> client.gui.screen() != null && client.gui.screen().getClass().getName()
                .equals("dev.totem.automata.client.CopperGolemMenuScreen"), 100);
        context.runOnClient(client -> { ObserverOwnedScreenCoordinator.close("automata_copper_golem"); applySession(false, target); });
        context.waitForScreen(null);
    }

    private static void supportsFailureThenRecover(ClientGameTestContext context) {
        UUID target = UUID.randomUUID();
        applySession(true, target);
        Screen source = context.computeOnClient(client -> ObserverAutomataIntegrationFixture.create(
                client.player.getInventory(), UUID.randomUUID()));
        var production = new AutomataObserverScreenProvider();
        ObserverScreenSnapshot first = captureOnClient(context, production, source, 200);
        ObserverScreenSnapshot recovery = captureOnClient(context, production, source, 201);
        AtomicBoolean failOnce = new AtomicBoolean(true);
        ObserverScreenProvider throwing = new ObserverScreenProvider() {
            @Override public String familyId() { return production.familyId(); }
            @Override public int protocolVersion() { return production.protocolVersion(); }
            @Override public java.util.Set<String> variants() { return production.variants(); }
            @Override public boolean supports(ObserverScreenSnapshot snapshot) {
                if (failOnce.getAndSet(false)) throw new IllegalStateException("integration supports failure");
                return production.supports(snapshot);
            }
            @Override public Optional<ObserverScreenSnapshot> capture(Screen screen, long sequence) {
                return production.capture(screen, sequence);
            }
            @Override public ObserverScreenHandle create(ObserverScreenContext observer,
                                                         ObserverScreenSnapshot snapshot) {
                return production.create(observer, snapshot);
            }
        };
        context.runOnClient(client -> {
            ObserverOwnedScreenCoordinator.replaceProvidersForTest(Map.of(throwing.familyId(), throwing));
            if (ObserverOwnedScreenCoordinator.open(first))
                throw new AssertionError("supports() exception unexpectedly opened an Observer screen");
        });
        context.waitFor(client -> client.gui.screen() != null
                && client.gui.screen().getClass().getSimpleName().equals("ObserverMetadataScreen"), 100);
        context.runOnClient(client -> {
            if (!ObserverOwnedScreenCoordinator.open(recovery))
                throw new AssertionError("higher-sequence snapshot did not recover after supports() exception");
        });
        context.waitFor(client -> client.gui.screen() != null && client.gui.screen().getClass().getName()
                .equals("dev.totem.automata.client.CopperGolemMenuScreen"), 100);
        context.runOnClient(client -> {
            ObserverOwnedScreenCoordinator.close("automata_copper_golem");
            ObserverOwnedScreenCoordinator.reloadProvidersForTest();
            applySession(false, target);
        });
        context.waitForScreen(null);
    }

    private static void applySession(boolean active, UUID target) {
        try {
            Method method = ObserverNativeClient.class.getDeclaredMethod("applySession", ObserverNativePayloads.NativeSession.class);
            method.setAccessible(true);
            method.invoke(null, new ObserverNativePayloads.NativeSession(active, target, active ? "Target" : "",
                    ObserverNativePayloads.PROTOCOL_VERSION, -1L));
        } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }

    private static void persist(Path screenshot, String name) {
        String workspace = System.getenv("GITHUB_WORKSPACE");
        if (workspace == null || workspace.isBlank()) return;
        try {
            Path dir = Path.of(workspace).resolve("build/owner-present-integration-screenshots");
            Files.createDirectories(dir);
            Files.copy(screenshot, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) { throw new RuntimeException(error); }
    }
}
