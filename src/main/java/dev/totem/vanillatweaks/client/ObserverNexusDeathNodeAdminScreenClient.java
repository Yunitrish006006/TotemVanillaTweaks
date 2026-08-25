package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNexusDeathNodeAdminPayloads;
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

/** Optional semantic adapter for TotemNexus death-node administration. */
public final class ObserverNexusDeathNodeAdminScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

    private static long lastRemoteSequence = -1L;
    private static boolean remoteOpen;
    private static String remoteTitle = "";
    private static String remoteOwnerQuery = "";
    private static String remoteDimensionQuery = "";
    private static String remoteStatusFilter = "all";
    private static String remoteTimeFilter = "all";
    private static int remoteScrollIndex;
    private static int remotePage;
    private static int remotePageSize = 1;
    private static int remoteTotalEntries;
    private static boolean remoteTruncated;
    private static boolean remoteAdministratorView;
    private static UUID remoteSelectedNodeId;
    private static boolean remoteConfirmationActive;
    private static String remoteConfirmationAction = "";
    private static List<ObserverNexusDeathNodeAdminPayloads.EntryState> remoteEntries = List.of();
    private static boolean suppressMirrorStop;
    private static long extractedFrames;

    private ObserverNexusDeathNodeAdminScreenClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ObserverNexusDeathNodeAdminPayloads.AdminRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverNexusDeathNodeAdminScreenClient::tick);
    }

    public static boolean isTargetScreen(Screen screen) {
        return screen != null && ObserverNexusDeathNodeAdminPayloads.SCREEN_CLASS.equals(screen.getClass().getName());
    }

    public static boolean hasRemoteScreen() {
        return remoteOpen && ObserverNativeClient.observerSessionActive();
    }

    private static void tick(Minecraft minecraft) {
        Screen screen = minecraft.gui.screen();
        if (ObserverNativeClient.targetStateEnabled() && minecraft.player != null && minecraft.level != null
                && ObserverNativeClient.targetSupportsScreen(ObserverNexusDeathNodeAdminPayloads.CAPABILITY)
                && isTargetScreen(screen)) {
            tickTarget(screen);
        } else {
            closeTargetIfNeeded();
        }

        if (!ObserverNativeClient.observerSessionActive()) {
            if (remoteOpen || minecraft.gui.screen() instanceof NativeDeathNodeAdminMirrorScreen) {
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
        ObserverNexusDeathNodeAdminPayloads.AdminState state = capture(screen);
        if (state == null) return;
        targetOpen = true;
        lastSnapshotNanos = now;
        ClientPlayNetworking.send(state);
    }

    private static void closeTargetIfNeeded() {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (ObserverNativeClient.targetSupportsScreen(ObserverNexusDeathNodeAdminPayloads.CAPABILITY)) {
            ClientPlayNetworking.send(ObserverNexusDeathNodeAdminPayloads.closed(++nextTargetSequence));
        }
    }

    private static ObserverNexusDeathNodeAdminPayloads.AdminState capture(Screen screen) {
        try {
            Object payload = fieldValue(screen, "payload");
            if (payload == null) return null;
            UUID confirmationNode = uuid(invoke(payload, "confirmationNodeId"));
            long confirmationExpires = longValue(invoke(payload, "confirmationExpiresAtMillis"));
            return new ObserverNexusDeathNodeAdminPayloads.AdminState(
                    ObserverNexusDeathNodeAdminPayloads.PROTOCOL_VERSION, ++nextTargetSequence, true,
                    ObserverNexusDeathNodeAdminPayloads.FAMILY_ID, screen.getClass().getName(),
                    screen.getTitle() == null ? "" : screen.getTitle().getString(),
                    text(fieldValueIfPresent(screen, "ownerQuery")), text(fieldValueIfPresent(screen, "dimensionId")),
                    enumText(fieldValueIfPresent(screen, "statusFilter")), enumText(fieldValueIfPresent(screen, "timeFilter")),
                    integer(fieldValueIfPresent(screen, "scrollIndex")), integer(invoke(payload, "page")),
                    integer(invoke(payload, "pageSize")), integer(invoke(payload, "totalEntries")),
                    bool(invoke(payload, "truncated")), bool(invoke(payload, "administratorView")),
                    uuid(fieldValueIfPresent(screen, "selectedNodeId")),
                    confirmationNode != null && confirmationExpires > System.currentTimeMillis(),
                    text(invoke(payload, "confirmationAction")), entries(invoke(payload, "entries")));
        } catch (RuntimeException error) {
            TotemVanillaTweaks.LOGGER.debug("Unable to read TotemNexus death-node admin state", error);
            return null;
        }
    }

    private static List<ObserverNexusDeathNodeAdminPayloads.EntryState> entries(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<ObserverNexusDeathNodeAdminPayloads.EntryState> result = new ArrayList<>();
        for (Object entry : list.stream().limit(ObserverNexusDeathNodeAdminPayloads.MAX_ENTRIES).toList()) {
            result.add(new ObserverNexusDeathNodeAdminPayloads.EntryState(
                    uuid(invoke(entry, "id")), uuid(invoke(entry, "ownerId")), text(invoke(entry, "ownerName")),
                    text(invoke(entry, "name")), text(invoke(entry, "status")), text(invoke(entry, "dimension")),
                    integer(invoke(entry, "x")), integer(invoke(entry, "y")), integer(invoke(entry, "z")),
                    longValue(invoke(entry, "createdGameTime")), longValue(invoke(entry, "updatedGameTime")),
                    stringList(invoke(entry, "diagnosticFlags"))));
        }
        return List.copyOf(result);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().limit(ObserverNexusDeathNodeAdminPayloads.MAX_DIAGNOSTICS).map(ObserverNexusDeathNodeAdminScreenClient::text).toList();
    }

    private static void acceptRelay(ObserverNexusDeathNodeAdminPayloads.AdminRelay p) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverNexusDeathNodeAdminPayloads.CAPABILITY)
                || targetId == null || !targetId.equals(p.targetId())
                || p.protocolVersion() != ObserverNexusDeathNodeAdminPayloads.PROTOCOL_VERSION
                || !ObserverNexusDeathNodeAdminPayloads.FAMILY_ID.equals(p.familyId())
                || p.sequence() <= lastRemoteSequence) return;
        lastRemoteSequence = p.sequence();
        if (!p.open()) { clearRemote(); closeMirror(); return; }
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        remoteOpen = true;
        remoteTitle = p.title();
        remoteOwnerQuery = p.ownerQuery();
        remoteDimensionQuery = p.dimensionQuery();
        remoteStatusFilter = p.statusFilter();
        remoteTimeFilter = p.timeFilter();
        remoteScrollIndex = p.scrollIndex();
        remotePage = p.page();
        remotePageSize = p.pageSize();
        remoteTotalEntries = p.totalEntries();
        remoteTruncated = p.truncated();
        remoteAdministratorView = p.administratorView();
        remoteSelectedNodeId = p.selectedNodeId();
        remoteConfirmationActive = p.confirmationActive();
        remoteConfirmationAction = p.confirmationAction();
        remoteEntries = List.copyOf(p.entries());
        ensureMirror();
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof NativeDeathNodeAdminMirrorScreen)) {
            suppressMirrorStop = true;
            try { minecraft.setScreenAndShow(new NativeDeathNodeAdminMirrorScreen()); }
            finally { suppressMirrorStop = false; }
        }
    }

    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof NativeDeathNodeAdminMirrorScreen)) return;
        suppressMirrorStop = true;
        try { minecraft.setScreenAndShow(null); }
        finally { suppressMirrorStop = false; }
    }

    private static void clearRemote() {
        remoteOpen = false;
        remoteTitle = "";
        remoteOwnerQuery = "";
        remoteDimensionQuery = "";
        remoteStatusFilter = "all";
        remoteTimeFilter = "all";
        remoteScrollIndex = 0;
        remotePage = 0;
        remotePageSize = 1;
        remoteTotalEntries = 0;
        remoteTruncated = false;
        remoteAdministratorView = false;
        remoteSelectedNodeId = null;
        remoteConfirmationActive = false;
        remoteConfirmationAction = "";
        remoteEntries = List.of();
    }

    private static ObserverNexusDeathNodeAdminPayloads.EntryState selectedEntry() {
        if (remoteSelectedNodeId == null) return null;
        for (var entry : remoteEntries) if (remoteSelectedNodeId.equals(entry.id())) return entry;
        return null;
    }

    private static final class NativeDeathNodeAdminMirrorScreen extends Screen {
        private NativeDeathNodeAdminMirrorScreen() { super(Component.literal("Observer Death Node Admin")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) ClientPlayNetworking.send(new ObserverPayloads.Stop());
            super.onClose();
        }
        @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, width, height, 0xA0000000);
            int pw = Math.min(620, Math.max(360, width - 24));
            int ph = Math.min(380, Math.max(260, height - 24));
            int left = (width - pw) / 2;
            int top = (height - ph) / 2;
            g.fill(left, top, left + pw, top + ph, 0xF016191D);
            g.outline(left, top, pw, ph, 0xFF657383);
            g.text(font, remoteTitle.isBlank() ? "Death Node Administration" : remoteTitle, left + 12, top + 10, 0xFFFFFFFF, false);
            g.text(font, "Page " + (remotePage + 1) + "  Total " + remoteTotalEntries + (remoteTruncated ? " +" : ""),
                    left + pw - 180, top + 10, 0xFFB8C0C8, false);
            g.text(font, "Owner: " + (remoteOwnerQuery.isBlank() ? "*" : remoteOwnerQuery), left + 12, top + 30, 0xFFD2D8E0, false);
            g.text(font, "Dimension: " + (remoteDimensionQuery.isBlank() ? "*" : remoteDimensionQuery), left + 180, top + 30, 0xFFD2D8E0, false);
            g.text(font, "Status: " + remoteStatusFilter + "  Time: " + remoteTimeFilter, left + 12, top + 44, 0xFFB8C0C8, false);
            g.text(font, remoteAdministratorView ? "Administrator view" : "Owner view", left + pw - 150, top + 44, 0xFFFFC857, false);

            int listTop = top + 64;
            int detailHeight = 72;
            int listBottom = top + ph - detailHeight - 40;
            g.fill(left + 10, listTop, left + pw - 10, listBottom, 0x80101010);
            g.outline(left + 10, listTop, pw - 20, listBottom - listTop, 0xFF3F4A56);
            int visibleRows = Math.max(1, (listBottom - listTop - 8) / 28);
            int start = Math.min(remoteScrollIndex, Math.max(0, remoteEntries.size() - visibleRows));
            int rowY = listTop + 4;
            for (int i = start; i < remoteEntries.size() && i < start + visibleRows; i++) {
                var entry = remoteEntries.get(i);
                boolean selected = entry.id().equals(remoteSelectedNodeId);
                g.fill(left + 14, rowY, left + pw - 14, rowY + 24, selected ? 0xFF2D3F54 : 0x9020252B);
                g.fill(left + 20, rowY + 7, left + 27, rowY + 14, "active".equals(entry.status()) ? 0xFF6AD98F : 0xFF9CA3AF);
                g.text(font, entry.name(), left + 32, rowY + 4, 0xFFFFFFFF, false);
                if (remoteAdministratorView) g.text(font, entry.ownerName(), left + pw - 220, rowY + 4, 0xFFD2D8E0, false);
                g.text(font, entry.status(), left + pw - 105, rowY + 4, 0xFFB8C0C8, false);
                g.text(font, entry.dimension() + "  " + entry.x() + ", " + entry.y() + ", " + entry.z(),
                        left + 32, rowY + 14, 0xFF9CA3AF, false);
                rowY += 28;
            }

            int detailTop = listBottom + 8;
            g.fill(left + 10, detailTop, left + pw - 10, top + ph - 32, 0x8020252B);
            var selected = selectedEntry();
            if (selected == null) {
                g.text(font, "No node selected", left + 18, detailTop + 8, 0xFFB8C0C8, false);
            } else {
                g.text(font, selected.name() + " — " + selected.status(), left + 18, detailTop + 6, 0xFFFFFFFF, false);
                g.text(font, "Owner: " + selected.ownerName(), left + 18, detailTop + 20, 0xFFD2D8E0, false);
                g.text(font, "Updated: " + selected.updatedGameTime(), left + 18, detailTop + 34, 0xFFB8C0C8, false);
                if (!selected.diagnosticFlags().isEmpty()) g.text(font, "Diagnostics: " + String.join(", ", selected.diagnosticFlags()),
                        left + pw / 2, detailTop + 34, 0xFFFFC857, false);
            }
            if (remoteConfirmationActive) g.text(font, "Confirmation pending: " + remoteConfirmationAction,
                    left + 12, top + ph - 20, 0xFFFF8080, false);
            extractedFrames++;
        }
    }

    private static Object fieldValue(Object owner, String name) {
        Object value = fieldValueIfPresent(owner, name);
        if (value == null) throw new IllegalStateException("Missing field " + owner.getClass().getName() + "." + name);
        return value;
    }

    private static Object fieldValueIfPresent(Object owner, String name) {
        if (owner == null) return null;
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
        return null;
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
    private static String enumText(Object value) { return value == null ? "all" : String.valueOf(value).toLowerCase(Locale.ROOT); }
    private static boolean bool(Object value) { return value instanceof Boolean b && b; }
    private static int integer(Object value) { return value instanceof Number n ? n.intValue() : 0; }
    private static long longValue(Object value) { return value instanceof Number n ? n.longValue() : 0L; }
    private static UUID uuid(Object value) { return value instanceof UUID id ? id : null; }
}
