#!/bin/bash
# Script to run the desktop app with Wayland display forwarding to host

# Use the host's Wayland display
export WAYLAND_DISPLAY=wayland-0
export XDG_RUNTIME_DIR=/tmp
export QT_QPA_PLATFORM=wayland

# Disable Xvfb - we're using real Wayland
unset DISPLAY

# Force software rendering for compatibility
export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe
export MESA_GL_VERSION_OVERRIDE=3.3
export MESA_GLSL_VERSION_OVERRIDE=330

echo "Running desktop app with Wayland display forwarding..."
echo "WAYLAND_DISPLAY=$WAYLAND_DISPLAY"
echo "XDG_RUNTIME_DIR=$XDG_RUNTIME_DIR"

./gradlew run
