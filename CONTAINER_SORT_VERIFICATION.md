# Container sort verification — 2026-09-09

The recovered fix restricts container sorting to exact vanilla storage menu
classes and player sorting to explicit vanilla menu classes. It validates menu
access and every destination before writing. Custom menus, including subclasses
of vanilla menus, reject both targets so an open item-backed inventory cannot be
detached from its tracked stack. Functional container slots remain outside the
generic sorter.

## Verification evidence

Existing successful runs were recovered from
`/tmp/totem-gui-diagnosis/vanilla-build.log` and
`/tmp/totem-gui-diagnosis/vanilla-gametest.log`. The first log records successful
`assemble`, `test`, and `compileGametestJava` tasks; the second records successful
`runGameTest` and all **24 required GameTests passing**. JUnit XML under
`build/test-results/test` records **35 suites, 87 tests, zero failures, errors,
or skipped tests**. Java: `/home/thomas/.local/temurin-25`.

The complete build lifecycle was then verified with:

```sh
env JAVA_HOME=/home/thomas/.local/temurin-25 ./gradlew \
  --gradle-user-home /tmp/totem-gui-fix-gradle \
  --project-cache-dir /tmp/totem-gui-fix-vanilla-cache build --offline
```

`/tmp/totem-gui-diagnosis/vanilla-final-build.log` records `:check`, `:build`,
and **BUILD SUCCESSFUL in 3m 23s**, exit code 0. This build automatically runs
`runGameTest`; all **24 required tests passed again** at 01:53:19 +08:00 and
the server finished saving/shutting down at 01:55:39. Unit tests and compiled
sources/artifacts were `UP-TO-DATE`; the artifact hash below remained unchanged.

The nine container-sort GameTests cover chest and other supported storage
families, player inventory isolation, item counts/components, crafting rejection
followed by ordinary ingredient consumption, functional-slot rejection,
destination restrictions with no partial writes, invalid/custom menus, and
carried-stack preservation.

Freshness was checked before reusing these results: both changed Java sources
were last modified at 01:28:48 +08:00, before their compiled classes and the
successful GameTest run (01:30–01:33 +08:00). The sources JAR contains the exact
current `ContainerSortService.java` bytes, and the runtime JAR contains the exact
current compiled service class bytes. No implementation or test source was
changed during this continuation. The later full build confirmed the same
sources and artifacts remained current.

SHA-256 at verification:

| File | SHA-256 |
| --- | --- |
| `src/main/java/dev/totem/vanillatweaks/inventory/ContainerSortService.java` | `b98c7bd374e6375943f7215afa53a6a54d3637547caa3efa0f0a5ff7af85d751` |
| `src/gametest/java/dev/totem/vanillatweaks/gametest/ContainerSortGameTest.java` | `1db9d71a976ffc93ae72e14d84ca0ef911f7a29e74be6168bff930633731c34c` |
| `build/libs/totem-vanilla-tweaks-0.1.26.jar` | `c41a25176b18b507a7ce442d6790a9cd1eee72890249e91cb260253e87fb65cc` |

## Scope and limits

Tests exercise the server sorting service with real Minecraft menus and mock
server players. This evidence does not establish manual mouse-input or
multiplayer-client end-to-end behavior, nor compatibility with arbitrary mods
that modify an exact vanilla menu's storage semantics. No Screen, Menu,
Observer provider, network payload, persistence format, or shared API changed.
Existing Gradle and mock-player deprecation warnings remain. Artifacts retain
the existing version and were not published or deployed.

## Release candidate 0.1.27

After validation, `gradle.properties` was bumped to 0.1.27 and bilingual release
notes were added at `.github/staging/modrinth-changelog-0.1.27.md`.
Running the same Gradle command above with `jar` instead of `build` succeeded
in 2 seconds; log: `/tmp/totem-gui-diagnosis/vanilla-release-jar.log`.

The release JAR `build/libs/totem-vanilla-tweaks-0.1.27.jar` packages version
0.1.27. All 304 class entries are byte-identical to the tested 0.1.26 artifact;
the only changed archive entry is `fabric.mod.json`, and its dependency values
are unchanged. `git diff --check` passes.

- SHA-256: `ee69ca82e1dbc7f75f5d37483f5506c3ed6c5f4d87346fe88ebeec41ae722390`
- SHA-512: `16ce5b1a3e508d0ac77bdb8b40f7591fe5260c811b41f45b43f69e1ecb319e76e611b1abbc4e19f9da32e4860ed4e9df9c59027cd5ac336999c82738ec80608a`
