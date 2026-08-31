## TotemVanillaTweaks 0.1.21

### Nexus Observer compatibility

- Updates the TotemNexus owned Observer provider contract to protocol v3 for TotemNexus 0.3.8.
- Relays the bounded filled-map MapId semantic state through the production Nexus Screen, including later snapshot updates.
- Adds module-present Observer coverage for filled maps and the compass, recovery compass, and book management-only interfaces.
- Keeps every Nexus projection read-only and uses the owning module's production Screen instead of a mirror or lookalike.

### Validation and privacy

- Keeps the complete Observer path framebuffer-free: no screenshots, framebuffer data, or video are transmitted.
- Retains input and packet suppression, exact family/variant/protocol validation, monotonic snapshot handling, remote cursor behavior, close lifecycle checks, and secret/private-input redaction.
- Revalidates unit/compile gates, module-present integration Client GameTests, dedicated three-JVM E2E, Production Runtime, screenshot evidence, owned-screen enforcement, and framebuffer-free/parity gates.

Minecraft 26.2 · Fabric · Java 25 · requires TotemCore 0.7.14 or newer within the 0.7.x compatibility line. TotemNexus integration targets 0.3.8.
