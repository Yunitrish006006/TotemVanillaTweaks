# Observer View Roadmap

## Current architecture

Observer View has completed the migration to **protocol-native observation**. Production code no longer transports full-screen images or framebuffer captures between the Target and Observer clients.

The current protocol is **Observer protocol v3**. Session authority remains on the dedicated server, while Target and Observer clients exchange only versioned structured state through server relay and reconstruct the observed experience locally.

### Architecture invariants

- Do **not** capture the Target player's full framebuffer for transport.
- Do **not** encode or relay whole-screen PNG/JPEG/video frames.
- Do **not** reintroduce `FrameChunk`, `FrameRelay`, `CaptureControl`, `DynamicTexture` frame installation, or screenshot transport as a compatibility fallback.
- Require compatible protocol-native client capabilities before `/observeui` starts a session.
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
- Protocol v3 does not fall back to image transport when an older/incompatible client connects; the Observer session is rejected instead.

## Current validation contract

The Observer implementation is validated through:

1. source-level CI that fails if retired framebuffer transport identifiers return to `src/main`;
2. Server GameTests for authoritative session/protocol behavior;
3. Client GameTests for v3 lifecycle, local UI reconstruction and removed frame surfaces;
4. production-runtime Client GameTests using the distribution namespace;
5. a real three-JVM E2E with Dedicated Server + Target Client + Observer Client.

The three-JVM path exercises protocol-native world/HUD observation, supported container state, unsupported-screen metadata handling, Stop, and server/client cleanup without receiving a full-screen image frame from the Target.

## Remaining work

Framebuffer removal is no longer a migration task. Future Observer work should extend the structured protocol instead of adding image fallback. Priorities include:

- broaden native reconstruction coverage for additional vanilla and modded screen families;
- version screen-family capabilities so unsupported UI can be identified before a session reaches that screen;
- add more structured player/equipment/effect/event state where visual fidelity requires it;
- prefer deltas for high-frequency state where practical;
- harden reconnect, dimension-change and unusual screen-transition cases;
- keep bandwidth, allocation and packet-size regression coverage in CI.

## Success criterion

The original architectural success criterion has been reached for the current supported Observer surface: an Observer can reconstruct the tested world/HUD/container/screen experience **without receiving full-screen image frames from the Target client**.

Feature-completeness for every possible vanilla/modded GUI is a separate ongoing compatibility goal; unsupported screens must continue to degrade to structured metadata rather than framebuffer transport.
