#!/bin/bash
# Script to run the desktop app with X11 forwarding to host display

# Use the host's X11 display (forwarded from Wayland via XWayland)
export DISPLAY=:0
export XAUTHORITY=/tmp/.X11-unix/Xauthority

# Force software rendering for compatibility in container
export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe
export MESA_GL_VERSION_OVERRIDE=3.3
export MESA_GLSL_VERSION_OVERRIDE=330

echo "Running desktop app with X11 display forwarding..."
echo "DISPLAY=$DISPLAY"

./gradlew run
