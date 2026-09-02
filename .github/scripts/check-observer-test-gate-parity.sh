#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
client_package="dev.totem.vanillatweaks.gametest"
client_source_dir="$repo_root/src/gametest/java/dev/totem/vanillatweaks/gametest"
client_manifest="$repo_root/src/gametest/resources/fabric.mod.json"
production_extractor_manifest="$repo_root/src/gametest/resources/observer-production-extractor-evidence.txt"
main_client_source_dir="$repo_root/src/main/java/dev/totem/vanillatweaks/client"
integration_client_package="dev.totem.vanillatweaks.client"
integration_client_source_dir="$repo_root/src/integrationGametest/java/dev/totem/vanillatweaks/client"
integration_client_manifest="$repo_root/src/integrationGametest/resources/fabric.mod.json"
e2e_package="dev.totem.vanillatweaks.e2e"
e2e_source_dir="$repo_root/src/e2e/java/dev/totem/vanillatweaks/e2e"
e2e_manifest="$repo_root/src/e2e/resources/fabric.mod.json"
workflow="$repo_root/.github/workflows/build.yml"
production_workflow="$repo_root/.github/workflows/production-runtime.yml"
publish_workflow="$repo_root/.github/workflows/publish-modrinth.yml"
dependency_summary_filter="$repo_root/.github/scripts/modrinth-dependency-summary.jq"
remote_dependency_filter="$repo_root/.github/scripts/verify-modrinth-remote-dependencies.jq"
build_script="$repo_root/build.gradle"
integration_build_script="$repo_root/.github/scripts/build-observer-integration-jars.sh"

failures=0

fail() {
  printf 'Observer test gate parity: %s\n' "$*" >&2
  failures=$((failures + 1))
}

class_name() {
  basename "$1" .java
}

camel_to_kebab() {
  sed -E 's#([A-Z]+)([A-Z][a-z])#\1-\2#g; s#([a-z0-9])([A-Z])#\1-\2#g' \
    | tr '[:upper:]' '[:lower:]'
}

add_unique() {
  local label="$1"
  local set_name="$2"
  local value="$3"
  local -n set_ref="$set_name"
  if [[ -v "set_ref[$value]" ]]; then
    fail "$label contains duplicate entry $value"
  fi
  set_ref["$value"]=1
}

assert_same_set() {
  local label="$1"
  local expected_name="$2"
  local actual_name="$3"
  local -n expected_ref="$expected_name"
  local -n actual_ref="$actual_name"
  local value

  for value in "${!expected_ref[@]}"; do
    if [[ ! -v "actual_ref[$value]" ]]; then
      fail "$label is missing $value"
    fi
  done
  for value in "${!actual_ref[@]}"; do
    if [[ ! -v "expected_ref[$value]" ]]; then
      fail "$label contains stale or unexpected entry $value"
    fi
  done
}

assert_source_literal_set() {
  local label="$1"
  local source="$2"
  local pattern="$3"
  shift 3
  local -A expected=()
  local -A actual=()
  local value

  for value in "$@"; do
    expected["$value"]=1
  done
  while IFS= read -r value; do
    [[ -n "$value" ]] && actual["$value"]=1
  done < <(grep -oE "$pattern" "$source" 2>/dev/null | tr -d '"' | sort -u || true)

  assert_same_set "$label" expected actual
}

assert_emitted_marker_set() {
  local label="$1"
  local source="$2"
  local pattern="$3"
  shift 3
  local flattened
  local -A expected_set=()
  local -A actual_set=()
  local value

  for value in "$@"; do
    expected_set["$value"]=1
  done
  flattened="$(tr '\n' ' ' < "$source")"
  while IFS= read -r value; do
    [[ -n "$value" ]] && actual_set["$value"]=1
  done < <(
    printf '%s\n' "$flattened" \
      | grep -oE "ObserverE2eCommon\\.marker[[:space:]]*\\([[:space:]]*\"$pattern\"" \
      | grep -oE "$pattern" \
      | sort -u \
      || true
  )

  assert_same_set "$label" expected_set actual_set
}

