# Dev Container Setup

## Features Included

The dev container is configured with the following features:

### 1. Android SDK
- NDK and CMake for native Android development
- Configured via `ghcr.io/CASL0/devcontainer-features/android-sdk:1`

### 2. Node.js & npm
- LTS version of Node.js
- npm package manager
- Configured via `ghcr.io/devcontainers/features/node:1`

### 3. System Packages
Graphics and development libraries for desktop app support:
- **Graphics**: `xvfb`, `mesa-utils` (for software rendering)
- **X11 Libraries**: `libxcursor1`, `libxrandr2`, `libxi6`, `libxxf86vm1`
- **Development**: `libgtk-3-dev`, `libwebkit2gtk-4.1-dev`
- **Utilities**: `curl`, `nano`, `vim`, `ninja-build`, `python3`

## Rebuilding the Container

After modifying `.devcontainer/devcontainer.json`, you need to rebuild:

1. Press `F1` or `Ctrl+Shift+P`
2. Type and select: **"Dev Containers: Rebuild Container"**
3. Wait for the rebuild to complete

## Display Configuration

The container mounts:
- **X11 socket**: `/tmp/.X11-unix` (for XWayland)
- **Wayland socket**: `/tmp/wayland-0` (mounted from host)

Environment variables:
- `DISPLAY`: Set to host display (`:0`)
- `WAYLAND_DISPLAY`: Set to `wayland-0`
- `XDG_RUNTIME_DIR`: Set to `/tmp`

## Running the Desktop App

Use the provided script:
```bash
./run_desktop_host.sh
```

This will display the app on your host screen via XWayland.
