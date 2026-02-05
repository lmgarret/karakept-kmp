#!/bin/bash

# Ensure gradlew is executable
chmod +x gradlew

# Ensure the SDK directory is owned by the current user
# The feature installs it as root, but we need it to be writable for builds/updates
sudo chown -R $(whoami) $ANDROID_HOME

# Fix permissions for volume-mounted directories
sudo chown -R $(whoami) $HOME/.android
sudo chown -R $(whoami) $HOME/.gemini

echo "Setup complete!"
