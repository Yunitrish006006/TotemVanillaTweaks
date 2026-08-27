package dev.totem.vanillatweaks.client;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import dev.totem.vanillatweaks.network.ObserverNativeScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverNexusScreenPayloads;
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

/** Optional semantic adapter for TotemNexus normal-player map/friends/registration screens. */
public final class ObserverNexusScreenClient {
    private static final String MAP_LEGACY = "dev.totem.nexus.client.NexusMapScreen";
    private static final String MAP_MODERN = "dev.totem.nexus.client.NexusSpaceUnitMapScreen";
    private static final String FRIENDS_LEGACY = "dev.totem.nexus.client.NexusFriendsScreen";
    private static final String FRIENDS_MODERN = "dev.totem.nexus.client.NexusSpaceUnitFriendsScreen";
    private static final String REGISTRATION_LEGACY = "dev.totem.nexus.client.NexusRegistrationPreviewScreen";
    private static final String REGISTRATION_MODERN = "dev.totem.nexus.client.NexusSpaceUnitRegistrationPreviewScreen";
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    static final int MAP_SAFE_SCREEN_MARGIN = 12;
    static final int MAP_MAX_PANEL_WIDTH = 640;
    static final int MAP_MAX_PANEL_HEIGHT = 360;
    static final int MAP_CONTENT_MARGIN = 12;
    static final int MAP_TEXT_HEIGHT = 9;
    static final int MAP_TITLE_Y = 10;
    static final int MAP_SOURCE_Y = 28;
    static final int MAP_FILTER_Y = 44;
    static final int MAP_ZOOM_Y = 58;
    static final int MAP_ROWS_Y = 80;
    static final int MAP_ROW_HEIGHT = 24;
    static final int MAP_ROW_PITCH = 27;
    static final int MAP_DETAIL_BOTTOM_MARGIN = 20;
    static final int MAP_ROW_DETAIL_GAP = 8;
    static final int MAP_MAX_VISIBLE_ROWS = 8;

    private static long nextTargetSequence;
    private static long lastSnapshotNanos;
    private static boolean targetOpen;
    private static String targetVariant = "";

    private static boolean remoteOpen;
    private static String remoteVariant = "";
    private static String remoteTitle = "";
    private static UUID remoteSourceId;
    private static String remoteSourceType = "";
    private static String remoteSourceName = "";
    private static String remoteSourceDimension = "";
    private static int remoteSourceX;
    private static int remoteSourceY;
    private static int remoteSourceZ;
    private static String remoteActiveDimension = "";
    private static UUID remoteSelectedId;
    private static int remoteListScroll;
    private static double remoteZoom = 1.0D;
    private static String remoteSearch = "";
    private static String remoteTypeFilter = "";
    private static String remoteFriendFilter = "";
    private static String remoteSortMode = "";
    private static boolean remoteShowMaterials;
    private static List<ObserverNexusScreenPayloads.MapEntryState> remoteMapEntries = List.of();
    private static int remoteFriendsScroll;
    private static List<ObserverNexusScreenPayloads.FriendEntryState> remoteFriendEntries = List.of();
    private static String remoteRegistrationDimension = "";
    private static int remoteRegistrationX;
    private static int remoteRegistrationY;
    private static int remoteRegistrationZ;
    private static int remoteRegistrationTier;
    private static int remoteResonancePercent;
    private static int remoteCompletenessPercent;
    private static int remoteWearPercent;
    private static int remoteConfirmSeconds;
    private static long extractedFrames;
    private static boolean suppressMirrorStop;

    private ObserverNexusScreenClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                ObserverNexusScreenPayloads.NexusRelay.TYPE,
                (payload, context) -> context.client().execute(() -> acceptRelay(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ObserverNexusScreenClient::tick);
    }

