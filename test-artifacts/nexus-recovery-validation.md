# Nexus Recovery Compass Observer validation

Validated on 2026-09-08 with Minecraft 26.2, Java 25, Fabric Loader 0.19.3 and Fabric API 0.154.2+26.2.

## Production change

The owning Nexus provider reconstructs `recovery_compass` with its production Screen at screen protocol 3. The generic relay server now accepts this exact declared variant. Unknown variants and old/future protocols remain rejected; session, capability, monotonically increasing sequence and read-only constraints are unchanged.

The first dedicated run identified the missing server allowlist entry despite successful direct-provider integration. After fixing the allowlist, all six Nexus variants completed the dedicated relay. A subsequent fixture-only text correction uses the existing inactive rescue-bonus message for its ordinary lodestone target.

## Validation

| Check | Result |
| --- | --- |
| Unit tests | 87 passed, including recovery variant open/close, forged variant and protocol rejection |
| Dedicated Fabric server GameTests through `build` | All 17 required tests passed |
| E2E, integration and GameTest source compilation | Passed |
| Module-present integration client suite | Passed; recovery initial state, later payload, native bounds, remote cursor, no inventory mutation, local input/packet suppression, Escape close |
| Development client GameTests | All 26 registered client tests passed, including explicit absent-owner recovery_compass@3 metadata fallback |
| Production runtime client GameTests | All 26 registered client tests passed with built distribution artifacts; owning optional modules absent |
| `test build` | Passed after production allowlist fix |
| Dedicated server + Target JVM + Observer JVM | Six Nexus variants passed; recovery initial render acknowledged before later update, selection retained, cursor rendered, mouse/key mutation suppressed, target close cleared screen/cursor |

The 26-test client suites ran before the final server-only allowlist change. The changed allowlist was then covered by unit tests, final build/server tests and the full dedicated relay run. All provider and screen behavior was unchanged by that follow-up.

## Artifact identities

- `TotemVanillaTweaks/build/libs/totem-vanilla-tweaks-0.1.25.jar`: SHA256 `81f049621f5cdab083828bf49f70e70ea0d7dc55a68cba10c1c578c94be5f505`.
- `TotemNexus/build/libs/totem-nexus-0.3.16.jar`: SHA256 `a293e7d3ed50803c5eda8f3a1c157fe67dfb6a53b8af95f557de83f08c5f2d8e`.
- Companion runtime jars: Core 0.7.18, Remnant 0.2.21, Automata 0.1.24, Villagers 0.1.36, Locksmith 0.1.10. Every integration/E2E invocation pinned these actual jars through the corresponding `-Ptotem*Jar` properties. Remnant was built with `remapJar` to produce its distribution jar.

## Commands and retained evidence

From TotemVanillaTweaks, using `../TotemCore/gradlew`, Java 25 and the explicit jar properties above:

```text
test compileE2eJava compileIntegrationGametestJava compileGametestJava
runIntegrationClientGametest                 (Xvfb)
runClientGameTest runProductionClientGameTest (Xvfb; sequential)
test build
prepareE2eClientLaunchInputs                 (fresh recovery-rescue-launch directory)
runE2eServer                                (dedicated server)
```

The two E2E clients used the freshly generated `runE2eTarget.args`/`runE2eObserver.args` and `launch.cfg`, each in a separate Xvfb JVM with target/observer roles. The bounded launcher requires every six-variant marker and screenshot; it archives old results before launch.

Raw logs and rendered output are retained locally under `build/recovery-rescue-validation/`; dedicated marker results and all six screenshots are under `build/e2e/results/`. Runner scripts used in this workspace are `/tmp/run-recovery-relay-gradle.sh` and `/tmp/run-nexus-recovery-observer-e2e.sh`.

Tracked screenshots:

- [Owning production Screen, native 1280×720](screenshots/nexus-recovery/owner-present.png)
- [Absent owner, development runtime](screenshots/nexus-recovery/owner-absent-dev.png)
- [Absent owner, production runtime](screenshots/nexus-recovery/owner-absent-production.png)
- [Dedicated three-JVM recovery destination list](screenshots/nexus-recovery/dedicated-recovery-compass.png)

Visual inspection checks native widgets/font, bounded translated labels, list-only presentation, selected ordinary destination and the remote cursor. These local screenshots are validation artifacts; the production Observer transport contains semantic state only, with no framebuffer capture or transmission.
