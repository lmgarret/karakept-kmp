#!/bin/bash

# Ensure gradlew is executable
chmod +x gradlew

# Ensure the SDK directory is owned by the current user
# The feature installs it as root, but we need it to be writable for builds/updates
sudo chown -R $(whoami) $ANDROID_HOME

# Fix permissions for volume-mounted directories
sudo chown -R $(whoami) $HOME/.android
sudo chown -R $(whoami) $HOME/.gemini

# Set up Flatpak for building and running the Linux desktop app
flatpak remote-add --user --if-not-exists flathub https://flathub.org/repo/flathub.flatpakrepo
flatpak install --user -y --noninteractive flathub org.gnome.Platform//46 org.gnome.Sdk//46

echo "Setup complete!"
