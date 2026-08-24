## TotemVanillaTweaks 0.1.16 Beta

### Critical runtime hotfix

- Fixed a Minecraft 26.2 production-runtime crash when a skeleton was loaded or spawned.
- The 0.1.15 distribution could contain a mixed namespace method descriptor (`DifficultyInstance.getDifficulty()` returning an intermediary `class_1267` type), causing a `NoSuchMethodError` during finite skeleton ammunition initialization.
- Aligned TotemVanillaTweaks with the existing TotemCore 26.2 build policy by keeping Modern Yarn only as a development naming layer and disabling distribution remapping with `fabric.loom.dontRemap=true`.

### Verification hardening

- Added a production-runtime Client GameTest gate using Loom's production run task.
- The new regression test boots a real Minecraft 26.2 singleplayer/integrated server in the distribution namespace, summons a skeleton through the server command path, waits for the `ServerEntityEvents.ENTITY_LOAD` ammunition initialization callback, then kills the skeleton.
- The production test uses an isolated Gradle dependency cache so named-development Fabric API tweaker normalization cannot contaminate the official production namespace.
- Existing Server GameTests, Client GameTests, and the Dedicated Server + Target Client + Observer Client three-JVM Observer E2E remain release gates.

### Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3+
- Fabric API 0.154.2+26.2 or newer compatible 26.2 build
- Java 25+
- TotemCore >=0.7.0 <0.8.0 (0.7.11 recommended)
