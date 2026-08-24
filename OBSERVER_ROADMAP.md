# Observer View Roadmap

## Current architecture

Observer View has completed the migration to **protocol-native observation**. Production code no longer transports full-screen images or framebuffer captures between the Target and Observer clients.

The current gameplay protocol is **Observer protocol v4** and the semantic screen transport is **screen protocol v2**. Session authority remains on the dedicated server, while Target and Observer clients exchange only versioned structured state through server relay and reconstruct the observed experience locally.

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

## Current validation contract

The Observer implementation is validated through:

1. source-level CI that fails if retired framebuffer transport identifiers return to `src/main`;
2. Server GameTests for authoritative session/protocol behavior;
3. Client GameTests for v4 lifecycle, capability-mask negotiation, metadata-only fallback, local container/furnace UI reconstruction and removed frame surfaces;
4. production-runtime Client GameTests using the distribution namespace;
5. a real three-JVM E2E with Dedicated Server + Target Client + Observer Client.

The three-JVM path exercises protocol-native world/HUD observation, negotiated semantic screen state, unsupported-screen metadata handling, Stop, and server/client cleanup without receiving a full-screen image frame from the Target.

The integrated client/server loopback verifies that Target and Observer receive the negotiated `container_slots` and `furnace` capabilities, that the capability mask is cleared on Stop, and that the server removes per-Observer capability state. Separate Client GameTests verify capability-mask fallback behavior and local furnace rendering.

## Remaining work

Framebuffer removal and screen-family negotiation are no longer migration tasks. Future Observer work should extend the structured protocol instead of adding image fallback. Priorities include:

- add semantic adapters for more vanilla screen families such as crafting, anvil, enchanting, merchant, beacon, book and sign flows where their state cannot be represented faithfully by `container_slots` alone;
- define opt-in adapter IDs/capabilities for supported modded screen families without treating arbitrary modded Screen classes as trusted semantic layouts;
- add more structured player/equipment/effect/event state where visual fidelity requires it;
- prefer deltas for high-frequency state where practical;
- harden reconnect, dimension-change, multi-Observer capability-union changes and unusual screen-transition cases;
- keep bandwidth, allocation, capability-mask and packet-size regression coverage in CI.

## Success criterion

The original architectural success criterion has been reached for the current supported Observer surface: an Observer can reconstruct the tested world/HUD/container/furnace/screen experience **without receiving full-screen image frames from the Target client**.

Feature-completeness for every possible vanilla/modded GUI is a separate ongoing compatibility goal; unsupported or unnegotiated screens must continue to degrade to structured metadata rather than framebuffer transport.
