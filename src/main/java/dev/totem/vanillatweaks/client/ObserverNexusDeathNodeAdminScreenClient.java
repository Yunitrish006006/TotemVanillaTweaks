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
import java.util.function.ToIntFunction;

/** Optional semantic adapter for TotemNexus death-node administration. */
public final class ObserverNexusDeathNodeAdminScreenClient {
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    static final int SAFE_SCREEN_MARGIN = 12;
    static final int MAX_PANEL_WIDTH = 620;
    static final int MAX_PANEL_HEIGHT = 380;
    static final int CONTENT_MARGIN = 12;
    static final int COLUMN_GAP = 8;
    static final int TEXT_HEIGHT = 9;
    static final int TITLE_Y = 8;
    static final int OWNER_Y = 22;
    static final int DIMENSION_Y = 34;
    static final int FILTER_Y = 46;
    static final int LIST_Y = 60;
    static final int LIST_PADDING = 4;
    static final int ROW_HEIGHT = 24;
    static final int ROW_PITCH = 28;
    static final int MAX_VISIBLE_ROWS = 8;
    static final int DETAIL_HEIGHT = 60;
    static final int DETAIL_GAP = 8;
    static final int DETAIL_TEXT_X = 8;
    static final int CONFIRM_Y_FROM_BOTTOM = 18;
    static final int CONFIRM_DETAIL_GAP = 6;
    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;

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
        long sequence = nextTargetSequence + 1L;
        ObserverNexusDeathNodeAdminPayloads.AdminState state = captureTargetState(screen, sequence);
        if (state == null) return;
        nextTargetSequence = sequence;
        targetOpen = true;
        lastSnapshotNanos = now;
        ClientPlayNetworking.send(state);
    }

    private static void closeTargetIfNeeded() {
        if (!targetOpen) return;
        targetOpen = false;
        lastSnapshotNanos = 0L;
        if (ObserverNativeClient.targetSupportsScreen(ObserverNexusDeathNodeAdminPayloads.CAPABILITY)) {
            ClientPlayNetworking.send(closedTargetState(++nextTargetSequence));
        }
    }

    /** Exact production extractor shared by the target tick and cross-module runtime gate. */
    public static ObserverNexusDeathNodeAdminPayloads.AdminState captureTargetState(Screen screen, long sequence) {
        if (!isTargetScreen(screen)) throw new IllegalArgumentException("Expected TotemNexus DeathNodeAdminScreen");
        try {
            Object payload = fieldValue(screen, "payload");
            if (payload == null) return null;
            UUID confirmationNode = uuid(invoke(payload, "confirmationNodeId"));
            long confirmationExpires = longValue(invoke(payload, "confirmationExpiresAtMillis"));
            return new ObserverNexusDeathNodeAdminPayloads.AdminState(
                    ObserverNexusDeathNodeAdminPayloads.PROTOCOL_VERSION, sequence, true,
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

    public static ObserverNexusDeathNodeAdminPayloads.AdminState closedTargetState(long sequence) {
        return ObserverNexusDeathNodeAdminPayloads.closed(sequence);
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
                || !ObserverRemoteSequenceTracker.accept(
                        ObserverNexusDeathNodeAdminPayloads.FAMILY_ID, p.targetId(), p.sequence())) return;
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

    /** Resolves a responsive administration panel while preserving complete list rows and the detail footer. */
    static DeathNodeAdminLayout deathNodeAdminLayout(
            int screenWidth,
            int screenHeight,
            int entryCount,
            int requestedScroll
    ) {
        int panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(1, screenWidth - SAFE_SCREEN_MARGIN * 2));
        int panelHeight = Math.min(MAX_PANEL_HEIGHT, Math.max(1, screenHeight - SAFE_SCREEN_MARGIN * 2));
        int left = (screenWidth - panelWidth) / 2;
        int top = (screenHeight - panelHeight) / 2;
        int contentWidth = Math.max(0, panelWidth - CONTENT_MARGIN * 2);

        int pageWidth = Math.min(140, Math.max(0, contentWidth / 3));
        int titleWidth = Math.max(0, contentWidth - pageWidth - COLUMN_GAP);
        int threeColumnWidth = Math.max(0, (contentWidth - COLUMN_GAP * 2) / 3);
        int thirdColumnWidth = Math.max(0, contentWidth - threeColumnWidth * 2 - COLUMN_GAP * 2);

        int confirmationY = panelHeight - CONFIRM_Y_FROM_BOTTOM;
        int detailBottom = confirmationY - CONFIRM_DETAIL_GAP;
        int detailTop = detailBottom - DETAIL_HEIGHT;
        int listBottom = detailTop - DETAIL_GAP;
        int rowStart = LIST_Y + LIST_PADDING;
        int lastAllowedRowBottom = listBottom - LIST_PADDING;
        int rowCapacity = lastAllowedRowBottom - rowStart < ROW_HEIGHT
                ? 0
                : 1 + (lastAllowedRowBottom - rowStart - ROW_HEIGHT) / ROW_PITCH;
        rowCapacity = Math.min(MAX_VISIBLE_ROWS, Math.max(0, rowCapacity));

        int safeEntryCount = Math.max(0, entryCount);
        int visibleRows = Math.min(safeEntryCount, rowCapacity);
        int maxStart = Math.max(0, safeEntryCount - visibleRows);
        int firstRow = Math.min(Math.max(0, requestedScroll), maxStart);
        return new DeathNodeAdminLayout(
                left, top, panelWidth, panelHeight, contentWidth,
                titleWidth, pageWidth, threeColumnWidth, thirdColumnWidth,
                listBottom, detailTop, detailBottom, confirmationY,
                rowCapacity, firstRow, visibleRows, screenWidth, screenHeight);
    }

    /** Fits semantic text to a measured font width without splitting a Unicode code point. */
    static BoundedLabel boundedLabel(String text, int maxWidth, ToIntFunction<String> widthOf) {
        String safeText = text == null ? "" : text;
        int safeMaxWidth = Math.max(0, maxWidth);
        if (safeText.isEmpty() || safeMaxWidth == 0) return new BoundedLabel("", 0, safeMaxWidth);
        int fullWidth = widthOf.applyAsInt(safeText);
        if (fullWidth <= safeMaxWidth) return new BoundedLabel(safeText, fullWidth, safeMaxWidth);
        String ellipsis = "…";
        if (widthOf.applyAsInt(ellipsis) > safeMaxWidth) return new BoundedLabel("", 0, safeMaxWidth);
        int low = 0;
        int high = safeText.codePointCount(0, safeText.length());
        while (low < high) {
            int middle = (low + high + 1) / 2;
            int end = safeText.offsetByCodePoints(0, middle);
            if (widthOf.applyAsInt(safeText.substring(0, end) + ellipsis) <= safeMaxWidth) low = middle;
            else high = middle - 1;
        }
        String fitted = safeText.substring(0, safeText.offsetByCodePoints(0, low)) + ellipsis;
        return new BoundedLabel(fitted, widthOf.applyAsInt(fitted), safeMaxWidth);
    }

    static record BoundedLabel(String text, int textWidth, int maxWidth) {
        boolean fits() { return textWidth <= maxWidth; }
    }

    static record DeathNodeAdminLayout(
            int left,
            int top,
            int panelWidth,
            int panelHeight,
            int contentWidth,
            int titleWidth,
            int pageWidth,
            int threeColumnWidth,
            int thirdColumnWidth,
            int listBottom,
            int detailTop,
            int detailBottom,
            int confirmationY,
            int rowCapacity,
            int firstRow,
            int visibleRows,
            int screenWidth,
            int screenHeight
    ) {
        int right() { return left + panelWidth; }
        int bottom() { return top + panelHeight; }
        int lastRowBottom() {
            return visibleRows == 0
                    ? LIST_Y
                    : LIST_Y + LIST_PADDING + (visibleRows - 1) * ROW_PITCH + ROW_HEIGHT;
        }
        boolean fits() {
            return left >= SAFE_SCREEN_MARGIN
                    && top >= SAFE_SCREEN_MARGIN
                    && screenWidth - right() >= SAFE_SCREEN_MARGIN
                    && screenHeight - bottom() >= SAFE_SCREEN_MARGIN
                    && FILTER_Y + TEXT_HEIGHT <= LIST_Y
                    && lastRowBottom() <= listBottom - LIST_PADDING
                    && detailTop >= listBottom + DETAIL_GAP
                    && detailTop + 42 + TEXT_HEIGHT <= detailBottom
                    && confirmationY + TEXT_HEIGHT < panelHeight;
        }
    }

    private static final class NativeDeathNodeAdminMirrorScreen extends ObserverMirrorScreen {
        private NativeDeathNodeAdminMirrorScreen() { super(Component.literal("Observer Death Node Admin")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) ClientPlayNetworking.send(new ObserverPayloads.Stop());
            super.onClose();
        }
        @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, width, height, 0xA0000000);
            DeathNodeAdminLayout layout = deathNodeAdminLayout(
                    width, height, remoteEntries.size(), remoteScrollIndex);
            int pw = layout.panelWidth();
            int ph = layout.panelHeight();
            int left = layout.left();
            int top = layout.top();
            int contentX = left + CONTENT_MARGIN;
            int contentRight = left + pw - CONTENT_MARGIN;
            g.fill(left, top, left + pw, top + ph, 0xF016191D);
            g.outline(left, top, pw, ph, 0xFF657383);

            BoundedLabel title = boundedLabel(
                    remoteTitle.isBlank() ? "Death Node Administration" : remoteTitle,
                    layout.titleWidth(), font::width);
            String pageText = "Page " + (remotePage + 1) + "  Total " + remoteTotalEntries
                    + (remoteTruncated ? " +" : "");
            BoundedLabel page = boundedLabel(pageText, layout.pageWidth(), font::width);
            g.text(font, title.text(), contentX, top + TITLE_Y, 0xFFFFFFFF, false);
            g.text(font, page.text(), contentRight - page.textWidth(), top + TITLE_Y, 0xFFB8C0C8, false);

            g.text(font, boundedLabel("Owner: " + (remoteOwnerQuery.isBlank() ? "*" : remoteOwnerQuery),
                    layout.contentWidth(), font::width).text(), contentX, top + OWNER_Y, 0xFFD2D8E0, false);
            g.text(font, boundedLabel("Dimension: " + (remoteDimensionQuery.isBlank() ? "*" : remoteDimensionQuery),
                    layout.contentWidth(), font::width).text(), contentX, top + DIMENSION_Y, 0xFFD2D8E0, false);

            int firstFilterX = contentX;
            int secondFilterX = firstFilterX + layout.threeColumnWidth() + COLUMN_GAP;
            int thirdFilterX = secondFilterX + layout.threeColumnWidth() + COLUMN_GAP;
            g.text(font, boundedLabel("Status: " + remoteStatusFilter,
                    layout.threeColumnWidth(), font::width).text(), firstFilterX, top + FILTER_Y, 0xFFB8C0C8, false);
            g.text(font, boundedLabel("Time: " + remoteTimeFilter,
                    layout.threeColumnWidth(), font::width).text(), secondFilterX, top + FILTER_Y, 0xFFB8C0C8, false);
            g.text(font, boundedLabel(remoteAdministratorView ? "Administrator view" : "Owner view",
                    layout.thirdColumnWidth(), font::width).text(), thirdFilterX, top + FILTER_Y, 0xFFFFC857, false);

            int listTop = top + LIST_Y;
            int listBottom = top + layout.listBottom();
            g.fill(left + 10, listTop, left + pw - 10, listBottom, 0x80101010);
            g.outline(left + 10, listTop, pw - 20, layout.listBottom() - LIST_Y, 0xFF3F4A56);
            int rowY = listTop + LIST_PADDING;
            int lastExclusive = layout.firstRow() + layout.visibleRows();
            for (int i = layout.firstRow(); i < lastExclusive; i++) {
                var entry = remoteEntries.get(i);
                boolean selected = entry.id().equals(remoteSelectedNodeId);
                g.fill(left + 14, rowY, left + pw - 14, rowY + 24, selected ? 0xFF2D3F54 : 0x9020252B);
                g.fill(left + 20, rowY + 7, left + 27, rowY + 14, "active".equals(entry.status()) ? 0xFF6AD98F : 0xFF9CA3AF);

                int rowTextX = left + 32;
                int rowTextRight = left + pw - 14;
                int rowTextWidth = Math.max(0, rowTextRight - rowTextX);
                int statusWidth = Math.min(72, Math.max(0, rowTextWidth / 5));
                int ownerWidth = remoteAdministratorView
                        ? Math.min(96, Math.max(0, rowTextWidth / 4))
                        : 0;
                int nameWidth = Math.max(0, rowTextWidth - statusWidth - ownerWidth
                        - (remoteAdministratorView ? COLUMN_GAP * 2 : COLUMN_GAP));
                int ownerX = rowTextX + nameWidth + COLUMN_GAP;
                int statusX = ownerX + ownerWidth + (remoteAdministratorView ? COLUMN_GAP : 0);
                g.text(font, boundedLabel(entry.name(), nameWidth, font::width).text(),
                        rowTextX, rowY + 3, 0xFFFFFFFF, false);
                if (remoteAdministratorView) {
                    g.text(font, boundedLabel(entry.ownerName(), ownerWidth, font::width).text(),
                            ownerX, rowY + 3, 0xFFD2D8E0, false);
                }
                g.text(font, boundedLabel(entry.status(), statusWidth, font::width).text(),
                        statusX, rowY + 3, 0xFFB8C0C8, false);

                String coordinates = entry.x() + ", " + entry.y() + ", " + entry.z();
                int coordinateWidth = Math.min(108, Math.max(0, rowTextWidth / 3));
                int dimensionWidth = Math.max(0, rowTextWidth - coordinateWidth - COLUMN_GAP);
                BoundedLabel coordinateLabel = boundedLabel(coordinates, coordinateWidth, font::width);
                g.text(font, boundedLabel(entry.dimension(), dimensionWidth, font::width).text(),
                        rowTextX, rowY + 14, 0xFF9CA3AF, false);
                g.text(font, coordinateLabel.text(), rowTextRight - coordinateLabel.textWidth(),
                        rowY + 14, 0xFF9CA3AF, false);
                rowY += ROW_PITCH;
            }

            int detailTop = top + layout.detailTop();
            int detailBottom = top + layout.detailBottom();
            g.fill(left + 10, detailTop, left + pw - 10, detailBottom, 0x8020252B);
            var selected = selectedEntry();
            int detailX = left + 10 + DETAIL_TEXT_X;
            int detailWidth = Math.max(0, pw - 20 - DETAIL_TEXT_X * 2);
            if (selected == null) {
                g.text(font, boundedLabel("No node selected", detailWidth, font::width).text(),
                        detailX, detailTop + 6, 0xFFB8C0C8, false);
            } else {
                int selectedStatusWidth = Math.min(100, Math.max(0, detailWidth / 3));
                int selectedNameWidth = Math.max(0, detailWidth - selectedStatusWidth - COLUMN_GAP);
                BoundedLabel selectedStatus = boundedLabel(selected.status(), selectedStatusWidth, font::width);
                g.text(font, boundedLabel(selected.name(), selectedNameWidth, font::width).text(),
                        detailX, detailTop + 6, 0xFFFFFFFF, false);
                g.text(font, selectedStatus.text(), detailX + detailWidth - selectedStatus.textWidth(),
                        detailTop + 6, 0xFFFFFFFF, false);
                g.text(font, boundedLabel("Owner: " + selected.ownerName(), detailWidth, font::width).text(),
                        detailX, detailTop + 18, 0xFFD2D8E0, false);
                g.text(font, boundedLabel("Updated: " + selected.updatedGameTime(), detailWidth, font::width).text(),
                        detailX, detailTop + 30, 0xFFB8C0C8, false);
                String diagnostics = selected.diagnosticFlags().isEmpty()
                        ? "Diagnostics: none"
                        : "Diagnostics: " + String.join(", ", selected.diagnosticFlags());
                g.text(font, boundedLabel(diagnostics, detailWidth, font::width).text(),
                        detailX, detailTop + 42, 0xFFFFC857, false);
            }
            if (remoteConfirmationActive) {
                g.text(font, boundedLabel("Confirmation pending: " + remoteConfirmationAction,
                        layout.contentWidth(), font::width).text(),
                        contentX, top + layout.confirmationY(), 0xFFFF8080, false);
            }
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
