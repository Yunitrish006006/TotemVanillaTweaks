# Observer View Roadmap

## Current architecture

Observer View has completed the migration to **protocol-native observation**. Production code no longer transports full-screen images or framebuffer captures between the Target and Observer clients.

The current gameplay protocol is **Observer protocol v4** and the semantic screen transport is **screen protocol v2**. Session authority remains on the dedicated server, while Target and Observer clients exchange only versioned structured state through server relay and reconstruct the observed experience locally.

The current four-way screen classification is recorded in
[`OBSERVER_SCREEN_GAP_AUDIT.md`](OBSERVER_SCREEN_GAP_AUDIT.md).

### Architecture invariants

- Do **not** capture the Target player's full framebuffer for transport.
- Do **not** encode or relay whole-screen PNG/JPEG/video frames.
- Do **not** reintroduce `FrameChunk`, `FrameRelay`, `CaptureControl`, `DynamicTexture` frame installation, or screenshot transport as a compatibility fallback.
- Require compatible protocol-native client capabilities before `/observeui` starts a session.
- Negotiate semantic screen families independently so one unsupported GUI family does not invalidate the whole gameplay/HUD Observer session.
- Render world state through Minecraft-native spectator/camera behavior and reconstruct HUD/UI state from structured protocol data.
- Keep session authorization, lifecycle, cleanup and capability negotiation independent from any renderer-specific representation.

CI enforces the no-frame invariant by rejecting production source that contains the retired framebuffer transport surfaces.

## Completed migration

### Phase 1 — structured state side channel — complete

- Introduced versioned Observer payloads and capability negotiation.
- Relayed Target camera/HUD state as structured values.
- Bound structured state to the server-authoritative `/observeui` lifecycle.

### Phase 2 — native gameplay rendering — complete

- Uses the server-authoritative spectator camera relationship for world/chunk/entity rendering.
- Removed gameplay PNG transport.
- Reconstructs Target HUD state locally from protocol data.

### Phase 3 — structured GUI/container replication — complete for the supported surface

- Added versioned structured screen and container relay.
- Reconstructs supported container/screen state locally on the Observer client.
- Unsupported screen classes are represented by a local metadata placeholder rather than a screenshot fallback.
- Screen lifecycle is session-bound and cleaned up on close/stop/disconnect.

### Phase 4 — framebuffer transport removal — complete

- Removed production framebuffer capture and screenshot encoding.
- Removed frame chunking/reassembly and frame relay payloads.
- Removed frame textures and `DynamicTexture` installation.
- Removed capture-control/frame-rate state and legacy frame counters.
- Removed the old framebuffer-only E2E mixin and PNG evidence path.
- Protocol-native observation does not fall back to image transport when an older/incompatible client connects; the Observer session is rejected instead.

### Phase 5 — semantic screen-family capability negotiation — complete

- Upgraded the gameplay/session protocol to v4 and semantic screen transport to v2.
- Added stable semantic screen-family identifiers and a versioned capability mask.
- Added `container_slots` as the first negotiated semantic family.
- `NativeControl` tells the Target which semantic screen families are needed by active Observers.
- `NativeSession` tells each Observer which semantic screen families were negotiated for that session.
- The server stores capabilities per Observer, sends each Target the union required by all active Observers, and filters semantic relay per Observer.
- Semantic container support is no longer mandatory for the whole Observer session.
- Unsupported or unnegotiated screen families degrade to metadata-only local placeholders and never fall back to framebuffer transport.

### Phase 6 — furnace semantic family — complete

- Added a dedicated stable `furnace` family and capability bit alongside `container_slots`.
- Added versioned furnace state/relay payloads for slots, cook progress, fuel progress and lit state.
- Target clients prefer the dedicated furnace adapter for furnace-family screens when negotiated, while retaining `container_slots` as a lower-fidelity semantic fallback.
- Server validation and per-Observer capability filtering apply independently to furnace relay.
- Observer clients reconstruct furnace-family UI locally, including semantic progress indicators, without receiving Target pixels.
- Client GameTests render furnace semantic state locally and persist screenshot evidence.

