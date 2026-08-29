# Observer Screen Gap Audit — Minecraft 26.2

This audit is limited to player-facing survival screens and production Screens
owned by Totem repositories. Every category remains framebuffer-free.

## 1. Dedicated semantic adapter

- Vanilla: container slots, furnace/blast furnace/smoker, books/lectern,
  player inventory/crafting table, merchant, anvil, enchanting, brewing,
  smithing, stonecutter, grindstone, loom, cartography, beacon, sign/hanging
  sign, crafter, advancements, statistics, horse/mount inventory, and pause.
- Totem owners: Remnant backpack, Automata copper golem, Nexus map/friends/
  registration, Nexus death-node administration, Villagers woodcutter, and
  Locksmith management.
- Each vanilla family instantiates its matching Mojang Screen/Menu. Each Totem
  family is created by the owning module through the TotemCore provider API.

## 2. Generic container is sufficient

- Exact allowlist only: `ContainerScreen`, `HopperScreen`, `DispenserScreen`,
  and `ShulkerBoxScreen`.
- Subclasses are not implicitly trusted. A new class must be audited before it
  is added to the allowlist or receives a dedicated semantic family.

## 3. Metadata-only by design

- `DeathScreen`: death details remain on the target.
- `ChatScreen`, `InBedChatScreen`, `SocialInteractionsScreen`: unsent/private
  text is never read or relayed.
- `DiscordConfigScreen`: URL, API key, channel drafts/configuration and all
  credentials are never read or relayed; title metadata is redacted.
- Nexus rename and access dialogs: name/player drafts are never read or
  relayed; title metadata is redacted.
- Native anvil rename fields, writable-book/signing fields and sign-editor
  lines stay on the target client. Their matching Mojang Screens are still
  reconstructed, but all unsent draft fields are blank in Observer state.

## 4. Semantic adapter pending

- No common survival screen found in the Minecraft 26.2 priority audit remains
  in this category.
- Unknown third-party Screens and future Minecraft/Totem Screens stay explicit
  pending metadata until audited. They never inherit generic slot handling from
  a superclass and never fall back to pixels.

## Change rule

Any new or changed production Screen must update this audit, the structured
policy allowlist, capability/sequence cleanup, Client GameTest evidence,
three-JVM E2E evidence, Production Runtime coverage, and screenshot counts in
the same change.
