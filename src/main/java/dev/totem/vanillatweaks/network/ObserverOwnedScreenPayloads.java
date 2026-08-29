package dev.totem.vanillatweaks.network;

import dev.totem.core.api.v1.client.observer.ObserverScreenSnapshot;
import dev.totem.vanillatweaks.TotemVanillaTweaks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Generic framebuffer-free transport for module-owned semantic Screens. */
public final class ObserverOwnedScreenPayloads {
    public static final int PROTOCOL_VERSION = 1;
    private static final int MAX_TEXT = 2048;
    private static final int MAX_FAMILY = 64;
    private static final int MAX_VARIANT = 64;
    private static final int MAX_STACK_BYTES = 16 * 1024;
    private static final int MAX_TOTAL_STACK_BYTES = 256 * 1024;
    public static final int MAX_PROVIDERS = 16;

    private ObserverOwnedScreenPayloads() { }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TotemVanillaTweaks.MOD_ID, path);
    }

    public record State(boolean open, ObserverScreenSnapshot snapshot) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(id("observer_owned_screen_state_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeBoolean(value.open()); writeSnapshot(buf, value.snapshot()); },
                buf -> new State(buf.readBoolean(), readSnapshot(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Relay(UUID targetId, boolean open, ObserverScreenSnapshot snapshot) implements CustomPacketPayload {
        public static final Type<Relay> TYPE = new Type<>(id("observer_owned_screen_relay_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Relay> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeUUID(value.targetId()); buf.writeBoolean(value.open()); writeSnapshot(buf, value.snapshot()); },
                buf -> new Relay(buf.readUUID(), buf.readBoolean(), readSnapshot(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Observer client declaration of the owner-screen providers it actually loaded. */
    public record ProviderSet(int protocolVersion, List<ProviderIdentity> providers) implements CustomPacketPayload {
        public static final Type<ProviderSet> TYPE = new Type<>(id("observer_owned_provider_set_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ProviderSet> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeVarInt(value.protocolVersion());
                    if (value.providers().size() > MAX_PROVIDERS) {
                        throw new IllegalArgumentException("Too many Observer owner-screen providers");
                    }
                    buf.writeVarInt(value.providers().size());
                    for (ProviderIdentity provider : value.providers()) {
                        buf.writeUtf(provider.familyId(), MAX_FAMILY);
                        buf.writeVarInt(provider.protocolVersion());
                    }
                },
                buf -> {
                    int protocol = buf.readVarInt();
                    int count = bounded(buf.readVarInt(), MAX_PROVIDERS, "provider");
                    List<ProviderIdentity> providers = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        providers.add(new ProviderIdentity(buf.readUtf(MAX_FAMILY), buf.readVarInt()));
                    }
                    return new ProviderSet(protocol, List.copyOf(providers));
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ProviderIdentity(String familyId, int protocolVersion) { }

    public static ObserverScreenSnapshot closed(String family, String variant, int screenProtocol, long sequence) {
        return new ObserverScreenSnapshot(family, variant, screenProtocol, sequence, Component.empty(),
                List.of(), new int[0], Map.of(), new byte[0]);
    }

    private static void writeSnapshot(RegistryFriendlyByteBuf buf, ObserverScreenSnapshot snapshot) {
        buf.writeUtf(snapshot.familyId(), MAX_FAMILY);
        buf.writeUtf(snapshot.variant(), MAX_VARIANT);
        buf.writeVarInt(snapshot.protocolVersion());
        buf.writeVarLong(snapshot.sequence());
        buf.writeUtf(snapshot.title().getString(), MAX_TEXT);
        var remoteSlots = snapshot.slots();
        buf.writeVarInt(remoteSlots.size());
        int totalStackBytes = 0;
        for (ItemStack stack : remoteSlots) {
            RegistryFriendlyByteBuf encoded = new RegistryFriendlyByteBuf(Unpooled.buffer(), buf.registryAccess());
            try {
                ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(encoded, stack);
                int size = encoded.readableBytes();
                if (size > MAX_STACK_BYTES || (totalStackBytes += size) > MAX_TOTAL_STACK_BYTES)
                    throw new IllegalArgumentException("Observer ItemStack component payload is too large");
                byte[] bytes = new byte[size]; encoded.getBytes(encoded.readerIndex(), bytes); buf.writeByteArray(bytes);
            } finally { encoded.release(); }
        }
        int[] data = snapshot.data();
        buf.writeVarInt(data.length); for (int value : data) buf.writeVarInt(value);
        buf.writeVarInt(snapshot.metadata().size());
        snapshot.metadata().forEach((key, value) -> { buf.writeUtf(key, 128); buf.writeUtf(value, MAX_TEXT); });
        buf.writeByteArray(snapshot.ownerPayload());
    }

    private static ObserverScreenSnapshot readSnapshot(RegistryFriendlyByteBuf buf) {
        String family = buf.readUtf(MAX_FAMILY), variant = buf.readUtf(MAX_VARIANT);
        int protocol = buf.readVarInt(); long sequence = buf.readVarLong();
        Component title = Component.literal(buf.readUtf(MAX_TEXT));
        int slotCount = bounded(buf.readVarInt(), ObserverScreenSnapshot.MAX_SLOTS, "slot");
        List<ItemStack> slots = new ArrayList<>(slotCount);
        int totalStackBytes = 0;
        for (int i = 0; i < slotCount; i++) {
            byte[] bytes = buf.readByteArray(MAX_STACK_BYTES);
            if ((totalStackBytes += bytes.length) > MAX_TOTAL_STACK_BYTES)
                throw new IllegalArgumentException("Observer ItemStack payload total is too large");
            RegistryFriendlyByteBuf encoded = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), buf.registryAccess());
            try {
                ItemStack stack = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(encoded);
                if (encoded.readableBytes() != 0) throw new IllegalArgumentException("Trailing Observer ItemStack bytes");
                slots.add(stack);
            } finally { encoded.release(); }
        }
        int dataCount = bounded(buf.readVarInt(), ObserverScreenSnapshot.MAX_DATA, "data");
        int[] data = new int[dataCount]; for (int i = 0; i < dataCount; i++) data[i] = buf.readVarInt();
        int metadataCount = bounded(buf.readVarInt(), ObserverScreenSnapshot.MAX_METADATA, "metadata");
        Map<String, String> metadata = new LinkedHashMap<>();
        for (int i = 0; i < metadataCount; i++) {
            if (metadata.put(buf.readUtf(128), buf.readUtf(MAX_TEXT)) != null)
                throw new IllegalArgumentException("Duplicate Observer metadata key");
        }
        byte[] ownerPayload = buf.readByteArray(ObserverScreenSnapshot.MAX_OWNER_PAYLOAD);
        return new ObserverScreenSnapshot(family, variant, protocol, sequence, title, slots, data, metadata, ownerPayload);
    }

    private static int bounded(int value, int max, String kind) {
        if (value < 0 || value > max) throw new IllegalArgumentException("Invalid Observer " + kind + " count");
        return value;
    }
}
