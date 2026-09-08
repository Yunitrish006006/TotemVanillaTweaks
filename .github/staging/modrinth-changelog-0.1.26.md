## TotemVanillaTweaks 0.1.26

- Supports Nexus 0.3.17 Recovery Compass destination lists through the owning
  module's production Screen at Observer screen protocol 3, without a map canvas.
- Accepts the exact `recovery_compass` server relay variant while continuing to
  reject unknown variants, stale protocols and non-monotonic semantic updates.
- Verifies initial and later recovery snapshots, read-only input/packet
  suppression, remote cursor and close cleanup across a dedicated server and
  two clients; absent owning modules retain unsupported metadata.
- Updates the pinned Nexus integration bundle and CI screenshot/lifecycle gates.

Minecraft 26.2 · Fabric · Java 25 · requires Fabric API and TotemCore 0.7.18
(external dependency), compatible with the 0.7.x Core line. Nexus is optional;
install Nexus 0.3.17 to use Recovery Compass Observer views.
