#!/bin/bash
# Unified desktop app launcher for Karakept
# Works in: devcontainer (Linux), native Linux, macOS (with XQuartz)

set -euo pipefail

# --- Display detection ---

# Helper: test X11 connectivity by opening/closing a connection
try_x11() {
    python3 -c "
import socket, os
display = os.environ.get('DISPLAY', '')
if not display:
    exit(1)
num = display.split(':')[-1].split('.')[0]
sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
try:
    sock.connect('/tmp/.X11-unix/X' + num)
    sock.sendall(b'l\x00\x0b\x00\x00\x00\x00\x00\x00\x00\x00\x00')
    resp = sock.recv(8)
    status = resp[0] if resp else 0
    exit(0 if status == 1 else 2)
except Exception:
    exit(1)
finally:
    sock.close()
" 2>/dev/null
}

jdk_supports_wayland() {
    local ver
    ver=$(java -version 2>&1 | head -1 | sed 's/.*"\([0-9]*\).*/\1/')
    [[ "$ver" -ge 24 ]] 2>/dev/null
}

detect_display() {
    # 1. Prefer Wayland if socket is available and JDK has WLToolkit (JDK 24+)
    if [[ -S "/tmp/wayland-0" ]] && jdk_supports_wayland; then
        export WAYLAND_DISPLAY=wayland-0
        export XDG_RUNTIME_DIR=/tmp
        # Unset DISPLAY so JDK picks Wayland, not X11
        unset DISPLAY 2>/dev/null || true
        echo "Using native Wayland display (WLToolkit)"
        return 0
    fi

    # 2. If DISPLAY is set, try X11
    if [[ -n "${DISPLAY:-}" ]]; then
        if try_x11; then
            echo "Using X11 display: $DISPLAY"
            return 0
        fi
        if [[ -S "/tmp/.X11-unix/X${DISPLAY#:}" ]]; then
            echo "ERROR: X11 connection to $DISPLAY refused (authorization required)."
            echo ""
            echo "  Run on your HOST machine:  xhost +local:"
            echo ""
            return 1
        fi
        echo "WARNING: DISPLAY=$DISPLAY but cannot connect. Trying auto-detection..."
    fi

    # 3. Auto-detect from X11 unix sockets
    for sock in /tmp/.X11-unix/X*; do
        [[ -S "$sock" ]] || continue
        local num="${sock##*/X}"
        export DISPLAY=":${num}"
        if try_x11; then
            echo "Auto-detected X11 display: $DISPLAY"
            return 0
        fi
        echo "ERROR: X11 socket found at $sock but authorization failed."
        echo ""
        echo "  Run on your HOST machine:  xhost +local:"
        echo ""
        return 1
    done

    # 4. macOS without DISPLAY — XQuartz instructions
    if [[ "$(uname)" == "Darwin" ]]; then
        echo "ERROR: No X11 display found."
        echo ""
        echo "  macOS requires XQuartz for desktop apps:"
        echo "    1. Install: brew install --cask xquartz"
        echo "    2. Open XQuartz, go to Preferences > Security"
        echo "    3. Check 'Allow connections from network clients'"
        echo "    4. Log out and back in, then retry"
        echo ""
        return 1
    fi

    # 5. Headless fallback with Xvfb
    if command -v Xvfb &>/dev/null; then
        echo "No display found. Starting virtual display (headless mode)..."
        echo "  (The app will run but you won't see a window on your screen)"
        if ! pgrep -x "Xvfb" > /dev/null; then
            Xvfb :99 -screen 0 1280x720x24 &
            sleep 1
        fi
        export DISPLAY=:99
        return 0
    fi

    # 6. Nothing works
    echo "ERROR: No display available."
    echo ""
    echo "  Options:"
    echo "    - Linux devcontainer: ensure Wayland or X11 socket is mounted"
    echo "    - For X11: run 'xhost +local:' on host"
    echo "    - macOS: install XQuartz (see above)"
    echo "    - Headless: install Xvfb (apt install xvfb)"
    echo ""
    return 1
}

if ! detect_display; then
    exit 1
fi

# --- D-Bus session setup ---
# The Linux system tray (energye/systray via ComposeNativeTray) requires a
# D-Bus session bus **with a StatusNotifierHost** (e.g. GNOME Shell, KDE Plasma).
# Without DBUS_SESSION_BUS_ADDRESS the native bridge panics with a nil-pointer
# dereference, so we always need a bus.
#
# In a devcontainer the host's D-Bus is not forwarded by default. We try to
# locate the host's D-Bus socket at common paths before falling back to
# dbus-launch (which starts an empty bus with no StatusNotifierHost).
if [[ -z "${DBUS_SESSION_BUS_ADDRESS:-}" ]]; then
    host_dbus_found=false
    # Try standard XDG runtime dir locations (host socket may be bind-mounted)
    for uid_dir in /run/user/*; do
        if [[ -S "$uid_dir/bus" ]]; then
            export DBUS_SESSION_BUS_ADDRESS="unix:path=$uid_dir/bus"
            echo "Using host D-Bus session at $uid_dir/bus"
            host_dbus_found=true
            break
        fi
    done
    if [[ "$host_dbus_found" == "false" ]]; then
        if command -v dbus-launch &>/dev/null; then
            eval "$(dbus-launch --sh-syntax)"
            echo "Started D-Bus session for system tray support"
            echo "  Note: tray icon requires a StatusNotifierHost (desktop panel)."
            echo "  In a devcontainer, mount the host D-Bus socket for tray support:"
            echo "    docker run -v /run/user/\$(id -u)/bus:/run/user/\$(id -u)/bus ..."
        else
            echo "INFO: dbus-launch not found; system tray icon will be disabled."
            echo "      Install with: sudo apt-get install -y dbus"
        fi
    fi
fi

echo "Launching desktop app..."
exec ./gradlew run
