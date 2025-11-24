#!/bin/bash

# Ensure gradlew is executable
chmod +x gradlew

# Accept Android licenses (just in case the feature didn't cover all)
yes | sdkmanager --licenses || true

echo "Setup complete!"
