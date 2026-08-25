package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import dev.totem.vanillatweaks.network.ObserverLocksmithManagementPayloads;
import dev.totem.vanillatweaks.network.ObserverPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Optional semantic adapter for TotemLocksmith's management screen. */
public final class ObserverLocksmithManagementScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static final String[] ACCESS_NAMES = {"private", "allowlist", "friends", "public"};
    private static final String[] AUTOMATION_NAMES = {"deny", "trusted", "all"};
    private static final String[] ROLE_NAMES = {"manager", "user", "blocked"};

    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static long lastRemoteSequence = -1L;
    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static UUID remoteLockId;
    private static long remoteRevision;
    private static String remoteOwnerName = "";
    private static boolean remoteOwnerActor;
    private static boolean remoteManagerActor;
    private static boolean remotePhysicalKeysRequired;
    private static int remoteAccessModeOrdinal;
    private static int remoteAutomationModeOrdinal;
    private static int remoteLogicalContainerCount;
    private static int remoteConnectorCount;
    private static String remoteTab = "access";
    private static int remoteMemberScroll;
    private static int remoteCandidateScroll;
    private static int remoteKeyScroll;
    private static List<ObserverLocksmithManagementPayloads.MemberState> remoteMembers = List.of();
    private static List<ObserverLocksmithManagementPayloads.KeyState> remoteKeys = List.of();
    private static List<ObserverLocksmithManagementPayloads.CandidateState> remoteCandidates = List.of();
    private static boolean suppressMirrorStop;
    private static long extractedFrames;

    private ObserverLocksmithManagementScreenClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ObserverLocksmithManagementPayloads.ManagementRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverLocksmithManagementScreenClient::tick);
    }

    public static boolean isTargetScreen(Screen screen) {
        return screen != null && ObserverLocksmithManagementPayloads.SCREEN_CLASS.equals(screen.getClass().getName());
    }

    private static void tick(Minecraft minecraft) {
        Screen screen = minecraft.gui.screen();
        if (ObserverNativeClient.targetStateEnabled() && minecraft.player != null && minecraft.level != null
                && ObserverNativeClient.targetSupportsScreen(ObserverLocksmithManagementPayloads.CAPABILITY)
                && isTargetScreen(screen)) {
            tickTarget(screen);
        } else {
            closeTargetIfNeeded();
        }

        if (!ObserverNativeClient.observerSessionActive()) {
            if (remoteOpen || minecraft.gui.screen() instanceof NativeLocksmithManagementMirrorScreen) {
                clearRemote();
                closeMirror();
            }
        } else if (remoteOpen) {
            ensureMirror();
        }
    }

    private static void tickTarget(Screen screen) {
        long now = System.nanoTime();
        if (targetOpen && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        ObserverLocksmithManagementPayloads.ManagementState state = capture(screen);
        if (state == null) return;
        targetOpen = true;
        lastSnapshotNanos = now;
        ClientPlayNetworking.send(state);
    }

    private static void closeTargetIfNeeded() {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (ObserverNativeClient.targetSupportsScreen(ObserverLocksmithManagementPayloads.CAPABILITY)) {
            ClientPlayNetworking.send(ObserverLocksmithManagementPayloads.closed(++nextTargetSequence));
        }
    }

    private static ObserverLocksmithManagementPayloads.ManagementState capture(Screen screen) {
        try {
            Object menu = fieldValue(screen, "menu");
            Object snapshot = invoke(menu, "snapshot");
            return new ObserverLocksmithManagementPayloads.ManagementState(
                    ObserverLocksmithManagementPayloads.PROTOCOL_VERSION, ++nextTargetSequence, true,
                    ObserverLocksmithManagementPayloads.FAMILY_ID, screen.getClass().getName(),
                    screen.getTitle() == null ? "" : screen.getTitle().getString(),
                    uuid(invoke(snapshot, "lockId")), longValue(invoke(snapshot, "revision")),
                    text(invoke(snapshot, "ownerName")), bool(invoke(snapshot, "ownerActor")),
                    bool(invoke(snapshot, "managerActor")), bool(invoke(snapshot, "physicalKeysRequired")),
                    integer(invoke(snapshot, "accessModeOrdinal")), integer(invoke(snapshot, "automationModeOrdinal")),
                    integer(invoke(snapshot, "logicalContainerCount")), integer(invoke(snapshot, "connectorCount")),
                    enumText(fieldValue(screen, "tab")), integer(fieldValue(screen, "memberScroll")),
                    integer(fieldValue(screen, "candidateScroll")), integer(fieldValue(screen, "keyScroll")),
                    members(invoke(snapshot, "members")), keys(invoke(snapshot, "keys")),
                    candidates(invoke(snapshot, "candidates")));
        } catch (RuntimeException error) {
            TotemVanillaTweaks.LOGGER.debug("Unable to read TotemLocksmith management state", error);
            return null;
        }
    }

    private static List<ObserverLocksmithManagementPayloads.MemberState> members(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<ObserverLocksmithManagementPayloads.MemberState> result = new ArrayList<>();
        for (Object row : list.stream().limit(ObserverLocksmithManagementPayloads.MAX_ROWS).toList()) {
            result.add(new ObserverLocksmithManagementPayloads.MemberState(
                    uuid(invoke(row, "playerId")), text(invoke(row, "name")), integer(invoke(row, "roleOrdinal"))));
        }
        return List.copyOf(result);
    }

    private static List<ObserverLocksmithManagementPayloads.KeyState> keys(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<ObserverLocksmithManagementPayloads.KeyState> result = new ArrayList<>();
        for (Object row : list.stream().limit(ObserverLocksmithManagementPayloads.MAX_ROWS).toList()) {
            result.add(new ObserverLocksmithManagementPayloads.KeyState(
                    uuid(invoke(row, "keyId")), text(invoke(row, "label"))));
        }
        return List.copyOf(result);
    }

    private static List<ObserverLocksmithManagementPayloads.CandidateState> candidates(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<ObserverLocksmithManagementPayloads.CandidateState> result = new ArrayList<>();
        for (Object row : list.stream().limit(ObserverLocksmithManagementPayloads.MAX_ROWS).toList()) {
            result.add(new ObserverLocksmithManagementPayloads.CandidateState(
                    uuid(invoke(row, "playerId")), text(invoke(row, "name"))));
        }
        return List.copyOf(result);
    }

    private static void acceptRelay(ObserverLocksmithManagementPayloads.ManagementRelay p) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverLocksmithManagementPayloads.CAPABILITY)
                || targetId == null || !targetId.equals(p.targetId())
                || p.protocolVersion() != ObserverLocksmithManagementPayloads.PROTOCOL_VERSION
                || !ObserverLocksmithManagementPayloads.FAMILY_ID.equals(p.familyId())
                || p.sequence() <= lastRemoteSequence) return;
        lastRemoteSequence = p.sequence();
        if (!p.open()) { clearRemote(); closeMirror(); return; }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteTitle = p.title();
        remoteLockId = p.lockId();
        remoteRevision = p.revision();
        remoteOwnerName = p.ownerName();
        remoteOwnerActor = p.ownerActor();
        remoteManagerActor = p.managerActor();
        remotePhysicalKeysRequired = p.physicalKeysRequired();
        remoteAccessModeOrdinal = p.accessModeOrdinal();
        remoteAutomationModeOrdinal = p.automationModeOrdinal();
        remoteLogicalContainerCount = p.logicalContainerCount();
        remoteConnectorCount = p.connectorCount();
        remoteTab = p.tab();
        remoteMemberScroll = p.memberScroll();
        remoteCandidateScroll = p.candidateScroll();
        remoteKeyScroll = p.keyScroll();
        remoteMembers = List.copyOf(p.members());
        remoteKeys = List.copyOf(p.keys());
        remoteCandidates = List.copyOf(p.candidates());
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof NativeLocksmithManagementMirrorScreen)) {
            suppressMirrorStop = true;
            try { minecraft.setScreenAndShow(new NativeLocksmithManagementMirrorScreen()); }
            finally { suppressMirrorStop = false; }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeLocksmithManagementMirrorScreen)) return;
        suppressMirrorStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressMirrorStop = false; }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteTitle = "";
        remoteLockId = null;
        remoteRevision = 0L;
        remoteOwnerName = "";
        remoteOwnerActor = false;
        remoteManagerActor = false;
        remotePhysicalKeysRequired = false;
        remoteAccessModeOrdinal = 0;
        remoteAutomationModeOrdinal = 0;
        remoteLogicalContainerCount = 0;
        remoteConnectorCount = 0;
        remoteTab = "access";
        remoteMemberScroll = 0;
        remoteCandidateScroll = 0;
        remoteKeyScroll = 0;
        remoteMembers = List.of();
        remoteKeys = List.of();
        remoteCandidates = List.of();
    }

    private static final class NativeLocksmithManagementMirrorScreen extends Screen {
        private NativeLocksmithManagementMirrorScreen() { super(Component.literal("Observer Locksmith Management")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) ClientPlayNetworking.send(new ObserverPayloads.Stop());
            super.onClose();
        }

        @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, width, height, 0xA0000000);
            int pw = Math.min(560, Math.max(330, width - 24));
            int ph = Math.min(380, Math.max(250, height - 24));
            int left = (width - pw) / 2;
            int top = (height - ph) / 2;
            g.fill(left, top, left + pw, top + ph, 0xFFE7DDC8);
            g.fill(left + 2, top + 2, left + pw - 2, top + 30, 0xFFB59669);
            g.outline(left, top, pw, ph, 0xFF4A4035);
            g.text(font, remoteTitle.isBlank() ? "Locksmith Management" : remoteTitle, left + 12, top + 8, 0xFF382B20, false);
            g.text(font, "Owner: " + remoteOwnerName, left + 12, top + 19, 0xFF675342, false);
            g.text(font, "Network: " + remoteLogicalContainerCount + " containers / " + remoteConnectorCount + " connectors",
                    left + pw - 250, top + 14, 0xFF675342, false);

            int tabY = top + 36;
            String[] tabs = {"access", "members", "keys"};
            for (int i = 0; i < tabs.length; i++) {
                int x = left + 12 + i * 94;
                boolean selected = tabs[i].equals(remoteTab);
                g.fill(x, tabY, x + 86, tabY + 20, selected ? 0xFFB59669 : 0xFFF4EEE1);
                g.outline(x, tabY, 86, 20, 0xFF806F61);
                g.text(font, tabs[i].toUpperCase(Locale.ROOT), x + 8, tabY + 6, 0xFF40352C, false);
            }

            switch (remoteTab) {
                case "members" -> drawMembers(g, left, top, pw, ph);
                case "keys" -> drawKeys(g, left, top, pw, ph);
                default -> drawAccess(g, left, top, pw, ph);
            }
            extractedFrames++;
        }

        private void drawAccess(GuiGraphicsExtractor g, int left, int top, int pw, int ph) {
            int y = top + 70;
            g.fill(left + 12, y, left + pw - 12, y + 32, remotePhysicalKeysRequired ? 0xFFFFE7C7 : 0xFFDDF3E4);
            g.text(font, remotePhysicalKeysRequired ? "Physical keys required" : "Convenient access",
                    left + 20, y + 7, remotePhysicalKeysRequired ? 0xFF81502A : 0xFF2F7145, false);
            g.text(font, remoteOwnerActor ? "Viewer: owner" : remoteManagerActor ? "Viewer: manager" : "Viewer: member",
                    left + pw - 150, y + 7, 0xFF66594D, false);

            y += 48;
            g.text(font, "Access mode", left + 14, y, 0xFF493A2E, false);
            y += 16;
            for (int i = 0; i < ACCESS_NAMES.length; i++) {
                int x = left + 14 + (i % 2) * 150;
                int by = y + (i / 2) * 28;
                boolean selected = i == remoteAccessModeOrdinal;
                g.fill(x, by, x + 136, by + 22, selected ? 0xFFB59669 : 0xFFF4EEE1);
                g.outline(x, by, 136, 22, 0xFF806F61);
                g.text(font, ACCESS_NAMES[i], x + 8, by + 7, 0xFF40352C, false);
            }

            y += 72;
            g.text(font, "Automation mode", left + 14, y, 0xFF493A2E, false);
            y += 16;
            for (int i = 0; i < AUTOMATION_NAMES.length; i++) {
                int x = left + 14 + i * 104;
                boolean selected = i == remoteAutomationModeOrdinal;
                g.fill(x, y, x + 94, y + 22, selected ? 0xFFB59669 : 0xFFF4EEE1);
                g.outline(x, y, 94, 22, 0xFF806F61);
                g.text(font, AUTOMATION_NAMES[i], x + 8, y + 7, 0xFF40352C, false);
            }
            g.text(font, "Revision: " + remoteRevision + "  Lock: " + shortId(remoteLockId), left + 14, top + ph - 20, 0xFF806F61, false);
        }

        private void drawMembers(GuiGraphicsExtractor g, int left, int top, int pw, int ph) {
            int y = top + 70;
            int mid = left + pw / 2;
            g.text(font, "Members", left + 14, y, 0xFF493A2E, false);
            g.text(font, "Candidates", mid + 8, y, 0xFF493A2E, false);
            g.fill(mid, y, mid + 1, top + ph - 16, 0xFFB8AA92);
            int rows = Math.max(1, Math.min(7, (ph - 100) / 26));
            for (int row = 0; row < rows; row++) {
                int index = remoteMemberScroll + row;
                if (index >= remoteMembers.size()) break;
                var member = remoteMembers.get(index);
                int by = y + 18 + row * 26;
                g.fill(left + 12, by, mid - 8, by + 22, 0xFFF4EEE1);
                g.text(font, member.name(), left + 18, by + 5, 0xFF40352C, false);
                g.text(font, roleName(member.roleOrdinal()), mid - 80, by + 5, 0xFF806F61, false);
            }
            for (int row = 0; row < rows; row++) {
                int index = remoteCandidateScroll + row;
                if (index >= remoteCandidates.size()) break;
                var candidate = remoteCandidates.get(index);
                int by = y + 18 + row * 26;
                g.fill(mid + 8, by, left + pw - 12, by + 22, 0xFFF4EEE1);
                g.text(font, candidate.name(), mid + 14, by + 5, 0xFF40352C, false);
                g.text(font, "+", left + pw - 28, by + 5, 0xFF2F7145, false);
            }
        }

        private void drawKeys(GuiGraphicsExtractor g, int left, int top, int pw, int ph) {
            int y = top + 70;
            g.text(font, "Active keys: " + remoteKeys.size(), left + 14, y, 0xFF493A2E, false);
            int rows = Math.max(1, Math.min(8, (ph - 100) / 26));
            for (int row = 0; row < rows; row++) {
                int index = remoteKeyScroll + row;
                if (index >= remoteKeys.size()) break;
                var key = remoteKeys.get(index);
                int by = y + 18 + row * 26;
                g.fill(left + 12, by, left + pw - 12, by + 22, 0xFFF4EEE1);
                g.text(font, key.label(), left + 18, by + 5, 0xFF40352C, false);
                g.text(font, shortId(key.keyId()), left + pw - 105, by + 5, 0xFF806F61, false);
                g.text(font, "revoke", left + pw - 60, by + 5, 0xFF9E3B32, false);
            }
            if (remoteOwnerActor) g.text(font, "Rotate keys available", left + 14, top + ph - 20, 0xFF9E3B32, false);
        }

        private String shortId(UUID id) {
            return id == null ? "--------" : id.toString().substring(0, 8);
        }

        private String roleName(int ordinal) {
            return ordinal >= 0 && ordinal < ROLE_NAMES.length ? ROLE_NAMES[ordinal] : "unknown";
        }
    }

    private static Object fieldValue(Object owner, String name) {
        if (owner == null) throw new IllegalStateException("Null owner for field " + name);
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException error) {
                throw new RuntimeException(error);
            }
        }
        throw new IllegalStateException("Missing field " + owner.getClass().getName() + "." + name);
    }

    private static Object invoke(Object owner, String methodName) {
        if (owner == null) return null;
        try {
            Method method = owner.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(owner);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String enumText(Object value) { return value == null ? "access" : String.valueOf(value).toLowerCase(Locale.ROOT); }
    private static boolean bool(Object value) { return value instanceof Boolean b && b; }
    private static int integer(Object value) { return value instanceof Number n ? n.intValue() : 0; }
    private static long longValue(Object value) { return value instanceof Number n ? n.longValue() : 0L; }
    private static UUID uuid(Object value) { return value instanceof UUID id ? id : null; }
}
