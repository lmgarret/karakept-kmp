#!/bin/bash
# Script to setup Android SDK locally if not using devcontainer

export ANDROID_HOME=$HOME/android-sdk
mkdir -p $ANDROID_HOME

if [ ! -d "$ANDROID_HOME/cmdline-tools" ]; then
    echo "Downloading Android Command Line Tools..."
    wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
    unzip cmdline-tools.zip -d $ANDROID_HOME/cmdline-tools
    mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest
    rm cmdline-tools.zip
fi

export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin

echo "Accepting licenses..."
yes | sdkmanager --licenses

echo "Installing platforms and build-tools..."
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

echo "Android SDK setup complete at $ANDROID_HOME"
echo "Please add 'export ANDROID_HOME=$HOME/android-sdk' to your shell profile."
