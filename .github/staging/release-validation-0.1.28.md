# 0.1.28 release workflow repair — 2026-09-11

The extraction commit `0360c3f513fe1247f1c4fd96c7d3af7e22365803` passed [Build](https://github.com/Yunitrish006006/TotemVanillaTweaks/actions/runs/34561327283) and [Production Runtime](https://github.com/Yunitrish006006/TotemVanillaTweaks/actions/runs/34561327244), but [publication attempt 34561327291](https://github.com/Yunitrish006006/TotemVanillaTweaks/actions/runs/34561327291) failed while invoking the deleted Observer integration build script. Publication was skipped.

The repaired workflow builds pinned TotemCore, runs gameplay unit/server/client and production-runtime tests, and rejects embedded Observer classes or metadata before publication. Observer cross-module and three-JVM validation remains owned by TotemObserver. Added the missing bilingual 0.1.28 changelog.

## Local validation

Java 25, TotemCore 0.7.18, isolated Gradle user home `/tmp/totem-update-vanilla-gradle`:

- `test assemble compileGametestJava`: PASS; 9 tests, no failures/errors/skips.
- `runGameTest`: PASS; all 24 required tests passed; Gradle exited successfully.
- `runClientGametest runProductionClientGameTest` under Xvfb: PASS; Gradle exited successfully.
- Built artifact identity: `totem-vanilla-tweaks-0.1.28.jar`, mod ID `totem-vanilla-tweaks`, version `0.1.28`.
- Repaired ownership check against that actual JAR: PASS.
- Ownership-check ZIP fixtures: 8/8 PASS (clean artifact accepted; embedded Observer class, each of three metadata violations, and each missing metadata entry rejected).
- Actionlint 1.7.12: PASS. All 11 workflow Bash blocks passed `bash -n`.
- Independent read-only review: the initial negated-pipeline failure-handling issue was fixed and re-reviewed; no remaining findings.

The existing shared Gradle cache contained Fabric API entries normalized to the wrong namespace for this module. A fresh isolated cache resolved that environment failure without changing dependencies or production code. Headless client logs also contain Realms test-account and unavailable X11 cursor messages; both runtime tasks completed successfully.

No remote publish/dry-run was dispatched and no commit or push was performed. These results establish local release preparation, not a new publication record.
