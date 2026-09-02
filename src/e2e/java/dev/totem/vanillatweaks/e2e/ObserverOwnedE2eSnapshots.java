package dev.totem.vanillatweaks.e2e;

import dev.totem.automata.network.CopperWrenchBindingsPayload;
import dev.totem.core.api.v1.client.observer.ObserverScreenSnapshot;
import dev.totem.locksmith.menu.LocksmithManagementOpenData;
import dev.totem.nexus.network.DeathNodeAdminPayload;
import dev.totem.nexus.network.SpaceUnitFriendsPayload;
import dev.totem.nexus.network.SpaceUnitMapPayload;
import dev.totem.nexus.network.SpaceUnitRegistrationPreviewPayload;
import dev.totem.nexus.space.TeleportInterfaceType;
import dev.totem.vanillatweaks.network.ObserverOwnedScreenPayloads;
import dev.totem.vanillatweaks.network.ObserverOwnedScreenProtocols;
import dev.totem.vanillatweaks.network.ObserverRemoteCursorPayloads;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Typed owner payload fixtures used by the real three-JVM generic relay. */
final class ObserverOwnedE2eSnapshots {
    static final int NEXUS_MAP_ID = 8801;
    static final UUID NEXUS_SOURCE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID NEXUS_TARGET_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");

    private ObserverOwnedE2eSnapshots() { }

    static ObserverOwnedScreenPayloads.State remnant(long sequence, int diamonds) {
        List<ItemStack> slots = emptySlots(122);
        slots.set(18, new ItemStack(Items.DIAMOND, diamonds));
        slots.set(108, new ItemStack(Items.NETHER_STAR));
        return open("remnant_backpack", "", sequence, "Netherite Backpack", slots,
                new int[]{8, 4}, Map.of(), new byte[0]);
    }

    static ObserverOwnedScreenPayloads.State automata(long sequence, int revision) {
        UUID golem = UUID.fromString("42000000-0000-0000-0000-000000000042");
        var binding = new CopperWrenchBindingsPayload.BindingEntry("minecraft:overworld", 10, 64, -5,
                "minecraft:chest", "minecraft:chest", true, true, true, "", 3, 1,
                List.of("minecraft:iron_ingot"), List.of("minecraft:dirt"), List.of("c:ingots"), List.of());
        var payload = new CopperWrenchBindingsPayload(golem, revision, true, "sorting", "searching",
                "minecraft:coal", 8, 1200, false, "minecraft:iron_pickaxe", 1, 12, 250,
                "minecraft:chest", 1, "", "", "", 0, binding, null, List.of(), false,
                "", 0, 0, List.of(), List.of(), List.of(), List.of(), List.of(binding));
        return open("automata_copper_golem", "", sequence, "Copper Golem", List.of(), new int[0],
                Map.of("golem_id", golem.toString()), encode(CopperWrenchBindingsPayload.CODEC, payload));
    }

    static ObserverOwnedScreenPayloads.State nexusCompass(long sequence, String name) {
        var payload = new SpaceUnitMapPayload(NEXUS_SOURCE_ID,
                "lodestone", name, "minecraft:overworld", 10, 64, 10,
                TeleportInterfaceType.COMPASS, SpaceUnitMapPayload.NO_MAP_ID,
                nexusEntries("message.deadrecall.space_unit.interface_bonus.compass"));
        return open("nexus", "compass", sequence, "Nexus Compass", List.of(), new int[0],
                Map.of("selected_unit_id", NEXUS_TARGET_ID.toString()),
                encode(SpaceUnitMapPayload.CODEC, payload));
    }

    static ObserverOwnedScreenPayloads.State nexusMap(long sequence, String name) {
        var payload = new SpaceUnitMapPayload(NEXUS_SOURCE_ID,
                "local", name, "minecraft:overworld", 10, 64, 10,
                TeleportInterfaceType.FILLED_MAP, NEXUS_MAP_ID,
                nexusEntries("message.deadrecall.space_unit.interface_bonus.filled_map.active"));
        return open("nexus", "map", sequence, "Nexus Map", List.of(), new int[0],
                Map.of(
                        "selected_unit_id", NEXUS_TARGET_ID.toString(),
                        "map_zoom", "2",
                        "map_pan_x", "0",
                        "map_pan_y", "-24"),
                encode(SpaceUnitMapPayload.CODEC, payload));
    }

    static ObserverOwnedScreenPayloads.State nexusManagement(long sequence, String name) {
        var payload = new SpaceUnitMapPayload(NEXUS_SOURCE_ID,
                "lodestone", name, "minecraft:overworld", 10, 64, 10,
                TeleportInterfaceType.BOOK, SpaceUnitMapPayload.NO_MAP_ID,
                List.of(nexusEntry(NEXUS_SOURCE_ID, name, 10, 10,
                        "message.deadrecall.space_unit.interface_bonus.book.active", false)));
        return open("nexus", "management", sequence, "Nexus Management", List.of(), new int[0], Map.of(),
                encode(SpaceUnitMapPayload.CODEC, payload));
    }

    private static List<SpaceUnitMapPayload.Entry> nexusEntries(String bonusMessage) {
        return List.of(
                nexusEntry(NEXUS_SOURCE_ID, "Home Nexus", 10, 10, bonusMessage, false),
                nexusEntry(NEXUS_TARGET_ID, "Mountain Relay", 34, -12, bonusMessage, true));
    }

