#!/usr/bin/env bash
set -euo pipefail

display="${TOTEM_GLX_DISPLAY:-:99}"
config_dir="$(mktemp -d)"
config="$config_dir/xorg.conf"
log="$config_dir/xorg.log"
cat >"$config" <<'EOF'
Section "ServerLayout"
    Identifier "TotemLayout"
    Screen 0 "TotemScreen"
EndSection

Section "Device"
    Identifier "TotemDummy"
    Driver "dummy"
    VideoRam 256000
EndSection

Section "Monitor"
    Identifier "TotemMonitor"
    HorizSync 30-80
    VertRefresh 50-75
EndSection

Section "Screen"
    Identifier "TotemScreen"
    Device "TotemDummy"
    Monitor "TotemMonitor"
    DefaultDepth 24
    SubSection "Display"
        Depth 24
        Modes "1280x720"
    EndSubSection
EndSection
EOF

sudo Xorg "$display" -ac -noreset -config "$config" +extension GLX +extension RANDR >"$log" 2>&1 &
xorg_pid=$!
cleanup() {
    set +e
    sudo kill "$xorg_pid" 2>/dev/null || true
    wait "$xorg_pid" 2>/dev/null || true
    rm -rf "$config_dir"
}
trap cleanup EXIT

ready=false
for _ in $(seq 1 30); do
    if DISPLAY="$display" xdpyinfo >/dev/null 2>&1; then
        ready=true
        break
    fi
    if ! kill -0 "$xorg_pid" 2>/dev/null; then
        cat "$log" >&2 || true
        exit 1
    fi
    sleep 1
done
if [[ "$ready" != true ]]; then
    cat "$log" >&2 || true
    exit 1
fi

DISPLAY="$display" "$@"

