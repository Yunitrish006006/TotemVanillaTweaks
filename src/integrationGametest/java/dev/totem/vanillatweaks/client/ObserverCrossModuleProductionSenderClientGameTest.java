package dev.totem.vanillatweaks.client;

import dev.totem.automata.client.CopperGolemMenuScreen;
import dev.totem.automata.client.ObserverAutomataIntegrationFixture;
import dev.totem.locksmith.client.LocksmithManagementScreen;
import dev.totem.locksmith.menu.LocksmithManagementMenu;
import dev.totem.locksmith.menu.LocksmithManagementOpenData;
import dev.totem.locksmith.menu.LocksmithMenus;
import dev.totem.nexus.client.NexusDeathNodeAdminScreen;
import dev.totem.nexus.client.NexusSpaceUnitMapScreen;
import dev.totem.nexus.client.ObserverNexusIntegrationFixture;
import dev.totem.nexus.network.DeathNodeAdminPayload;
import dev.totem.nexus.network.SpaceUnitFriendsPayload;
import dev.totem.nexus.network.SpaceUnitMapPayload;
import dev.totem.nexus.network.SpaceUnitRegistrationPreviewPayload;
import dev.totem.nexus.space.TeleportInterfaceType;
import dev.totem.remnant.client.screen.BackpackScreen;
import dev.totem.remnant.inventory.BackpackMenu;
import dev.totem.vanillatweaks.network.ObserverAutomataCopperGolemPayloads;
import dev.totem.vanillatweaks.network.ObserverLocksmithManagementPayloads;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNexusDeathNodeAdminPayloads;
import dev.totem.vanillatweaks.network.ObserverNexusScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverRemnantBackpackPayloads;
import dev.totem.vanillatweaks.network.ObserverVillagersWoodcutterPayloads;
import dev.totem.vanillatweaks.observer.ObserverAutomataCopperGolemRelayManager;
import dev.totem.vanillatweaks.observer.ObserverLocksmithManagementRelayManager;
import dev.totem.vanillatweaks.observer.ObserverNativeSessionManager;
import dev.totem.vanillatweaks.observer.ObserverNexusDeathNodeAdminRelayManager;
import dev.totem.vanillatweaks.observer.ObserverNexusRelayManager;
import dev.totem.vanillatweaks.observer.ObserverVillagersWoodcutterRelayManager;
import dev.totem.villagers.client.WoodcutterScreen;
import dev.totem.villagers.woodcutter.WoodcutterMenu;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * Constructs the six optional modules' real production screens and calls the
 * exact framebuffer-free capture helper used by each target tick. Five screens
 * are displayed; Nexus 0.3.5 Death Admin is initialized off-screen because its
 * production renderer currently emits an out-of-bounds fixed widget scissor.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ObserverCrossModuleProductionSenderClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();
            // Match Nexus' own production visual gate; its admin panel requires
            // more vertical room than Fabric's 854x480 Client GameTest default.
            context.getInput().resizeWindow(1280, 720);
            verifyRemnantBackpack(context);
            verifyAutomataCopperGolem(context);
            verifyNexus(context);
            verifyVillagersWoodcutter(context);
            verifyNexusDeathNodeAdmin(context);
            verifyLocksmithManagement(context);
        }
    }

    private static void verifyRemnantBackpack(ClientGameTestContext context) {
        normalizeInput(context);
        BackpackScreen screen = context.computeOnClient(client -> {
            BackpackMenu menu = BackpackMenu.clientSide(41, client.player.getInventory(), 8, 4);
            BackpackScreen value = new BackpackScreen(menu, client.player.getInventory(), Component.literal("Backpack"));
            client.setScreenAndShow(value);
            return value;
        });
        context.waitForScreen(BackpackScreen.class);
        context.waitTicks(2);
        ObserverRemnantBackpackPayloads.BackpackState state = context.computeOnClient(client ->
                ObserverRemnantBackpackScreenClient.captureTargetState(screen, 1L));
        require(state.open() && state.rowCount() == 8 && state.upgradeSlotCount() == 4,
                "Remnant production state lost backpack geometry");
        assertOrdinalSlots(state.slots(), screen.getMenu().slots.size(), "Remnant backpack");
        assertSuppressed(screen, ObserverNativeScreenPayloads.CAPABILITY_REMNANT_BACKPACK);
        assertValid(ObserverNativeSessionManager.class, "validRemnantBackpack", state);
        assertValid(ObserverNativeSessionManager.class, "validRemnantBackpack",
                ObserverRemnantBackpackScreenClient.closedTargetState(2L));
        close(context);
    }

    private static void verifyAutomataCopperGolem(ClientGameTestContext context) {
        normalizeInput(context);
        UUID golemId = UUID.fromString("00000000-0000-0000-0000-000000000042");
        CopperGolemMenuScreen screen = context.computeOnClient(client -> {
            CopperGolemMenuScreen value = ObserverAutomataIntegrationFixture.create(client.player.getInventory(), golemId);
            client.setScreenAndShow(value);
            return value;
        });
        context.waitForScreen(CopperGolemMenuScreen.class);
        context.waitTicks(2);
        ObserverAutomataCopperGolemPayloads.CopperGolemState state = context.computeOnClient(client -> {
            ObserverAutomataIntegrationFixture.enterPrivateEditorValues(screen);
            return ObserverAutomataCopperGolemScreenClient.captureTargetState(screen, 3L);
        });
        require(state != null && state.open() && state.apiKeyConfigured(),
                "Automata production state or configured-key flag was absent");
        assertOrdinalSlots(state.slots(), screen.getMenu().slots.size(), "Automata Copper Golem");
        assertSuppressed(screen, ObserverNativeScreenPayloads.CAPABILITY_AUTOMATA_COPPER_GOLEM);
        assertAutomataPrivacy(state);
        assertValid(ObserverAutomataCopperGolemRelayManager.class, "valid", state);
        assertValid(ObserverAutomataCopperGolemRelayManager.class, "valid",
                ObserverAutomataCopperGolemScreenClient.closedTargetState(4L));
        close(context);
    }

    private static void verifyNexus(ClientGameTestContext context) {
        normalizeInput(context);
        UUID sourceId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        SpaceUnitMapPayload mapPayload = new SpaceUnitMapPayload(
                sourceId, "local", "Home", "minecraft:overworld", 10, 64, 10,
                TeleportInterfaceType.COMPASS, List.of());
        NexusSpaceUnitMapScreen map = context.computeOnClient(client -> {
            NexusSpaceUnitMapScreen value = new NexusSpaceUnitMapScreen(mapPayload);
            client.setScreenAndShow(value);
            return value;
        });
        awaitExactScreen(context, "dev.totem.nexus.client.NexusSpaceUnitMapScreen");
        ObserverNexusScreenPayloads.NexusState mapState = context.computeOnClient(client ->
                ObserverNexusScreenClient.captureTargetState(map, 5L));
        require(ObserverNexusScreenPayloads.VARIANT_MAP.equals(mapState.variant())
                        && sourceId.equals(mapState.sourceId()), "Nexus map variant was not captured");
        assertSuppressed(map, ObserverNativeScreenPayloads.CAPABILITY_NEXUS);
        assertValid(ObserverNexusRelayManager.class, "valid", mapState);

        UUID friendId = UUID.fromString("10000000-0000-0000-0000-000000000002");
        Screen friends = context.computeOnClient(client -> {
            Screen value = ObserverNexusIntegrationFixture.friends(new SpaceUnitFriendsPayload(List.of(
                    new SpaceUnitFriendsPayload.Entry(friendId, "Friend", true, "friend"))));
            client.setScreenAndShow(value);
            return value;
        });
        awaitExactScreen(context, "dev.totem.nexus.client.NexusSpaceUnitFriendsScreen");
        ObserverNexusScreenPayloads.NexusState friendsState = context.computeOnClient(client ->
                ObserverNexusScreenClient.captureTargetState(friends, 6L));
        require(ObserverNexusScreenPayloads.VARIANT_FRIENDS.equals(friendsState.variant())
                        && friendsState.friendEntries().size() == 1, "Nexus friends variant was not captured");
        assertSuppressed(friends, ObserverNativeScreenPayloads.CAPABILITY_NEXUS);
        assertValid(ObserverNexusRelayManager.class, "valid", friendsState);

        Screen registration = context.computeOnClient(client -> {
            Screen value = ObserverNexusIntegrationFixture.registration(new SpaceUnitRegistrationPreviewPayload(
                    "minecraft:overworld", 20, 70, -8, 3, 84, 92, 7, 20));
            client.setScreenAndShow(value);
            return value;
        });
        awaitExactScreen(context, "dev.totem.nexus.client.NexusSpaceUnitRegistrationPreviewScreen");
        ObserverNexusScreenPayloads.NexusState registrationState = context.computeOnClient(client ->
                ObserverNexusScreenClient.captureTargetState(registration, 7L));
        require(ObserverNexusScreenPayloads.VARIANT_REGISTRATION.equals(registrationState.variant())
                        && registrationState.registrationTier() == 3,
                "Nexus registration variant was not captured");
        assertSuppressed(registration, ObserverNativeScreenPayloads.CAPABILITY_NEXUS);
        assertValid(ObserverNexusRelayManager.class, "valid", registrationState);
        assertValid(ObserverNexusRelayManager.class, "valid", ObserverNexusScreenClient.closedTargetState(8L));
        close(context);
    }

    private static void verifyVillagersWoodcutter(ClientGameTestContext context) {
        normalizeInput(context);
        WoodcutterScreen screen = context.computeOnClient(client -> {
            WoodcutterMenu menu = new WoodcutterMenu(43, client.player.getInventory());
            WoodcutterScreen value = new WoodcutterScreen(menu, client.player.getInventory(), Component.literal("Woodcutter"));
            client.setScreenAndShow(value);
            return value;
        });
        context.waitForScreen(WoodcutterScreen.class);
        ObserverVillagersWoodcutterPayloads.WoodcutterState state = context.computeOnClient(client ->
                ObserverVillagersWoodcutterScreenClient.captureTargetState(screen, 9L));
        assertOrdinalSlots(state.slots(), screen.getMenu().slots.size(), "Villagers Woodcutter");
        assertSuppressed(screen, ObserverVillagersWoodcutterPayloads.CAPABILITY);
        assertValid(ObserverVillagersWoodcutterRelayManager.class, "valid", state);
        assertValid(ObserverVillagersWoodcutterRelayManager.class, "valid",
                ObserverVillagersWoodcutterScreenClient.closedTargetState(10L));
        close(context);
    }

    private static void verifyNexusDeathNodeAdmin(ClientGameTestContext context) {
        normalizeInput(context);
        UUID nodeId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID ownerId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        DeathNodeAdminPayload payload = new DeathNodeAdminPayload(List.of(new DeathNodeAdminPayload.Entry(
                nodeId, ownerId, "Owner", "Death Node", "active", "minecraft:overworld",
                4, 64, 8, 10L, 20L, List.of("healthy"))), false,
                0, 20, 1, 20L, true, null, null, "", 0L);
        NexusDeathNodeAdminScreen screen = context.computeOnClient(client -> {
            NexusDeathNodeAdminScreen value = new NexusDeathNodeAdminScreen(payload);
            // Do not call setScreenAndShow here: Nexus 0.3.5 renders its fixed
            // lower-right widget scissor outside the render area and crashes
            // before Observer can inspect the screen. Initializing the actual
            // production Screen still exercises the exact sender extractor and
            // server validator without claiming runtime display coverage.
            value.init(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
            return value;
        });
        ObserverNexusDeathNodeAdminPayloads.AdminState state = context.computeOnClient(client ->
                ObserverNexusDeathNodeAdminScreenClient.captureTargetState(screen, 11L));
        require(state.entries().size() == 1 && nodeId.equals(state.selectedNodeId()),
                "Nexus Death Admin production state was not captured");
        assertSuppressed(screen, ObserverNexusDeathNodeAdminPayloads.CAPABILITY);
        assertValid(ObserverNexusDeathNodeAdminRelayManager.class, "valid", state);
        assertValid(ObserverNexusDeathNodeAdminRelayManager.class, "valid",
                ObserverNexusDeathNodeAdminScreenClient.closedTargetState(12L));
        context.runOnClient(client -> screen.removed());
    }

    private static void verifyLocksmithManagement(ClientGameTestContext context) {
        normalizeInput(context);
        UUID lockId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID memberId = UUID.fromString("30000000-0000-0000-0000-000000000002");
        UUID keyId = UUID.fromString("30000000-0000-0000-0000-000000000003");
        UUID candidateId = UUID.fromString("30000000-0000-0000-0000-000000000004");
        LocksmithManagementOpenData data = new LocksmithManagementOpenData(
                lockId, 7L, "Owner", true, true, false, 1, 1, 2, 3,
                List.of(new LocksmithManagementOpenData.MemberView(memberId, "Member", 1)),
                List.of(new LocksmithManagementOpenData.KeyView(keyId, "Key 1")),
                List.of(new LocksmithManagementOpenData.PlayerView(candidateId, "Candidate")));
        LocksmithManagementScreen screen = context.computeOnClient(client -> {
            LocksmithManagementMenu menu = new LocksmithManagementMenu(
                    LocksmithMenus.MANAGEMENT, 44, client.player.getInventory(), data);
            LocksmithManagementScreen value = new LocksmithManagementScreen(
                    menu, client.player.getInventory(), Component.literal("Locksmith Management"));
            client.setScreenAndShow(value);
            return value;
        });
        context.waitForScreen(LocksmithManagementScreen.class);
        ObserverLocksmithManagementPayloads.ManagementState state = context.computeOnClient(client ->
                ObserverLocksmithManagementScreenClient.captureTargetState(screen, 13L));
        require(lockId.equals(state.lockId()) && state.members().size() == 1
                        && state.keys().size() == 1 && state.candidates().size() == 1,
                "Locksmith production snapshot was not captured");
        assertSuppressed(screen, ObserverLocksmithManagementPayloads.CAPABILITY);
        assertValid(ObserverLocksmithManagementRelayManager.class, "valid", state);
        assertValid(ObserverLocksmithManagementRelayManager.class, "valid",
                ObserverLocksmithManagementScreenClient.closedTargetState(14L));
        close(context);
    }

    private static void assertAutomataPrivacy(ObserverAutomataCopperGolemPayloads.CopperGolemState state) {
        require(ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED.equals(state.editorApiUrl()),
                "Automata API URL was not redacted");
        require(ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED.equals(state.editorModel()),
                "Automata model was not redacted");
        require(ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED.equals(state.editorGatheringPrompt()),
                "Automata gathering prompt was not redacted");
        require(ObserverAutomataCopperGolemPayloads.TOKEN_CONFIGURED.equals(state.editorBindingPrompt()),
                "Automata binding prompt was not redacted");
        require(ObserverAutomataCopperGolemPayloads.TOKEN_VALID.equals(state.cacheValueText()),
                "Automata private cache editor value was not reduced to its validity token");
        String wireState = state.toString();
        for (String secret : List.of(
                ObserverAutomataIntegrationFixture.PRIVATE_API_URL,
                ObserverAutomataIntegrationFixture.PRIVATE_API_KEY,
                ObserverAutomataIntegrationFixture.PRIVATE_MODEL,
                ObserverAutomataIntegrationFixture.PRIVATE_GATHERING_PROMPT,
                ObserverAutomataIntegrationFixture.PRIVATE_BINDING_PROMPT,
                ObserverAutomataIntegrationFixture.PRIVATE_CACHE_VALUE)) {
            require(!wireState.contains(secret), "Automata Observer payload leaked private value: " + secret);
        }
    }

    private static void assertOrdinalSlots(List<ObserverNativeScreenPayloads.SlotState> slots,
                                           int expectedSize, String family) {
        require(slots.size() == expectedSize, family + " slot count did not match the real menu");
        for (int index = 0; index < slots.size(); index++) {
            require(slots.get(index).index() == index,
                    family + " payload used a backing-container index at menu ordinal " + index);
        }
    }

    private static void assertSuppressed(Screen screen, long capability) {
        require(ObserverStructuredScreenPolicy.suppressGenericMetadata(screen.getClass().getName(), capability),
                "Generic metadata was not suppressed for " + screen.getClass().getName());
    }

    private static void assertValid(Class<?> owner, String methodName, Object state) {
        try {
            Method method = owner.getDeclaredMethod(methodName, state.getClass());
            method.setAccessible(true);
            if (!Boolean.TRUE.equals(method.invoke(null, state))) {
                throw new AssertionError(owner.getSimpleName() + "." + methodName + " rejected production state " + state);
            }
        } catch (InvocationTargetException error) {
            throw new AssertionError("Server validator threw for " + state.getClass().getSimpleName(), error.getCause());
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Unable to call server validator " + owner.getName() + "." + methodName, error);
        }
    }

    private static void awaitExactScreen(ClientGameTestContext context, String className) {
        context.waitFor(client -> client.gui.screen() != null
                && className.equals(client.gui.screen().getClass().getName()), 100);
    }

    private static void close(ClientGameTestContext context) {
        context.runOnClient(client -> client.setScreenAndShow(null));
        context.waitForScreen(null);
    }

    private static void normalizeInput(ClientGameTestContext context) {
        context.getInput().setCursorPos(10, 10);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
