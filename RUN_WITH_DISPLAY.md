# Running Desktop App with Display on Host Screen

## Quick Start

Run this command to display the app on your screen:

```bash
./run_desktop_host.sh
```

The app window should appear on your host screen!

## How It Works

- **Your setup**: Wayland compositor on host
- **XWayland**: X11 compatibility layer (automatically runs on Wayland)
- **Compose Desktop**: Uses AWT/Swing which requires X11
- **Solution**: App connects to XWayland (`:0`) which forwards to your Wayland display

## Scripts Available

- **`./run_desktop_host.sh`** - Display on your host screen (recommended)
- **`./run_desktop.sh`** - Run in virtual display (headless, for testing)

## Troubleshooting

If the window doesn't appear, you may need to allow container access to your display:

```bash
# On your host machine (outside container)
xhost +local:
```

Then try running the app again in the container.

## Technical Details

The devcontainer already has:
- X11 socket mounted: `/tmp/.X11-unix`
- Wayland socket mounted: `/tmp/wayland-0`
- Environment configured: `DISPLAY=:0`

The app uses XWayland (X11 on Wayland) because Compose Desktop is built on AWT/Swing, which doesn't support pure Wayland.
