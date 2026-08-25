package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Framebuffer-free semantic transport for the vanilla Statistics screen. */
public final class ObserverStatsScreenPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final long CAPABILITY = 1L << 23;
    public static final String FAMILY_ID = "stats";
    public static final String SCREEN_CLASS = "net.minecraft.client.gui.screens.achievement.StatsScreen";
    public static final int MAX_GENERAL_ROWS = 192;
    public static final int MAX_ITEM_ROWS = 256;
    public static final int MAX_MOB_ROWS = 160;
    private static final int MAX_ID = 256;
    private static final int MAX_TEXT = 512;

    private ObserverStatsScreenPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record GeneralRow(String statId, String label, int rawValue, String formattedValue) {}
    public record ItemRow(String itemId, int mined, int broken, int crafted, int used, int pickedUp, int dropped) {}
    public record MobRow(String entityId, String name, int killed, int killedBy) {}

    public record StatsState(
            int protocolVersion, long sequence, boolean open, String familyId, String screenClass, String title,
            String activeTab, boolean loading, double scrollAmount, String itemSortColumn, int itemSortOrder,
            List<GeneralRow> generalRows, List<ItemRow> itemRows, List<MobRow> mobRows
    ) implements CustomPacketPayload {
        public static final Type<StatsState> TYPE = new Type<>(id("observer_stats_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, StatsState> CODEC = StreamCodec.of(
                ObserverStatsScreenPayloads::writeState, ObserverStatsScreenPayloads::readState);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record StatsRelay(
            UUID targetId, int protocolVersion, long sequence, boolean open, String familyId, String screenClass,
            String title, String activeTab, boolean loading, double scrollAmount, String itemSortColumn, int itemSortOrder,
            List<GeneralRow> generalRows, List<ItemRow> itemRows, List<MobRow> mobRows
    ) implements CustomPacketPayload {
        public static final Type<StatsRelay> TYPE = new Type<>(id("observer_stats_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, StatsRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.screenClass(), value.title(), value.activeTab(), value.loading(), value.scrollAmount(),
                            value.itemSortColumn(), value.itemSortOrder(), value.generalRows(), value.itemRows(), value.mobRows());
                },
                buf -> relay(buf.readUUID(), readState(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static StatsState closed(long sequence) {
        return new StatsState(PROTOCOL_VERSION, sequence, false, FAMILY_ID, "", "", "general", false,
                0.0D, "", 0, List.of(), List.of(), List.of());
    }

    public static StatsRelay relay(UUID targetId, StatsState state) {
        return new StatsRelay(targetId, state.protocolVersion(), state.sequence(), state.open(), state.familyId(),
                state.screenClass(), state.title(), state.activeTab(), state.loading(), state.scrollAmount(),
                state.itemSortColumn(), state.itemSortOrder(), state.generalRows(), state.itemRows(), state.mobRows());
    }

    private static void writeState(FriendlyByteBuf buf, StatsState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.screenClass(),
                value.title(), value.activeTab(), value.loading(), value.scrollAmount(), value.itemSortColumn(),
                value.itemSortOrder(), value.generalRows(), value.itemRows(), value.mobRows());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String screenClass, String title, String activeTab,
                                    boolean loading, double scrollAmount, String itemSortColumn, int itemSortOrder,
                                    List<GeneralRow> generalRows, List<ItemRow> itemRows, List<MobRow> mobRows) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, 64);
        buf.writeUtf(screenClass, MAX_ID);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeUtf(activeTab, 16);
        buf.writeBoolean(loading);
        buf.writeDouble(scrollAmount);
        buf.writeUtf(itemSortColumn, 32);
        buf.writeInt(itemSortOrder);

        int generalCount = Math.min(generalRows.size(), MAX_GENERAL_ROWS);
        buf.writeVarInt(generalCount);
        for (int i = 0; i < generalCount; i++) {
            GeneralRow row = generalRows.get(i);
            buf.writeUtf(row.statId(), MAX_ID);
            buf.writeUtf(row.label(), MAX_TEXT);
            buf.writeInt(row.rawValue());
            buf.writeUtf(row.formattedValue(), MAX_TEXT);
        }

        int itemCount = Math.min(itemRows.size(), MAX_ITEM_ROWS);
        buf.writeVarInt(itemCount);
        for (int i = 0; i < itemCount; i++) {
            ItemRow row = itemRows.get(i);
            buf.writeUtf(row.itemId(), MAX_ID);
            buf.writeInt(row.mined());
            buf.writeInt(row.broken());
            buf.writeInt(row.crafted());
            buf.writeInt(row.used());
            buf.writeInt(row.pickedUp());
            buf.writeInt(row.dropped());
        }

        int mobCount = Math.min(mobRows.size(), MAX_MOB_ROWS);
        buf.writeVarInt(mobCount);
        for (int i = 0; i < mobCount; i++) {
            MobRow row = mobRows.get(i);
            buf.writeUtf(row.entityId(), MAX_ID);
            buf.writeUtf(row.name(), MAX_TEXT);
            buf.writeInt(row.killed());
            buf.writeInt(row.killedBy());
        }
    }

    private static StatsState readState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(64);
        String screenClass = buf.readUtf(MAX_ID);
        String title = buf.readUtf(MAX_TEXT);
        String activeTab = buf.readUtf(16);
        boolean loading = buf.readBoolean();
        double scrollAmount = buf.readDouble();
        String itemSortColumn = buf.readUtf(32);
        int itemSortOrder = buf.readInt();

        int generalCount = boundedCount(buf.readVarInt(), MAX_GENERAL_ROWS, "general");
        List<GeneralRow> generalRows = new ArrayList<>(generalCount);
        for (int i = 0; i < generalCount; i++) {
            generalRows.add(new GeneralRow(buf.readUtf(MAX_ID), buf.readUtf(MAX_TEXT), buf.readInt(), buf.readUtf(MAX_TEXT)));
        }

        int itemCount = boundedCount(buf.readVarInt(), MAX_ITEM_ROWS, "item");
        List<ItemRow> itemRows = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            itemRows.add(new ItemRow(buf.readUtf(MAX_ID), buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readInt()));
        }

        int mobCount = boundedCount(buf.readVarInt(), MAX_MOB_ROWS, "mob");
        List<MobRow> mobRows = new ArrayList<>(mobCount);
        for (int i = 0; i < mobCount; i++) {
            mobRows.add(new MobRow(buf.readUtf(MAX_ID), buf.readUtf(MAX_TEXT), buf.readInt(), buf.readInt()));
        }

        return new StatsState(protocolVersion, sequence, open, familyId, screenClass, title, activeTab, loading,
                scrollAmount, itemSortColumn, itemSortOrder, List.copyOf(generalRows), List.copyOf(itemRows), List.copyOf(mobRows));
    }

    private static int boundedCount(int count, int max, String kind) {
        if (count < 0 || count > max) throw new IllegalArgumentException("Observer stats " + kind + " count out of range: " + count);
        return count;
    }
}
