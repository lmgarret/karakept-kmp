#!/bin/bash

# Ensure gradlew is executable
chmod +x gradlew

# Ensure the SDK directory is owned by the current user
# The feature installs it as root, but we need it to be writable for builds/updates
sudo chown -R $(whoami) $ANDROID_HOME

# Fix permissions for volume-mounted directories
sudo chown -R $(whoami) $HOME/.android
sudo chown -R $(whoami) $HOME/.gemini

# Set up Flatpak for building and running the Linux desktop app.
# remote-add doesn't need dbus, but install does — use dbus-run-session.
sudo service dbus start 2>/dev/null || true
flatpak remote-add --user --if-not-exists flathub https://flathub.org/repo/flathub.flatpakrepo
dbus-run-session -- flatpak install --user -y --noninteractive flathub org.gnome.Platform//48 org.gnome.Sdk//48

echo "Setup complete!"