    private static SpaceUnitMapPayload.Entry nexusEntry(
            UUID id, String name, int x, int z, String bonusMessage, boolean canTeleport) {
        return new SpaceUnitMapPayload.Entry(
                id, "lodestone", name, "private", false, "minecraft:overworld", x, 64, z,
                0.92D, 2, canTeleport ? 32 : 0,
                0, 0, 0, 0, 0, 20,
                0, 0,
                20, 16,
                4, 3,
                0,
                0, 0,
                true, bonusMessage,
                false, true, true, 1, 2, canTeleport,
                canTeleport ? "" : "message.deadrecall.space_unit.teleport_blocked.same_source");
    }

    static ObserverOwnedScreenPayloads.State nexusFriends(long sequence, String name) {
        var payload = new SpaceUnitFriendsPayload(List.of(new SpaceUnitFriendsPayload.Entry(
                UUID.fromString("10000000-0000-0000-0000-000000000002"), name, true, "friend")));
        return open("nexus", "friends", sequence, "Nexus Friends", List.of(), new int[0], Map.of(),
                encode(SpaceUnitFriendsPayload.CODEC, payload));
    }

    static ObserverOwnedScreenPayloads.State nexusRegistration(long sequence, int tier) {
        var payload = new SpaceUnitRegistrationPreviewPayload("minecraft:overworld", 20, 70, -8,
                tier, 84, 92, 7, 20);
        return open("nexus", "registration", sequence, "Nexus Registration", List.of(), new int[0], Map.of(),
                encode(SpaceUnitRegistrationPreviewPayload.CODEC, payload));
    }

    static ObserverOwnedScreenPayloads.State deathAdmin(long sequence, int totalEntries) {
        UUID node = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID owner = UUID.fromString("20000000-0000-0000-0000-000000000002");
        var payload = new DeathNodeAdminPayload(List.of(new DeathNodeAdminPayload.Entry(node, owner, "Steve",
                "Death Node", "active", "minecraft:overworld", 4, 64, 8, 10L, 20L,
                List.of("duplicate_active_location"))),
                true, 1, 20, totalEntries, 20L, true, node,
                UUID.fromString("20000000-0000-0000-0000-000000000003"), "purge", Long.MAX_VALUE);
        return open("nexus_death_node_admin", "", sequence, "Death Node Administration", List.of(),
                new int[0], Map.of(), encode(DeathNodeAdminPayload.CODEC, payload));
    }

    static ObserverOwnedScreenPayloads.State villagers(long sequence, int requiredInput) {
        List<ItemStack> slots = emptySlots(38);
        slots.set(0, new ItemStack(Items.OAK_LOG, requiredInput));
        return open("villagers_woodcutter", "", sequence, "Woodcutter", slots,
                new int[]{0, 3, requiredInput}, Map.of(), new byte[0]);
    }

    static ObserverOwnedScreenPayloads.State locksmith(long sequence, long revision) {
        UUID lock = UUID.fromString("44444444-4444-4444-4444-444444444444");
        var data = new LocksmithManagementOpenData(lock, revision, "Sky", true, true, true, 1, 1, 6, 2,
                List.of(new LocksmithManagementOpenData.MemberView(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "Alex", 0)),
                List.of(new LocksmithManagementOpenData.KeyView(
                        UUID.fromString("11111111-2222-3333-4444-555555555555"), "Vault Key")),
                List.of(new LocksmithManagementOpenData.PlayerView(
                        UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"), "Builder")));
        return open("locksmith_management", "", sequence, "Locksmith Management", List.of(), new int[0],
                Map.of("lock_id", lock.toString()), encodeRegistry(LocksmithManagementOpenData.STREAM_CODEC, data));
    }

    static ObserverOwnedScreenPayloads.State close(String family, String variant, long sequence) {
        return new ObserverOwnedScreenPayloads.State(false, ObserverOwnedScreenPayloads.closed(
                family, variant, ObserverOwnedScreenProtocols.expected(family), sequence));
    }

    static ObserverRemoteCursorPayloads.State cursor(String family, String variant, long sequence) {
        return new ObserverRemoteCursorPayloads.State(ObserverRemoteCursorPayloads.PROTOCOL_VERSION, sequence,
                family, variant, ObserverOwnedScreenProtocols.expected(family), 88, 83, 176, 166, ItemStack.EMPTY);
    }

    static ObserverRemoteCursorPayloads.State namedCursor(String family, String variant, long sequence) {
        ItemStack carried = new ItemStack(Items.DIAMOND, 5);
        carried.set(DataComponents.CUSTOM_NAME, Component.literal("Remote Cursor Diamond"));
        return new ObserverRemoteCursorPayloads.State(ObserverRemoteCursorPayloads.PROTOCOL_VERSION, sequence,
                family, variant, ObserverOwnedScreenProtocols.expected(family), 88, 83, 176, 166, carried);
    }

    private static ObserverOwnedScreenPayloads.State open(String family, String variant, long sequence, String title,
                                                           List<ItemStack> slots, int[] data, Map<String, String> metadata,
                                                           byte[] ownerPayload) {
        return new ObserverOwnedScreenPayloads.State(true, new ObserverScreenSnapshot(family, variant,
                ObserverOwnedScreenProtocols.expected(family), sequence,
                Component.literal(title), slots, data, metadata, ownerPayload));
    }

    private static List<ItemStack> emptySlots(int size) {
        List<ItemStack> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(ItemStack.EMPTY);
        return result;
    }

    private static <T> byte[] encode(StreamCodec<FriendlyByteBuf, T> codec, T value) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, value);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static <T> byte[] encodeRegistry(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) throw new IllegalStateException("Client registry access is unavailable");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), minecraft.level.registryAccess());
        try {
            codec.encode(buffer, value);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }
}
