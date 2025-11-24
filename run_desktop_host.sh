#!/bin/bash
# Script to run the desktop app using XWayland (X11 on Wayland)
# This displays the app on your host screen

# Use the XWayland display from host
export DISPLAY=:0

# Force software rendering (container has no GPU access)
export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe
export MESA_GL_VERSION_OVERRIDE=3.3
export MESA_GLSL_VERSION_OVERRIDE=330

echo "Running desktop app with XWayland display forwarding..."
echo "DISPLAY=$DISPLAY"
echo "The app window should appear on your host screen!"

./gradlew run
