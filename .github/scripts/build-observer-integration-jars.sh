#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
lockstep_root="${OBSERVER_LOCKSTEP_ROOT:-$repo_root/.lockstep}"
core="$lockstep_root/TotemCore"
core_jar="$core/build/libs/totem-core-0.7.18.jar"
wrapper="$core/gradlew"

assert_checkout() {
  local repo="$1" commit="$2" version="$3"
  test "$(git -C "$lockstep_root/$repo" rev-parse HEAD)" = "$commit"
  test "$(sed -n 's/^mod_version=//p' "$lockstep_root/$repo/gradle.properties")" = "$version"
}

assert_production_jar() {
  local archive="$1" mod_id="$2" version="$3"
  local entries

  test -f "$archive"
  case "$archive" in
    */build/libs/*.jar) ;;
    *)
      printf 'Expected a production JAR under build/libs, got %s\n' "$archive" >&2
      return 1
      ;;
  esac
  case "$(basename "$archive")" in
    *-dev.jar|*-sources.jar)
      printf 'Refusing non-production integration artifact %s\n' "$archive" >&2
      return 1
      ;;
  esac

  entries="$(jar tf "$archive")"
  grep -Fxq 'fabric.mod.json' <<< "$entries"
  if grep -Eq '/(gametest|integrationGametest|e2e)/' <<< "$entries"; then
    printf 'Production integration artifact contains test-only classes: %s\n' "$archive" >&2
    return 1
  fi
  unzip -p "$archive" fabric.mod.json \
    | jq -e --arg mod_id "$mod_id" --arg version "$version" \
        '.id == $mod_id and .version == $version' >/dev/null
}

assert_checkout TotemCore f87cd10fe8aefce77925b9e75dad237f17f38966 0.7.18
assert_checkout TotemExcavation f40b94fd5d9de8b47534343c76a95f62926d2b1b 0.1.13
assert_checkout TotemRemnant 1d89395f93d8ea817947db4653919a11355eb548 0.2.21
assert_checkout TotemAutomata cc4bdb022615faad73bc9e5c0ef6d52b9d0970e6 0.1.24
assert_checkout TotemNexus e3f91a19790f9da85494b9ae1d5770dda12e0e43 0.3.17
assert_checkout TotemVillagers 615f83c5c3534a40e6ae7a2a0713390512f8b64c 0.1.36
assert_checkout TotemLocksmith 9e8e25d44887a33839dc2a3b92a424ca4b931e00 0.1.10

chmod +x "$wrapper"
"$wrapper" -p "$core" jar --no-daemon --stacktrace
assert_production_jar "$core_jar" totem-core 0.7.18

"$wrapper" -p "$lockstep_root/TotemExcavation" \
  -PtotemCoreJar="$core_jar" jar --no-daemon --stacktrace
excavation_jar="$lockstep_root/TotemExcavation/build/libs/totem-excavation-0.1.13.jar"
assert_production_jar "$excavation_jar" totem-excavation 0.1.13

"$wrapper" -p "$lockstep_root/TotemRemnant" \
  -PtotemCoreJar="$core_jar" remapJar --no-daemon --stacktrace
remnant_jar="$lockstep_root/TotemRemnant/build/libs/totem-remnant-0.2.21.jar"
assert_production_jar "$remnant_jar" totem-remnant 0.2.21

"$wrapper" -p "$lockstep_root/TotemAutomata" \
  -PtotemCoreJar="$core_jar" \
  -PtotemExcavationJar="$excavation_jar" \
  -PincludeTotemExcavationRuntime=false jar --no-daemon --stacktrace
assert_production_jar \
  "$lockstep_root/TotemAutomata/build/libs/totem-automata-0.1.24.jar" \
  totem-automata 0.1.24

"$wrapper" -p "$lockstep_root/TotemNexus" \
  -PtotemCoreJar="$core_jar" jar --no-daemon --stacktrace
assert_production_jar \
  "$lockstep_root/TotemNexus/build/libs/totem-nexus-0.3.17.jar" \
  totem-nexus 0.3.17

"$wrapper" -p "$lockstep_root/TotemVillagers" \
  -PtotemCoreJar="$core_jar" -PtotemRemnantJar="$remnant_jar" \
  jar --no-daemon --stacktrace
assert_production_jar \
  "$lockstep_root/TotemVillagers/build/libs/totem-villagers-0.1.36.jar" \
  totem-villagers 0.1.36

"$wrapper" -p "$lockstep_root/TotemLocksmith" \
  -PtotemCoreJar="$core_jar" jar --no-daemon --stacktrace
assert_production_jar \
  "$lockstep_root/TotemLocksmith/build/libs/totem-locksmith-0.1.10.jar" \
  totem-locksmith 0.1.10
