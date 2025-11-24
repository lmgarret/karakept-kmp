# Running Desktop App in Dev Container - FIXED

The desktop app can now run successfully in the dev container! The issue was a **Skiko native library version mismatch** that has been resolved.

## What Was Fixed

1. **Skiko Version Mismatch**: The app was using Skiko 0.9.4.2 for the core library but 0.8.15 for the native runtime, causing `UnsatisfiedLinkError: RenderNodeContext_nMake`
2. **Dependency Resolution**: Added forced dependency resolution in `composeApp/build.gradle.kts` to ensure all Skiko components use version 0.9.4.2
3. **Graphics Environment**: Configured Xvfb (virtual framebuffer) for headless rendering in the container

## How to Run the Desktop App

### Quick Start (Recommended)

Simply run the provided script:

```bash
./run_desktop.sh
```

This script automatically:
- Starts Xvfb virtual display if not running
- Sets required environment variables for software rendering
- Runs the desktop app

### Manual Method

If you prefer to run manually:

```bash
# 1. Start Xvfb virtual display (if not already running)
Xvfb :99 -screen 0 1024x768x24 &

# 2. Set environment variables and run
export DISPLAY=:99
export LIBGL_ALWAYS_SOFTWARE=1
./gradlew run
```

## Technical Details

### Changes Made

#### 1. Updated `composeApp/build.gradle.kts`

Added dependency resolution strategy to force consistent Skiko versions:

```kotlin
// Force Skiko version to resolve version mismatch
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.9.4.2")
        force("org.jetbrains.skiko:skiko:0.9.4.2")
        force("org.jetbrains.skiko:skiko-awt:0.9.4.2")
    }
}
```

#### 2. Installed Required Packages

The following packages were installed in the dev container:
- `xvfb` - Virtual framebuffer for headless X server
- `mesa-utils` - Mesa 3D graphics utilities
- `libxcursor1`, `libxrandr2`, `libxi6`, `libxxf86vm1` - X11 libraries

#### 3. Environment Variables

For software rendering in the container:
- `DISPLAY=:99` - Points to the Xvfb virtual display
- `LIBGL_ALWAYS_SOFTWARE=1` - Forces Mesa software rendering
- `GALLIUM_DRIVER=llvmpipe` - Uses LLVM pipe for software rasterization

### Why This Works

Dev containers don't have access to physical GPUs, so Compose Desktop's Skiko graphics library needs:
1. **Correct native library versions** - All Skiko components must match
2. **Virtual display** - Xvfb provides a headless X server
3. **Software rendering** - Mesa's llvmpipe provides CPU-based OpenGL

## Troubleshooting

### If the app doesn't start

1. **Check if Xvfb is running**:
   ```bash
   pgrep Xvfb
   ```

2. **Restart Xvfb**:
   ```bash
   pkill Xvfb
   Xvfb :99 -screen 0 1024x768x24 &
   ```

3. **Clear Gradle cache** (if you see version conflicts):
   ```bash
   ./gradlew clean
   rm -rf ~/.gradle/caches
   ```

4. **Clear Skiko cache**:
   ```bash
   rm -rf ~/.skiko
   ```

### If you see "UnsatisfiedLinkError"

This means the Skiko native libraries are mismatched. Run:
```bash
./gradlew clean
rm -rf ~/.skiko
./run_desktop.sh
```

## Notes

- The app runs in a **virtual display**, so you won't see the GUI directly in the container
- This setup is primarily for **testing and development** purposes
- For actual GUI interaction, consider running the app on your host machine or using X11/Wayland forwarding (see `WAYLAND_SETUP.md`)
- The Xvfb display is set to 1024x768 resolution - you can modify this in `run_desktop.sh` if needed

## Why No Window Appears

When you run `./run_desktop.sh`, the app successfully starts and runs, but you won't see a window because:

1. **Virtual Display**: The app renders to Xvfb (virtual framebuffer) at display `:99`, not your actual screen
2. **Container Isolation**: The dev container doesn't have direct access to your host's display server

### Options to See the GUI

#### Option 1: Run on Host Machine (Recommended for GUI Testing)

The simplest way to see and interact with the GUI is to run the app directly on your host machine:

```bash
# On your host machine (not in the container)
./gradlew run
```

#### Option 2: X11/Wayland Forwarding (Advanced)

Follow the instructions in `WAYLAND_SETUP.md` to set up display forwarding from the container to your host. This requires:
- Rebuilding the dev container with proper mounts
- Configuring X11/Wayland permissions on your host
- Setting the `DISPLAY` environment variable to point to your host display

#### Option 3: VNC Server (For Remote Access)

If you need to see the GUI running in the container, you can set up a VNC server:

```bash
# Install VNC server
sudo apt-get install -y x11vnc

# Start VNC server connected to Xvfb
x11vnc -display :99 -forever -nopw -listen 0.0.0.0 -xkb &

# Then connect with a VNC client to localhost:5900
```

## Verifying the App is Running

Even though you can't see the window, you can verify the app is running successfully:

```bash
# Check if the process is running
ps aux | grep java

# Check if it's connected to the virtual display
echo $DISPLAY
# Should show: :99

# The app is working if you see:
# - No errors in the terminal
# - Gradle shows "> :composeApp:run" and stays at 95% EXECUTING
```

To stop the app, press `Ctrl+C` in the terminal.
