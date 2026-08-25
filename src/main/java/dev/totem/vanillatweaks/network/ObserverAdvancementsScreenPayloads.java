package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Framebuffer-free semantic transport for the vanilla Advancements screen. */
public final class ObserverAdvancementsScreenPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final long CAPABILITY = 1L << 22;
    public static final String FAMILY_ID = "advancements";
    public static final String SCREEN_CLASS = "net.minecraft.client.gui.screens.advancements.AdvancementsScreen";
    public static final int MAX_TABS = 32;
    public static final int MAX_NODES = 256;
    private static final int MAX_ID = 256;
    private static final int MAX_TEXT = 512;

    private ObserverAdvancementsScreenPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record TabState(String rootId, String title, String iconItemId) {}

    public record NodeState(
            String id, String rootId, String parentId,
            String title, String description, String iconItemId, String type,
            float x, float y, float progress, boolean done, boolean hidden
    ) {}

    public record AdvancementsState(
            int protocolVersion, long sequence, boolean open, String familyId, String screenClass, String title,
            String selectedRootId, double scrollX, double scrollY,
            List<TabState> tabs, List<NodeState> nodes
    ) implements CustomPacketPayload {
        public static final Type<AdvancementsState> TYPE = new Type<>(id("observer_advancements_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, AdvancementsState> CODEC = StreamCodec.of(
                ObserverAdvancementsScreenPayloads::writeState, ObserverAdvancementsScreenPayloads::readState);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record AdvancementsRelay(
            UUID targetId, int protocolVersion, long sequence, boolean open, String familyId, String screenClass,
            String title, String selectedRootId, double scrollX, double scrollY,
            List<TabState> tabs, List<NodeState> nodes
    ) implements CustomPacketPayload {
        public static final Type<AdvancementsRelay> TYPE = new Type<>(id("observer_advancements_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, AdvancementsRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.screenClass(), value.title(), value.selectedRootId(), value.scrollX(), value.scrollY(),
                            value.tabs(), value.nodes());
                },
                buf -> relay(buf.readUUID(), readState(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static AdvancementsState closed(long sequence) {
        return new AdvancementsState(PROTOCOL_VERSION, sequence, false, FAMILY_ID, "", "", "", 0.0D, 0.0D,
                List.of(), List.of());
    }

    public static AdvancementsRelay relay(UUID targetId, AdvancementsState state) {
        return new AdvancementsRelay(targetId, state.protocolVersion(), state.sequence(), state.open(), state.familyId(),
                state.screenClass(), state.title(), state.selectedRootId(), state.scrollX(), state.scrollY(),
                state.tabs(), state.nodes());
    }

    private static void writeState(FriendlyByteBuf buf, AdvancementsState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.screenClass(),
                value.title(), value.selectedRootId(), value.scrollX(), value.scrollY(), value.tabs(), value.nodes());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String screenClass, String title, String selectedRootId,
                                    double scrollX, double scrollY, List<TabState> tabs, List<NodeState> nodes) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, 64);
        buf.writeUtf(screenClass, MAX_ID);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeUtf(selectedRootId, MAX_ID);
        buf.writeDouble(scrollX);
        buf.writeDouble(scrollY);

        int tabCount = Math.min(tabs.size(), MAX_TABS);
        buf.writeVarInt(tabCount);
        for (int i = 0; i < tabCount; i++) {
            TabState tab = tabs.get(i);
            buf.writeUtf(tab.rootId(), MAX_ID);
            buf.writeUtf(tab.title(), MAX_TEXT);
            buf.writeUtf(tab.iconItemId(), MAX_ID);
        }

        int nodeCount = Math.min(nodes.size(), MAX_NODES);
        buf.writeVarInt(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            NodeState node = nodes.get(i);
            buf.writeUtf(node.id(), MAX_ID);
            buf.writeUtf(node.rootId(), MAX_ID);
            buf.writeUtf(node.parentId(), MAX_ID);
            buf.writeUtf(node.title(), MAX_TEXT);
            buf.writeUtf(node.description(), MAX_TEXT);
            buf.writeUtf(node.iconItemId(), MAX_ID);
            buf.writeUtf(node.type(), 32);
            buf.writeFloat(node.x());
            buf.writeFloat(node.y());
            buf.writeFloat(node.progress());
            buf.writeBoolean(node.done());
            buf.writeBoolean(node.hidden());
        }
    }

    private static AdvancementsState readState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(64);
        String screenClass = buf.readUtf(MAX_ID);
        String title = buf.readUtf(MAX_TEXT);
        String selectedRootId = buf.readUtf(MAX_ID);
        double scrollX = buf.readDouble();
        double scrollY = buf.readDouble();

        int tabCount = boundedCount(buf.readVarInt(), MAX_TABS, "tab");
        List<TabState> tabs = new ArrayList<>(tabCount);
        for (int i = 0; i < tabCount; i++) {
            tabs.add(new TabState(buf.readUtf(MAX_ID), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_ID)));
        }

        int nodeCount = boundedCount(buf.readVarInt(), MAX_NODES, "node");
        List<NodeState> nodes = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            nodes.add(new NodeState(
                    buf.readUtf(MAX_ID), buf.readUtf(MAX_ID), buf.readUtf(MAX_ID),
                    buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_ID), buf.readUtf(32),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean()));
        }
        return new AdvancementsState(protocolVersion, sequence, open, familyId, screenClass, title, selectedRootId,
                scrollX, scrollY, List.copyOf(tabs), List.copyOf(nodes));
    }

    private static int boundedCount(int count, int max, String kind) {
        if (count < 0 || count > max) {
            throw new IllegalArgumentException("Observer advancements " + kind + " count out of range: " + count);
        }
        return count;
    }
}
