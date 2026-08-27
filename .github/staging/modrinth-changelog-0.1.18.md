## TotemVanillaTweaks 0.1.18 Beta

### Observer families

- Expanded Observer v4 / screen protocol v2 from the initial container and furnace support to 24 negotiated semantic family capabilities.
- Added vanilla semantic reconstruction for books, crafting, merchants, anvils, enchanting, brewing, smithing, stonecutting, grindstones, looms, cartography tables, beacons, signs, crafters, advancements and statistics.
- Repaired the player inventory sender and mirror with all 46 real menu slots, including crafting result/grid, armor and offhand, plus bounded active-effect and Recipe Book state.
- Rebuilt Loom and Stonecutter payloads from their visible production Screen/Menu data, including real pattern/recipe catalogues, scrolling, selection and result previews.
- Added optional semantic integrations for TotemRemnant backpacks, TotemAutomata Copper Golems, TotemNexus map/friends/registration and death-node administration, TotemLocksmith management, and TotemVillagers woodcutters.
- Added PauseScreen reconstruction from structured metadata while retaining metadata-only fallback for screens without a negotiated family.

### Privacy and rendering

- Observer transport remains framebuffer-free: no screenshots, framebuffer data or video frames are sent by production code.
- Unsent chat/command text and Automata editor credentials are excluded from Observer transport.
- Recipe Book relays expose only semantic search activity and never the player's search query.
- Corrected native-scale layout bounds and label overlap across furnace, crafting, brewing, smithing, grindstone, loom, beacon, Nexus map, Nexus death administration and statistics mirrors.

### Release gates

- Expanded capability, relay, loopback and three-JVM coverage for every registered semantic family.
- Added exhaustive gate-parity checks tying Client GameTests, E2E bridges, workflow markers and the expected native-scale PNG artifact count together.
- Added real production Screen/Menu extractor gates for every vanilla family and pinned cross-module runtime gates for Remnant, Automata, Nexus, Villagers and Locksmith integrations.
- Production Runtime now requires exactly 30 fresh native-scale GUI screenshots from the built distribution JAR.
- Kept Server GameTests, Client GameTests, three-JVM E2E, production-runtime validation and framebuffer-free source checks as release blockers.

Minecraft 26.2 · Fabric · Java 25 · requires Fabric API and TotemCore `>=0.7.0 <0.8.0`.
