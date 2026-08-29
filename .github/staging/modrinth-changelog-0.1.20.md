## TotemVanillaTweaks 0.1.20

- Reconstructs vanilla Observer families with the matching Mojang Screen and Menu implementations.
- Delegates TotemRemnant, TotemAutomata, TotemNexus, TotemVillagers, and TotemLocksmith screens to their owning modules through TotemCore 0.7.12.
- Keeps all Observer rendering framebuffer-free and blocks observer-side input, menu mutation, and packets.
- Relays bounded semantic snapshots, carried stacks, and remote cursor state with strict family, protocol, variant, and sequence validation.
- Retains redaction for unsent chat, commands, rename text, book drafts, sign text, and comparable private input.
- Includes unit, loopback, Client GameTest, owner-present integration, three-JVM E2E, and Production Runtime release gates.

Minecraft 26.2 · Fabric · Java 25 · requires TotemCore 0.7.12 or newer within the 0.7.x compatibility line.