### Phase 7 — expanded semantic coverage — active compatibility audit

- The 24 pre-existing semantic families occupy bits 0 through 23: `container_slots`, `furnace`,
  `book`, `crafting`, `merchant`, `anvil`, `enchanting`, `remnant_backpack`,
  `automata_copper_golem`, `nexus`, `villagers_woodcutter`, `brewing`, `smithing`,
  `stonecutter`, `grindstone`, `loom`, `cartography`, `beacon`, `sign`, `crafter`,
  `nexus_death_node_admin`, `locksmith_management`, `advancements` and `stats`.
- `nexus` covers its map, friends and registration variants; the death-node administrator is a
  separate capability with independent validation and relay filtering.
- PauseScreen is reconstructed from structured screen metadata without allocating another
  capability bit.
- Dedicated family senders suppress lower-priority generic container or metadata output, and
  lifecycle sequences are cleared with the Observer session.
- Layout-focused unit tests and native-scale Client GameTest screenshots cover the semantic
  views, while each registered relay is represented in the three-JVM E2E manifest.
- Screen metadata never includes a player's unsent ChatScreen message or command text.
- Native anvil, writable-book/signing and sign-editor reconstruction keeps
  unsent rename/page/title/line drafts blank and proves that with sentinel
  production-extractor coverage.
- The remote cursor uses bit 24. `horse_inventory` uses bit 25 and reconstructs the genuine
  `HorseInventoryScreen`; the next capability starts at bit 26.

Recipe Book state is carried inside the `crafting` family rather than consuming another capability
bit. It includes visibility, narrow-width mode, filtering, whether a search is active, selected tab
and page state, but never the user's search text. Death, sleeping, Chat and Social Interactions use
explicit metadata-only privacy classifications where no dedicated semantic family is warranted.
Discord configuration plus Nexus rename/access dialogs are also metadata-only and redact their
titles; URL, key, token, credential and draft fields are never inspected or relayed.

## Current validation contract

The Observer implementation is validated through:

1. source-level CI that fails if retired framebuffer transport identifiers return to `src/main`;
2. Server GameTests for authoritative session/protocol behavior;
3. Client GameTests for v4 lifecycle, capability-mask negotiation, metadata-only fallback, local semantic UI reconstruction and removed frame surfaces;
4. production-runtime Client GameTests using the distribution namespace;
5. a real three-JVM E2E with Dedicated Server + Target Client + Observer Client.

The three-JVM path exercises protocol-native world/HUD observation, every registered semantic relay,
unsupported-screen metadata handling, Stop, and server/client cleanup without receiving a
full-screen image frame from the Target.

The integrated client/server loopback verifies the complete registered capability mask, that the
mask is cleared on Stop, and that the server removes per-Observer capability state. The gate-parity
script keeps Client GameTest entrypoints, three-JVM bridges, workflow marker assertions and
production screenshot counts in lockstep.

## Remaining work

Framebuffer removal and screen-family negotiation are no longer migration tasks. Future Observer work should extend the structured protocol instead of adding image fallback. Priorities include:

- define opt-in adapter IDs/capabilities for supported modded screen families without treating arbitrary modded Screen classes as trusted semantic layouts;
- keep an explicit classification for vanilla screens without a dedicated adapter, adding a family only when generic container or metadata reconstruction is insufficient;
- add more structured player/equipment/effect/event state where visual fidelity requires it;
- prefer deltas for high-frequency state where practical;
- harden reconnect, dimension-change, multi-Observer capability-union changes and unusual screen-transition cases;
- keep bandwidth, allocation, capability-mask and packet-size regression coverage in CI.

## Success criterion

The framebuffer-free architecture is established for the tested surface: world/HUD, 25 negotiated
semantic families, remote cursor, and intentional metadata-only states are covered without receiving
full-screen image frames from the Target client. GUI coverage remains an explicit gap audit rather
than a claim that every vanilla or modded screen is complete. Unsupported or unnegotiated screens
must remain classified metadata and must never gain a pixel-stream fallback.
