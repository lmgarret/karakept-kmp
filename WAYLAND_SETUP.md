# Running Desktop App with Wayland Forwarding

I've updated the devcontainer configuration to support Wayland forwarding. Here's what you need to do:

## Steps to Enable GUI Forwarding

### 1. Rebuild the Devcontainer

Since the devcontainer configuration has changed, you need to rebuild it:

1. Press `F1` or `Ctrl+Shift+P` in VS Code
2. Type and select: **"Dev Containers: Rebuild Container"**
3. Wait for the container to rebuild

### 2. Verify Environment Variables

After rebuilding, check that the environment variables are set:

```bash
echo $WAYLAND_DISPLAY
echo $XDG_RUNTIME_DIR
```

### 3. Run the Desktop App

```bash
./gradlew run
```

## Troubleshooting

### If you get permission errors:

On your **host machine** (not in the container), run:
```bash
# Allow container to access Wayland socket
xhost +local:
```

### If Wayland socket is not found:

Check your Wayland display socket location on the host:
```bash
echo $XDG_RUNTIME_DIR/$WAYLAND_DISPLAY
```

If it's different from the default, you may need to adjust the mount path in `.devcontainer/devcontainer.json`.

### Alternative: Use X11 Compatibility Layer

If Wayland forwarding doesn't work, you can use XWayland:

On your **host machine**:
```bash
# Install xhost if not already installed
sudo apt install x11-xserver-utils

# Allow container access
xhost +local:docker
```

Then in the container:
```bash
export DISPLAY=:0
./gradlew run
```

## What I Changed

I updated `.devcontainer/devcontainer.json` to:
- Mount the Wayland socket from your host into the container
- Mount X11 socket as a fallback
- Set appropriate environment variables (`WAYLAND_DISPLAY`, `XDG_RUNTIME_DIR`, etc.)

After rebuilding the container, the desktop app should be able to display on your Wayland session!
