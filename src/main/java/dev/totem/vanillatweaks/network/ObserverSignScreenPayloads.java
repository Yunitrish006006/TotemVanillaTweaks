package dev.totem.vanillatweaks.network;

import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Sign editor semantic transport for standing/wall and hanging signs. */
public final class ObserverSignScreenPayloads {
    public static final int PROTOCOL_VERSION = 1;
    public static final long CAPABILITY = 1L << 18;
    public static final String FAMILY_ID = "sign";
    public static final String SIGN_SCREEN_CLASS = "net.minecraft.client.gui.screens.inventory.SignEditScreen";
    public static final String HANGING_SIGN_SCREEN_CLASS = "net.minecraft.client.gui.screens.inventory.HangingSignEditScreen";
    private static final int MAX_TEXT = 384;
    public static final int LINE_COUNT = 4;

    private ObserverSignScreenPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record SignState(int protocolVersion, long sequence, boolean open, String familyId,
                            String screenClass, String title, String variant, boolean frontText,
                            int currentLine, String color, boolean glowing, List<String> lines)
            implements CustomPacketPayload {
        public static final Type<SignState> TYPE = new Type<>(id("observer_sign_state_v1"));
        public static final StreamCodec<FriendlyByteBuf, SignState> CODEC = StreamCodec.of(
                ObserverSignScreenPayloads::writeState, ObserverSignScreenPayloads::readState);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SignRelay(UUID targetId, int protocolVersion, long sequence, boolean open, String familyId,
                            String screenClass, String title, String variant, boolean frontText,
                            int currentLine, String color, boolean glowing, List<String> lines)
            implements CustomPacketPayload {
        public static final Type<SignRelay> TYPE = new Type<>(id("observer_sign_relay_v1"));
        public static final StreamCodec<FriendlyByteBuf, SignRelay> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.targetId());
                    writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(),
                            value.screenClass(), value.title(), value.variant(), value.frontText(), value.currentLine(),
                            value.color(), value.glowing(), value.lines());
                },
                buf -> relay(buf.readUUID(), readState(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static SignState closed(long sequence) {
        return new SignState(PROTOCOL_VERSION, sequence, false, FAMILY_ID, "", "", "", true,
                0, "", false, List.of());
    }

    public static SignRelay relay(UUID targetId, SignState state) {
        return new SignRelay(targetId, state.protocolVersion(), state.sequence(), state.open(), state.familyId(),
                state.screenClass(), state.title(), state.variant(), state.frontText(), state.currentLine(),
                state.color(), state.glowing(), state.lines());
    }

    private static void writeState(FriendlyByteBuf buf, SignState value) {
        writeFields(buf, value.protocolVersion(), value.sequence(), value.open(), value.familyId(), value.screenClass(),
                value.title(), value.variant(), value.frontText(), value.currentLine(), value.color(), value.glowing(),
                value.lines());
    }

    private static void writeFields(FriendlyByteBuf buf, int protocolVersion, long sequence, boolean open,
                                    String familyId, String screenClass, String title, String variant,
                                    boolean frontText, int currentLine, String color, boolean glowing,
                                    List<String> lines) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(sequence);
        buf.writeBoolean(open);
        buf.writeUtf(familyId, MAX_TEXT);
        buf.writeUtf(screenClass, MAX_TEXT);
        buf.writeUtf(title, MAX_TEXT);
        buf.writeUtf(variant, MAX_TEXT);
        buf.writeBoolean(frontText);
        buf.writeVarInt(currentLine);
        buf.writeUtf(color, MAX_TEXT);
        buf.writeBoolean(glowing);
        int count = Math.min(lines.size(), LINE_COUNT);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) buf.writeUtf(lines.get(i), MAX_TEXT);
    }

    private static SignState readState(FriendlyByteBuf buf) {
        int protocolVersion = buf.readVarInt();
        long sequence = buf.readLong();
        boolean open = buf.readBoolean();
        String familyId = buf.readUtf(MAX_TEXT);
        String screenClass = buf.readUtf(MAX_TEXT);
        String title = buf.readUtf(MAX_TEXT);
        String variant = buf.readUtf(MAX_TEXT);
        boolean frontText = buf.readBoolean();
        int currentLine = buf.readVarInt();
        String color = buf.readUtf(MAX_TEXT);
        boolean glowing = buf.readBoolean();
        int count = buf.readVarInt();
        if (count < 0 || count > LINE_COUNT) throw new IllegalArgumentException("Observer sign line count out of range: " + count);
        List<String> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) lines.add(buf.readUtf(MAX_TEXT));
        return new SignState(protocolVersion, sequence, open, familyId, screenClass, title, variant, frontText,
                currentLine, color, glowing, List.copyOf(lines));
    }
}