    static boolean isTargetScreen(Screen screen) {
        if (screen == null) return false;
        String name = screen.getClass().getName();
        return MAP_LEGACY.equals(name) || MAP_MODERN.equals(name)
                || FRIENDS_LEGACY.equals(name) || FRIENDS_MODERN.equals(name)
                || REGISTRATION_LEGACY.equals(name) || REGISTRATION_MODERN.equals(name);
    }

    static boolean hasRemoteScreen() {
        return remoteOpen && ObserverNativeClient.observerSessionActive();
    }

    private static void tick(Minecraft minecraft) {
        Screen screen = minecraft.gui.screen();
        if (ObserverNativeClient.targetStateEnabled() && minecraft.player != null && minecraft.level != null
                && ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_NEXUS)
                && isTargetScreen(screen)) {
            tickTarget(screen);
        } else {
            closeTargetIfNeeded();
        }

        if (!ObserverNativeClient.observerSessionActive()) {
            if (remoteOpen || isMirror(minecraft.gui.screen())) {
                clearRemote();
                closeMirror();
            }
        } else if (remoteOpen) {
            ensureMirror();
        }
    }

    private static void tickTarget(Screen screen) {
        long now = System.nanoTime();
        String variant = variantFor(screen);
        if (targetOpen && variant.equals(targetVariant) && now - lastSnapshotNanos < SNAPSHOT_INTERVAL_NANOS) return;
        long sequence = nextTargetSequence + 1L;
        ObserverNexusScreenPayloads.NexusState state = captureTargetState(screen, sequence);
        if (state == null) return;
        nextTargetSequence = sequence;
        targetOpen = true;
        targetVariant = variant;
        lastSnapshotNanos = now;
        ClientPlayNetworking.send(state);
    }

    private static void closeTargetIfNeeded() {
        if (!targetOpen) return;
        targetOpen = false;
        targetVariant = "";
        lastSnapshotNanos = 0L;
        if (ObserverNativeClient.targetSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_NEXUS)) {
            ClientPlayNetworking.send(closedTargetState(++nextTargetSequence));
        }
    }

    /** Exact production extractor shared by the target tick and cross-module runtime gate. */
    public static ObserverNexusScreenPayloads.NexusState captureTargetState(Screen screen, long sequence) {
        if (!isTargetScreen(screen)) throw new IllegalArgumentException("Expected a supported TotemNexus screen");
        String variant = variantFor(screen);
        try {
            return switch (variant) {
                case ObserverNexusScreenPayloads.VARIANT_MAP -> captureMap(screen, sequence);
                case ObserverNexusScreenPayloads.VARIANT_FRIENDS -> captureFriends(screen, sequence);
                case ObserverNexusScreenPayloads.VARIANT_REGISTRATION -> captureRegistration(screen, sequence);
                default -> null;
            };
        } catch (RuntimeException error) {
            TotemVanillaTweaks.LOGGER.debug("Unable to read TotemNexus screen state from {}", screen.getClass().getName(), error);
            return null;
        }
    }

    public static ObserverNexusScreenPayloads.NexusState closedTargetState(long sequence) {
        return ObserverNexusScreenPayloads.closed(sequence);
    }

    private static ObserverNexusScreenPayloads.NexusState captureMap(Screen screen, long sequence) {
        Object payload = fieldValue(screen, "payload");
        if (payload == null) return null;
        UUID sourceId = uuid(invoke(payload, "sourceUnitId"));
        String sourceDimension = text(invoke(payload, "sourceDimension"));
        boolean modern = MAP_MODERN.equals(screen.getClass().getName());
        UUID selected = uuid(fieldValueIfPresent(screen, modern ? "selectedUnitId" : "selected"));
        String activeDimension = modern ? text(fieldValueIfPresent(screen, "activeDimension")) : sourceDimension;
        int scroll = modern ? integer(fieldValueIfPresent(screen, "listScrollIndex")) : 0;
        double zoom = modern ? decimal(fieldValueIfPresent(screen, "zoom"), 1.0D) : 1.0D;
        String search = modern ? text(fieldValueIfPresent(screen, "searchQuery")) : "";
        String typeFilter = modern ? enumText(fieldValueIfPresent(screen, "typeFilter")) : "all";
        String friendFilter = modern ? enumText(fieldValueIfPresent(screen, "friendFilter")) : "all";
        String sortMode = modern ? enumText(fieldValueIfPresent(screen, "sortMode")) : "name";
        boolean showMaterials = modern && bool(fieldValueIfPresent(screen, "showMaterials"));

        return baseState(screen, sequence, ObserverNexusScreenPayloads.VARIANT_MAP,
                sourceId, text(invoke(payload, "sourceType")), text(invoke(payload, "sourceName")), sourceDimension,
                integer(invoke(payload, "sourceX")), integer(invoke(payload, "sourceY")), integer(invoke(payload, "sourceZ")),
                activeDimension, selected, scroll, zoom, search, typeFilter, friendFilter, sortMode, showMaterials,
                mapEntries(invoke(payload, "entries")), 0, List.of(), "", 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static ObserverNexusScreenPayloads.NexusState captureFriends(Screen screen, long sequence) {
        Object payload = fieldValue(screen, "payload");
        List<ObserverNexusScreenPayloads.FriendEntryState> entries = payload == null
                ? List.of() : friendEntries(invoke(payload, "entries"));
        boolean modern = FRIENDS_MODERN.equals(screen.getClass().getName());
        UUID selected = uuid(fieldValueIfPresent(screen, modern ? "selectedPlayerId" : "selected"));
        int scroll = modern ? integer(fieldValueIfPresent(screen, "scrollIndex")) : 0;
        return baseState(screen, sequence, ObserverNexusScreenPayloads.VARIANT_FRIENDS,
                null, "", "", "", 0, 0, 0, "", selected, 0, 1.0D,
                "", "", "", "", false, List.of(), scroll, entries,
                "", 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static ObserverNexusScreenPayloads.NexusState captureRegistration(Screen screen, long sequence) {
        Object payload = fieldValue(screen, "payload");
        if (payload == null) return null;
        return baseState(screen, sequence, ObserverNexusScreenPayloads.VARIANT_REGISTRATION,
                null, "", "", "", 0, 0, 0, "", null, 0, 1.0D,
                "", "", "", "", false, List.of(), 0, List.of(),
                text(invoke(payload, "dimension")), integer(invoke(payload, "x")), integer(invoke(payload, "y")),
                integer(invoke(payload, "z")), integer(invoke(payload, "tier")),
                integer(invoke(payload, "resonancePercent")), integer(invoke(payload, "completenessPercent")),
                integer(invoke(payload, "wearPercent")), integer(invoke(payload, "confirmSeconds")));
    }

    private static ObserverNexusScreenPayloads.NexusState baseState(
            Screen screen, long sequence, String variant,
            UUID sourceId, String sourceType, String sourceName, String sourceDimension,
            int sourceX, int sourceY, int sourceZ,
            String activeDimension, UUID selectedId, int listScrollIndex, double zoom,
            String searchQuery, String typeFilter, String friendFilter, String sortMode, boolean showMaterials,
            List<ObserverNexusScreenPayloads.MapEntryState> mapEntries,
            int friendsScrollIndex, List<ObserverNexusScreenPayloads.FriendEntryState> friendEntries,
            String registrationDimension, int registrationX, int registrationY, int registrationZ,
            int registrationTier, int resonancePercent, int completenessPercent, int wearPercent, int confirmSeconds) {
        return new ObserverNexusScreenPayloads.NexusState(
                ObserverNexusScreenPayloads.PROTOCOL_VERSION, sequence, true,
                ObserverNativeScreenPayloads.FAMILY_NEXUS, variant, screen.getClass().getName(),
                screen.getTitle() == null ? "" : screen.getTitle().getString(), sourceId, sourceType, sourceName,
                sourceDimension, sourceX, sourceY, sourceZ, activeDimension, selectedId, listScrollIndex, zoom,
                searchQuery, typeFilter, friendFilter, sortMode, showMaterials, mapEntries, friendsScrollIndex,
                friendEntries, registrationDimension, registrationX, registrationY, registrationZ, registrationTier,
                resonancePercent, completenessPercent, wearPercent, confirmSeconds);
    }

    private static List<ObserverNexusScreenPayloads.MapEntryState> mapEntries(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<ObserverNexusScreenPayloads.MapEntryState> result = new ArrayList<>();
        for (Object e : list.stream().limit(ObserverNexusScreenPayloads.MAX_MAP_ENTRIES).toList()) {
            result.add(new ObserverNexusScreenPayloads.MapEntryState(
                    uuid(invoke(e, "id")), text(invoke(e, "name")), text(invoke(e, "type")),
                    text(invoke(e, "dimension")), text(invoke(e, "visibility")), bool(invoke(e, "friendShared")),
                    bool(invoke(e, "favorite")), bool(invoke(e, "manageable")), bool(invoke(e, "owned")),
                    bool(invoke(e, "canTeleport")), text(invoke(e, "blockedReason")), integer(invoke(e, "tier")),
                    decimal(invoke(e, "resonance"), 0.0D), integer(invoke(e, "distanceBlocks")),
                    integer(invoke(e, "finalFoodCost")), integer(invoke(e, "amethystCost")),
                    integer(invoke(e, "prepareTicks")), integer(invoke(e, "maxHorizontalDeviation")),
                    integer(invoke(e, "damageChancePercent")), integer(invoke(e, "structureWearChancePercent"))));
        }
        return List.copyOf(result);
    }

    private static List<ObserverNexusScreenPayloads.FriendEntryState> friendEntries(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().limit(ObserverNexusScreenPayloads.MAX_FRIEND_ENTRIES)
                .map(e -> new ObserverNexusScreenPayloads.FriendEntryState(uuid(invoke(e, "id")),
                        text(invoke(e, "name")), bool(invoke(e, "online")), text(invoke(e, "status"))))
                .toList();
    }

    private static void acceptRelay(ObserverNexusScreenPayloads.NexusRelay p) {
        UUID targetId = ObserverNativeClient.observerTargetId();
        if (!ObserverNativeClient.observerSessionActive()
                || !ObserverNativeClient.observerSupportsScreen(ObserverNativeScreenPayloads.CAPABILITY_NEXUS)
                || targetId == null || !targetId.equals(p.targetId())
                || p.protocolVersion() != ObserverNexusScreenPayloads.PROTOCOL_VERSION
                || !ObserverNativeScreenPayloads.FAMILY_NEXUS.equals(p.familyId())
                || !ObserverRemoteSequenceTracker.accept(
                        ObserverNativeScreenPayloads.FAMILY_NEXUS, p.targetId(), p.sequence())) return;
        if (!p.open()) {
            clearRemote();
            closeMirror();
            return;
        }
        remoteOpen = true;
        remoteVariant = p.variant();
        remoteTitle = p.title();
        remoteSourceId = p.sourceId(); remoteSourceType = p.sourceType(); remoteSourceName = p.sourceName();
        remoteSourceDimension = p.sourceDimension(); remoteSourceX = p.sourceX(); remoteSourceY = p.sourceY(); remoteSourceZ = p.sourceZ();
        remoteActiveDimension = p.activeDimension(); remoteSelectedId = p.selectedId(); remoteListScroll = p.listScrollIndex();
        remoteZoom = p.zoom(); remoteSearch = p.searchQuery(); remoteTypeFilter = p.typeFilter(); remoteFriendFilter = p.friendFilter();
        remoteSortMode = p.sortMode(); remoteShowMaterials = p.showMaterials(); remoteMapEntries = List.copyOf(p.mapEntries());
        remoteFriendsScroll = p.friendsScrollIndex(); remoteFriendEntries = List.copyOf(p.friendEntries());
        remoteRegistrationDimension = p.registrationDimension(); remoteRegistrationX = p.registrationX();
        remoteRegistrationY = p.registrationY(); remoteRegistrationZ = p.registrationZ(); remoteRegistrationTier = p.registrationTier();
        remoteResonancePercent = p.resonancePercent(); remoteCompletenessPercent = p.completenessPercent();
        remoteWearPercent = p.wearPercent(); remoteConfirmSeconds = p.confirmSeconds();
        ObserverNativeScreenClient.applyGenericScreenState(false, "", "");
        ensureMirror();
    }

    private static void clearRemote() {
        remoteOpen = false; remoteVariant = ""; remoteTitle = ""; remoteSourceId = null; remoteSourceType = "";
        remoteSourceName = ""; remoteSourceDimension = ""; remoteSourceX = remoteSourceY = remoteSourceZ = 0;
        remoteActiveDimension = ""; remoteSelectedId = null; remoteListScroll = 0; remoteZoom = 1.0D;
        remoteSearch = ""; remoteTypeFilter = ""; remoteFriendFilter = ""; remoteSortMode = ""; remoteShowMaterials = false;
        remoteMapEntries = List.of(); remoteFriendsScroll = 0; remoteFriendEntries = List.of();
        remoteRegistrationDimension = ""; remoteRegistrationX = remoteRegistrationY = remoteRegistrationZ = 0;
        remoteRegistrationTier = remoteResonancePercent = remoteCompletenessPercent = remoteWearPercent = remoteConfirmSeconds = 0;
    }

    private static void ensureMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!remoteOpen || !ObserverNativeClient.observerSessionActive()) return;
        if (!(minecraft.gui.screen() instanceof NativeNexusMirrorScreen)) replaceScreen(new NativeNexusMirrorScreen());
    }

    private static boolean isMirror(Screen screen) { return screen instanceof NativeNexusMirrorScreen; }
    private static void closeMirror() {
        Minecraft minecraft = Minecraft.getInstance();
        if (isMirror(minecraft.gui.screen())) replaceScreen(null);
    }
    private static void replaceScreen(Screen screen) {
        suppressMirrorStop = true;
        try { Minecraft.getInstance().setScreenAndShow(screen); }
        finally { suppressMirrorStop = false; }
    }

    private static String variantFor(Screen screen) {
        String name = screen.getClass().getName();
        if (MAP_LEGACY.equals(name) || MAP_MODERN.equals(name)) return ObserverNexusScreenPayloads.VARIANT_MAP;
        if (FRIENDS_LEGACY.equals(name) || FRIENDS_MODERN.equals(name)) return ObserverNexusScreenPayloads.VARIANT_FRIENDS;
        if (REGISTRATION_LEGACY.equals(name) || REGISTRATION_MODERN.equals(name)) return ObserverNexusScreenPayloads.VARIANT_REGISTRATION;
        return "";
    }

    private static Object fieldValue(Object owner, String name) {
        Object value = fieldValueIfPresent(owner, name);
        if (value == Missing.INSTANCE) throw new IllegalStateException("Missing field " + name + " on " + owner.getClass().getName());
        return value;
    }

    private static Object fieldValueIfPresent(Object owner, String name) {
        if (owner == null) return null;
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException error) {
                throw new RuntimeException(error);
            }
        }
        return Missing.INSTANCE;
    }

    private static Object invoke(Object owner, String name) {
        if (owner == null || owner == Missing.INSTANCE) return null;
        try {
            Method method = owner.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(owner);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException("Missing method " + name + " on " + owner.getClass().getName(), error);
        }
    }

    private static String text(Object value) { return value == null || value == Missing.INSTANCE ? "" : String.valueOf(value); }
    private static String enumText(Object value) { return text(value).toLowerCase(Locale.ROOT); }
    private static UUID uuid(Object value) { return value instanceof UUID id ? id : null; }
    private static int integer(Object value) { return value instanceof Number n ? n.intValue() : 0; }
    private static double decimal(Object value, double fallback) { return value instanceof Number n ? n.doubleValue() : fallback; }
    private static boolean bool(Object value) { return value instanceof Boolean b && b; }

    /** Resolves the responsive map viewport without consuming the vanilla safe margin. */
    static MapLayout mapLayout(int screenWidth, int screenHeight, int entryCount, int requestedScroll) {
        int safeWidth = Math.max(1, screenWidth - MAP_SAFE_SCREEN_MARGIN * 2);
        int safeHeight = Math.max(1, screenHeight - MAP_SAFE_SCREEN_MARGIN * 2);
        int panelWidth = Math.min(MAP_MAX_PANEL_WIDTH, safeWidth);
        int panelHeight = Math.min(MAP_MAX_PANEL_HEIGHT, safeHeight);
        int left = (screenWidth - panelWidth) / 2;
        int top = (screenHeight - panelHeight) / 2;
        int contentWidth = Math.max(0, panelWidth - MAP_CONTENT_MARGIN * 2);
        int detailY = panelHeight - MAP_DETAIL_BOTTOM_MARGIN;
        int listBottom = detailY - MAP_ROW_DETAIL_GAP;
        int rowPixels = Math.max(0, listBottom - MAP_ROWS_Y);
        int rowCapacity = rowPixels < MAP_ROW_HEIGHT
                ? 0
                : 1 + (rowPixels - MAP_ROW_HEIGHT) / MAP_ROW_PITCH;
        rowCapacity = Math.min(MAP_MAX_VISIBLE_ROWS, rowCapacity);

        int safeEntryCount = Math.max(0, entryCount);
        int visibleRows = Math.min(safeEntryCount, rowCapacity);
        int maxStart = Math.max(0, safeEntryCount - visibleRows);
        int firstRow = Math.min(Math.max(0, requestedScroll), maxStart);
        return new MapLayout(left, top, panelWidth, panelHeight, contentWidth,
                detailY, listBottom, rowCapacity, firstRow, visibleRows,
                screenWidth, screenHeight);
    }

    /** Fits map semantics to a measured font width without splitting a Unicode code point. */
    static MapLabel mapLabel(String text, int maxWidth, ToIntFunction<String> widthOf) {
        String safeText = text == null ? "" : text;
        int safeMaxWidth = Math.max(0, maxWidth);
        if (safeText.isEmpty() || safeMaxWidth == 0) return new MapLabel("", 0, safeMaxWidth);
        if (widthOf.applyAsInt(safeText) <= safeMaxWidth) {
            return new MapLabel(safeText, widthOf.applyAsInt(safeText), safeMaxWidth);
        }
        String ellipsis = "…";
        int ellipsisWidth = widthOf.applyAsInt(ellipsis);
        if (ellipsisWidth > safeMaxWidth) return new MapLabel("", 0, safeMaxWidth);
        int low = 0;
        int high = safeText.codePointCount(0, safeText.length());
        while (low < high) {
            int middle = (low + high + 1) / 2;
            int end = safeText.offsetByCodePoints(0, middle);
            if (widthOf.applyAsInt(safeText.substring(0, end) + ellipsis) <= safeMaxWidth) low = middle;
            else high = middle - 1;
        }
        String fitted = safeText.substring(0, safeText.offsetByCodePoints(0, low)) + ellipsis;
        return new MapLabel(fitted, widthOf.applyAsInt(fitted), safeMaxWidth);
    }

    static record MapLabel(String text, int textWidth, int maxWidth) {
        boolean fits() { return textWidth <= maxWidth; }
    }

    static record MapLayout(
            int left,
            int top,
            int panelWidth,
            int panelHeight,
            int contentWidth,
            int detailY,
            int listBottom,
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
                    ? MAP_ROWS_Y
                    : MAP_ROWS_Y + (visibleRows - 1) * MAP_ROW_PITCH + MAP_ROW_HEIGHT;
        }
        boolean fits() {
            return left >= MAP_SAFE_SCREEN_MARGIN
                    && top >= MAP_SAFE_SCREEN_MARGIN
                    && screenWidth - right() >= MAP_SAFE_SCREEN_MARGIN
                    && screenHeight - bottom() >= MAP_SAFE_SCREEN_MARGIN
                    && MAP_ZOOM_Y + MAP_TEXT_HEIGHT <= MAP_ROWS_Y
                    && lastRowBottom() <= listBottom
                    && detailY + MAP_TEXT_HEIGHT < panelHeight;
        }
    }

    private enum Missing { INSTANCE }

    private static final class NativeNexusMirrorScreen extends ObserverMirrorScreen {
        private NativeNexusMirrorScreen() { super(Component.literal("Observer Nexus")); }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, width, height, 0xA0000000);
            switch (remoteVariant) {
                case ObserverNexusScreenPayloads.VARIANT_MAP -> drawMap(g);
                case ObserverNexusScreenPayloads.VARIANT_FRIENDS -> drawFriends(g);
                case ObserverNexusScreenPayloads.VARIANT_REGISTRATION -> drawRegistration(g);
                default -> drawUnknown(g);
            }
            extractedFrames++;
        }

        private void drawMap(GuiGraphicsExtractor g) {
            MapLayout layout = mapLayout(width, height, remoteMapEntries.size(), remoteListScroll);
            int panelWidth = layout.panelWidth(), panelHeight = layout.panelHeight();
            int left = layout.left(), top = layout.top();
            panel(g, left, top, panelWidth, panelHeight);
            int textX = left + MAP_CONTENT_MARGIN;
            int textWidth = layout.contentWidth();
            g.text(font, mapLabel(titleText("Nexus Map"), textWidth, font::width).text(),
                    textX, top + MAP_TITLE_Y, 0xFFFFFFFF, true);
            g.text(font, mapLabel(remoteSourceName + " · " + remoteActiveDimension,
                            textWidth, font::width).text(),
                    textX, top + MAP_SOURCE_Y, 0xFFB8C0C8, false);
            String filters = "Search: " + remoteSearch + "  Type: " + remoteTypeFilter
                    + "  Friend: " + remoteFriendFilter + "  Sort: " + remoteSortMode;
            g.text(font, mapLabel(filters, textWidth, font::width).text(),
                    textX, top + MAP_FILTER_Y, 0xFF9FB0C0, false);
            String zoom = "Zoom " + String.format(Locale.ROOT, "%.2f", remoteZoom) + "  scroll " + remoteListScroll
                    + (remoteShowMaterials ? "  [materials]" : "");
            g.text(font, mapLabel(zoom, textWidth, font::width).text(),
                    textX, top + MAP_ZOOM_Y, 0xFF93A4B5, false);

            int rowY = top + MAP_ROWS_Y;
            int lastExclusive = layout.firstRow() + layout.visibleRows();
            for (int i = layout.firstRow(); i < lastExclusive; i++) {
                var e = remoteMapEntries.get(i);
                boolean selected = e.id().equals(remoteSelectedId);
                g.fill(textX, rowY, left + panelWidth - MAP_CONTENT_MARGIN, rowY + MAP_ROW_HEIGHT,
                        selected ? 0xFF33495D : 0xFF202830);
                String status = e.canTeleport() ? "ready" : e.blockedReason();
                int rowTextWidth = Math.max(0, textWidth - 12);
                String row = e.name() + " · " + e.type() + " · " + e.dimension() + " · " + status;
                g.text(font, mapLabel(row, rowTextWidth, font::width).text(),
                        textX + 6, rowY + 7, 0xFFE0E6EC, false);
                rowY += MAP_ROW_PITCH;
            }
            var selected = selectedMapEntry();
            if (selected != null) {
                String detail = "Tier " + selected.tier() + "  resonance " + Math.round(selected.resonance() * 100.0D)
                        + "%  distance " + selected.distanceBlocks() + "  food " + selected.foodCost()
                        + "  amethyst " + selected.amethystCost();
                g.text(font, mapLabel(detail, textWidth, font::width).text(),
                        textX, top + layout.detailY(), 0xFFFFD166, false);
            }
        }

        private void drawFriends(GuiGraphicsExtractor g) {
            int panelWidth = Math.min(330, Math.max(260, width - 24));
            int panelHeight = Math.min(238, Math.max(200, height - 24));
            int left = (width - panelWidth) / 2, top = (height - panelHeight) / 2;
            panel(g, left, top, panelWidth, panelHeight);
            g.text(font, titleText("Nexus Friends"), left + 12, top + 10, 0xFFFFFFFF, true);
            g.text(font, "Friends: " + remoteFriendEntries.size(), left + panelWidth - 94, top + 10, 0xFFB8C0C8, false);
            int start = Math.min(Math.max(0, remoteFriendsScroll), Math.max(0, remoteFriendEntries.size() - 1));
            int rowY = top + 42;
            for (int i = start; i < remoteFriendEntries.size() && rowY + 24 < top + panelHeight - 32; i++) {
                var e = remoteFriendEntries.get(i);
                boolean selected = e.id().equals(remoteSelectedId);
                g.fill(left + 12, rowY, left + panelWidth - 12, rowY + 24, selected ? 0xFF33495D : 0xFF202830);
                g.fill(left + 18, rowY + 8, left + 26, rowY + 16, e.online() ? 0xFF6AD98F : 0xFF6B7280);
                g.text(font, trim(e.name() + " [" + e.status() + "]", 38), left + 32, rowY + 7, 0xFFE0E6EC, false);
                rowY += 28;
            }
        }

        private void drawRegistration(GuiGraphicsExtractor g) {
            int panelWidth = Math.min(286, Math.max(250, width - 24));
            int panelHeight = 142;
            int left = (width - panelWidth) / 2, top = (height - panelHeight) / 2;
            panel(g, left, top, panelWidth, panelHeight);
            g.text(font, titleText("Nexus Registration"), left + 12, top + 10, 0xFFFFFFFF, true);
            g.text(font, trim("Position: " + remoteRegistrationDimension + " " + remoteRegistrationX + ", "
                    + remoteRegistrationY + ", " + remoteRegistrationZ, 42), left + 12, top + 31, 0xFFB8C0C8, false);
            g.text(font, "Tier " + remoteRegistrationTier + " · resonance " + remoteResonancePercent
                    + "% · complete " + remoteCompletenessPercent + "% · wear " + remoteWearPercent + "%",
                    left + 12, top + 49, 0xFFE0E6EC, false);
            g.text(font, "Confirm within " + remoteConfirmSeconds + "s", left + 12, top + 69, 0xFFFFD166, false);
            g.text(font, "Registration confirmation is pending.", left + 12, top + 88, 0xFF93A4B5, false);
        }

        private void drawUnknown(GuiGraphicsExtractor g) {
            int left = Math.max(10, width / 2 - 160), top = Math.max(10, height / 2 - 40);
            panel(g, left, top, 320, 80);
            g.text(font, "Unknown Nexus semantic variant", left + 12, top + 28, 0xFFFFFFFF, true);
        }

        private void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
            g.fill(x, y, x + w, y + h, 0xF016191D);
            g.outline(x, y, w, h, 0xFF657383);
        }

        private String titleText(String fallback) { return remoteTitle.isBlank() ? fallback : remoteTitle; }
        private String trim(String value, int max) {
            if (value == null) return "";
            return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
        }
        private ObserverNexusScreenPayloads.MapEntryState selectedMapEntry() {
            if (remoteSelectedId == null) return null;
            return remoteMapEntries.stream().filter(e -> remoteSelectedId.equals(e.id())).findFirst().orElse(null);
        }

        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() {
            if (!suppressMirrorStop && ObserverNativeClient.observerSessionActive()) ClientPlayNetworking.send(new ObserverPayloads.Stop());
            super.onClose();
        }
    }
}