assert_exclusive_emitted_marker() {
  local label="$1"
  local expected_source="$2"
  local marker="$3"
  local source
  local flattened
  local -a owners=()

  for source in "$e2e_source_dir"/*.java; do
    flattened="$(tr '\n' ' ' < "$source")"
    if printf '%s\n' "$flattened" \
        | grep -qE "ObserverE2eCommon\\.marker[[:space:]]*\\([[:space:]]*\"$marker\""; then
      owners+=("$source")
    fi
  done

  if (( ${#owners[@]} != 1 )) || [[ "${owners[0]:-}" != "$expected_source" ]]; then
    fail "$label must be emitted only by $(basename "$expected_source"); owners: ${owners[*]:-none}"
  fi
}

assert_workflow_evidence() {
  local owner="$1"
  shift
  local marker
  for marker in "$@"; do
    if [[ ! -v "workflow_success_assertions[$marker]" ]]; then
      fail "$owner evidence $marker is not an explicit E2E success assertion in build.yml"
    fi
  done
}

expect_workflow_evidence() {
  local marker
  for marker in "$@"; do
    expected_workflow_assertions["$marker"]=1
  done
}

if ! command -v jq >/dev/null 2>&1; then
  printf 'Observer test gate parity: jq is required to parse Fabric entrypoint manifests.\n' >&2
  exit 2
fi

shopt -s nullglob

# Production Client GameTests must remain a real release-JAR gate. Minecraft
# 26.2 is unobfuscated, so the non-remapping Loom plugin and official Fabric
# runtime artifacts are required; mutating Gradle's shared dependency cache is
# never an acceptable namespace workaround.
if ! grep -Fq "id 'net.fabricmc.fabric-loom' version '1.17.12'" "$build_script"; then
  fail 'build.gradle must use the Minecraft 26.2 non-remapping Fabric Loom plugin'
fi
if grep -Fq 'normalizeFabricApiTweakersForNamedDevRuntime' "$build_script" "$workflow"; then
  fail 'build/CI must not mutate Fabric API dependency jars or Gradle shared caches'
fi
for gradle_workflow in "$workflow" "$production_workflow" "$publish_workflow"; do
  setup_gradle_count="$(grep -Fc 'uses: gradle/actions/setup-gradle@v4' "$gradle_workflow" || true)"
  cache_disabled_count="$(grep -Ec '^[[:space:]]+cache-disabled: true$' "$gradle_workflow" || true)"
  if [[ "$setup_gradle_count" == 0 || "$cache_disabled_count" != "$setup_gradle_count" ]]; then
    fail "$(basename "$gradle_workflow") must disable every setup-gradle cache restore/write; found $cache_disabled_count/$setup_gradle_count"
  fi
done
if ! grep -Fq 'TOTEM_CORE_DEPENDENCY_FILE: totem-core-0.7.16.jar' "$publish_workflow" \
    || ! grep -Fq -- "--arg core '>=0.7.16 <0.8.0'" "$publish_workflow" \
    || grep -Fq 'TOTEM_CORE_REFERENCE_VERSION_ID:' "$publish_workflow" \
    || grep -Fq 'TOTEM_CORE_PROJECT_ID:' "$publish_workflow"; then
  fail 'Modrinth publication must validate and use the exact built TotemCore 0.7.16 dependency, never a stale range or guessed project/version ID'
fi
if ! grep -Fq '[{project_id:$fabric,dependency_type:"required"},{file_name:$core_file,dependency_type:"required"}]' "$publish_workflow" \
    || ! grep -Fq 'core_dependency_file="${core_archive}-${core_version}.jar"' "$publish_workflow" \
    || ! grep -Fq 'core_dependency_file" != "$TOTEM_CORE_DEPENDENCY_FILE" || ! -f "$core_artifact"' "$publish_workflow" \
    || ! grep -Fq -- '-f .github/scripts/verify-modrinth-remote-dependencies.jq' "$publish_workflow" \
    || [[ ! -f "$remote_dependency_filter" ]]; then
  fail 'Modrinth publication must submit the exact TotemCore file and reuse the strict remote dependency verifier'
fi
if [[ -f "$remote_dependency_filter" ]]; then
  remote_dependencies_exact='{"dependencies":[{"dependency_type":"required","project_id":"P7dR8mSH","version_id":null,"file_name":null},{"dependency_type":"required","project_id":null,"version_id":null,"file_name":"totem-core-0.7.16.jar"}]}'
  remote_dependencies_normalized='{"dependencies":[{"dependency_type":"required","project_id":"P7dR8mSH","version_id":null,"file_name":null},{"dependency_type":"required","project_id":null,"version_id":null,"file_name":null}]}'
  remote_dependencies_wrong_file='{"dependencies":[{"dependency_type":"required","project_id":"P7dR8mSH","version_id":null,"file_name":null},{"dependency_type":"required","project_id":null,"version_id":null,"file_name":"wrong-core.jar"}]}'
  remote_dependencies_extra='{"dependencies":[{"dependency_type":"required","project_id":"P7dR8mSH","version_id":null,"file_name":null},{"dependency_type":"required","project_id":null,"version_id":null,"file_name":null},{"dependency_type":"optional","project_id":"extra","version_id":null,"file_name":null}]}'

  for accepted_dependencies in "$remote_dependencies_exact" "$remote_dependencies_normalized"; do
    if ! jq -e \
        --arg fabric 'P7dR8mSH' \
        --arg core_file 'totem-core-0.7.16.jar' \
        -f "$remote_dependency_filter" <<<"$accepted_dependencies" >/dev/null; then
      fail 'Modrinth remote dependency verifier must accept exact and normalized-null TotemCore file_name metadata'
    fi
  done

  for rejected_dependencies in "$remote_dependencies_wrong_file" "$remote_dependencies_extra"; do
    if jq -e \
        --arg fabric 'P7dR8mSH' \
        --arg core_file 'totem-core-0.7.16.jar' \
        -f "$remote_dependency_filter" <<<"$rejected_dependencies" >/dev/null; then
      fail 'Modrinth remote dependency verifier must reject wrong TotemCore file names and extra dependencies'
    fi
  done
fi
if ! grep -Fq './gradlew -PtotemCoreJar="$core_jar" clean jar --no-daemon --stacktrace' "$publish_workflow" \
    || ! grep -Fq 'version ${v} already exists with a different artifact SHA-512; refusing to overwrite it. Bump mod_version.' "$publish_workflow" \
    || ! grep -Fq "fetch_modrinth_json 'release versions'" "$publish_workflow" \
    || ! grep -Fq '.project_type == "mod"' "$publish_workflow" \
    || ! grep -Fq 'requested_status=' "$publish_workflow"; then
  fail 'Modrinth publication must clean-build its JAR and report project state plus explicit existing-version conflicts'
fi
if [[ ! -f "$dependency_summary_filter" ]] \
    || ! grep -Fq 'jq -c -f .github/scripts/modrinth-dependency-summary.jq /tmp/remote.json' "$publish_workflow" \
    || ! grep -Fq "printf 'Published Modrinth dependency summary: %s\\n' \"\$dependency_summary\" >&2" "$publish_workflow"; then
  fail 'Modrinth verification failures must emit the dedicated redacted dependency summary'
else
  dependency_summary_input='{"token":"must-not-leak","author":{"id":"private"},"unrelated":"must-not-leak","dependencies":[{"dependency_type":"required","project_id":"P7dR8mSH","version_id":null,"private":"must-not-leak"},{"dependency_type":"required","project_id":null,"version_id":null,"file_name":"totem-core-0.7.16.jar","private":"must-not-leak"}]}'
  dependency_summary_expected='{"dependency_count":2,"dependencies":[{"dependency_type":"required","project_id":"P7dR8mSH","version_id":null,"file_name_present":false,"file_name":null},{"dependency_type":"required","project_id":null,"version_id":null,"file_name_present":true,"file_name":"totem-core-0.7.16.jar"}]}'
  if ! dependency_summary_actual="$(jq -c -f "$dependency_summary_filter" <<<"$dependency_summary_input")"; then
    fail 'Modrinth dependency summary filter rejected valid metadata'
  elif [[ "$dependency_summary_actual" != "$dependency_summary_expected" ]]; then
    fail 'Modrinth dependency summary filter emitted fields beyond the approved projection'
  fi

  dependency_summary_invalid='{"token":"must-not-leak","dependencies":{"unexpected":"must-not-leak"}}'
  dependency_summary_empty='{"dependency_count":null,"dependencies":[]}'
  if ! dependency_summary_actual="$(jq -c -f "$dependency_summary_filter" <<<"$dependency_summary_invalid")"; then
    fail 'Modrinth dependency summary filter rejected non-array dependency metadata'
  elif [[ "$dependency_summary_actual" != "$dependency_summary_empty" ]]; then
    fail 'Modrinth dependency summary filter must redact malformed dependency metadata'
  fi
fi
if grep -Eq '^[[:space:]]+test([[:space:]]|$)' "$publish_workflow"; then
  fail 'Modrinth publication preconditions must emit explicit non-secret errors instead of bare test exits'
fi
release_dry_run_filter='(.dry_run // false) | booleans | tostring'
if ! grep -Fq "jq -er '$release_dry_run_filter'" "$publish_workflow"; then
  fail 'Modrinth release requests must accept false without weakening dry_run boolean validation'
fi
for dry_run_case in '{}' '{"dry_run":null}' '{"dry_run":false}' '{"dry_run":true}'; do
  expected_dry_run=false
  [[ "$dry_run_case" == '{"dry_run":true}' ]] && expected_dry_run=true
  if ! actual_dry_run="$(jq -er "$release_dry_run_filter" <<<"$dry_run_case")"; then
    fail "Modrinth dry_run parser rejected valid input $dry_run_case"
  elif [[ "$actual_dry_run" != "$expected_dry_run" ]]; then
    fail "Modrinth dry_run parser returned $actual_dry_run for $dry_run_case; expected $expected_dry_run"
  fi
done
for dry_run_case in '{"dry_run":"false"}' '{"dry_run":0}' '{"dry_run":[]}' '{"dry_run":{}}'; do
  if jq -er "$release_dry_run_filter" <<<"$dry_run_case" >/dev/null 2>&1; then
    fail "Modrinth dry_run parser accepted non-boolean input $dry_run_case"
  fi
done
if ! grep -Fq 'productionRuntimeMods "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"' "$build_script" \
    || ! grep -Fq 'productionRuntimeMods files(totemCoreJar)' "$build_script"; then
  fail 'Production Runtime must load the official Fabric API and pinned TotemCore jars'
fi
if ! grep -Fq "tasks.register('runProductionClientGameTest', net.fabricmc.loom.task.prod.ClientProductionRunTask)" "$build_script" \
    || ! grep -Fq "dependsOn('productionGametestModJar')" "$build_script" \
    || ! grep -Fq "mods.from(tasks.named('productionGametestModJar').flatMap { it.archiveFile })" "$build_script" \
    || ! grep -Fq "jvmArgs.add('-Dfabric.client.gametest')" "$build_script"; then
  fail 'runProductionClientGameTest must load the built distribution and production GameTest mod'
fi

client_step_count="$(grep -Ec '^[[:space:]]*- name: Run client GameTests$' "$workflow" || true)"
if [[ "$client_step_count" != 1 ]]; then
  fail "build.yml must contain exactly one development Client GameTest step; found $client_step_count"
fi
client_step="$({
  awk '
    /^      - name: Run client GameTests$/ { in_step = 1 }
    in_step && seen && /^      - name:/ { exit }
    in_step { print; seen = 1 }
  ' "$workflow"
} || true)"
if ! grep -Fq 'xvfb-run -a ./gradlew -PtotemCoreJar="$core_jar" runClientGametest --no-daemon --stacktrace' \
    <<< "$client_step"; then
  fail 'development Client GameTest CI step must execute under xvfb-run with pinned Core and fail normally'
fi
if grep -Eq 'continue-on-error|runClientGametest[^\n]*(\|\|[[:space:]]*true|--exclude-task|[[:space:]]-x[[:space:]])' \
    <<< "$client_step"; then
  fail 'development Client GameTest CI step must not ignore or exclude the task failure'
fi

if ! grep -Fq "tasks.register('prepareE2eClientLaunchInputs')" "$build_script" \
    || ! grep -Fq 'outputs.upToDateWhen { false }' "$build_script" \
    || ! grep -Fq "fabric.defaultModDistributionNamespace=official" "$build_script" \
    || ! grep -Fq "lowerClasspath.contains('org.relativitymc')" "$build_script" \
    || ! grep -Fq "lowerClasspath.contains('modern-yarn')" "$build_script"; then
  fail 'build.gradle must freshly derive official-namespace E2E launch inputs from current run-task classpaths'
fi
e2e_step="$({
  awk '
    /^      - name: Run dedicated-server two-client Observer E2E$/ { in_step = 1 }
    in_step && seen && /^      - name:/ { exit }
    in_step { print; seen = 1 }
  ' "$workflow"
} || true)"
if ! grep -Fq -- '-Pe2eLaunchInputsDir="$launch_dir"' <<< "$e2e_step" \
    || ! grep -Fq 'prepareE2eClientLaunchInputs' <<< "$e2e_step" \
    || ! grep -Fq 'launch_cfg="$launch_dir/launch.cfg"' <<< "$e2e_step"; then
  fail 'three-JVM E2E must consume launch inputs freshly produced by prepareE2eClientLaunchInputs'
fi
if grep -Fq 'build/loom-cache/argFiles/' <<< "$e2e_step"; then
  fail 'three-JVM E2E must not copy run-task argfile side effects from build/loom-cache'
fi
if ! grep -Fq "grep -Fq 'fabric.defaultModDistributionNamespace=official' \"\$launch_cfg\"" <<< "$e2e_step" \
    || ! grep -Fq "grep -Eqi 'org\\.relativitymc|modern-yarn' \"\$launch_cfg\" \"\$target_args\" \"\$observer_args\"" <<< "$e2e_step" \
    || ! grep -Fq 'cmp -s "$target_args" "$observer_args"' <<< "$e2e_step"; then
  fail 'three-JVM E2E must reject wrong-namespace, stale, or inconsistent direct-launch inputs'
fi

production_step_count="$(grep -Ec '^[[:space:]]*- name: Run production Client GameTests$' "$workflow" || true)"
if [[ "$production_step_count" != 1 ]]; then
  fail "build.yml must contain exactly one Production Client GameTest step; found $production_step_count"
fi
production_step="$({
  awk '
    /^      - name: Run production Client GameTests$/ { in_step = 1 }
    in_step && seen && /^      - name:/ { exit }
    in_step { print; seen = 1 }
  ' "$workflow"
} || true)"
if ! grep -Fq './gradlew -PtotemCoreJar="$core_jar" runProductionClientGameTest --no-daemon --stacktrace' \
    <<< "$production_step"; then
  fail 'Production Client GameTest CI step must execute runProductionClientGameTest with pinned Core and fail normally'
fi
if ! grep -Fq 'production_screenshot_count="$(find "$production_screenshots" -maxdepth 1 -type f -name '\''*.png'\'' | wc -l)"' \
    <<< "$production_step" \
    || ! grep -Fq 'if [[ "$production_screenshot_count" != 31 ]]; then' <<< "$production_step"; then
  fail 'Production Client GameTest CI step must require exactly 31 persisted screenshots'
fi
if grep -Eq 'continue-on-error|runProductionClientGameTest[^\n]*(\|\|[[:space:]]*true|--exclude-task|[[:space:]]-x[[:space:]])' \
    <<< "$production_step"; then
  fail 'Production Client GameTest CI step must not ignore or exclude the production task failure'
fi
e2e_step_line="$(grep -n -m1 '^[[:space:]]*- name: Run dedicated-server two-client Observer E2E$' "$workflow" | cut -d: -f1 || true)"
production_step_line="$(grep -n -m1 '^[[:space:]]*- name: Run production Client GameTests$' "$workflow" | cut -d: -f1 || true)"
if [[ -z "$e2e_step_line" || -z "$production_step_line" ]] \
    || (( production_step_line <= e2e_step_line )); then
  fail 'Production Client GameTests must remain after the integrated three-JVM E2E gate'
fi

# Parse only executable file assertions from the single three-JVM completion
# condition. A marker mentioned in a comment, echo, artifact path, or elsewhere
# in the workflow is deliberately not accepted as release-gate evidence.
workflow_success_start_count="$(
  grep -Ec '^[[:space:]]*if \[\[ -f "\$results/server-session-started\.txt"[[:space:]]*\\$' "$workflow" \
    || true
)"
if [[ "$workflow_success_start_count" != 1 ]]; then
  fail "build.yml must contain exactly one Observer E2E success condition; found $workflow_success_start_count"
fi
workflow_success_block="$(
  sed -n \
    '\#^[[:space:]]*if \[\[ -f "$results/server-session-started\.txt"#,'\
'\#^[[:space:]]*&& -f "$results/server-cleanup-ok\.txt"[[:space:]]*\]\]; then#p' \
    "$workflow"
)"
if [[ -z "$workflow_success_block" ]]; then
  fail 'could not isolate the Observer E2E success condition from build.yml'
fi

declare -A workflow_success_assertions=()
while IFS= read -r marker; do
  [[ -n "$marker" ]] && add_unique 'Observer E2E success condition' workflow_success_assertions "$marker"
done < <(
  printf '%s\n' "$workflow_success_block" \
    | sed -nE 's#^[[:space:]]*(if \[\[ |&& )-f "\$results/([A-Za-z0-9._-]+)"([[:space:]]*\\|[[:space:]]*\]\]; then)[[:space:]]*$#\2#p'
)

declare -A expected_workflow_assertions=()
fixed_workflow_evidence=(
  'server-session-started.txt'
  'target-world-ready.txt'
  'target-native-state-enabled.txt'
  'target-native-no-frame.txt'
  'observer-native-state-ok.txt'
  'observer-native-camera-ok.txt'
  'observer-native-hud-ok.txt'
  'observer-native-world-settled.txt'
  'observer-native-world-saved.txt'
  'observer-native-world.png'
  'observer-native-hud.png'
  'observer-ready-for-container.txt'
  'target-native-container-opened.txt'
  'target-native-container-state-sent.txt'
  'observer-native-container-ok.txt'
  'observer-native-container-saved.txt'
  'observer-native-container.png'
  'target-native-container-closed.txt'
  'target-native-crafting-sender-priority-suppressed.txt'
  'observer-native-crafting-mirror-priority-suppressed.txt'
  'observer-ready-for-generic-screen.txt'
  'target-native-generic-opened.txt'
  'target-native-generic-no-frame.txt'
  'observer-native-generic-screen-ok.txt'
  'observer-native-generic-screen-saved.txt'
  'observer-native-generic-screen.png'
  'target-native-generic-closed.txt'
  'observer-complete.txt'
  'target-complete.txt'
  'server-cleanup-ok.txt'
)
expect_workflow_evidence "${fixed_workflow_evidence[@]}"

# Client GameTest source files and fabric-client-gametest entrypoints must be a
# true two-way set match. This catches both an unregistered test and a stale
# manifest entry (including duplicates), rather than only checking containment.
declare -A client_sources=()
declare -A client_entrypoints=()
for source in "$client_source_dir"/*ClientGameTest.java; do
  class="$(class_name "$source")"
  if ! grep -q 'implements FabricClientGameTest' "$source"; then
    fail "$class is named as a ClientGameTest but does not implement FabricClientGameTest"
    continue
  fi
  client_sources["$client_package.$class"]=1
done
while IFS= read -r entrypoint; do
  add_unique 'fabric-client-gametest manifest' client_entrypoints "$entrypoint"
done < <(jq -r '.entrypoints["fabric-client-gametest"][]' "$client_manifest")
assert_same_set 'fabric-client-gametest manifest' client_sources client_entrypoints
if (( ${#client_sources[@]} != 26 )); then
  fail "Observer Client GameTest baseline must remain 26 tests; found ${#client_sources[@]}"
fi

# Production extractor evidence is stronger than a relay screenshot: each row
# names a real Screen/Menu Client GameTest method and the exact production
# capture helper shared with tickTarget. Keep this explicit list in lockstep so
# a newly synthetic-only vanilla family cannot silently satisfy the release gate.
production_extractor_families=(
  container_slots furnace crafting anvil enchanting merchant brewing smithing
  stonecutter grindstone loom cartography beacon crafter book sign advancements stats
  remnant_backpack automata_copper_golem nexus villagers_woodcutter
  nexus_death_node_admin locksmith_management horse_inventory
)
declare -A expected_production_extractors=()
declare -A actual_production_extractors=()
for family in "${production_extractor_families[@]}"; do
  expected_production_extractors["$family"]=1
done
declare -A integration_client_entrypoints=()
while IFS= read -r entrypoint; do
  add_unique 'integration fabric-client-gametest manifest' integration_client_entrypoints "$entrypoint"
done < <(jq -r '.entrypoints["fabric-client-gametest"][]' "$integration_client_manifest")
if (( ${#integration_client_entrypoints[@]} != 1 )); then
  fail "cross-module integration manifest must contain exactly one Client GameTest; found ${#integration_client_entrypoints[@]}"
fi

while IFS='|' read -r family test_class verification_method capture_helper runtime_task trailing; do
  [[ -z "$family" || "$family" == \#* ]] && continue
  if [[ -n "${trailing:-}" || -z "$test_class" || -z "$verification_method" || -z "$capture_helper" || -z "$runtime_task" ]]; then
    fail "invalid production extractor manifest row for $family"
    continue
  fi
  add_unique 'production extractor manifest' actual_production_extractors "$family"
  case "$runtime_task" in
    runClientGametest)
      source="$client_source_dir/$test_class.java"
      registered="${client_sources[$client_package.$test_class]:-}"
      ;;
    runIntegrationClientGametest)
      source="$integration_client_source_dir/$test_class.java"
      registered="${integration_client_entrypoints[$integration_client_package.$test_class]:-}"
      ;;
    *)
      fail "$family production extractor evidence names unsupported runtime task $runtime_task"
      continue
      ;;
  esac
  if [[ ! -f "$source" || -z "$registered" ]]; then
      fail "$family production extractor evidence references unregistered $test_class in $runtime_task"
      continue
    fi
  if ! grep -Eq "private static void[[:space:]]+$verification_method[[:space:]]*\(" "$source"; then
    fail "$family production extractor evidence method $test_class.$verification_method is missing"
  fi
  if ! grep -Eq "(\"$capture_helper\"|\.$capture_helper[[:space:]]*\()" "$source"; then
    fail "$family production extractor evidence does not invoke $capture_helper"
  fi
done < "$production_extractor_manifest"
assert_same_set 'production extractor family manifest' expected_production_extractors actual_production_extractors

if ! grep -Fq "tasks.named('runIntegrationClientGametest')" "$build_script"; then
  fail 'cross-module production extractor evidence must have a Gradle runtime gate'
fi
for integration_workflow in "$workflow" "$production_workflow" "$publish_workflow"; do
  integration_build_count="$(grep -Fc 'bash .github/scripts/build-observer-integration-jars.sh' \
    "$integration_workflow" || true)"
  integration_runtime_count="$(grep -Fc 'runIntegrationClientGametest --no-daemon --stacktrace' \
    "$integration_workflow" || true)"
  if [[ "$integration_build_count" != 1 || "$integration_runtime_count" != 1 ]]; then
    fail "$(basename "$integration_workflow") must build pinned optional modules and run exactly one cross-module gate; found build=$integration_build_count runtime=$integration_runtime_count"
  fi
  if [[ "$(grep -Fc 'build/owner-present-integration-screenshots/*.png' "$integration_workflow" || true)" != 1 \
      || "$(grep -Fc 'if [[ "$count" != 9 ]]; then' "$integration_workflow" || true)" != 1 \
      || "$(grep -Fc 'Expected exactly 9 owner-present screenshots; found $count.' "$integration_workflow" || true)" != 1 ]]; then
    fail "$(basename "$integration_workflow") must separately count and upload exactly eight owner-present screenshots"
  fi
done
for checkout in \
  'TotemCore b0b57bc98a98140a1c12a660a33952ea61167278 0.7.16' \
  'TotemExcavation 6b54011195b81ec9a9a09146d162ba303ebd8ee4 0.1.8' \
  'TotemRemnant a3f9231b50c6c55f03a8c145e80644bd6cff7021 0.2.16' \
  'TotemAutomata b007eeb0ef0417804fe2abb25524842480419ae1 0.1.18' \
  'TotemNexus 781c4593b03c6d620c5bca575a69053612aef240 0.3.13' \
  'TotemVillagers c4d37815c45b86f102939ec5e43b171692a406e0 0.1.33' \
  'TotemLocksmith c89d380b88b10a3f0f55044e04266bc8ada70357 0.1.6'; do
  if ! grep -Fq "assert_checkout $checkout" "$integration_build_script"; then
    fail "cross-module integration build script is missing exact checkout $checkout"
  fi
done
production_jar_assertion_count="$(grep -Ec '^assert_production_jar ' "$integration_build_script" || true)"
if [[ "$production_jar_assertion_count" != 7 ]]; then
  fail "cross-module integration build script must validate all seven production JARs; found $production_jar_assertion_count"
fi
if ! grep -Fq '*/build/libs/*.jar)' "$integration_build_script" \
    || ! grep -Fq '*-dev.jar|*-sources.jar)' "$integration_build_script" \
    || ! grep -Fq "jar tf \"\$archive\"" "$integration_build_script" \
    || ! grep -Fq "unzip -p \"\$archive\" fabric.mod.json" "$integration_build_script"; then
  fail 'cross-module integration build script must reject dev/source/test-only artifacts and verify production mod metadata'
fi
remnant_build_step="$({
  awk '
    index($0, "\"$wrapper\" -p \"$lockstep_root/TotemRemnant\"") { in_step = 1 }
    in_step { print }
    in_step && /^assert_production_jar "\$remnant_jar"/ { exit }
  ' "$integration_build_script"
} || true)"
if ! grep -Fq 'remapJar --no-daemon --stacktrace' <<< "$remnant_build_step" \
    || grep -Fq ' jar --no-daemon --stacktrace' <<< "$remnant_build_step"; then
  fail 'pinned Remnant split-environment checkout must use remapJar for its build/libs production artifact'
fi

# Every local semantic reconstruction uses the TotemCore read-only marker. This
# prevents nested retransmission and permanently rejects hand-drawn mirrors.
ui_client="$main_client_source_dir/ObserverUiClient.java"
if ! grep -Fq 'ObserverOwnedScreenCoordinator.isReadOnlyObserverScreen(screen)' "$ui_client"; then
  fail 'ObserverUiClient metadata capture must centrally exclude read-only observer screens'
fi
if grep -Eq 'Observer[A-Za-z0-9]+ScreenClient\.isNativeObserverScreen\(screen\)' "$ui_client"; then
  fail 'ObserverUiClient must not use a hand-maintained mirror class exclusion list'
fi
if grep -RhEq 'class[[:space:]]+[A-Za-z0-9_]*(Mirror|Lookalike)Screen[[:space:]]+extends' \
    "$main_client_source_dir"; then
  fail 'Observer production source must not contain hand-drawn Mirror/Lookalike Screen classes'
fi
if ! grep -Rhq 'implements[[:space:]].*ObserverReadOnlyScreen' "$main_client_source_dir"; then
  fail 'Mojang observer reconstructions must implement ObserverReadOnlyScreen'
fi
if ! grep -Fq 'String unsent = "/login client-only-secret"' \
    "$client_source_dir/ObserverVanillaProductionSenderClientGameTest.java" \
    || ! grep -Fq 'captureScreenMetadata' \
    "$client_source_dir/ObserverVanillaProductionSenderClientGameTest.java"; then
  fail 'production Client GameTest must prove unsent ChatScreen text is excluded from metadata'
fi

# The E2E main initializer is the server coordinator. The client set is exactly
# the shared driver plus every Observer*E2eBridge source file.
declare -A e2e_main_expected=(["$e2e_package.ObserverE2eCommon"]=1)
declare -A e2e_main_entrypoints=()
declare -A e2e_client_expected=(["$e2e_package.ObserverE2eClient"]=1)
declare -A e2e_client_entrypoints=()
declare -A bridge_classes=()

if ! grep -q 'implements ModInitializer' "$e2e_source_dir/ObserverE2eCommon.java"; then
  fail 'ObserverE2eCommon must remain the ModInitializer server coordinator'
fi
if ! grep -Fq 'markerExists("target-connect-started.txt")' "$e2e_source_dir/ObserverE2eCommon.java" \
    || ! grep -Fq 'markerExists("observer-connect-started.txt")' "$e2e_source_dir/ObserverE2eCommon.java"; then
  fail 'ObserverE2eCommon join deadline must wait for both client connect-attempt markers'
fi
if ! grep -Fq 'bothClientsSeenTick,' "$e2e_source_dir/ObserverE2eCommon.java" \
    || ! grep -Fq 'PAYLOAD_ADVERTISEMENT_TIMEOUT_TICKS' "$e2e_source_dir/ObserverE2eCommon.java"; then
  fail 'ObserverE2eCommon payload deadline must be anchored after both clients are present'
fi
if grep -Eq 'firstClientSeenTick[[:space:]]*\+[[:space:]]*[A-Z_]*TIMEOUT' \
    "$e2e_source_dir/ObserverE2eCommon.java"; then
  fail 'ObserverE2eCommon must not anchor join/payload deadlines to the first client'
fi
if ! grep -q 'implements ClientModInitializer' "$e2e_source_dir/ObserverE2eClient.java"; then
  fail 'ObserverE2eClient must remain the ClientModInitializer shared client driver'
fi

for source in "$e2e_source_dir"/Observer*E2eBridge.java; do
  class="$(class_name "$source")"
  if ! grep -q 'implements ClientModInitializer' "$source"; then
    fail "$class is named as an E2E bridge but does not implement ClientModInitializer"
  fi
  bridge_classes["$class"]=1
  e2e_client_expected["$e2e_package.$class"]=1
done
while IFS= read -r entrypoint; do
  add_unique 'E2E main manifest' e2e_main_entrypoints "$entrypoint"
done < <(jq -r '.entrypoints.main[]' "$e2e_manifest")
while IFS= read -r entrypoint; do
  add_unique 'E2E client manifest' e2e_client_entrypoints "$entrypoint"
done < <(jq -r '.entrypoints.client[]' "$e2e_manifest")
assert_same_set 'E2E main manifest' e2e_main_expected e2e_main_entrypoints
assert_same_set 'E2E client manifest' e2e_client_expected e2e_client_entrypoints
if (( ${#bridge_classes[@]} != 23 )); then
  fail "Observer E2E bridge baseline must remain 23 bridges; found ${#bridge_classes[@]}"
fi

declare -A regular_bridge_slugs=()
regular_bridge_count=0

for class in "${!bridge_classes[@]}"; do
  source="$e2e_source_dir/$class.java"
  stem="${class#Observer}"
  stem="${stem%E2eBridge}"

  case "$class" in
    ObserverCraftingE2eBridge|ObserverNexusE2eBridge)
      continue
      ;;
  esac

  slug="$(printf '%s\n' "$stem" | camel_to_kebab)"
  regular_bridge_slugs["$slug"]=1
  regular_bridge_count=$((regular_bridge_count + 1))

  client_test="$client_package.Observer${stem}ClientGameTest"
  if [[ ! -v "client_sources[$client_test]" ]]; then
    fail "$class has no matching $client_test Client GameTest"
  fi

  ready="observer-ready-for-$slug.txt"
  state="target-native-$slug-state-sent.txt"
  ok="observer-native-$slug-ok.txt"
  saved="observer-native-$slug-saved.txt"
  png="observer-native-$slug.png"
  close="target-native-$slug-close-sent.txt"
  closed="observer-native-$slug-closed.txt"

  expect_workflow_evidence "$ready" "$state" "$ok" "$saved" "$png" "$close" "$closed"

  assert_emitted_marker_set "$class ready marker" "$source" \
    'observer-ready-for-[a-z0-9-]+\.txt' "$ready"
  assert_emitted_marker_set "$class state marker" "$source" \
    'target-native-[a-z0-9-]+-state-sent\.txt' "$state"
  assert_emitted_marker_set "$class rendered marker" "$source" \
    'observer-native-[a-z0-9-]+-ok\.txt' "$ok"
  assert_emitted_marker_set "$class screenshot-saved marker" "$source" \
    'observer-native-[a-z0-9-]+-saved\.txt' "$saved"
  assert_source_literal_set "$class screenshot filename" "$source" \
    '"observer-native-[a-z0-9-]+\.png"' "$png"
  assert_emitted_marker_set "$class close-sent marker" "$source" \
    'target-native-[a-z0-9-]+-close-sent\.txt' "$close"
  assert_emitted_marker_set "$class closed marker" "$source" \
    'observer-native-[a-z0-9-]+-closed\.txt' "$closed"
  assert_workflow_evidence "$class" "$ready" "$state" "$ok" "$saved" "$png" "$close" "$closed"
done
if (( regular_bridge_count != 21 )); then
  fail "Observer regular lifecycle baseline must remain 21 bridges; found $regular_bridge_count"
fi

# Crafting intentionally reuses the generic Inventory/container proof. The
# bridge specializes state/priority assertions while ObserverE2eClient owns the
# rest of this seven-file lifecycle.
crafting_source="$e2e_source_dir/ObserverCraftingE2eBridge.java"
driver_source="$e2e_source_dir/ObserverE2eClient.java"
if ! grep -Fq 'if (markerExists("observer-native-generic-screen-saved.txt")' "$driver_source" \
    || ! grep -Fq '&& markerExists("target-native-generic-no-frame.txt")) {' "$driver_source"; then
  fail 'ObserverE2eClient must keep generic Screen open until screenshot and framebuffer-free evidence exist'
fi
crafting_evidence=(
  'observer-ready-for-container.txt'
  'target-native-container-opened.txt'
  'target-native-container-state-sent.txt'
  'observer-native-container-ok.txt'
  'observer-native-container-saved.txt'
  'observer-native-container.png'
  'target-native-container-closed.txt'
  'target-native-crafting-sender-priority-suppressed.txt'
  'observer-native-crafting-mirror-priority-suppressed.txt'
)
if ! grep -Fq 'private static final Class<?> DRIVER = ObserverE2eClient.class;' "$crafting_source"; then
  fail 'ObserverCraftingE2eBridge must continue to extend the ObserverE2eClient container driver'
fi
for marker in "${crafting_evidence[@]}"; do
  if ! grep -Fq "\"$marker\"" "$crafting_source" \
      && ! grep -Fq "\"$marker\"" "$driver_source"; then
    fail "Crafting/container exception does not produce $marker"
  fi
done
assert_emitted_marker_set 'ObserverCraftingE2eBridge target priority proof' "$crafting_source" \
  'target-native-crafting-sender-priority-suppressed\.txt' \
  'target-native-crafting-sender-priority-suppressed.txt'
assert_emitted_marker_set 'ObserverCraftingE2eBridge observer priority proof' "$crafting_source" \
  'observer-native-crafting-mirror-priority-suppressed\.txt' \
  'observer-native-crafting-mirror-priority-suppressed.txt'
assert_exclusive_emitted_marker 'Crafting target priority proof' "$crafting_source" \
  'target-native-crafting-sender-priority-suppressed\.txt'
assert_exclusive_emitted_marker 'Crafting observer priority proof' "$crafting_source" \
  'observer-native-crafting-mirror-priority-suppressed\.txt'
assert_workflow_evidence 'Crafting/container exception' "${crafting_evidence[@]}"
if [[ ! -v "client_sources[$client_package.ObserverUiClientGameTest]" ]]; then
  fail 'Crafting/container exception requires ObserverUiClientGameTest'
fi
if ! grep -Fq '"observer-ui-native-player-inventory-screen.png"' \
    "$client_source_dir/ObserverUiClientGameTest.java"; then
  fail 'ObserverUiClientGameTest must persist the native player-inventory semantic screenshot'
fi

# Nexus is one bridge with five sequential semantic variants and one shared
# close. Its 23-file contract is deliberately separate from the 7-file family
# convention above.
nexus_source="$e2e_source_dir/ObserverNexusE2eBridge.java"
nexus_ready=('observer-ready-for-nexus-compass.txt')
nexus_states=(
  'target-native-nexus-compass-state-sent.txt'
  'target-native-nexus-map-state-sent.txt'
  'target-native-nexus-management-state-sent.txt'
  'target-native-nexus-friends-state-sent.txt'
  'target-native-nexus-registration-state-sent.txt'
)
nexus_ok=(
  'observer-native-nexus-compass-ok.txt'
  'observer-native-nexus-map-ok.txt'
  'observer-native-nexus-management-ok.txt'
  'observer-native-nexus-friends-ok.txt'
  'observer-native-nexus-registration-ok.txt'
)
nexus_saved=(
  'observer-native-nexus-compass-saved.txt'
  'observer-native-nexus-map-saved.txt'
  'observer-native-nexus-management-saved.txt'
  'observer-native-nexus-friends-saved.txt'
  'observer-native-nexus-registration-saved.txt'
)
nexus_png=(
  'observer-native-nexus-compass.png'
  'observer-native-nexus-map.png'
  'observer-native-nexus-management.png'
  'observer-native-nexus-friends.png'
  'observer-native-nexus-registration.png'
)
nexus_close=('target-native-nexus-close-sent.txt')
nexus_closed=('observer-native-nexus-closed.txt')
nexus_evidence=(
  "${nexus_ready[@]}"
  "${nexus_states[@]}"
  "${nexus_ok[@]}"
  "${nexus_saved[@]}"
  "${nexus_png[@]}"
  "${nexus_close[@]}"
  "${nexus_closed[@]}"
)
expect_workflow_evidence "${nexus_evidence[@]}"
assert_emitted_marker_set 'ObserverNexusE2eBridge ready marker' "$nexus_source" \
  'observer-ready-for-[a-z0-9-]+\.txt' "${nexus_ready[@]}"
assert_emitted_marker_set 'ObserverNexusE2eBridge state markers' "$nexus_source" \
  'target-native-[a-z0-9-]+-state-sent\.txt' "${nexus_states[@]}"
assert_emitted_marker_set 'ObserverNexusE2eBridge rendered markers' "$nexus_source" \
  'observer-native-[a-z0-9-]+-ok\.txt' "${nexus_ok[@]}"
assert_source_literal_set 'ObserverNexusE2eBridge screenshot-saved markers' "$nexus_source" \
  '"observer-native-[a-z0-9-]+-saved\.txt"' "${nexus_saved[@]}"
assert_source_literal_set 'ObserverNexusE2eBridge screenshot filenames' "$nexus_source" \
  '"observer-native-[a-z0-9-]+\.png"' "${nexus_png[@]}"
assert_emitted_marker_set 'ObserverNexusE2eBridge close-sent marker' "$nexus_source" \
  'target-native-[a-z0-9-]+-close-sent\.txt' "${nexus_close[@]}"
assert_emitted_marker_set 'ObserverNexusE2eBridge closed marker' "$nexus_source" \
  'observer-native-[a-z0-9-]+-closed\.txt' "${nexus_closed[@]}"
assert_workflow_evidence 'ObserverNexusE2eBridge' "${nexus_evidence[@]}"
for variant in compass map management friends registration; do
  if ! grep -Fq "${variant}RenderBarrier = observeVariant(" "$nexus_source"; then
    fail "ObserverNexusE2eBridge must arm a render-frame barrier for the $variant variant"
  fi
done
if ! grep -Fq 'private record RenderBarrier(long sequence, long frameBaseline)' "$nexus_source" \
    || ! grep -Fq 'ObserverE2eSequenceEvidence.accepted(ObserverNativeScreenPayloads.FAMILY_NEXUS) == barrier.sequence()' "$nexus_source" \
    || ! grep -Fq 'ObserverOwnedScreenCoordinator.renderGeneration()' "$nexus_source" \
    || ! grep -Fq '> barrier.frameBaseline()' "$nexus_source"; then
  fail 'ObserverNexusE2eBridge screenshots must wait for a frame rendered after each received sequence'
fi
if [[ ! -v "client_sources[$client_package.ObserverNexusClientGameTest]" ]]; then
  fail 'ObserverNexusE2eBridge requires ObserverNexusClientGameTest'
fi

# These are the 15 post-Nexus families whose full 7-stage proof used to run
# without being required by CI. Keeping the named baseline here makes the
# 15 x 7 = 105 repaired assertions reviewable, while the source-derived loop
# above makes any future family fail until all seven assertions are added.
late_family_slugs=(
  'villagers-woodcutter'
  'brewing'
  'smithing'
  'stonecutter'
  'grindstone'
  'loom'
  'cartography'
  'beacon'
  'sign'
  'crafter'
  'nexus-death-node-admin'
  'locksmith-management'
  'pause-screen'
  'advancements'
  'stats'
)
declare -A late_family_seen=()
late_evidence_count=0
for slug in "${late_family_slugs[@]}"; do
  add_unique 'late Observer family baseline' late_family_seen "$slug"
  if [[ ! -v "regular_bridge_slugs[$slug]" ]]; then
    fail "late Observer family baseline has no regular bridge for $slug"
  fi
  markers=(
    "observer-ready-for-$slug.txt"
    "target-native-$slug-state-sent.txt"
    "observer-native-$slug-ok.txt"
    "observer-native-$slug-saved.txt"
    "observer-native-$slug.png"
    "target-native-$slug-close-sent.txt"
    "observer-native-$slug-closed.txt"
  )
  assert_workflow_evidence "late Observer family $slug" "${markers[@]}"
  late_evidence_count=$((late_evidence_count + ${#markers[@]}))
done
if (( ${#late_family_seen[@]} != 15 || late_evidence_count != 105 )); then
  fail "late Observer evidence baseline must remain 15 families x 7 files = 105; found ${#late_family_seen[@]} families and $late_evidence_count files"
fi

# This final two-way comparison rejects missing, duplicated (during parsing),
# and stale success assertions. Every accepted marker must be both produced by
# the validated drivers/bridges above and required by the executable condition.
assert_same_set 'Observer E2E workflow success condition' \
  expected_workflow_assertions workflow_success_assertions

if (( failures > 0 )); then
  exit 1
fi

printf '%s\n' \
  "Observer test gate parity passed: ${#client_sources[@]} Client GameTests match their manifest." \
  "Observer E2E parity passed: ${#bridge_classes[@]} bridges plus Common/Client drivers match their manifests." \
  "Observer lifecycle parity passed: $regular_bridge_count regular bridges, Crafting/container and five-variant Nexus exceptions, including 105 late-family evidence files."
