#!/bin/bash
# Script to run the desktop app in dev container with virtual display

# Start Xvfb if not already running
if ! pgrep -x "Xvfb" > /dev/null; then
    echo "Starting Xvfb virtual display..."
    Xvfb :99 -screen 0 1024x768x24 &
    sleep 2
fi

# Set environment variables for software rendering
export DISPLAY=:99
export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe
export MESA_GL_VERSION_OVERRIDE=3.3
export MESA_GLSL_VERSION_OVERRIDE=330
# Disable hardware acceleration for Skiko
export SKIKO_RENDER_API=SOFTWARE

echo "Running desktop app with virtual display..."
./gradlew run
