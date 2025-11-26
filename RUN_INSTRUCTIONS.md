# How to Run Karakept from the Devcontainer

## Summary

I've successfully restored the Gradle wrapper and fixed the build configuration. The project now builds successfully!

## ✅ What Was Fixed

1. **Installed Gradle 8.9** using SDKMAN (required minimum 8.7 for Android Gradle Plugin)
2. **Generated Gradle wrapper** (`gradlew`, `gradle-wrapper.jar`, `gradle-wrapper.properties`)
3. **Fixed dependency issue** - Removed incompatible `ktor-client-darwin` from desktop target
4. **Added missing imports** in `AppModule.kt`
5. **Updated compileSdk to 35** as required by Room library
6. **Fixed Android icon** reference in manifest

## 🚀 Running the Application

### Option 1: Android (Recommended for Devcontainer)

The Android build is now working. To install on a device or emulator:

#### Debug Build (Development)
```bash
# Build the debug APK
./gradlew assembleDebug

# The APK will be at:
# composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

#### Release Build (Optimized)
```bash
# Build the release APK (uses debug signing for testing)
./gradlew assembleRelease

# The APK will be at:
# composeApp/build/outputs/apk/release/composeApp-release.apk
```

> **Note**: The release build currently uses debug signing for convenience. For production deployment, you should configure proper release signing in `composeApp/build.gradle.kts`.

To install on a connected device/emulator:
```bash
# Install debug version
./gradlew installDebug

# Or install release version
./gradlew installRelease
```

**Note**: You need to connect an Android device via ADB or set up an emulator. From outside the devcontainer, you can use:
```bash
adb connect <device-ip>
```

### Option 2: Desktop (Requires X11 Display)

The desktop version **cannot run directly in the devcontainer** because it requires a graphical display (X11). 

You have two options:

#### A. Run with X11 Forwarding (Advanced)
```bash
# This requires setting up X11 forwarding from your host
export DISPLAY=:0
./gradlew run
```

#### B. Build and Run on Host Machine
```bash
# Build a distributable package
./gradlew packageDistributionForCurrentOS

# The output will be in:
# composeApp/build/compose/binaries/main/
```

## 📋 Other Useful Commands

```bash
# List all available tasks
./gradlew tasks

# Build without running
./gradlew build

# Clean build
./gradlew clean

# Run tests
./gradlew test
```

## 🔧 Troubleshooting

### "No connected devices" error
- Connect an Android device via USB and enable USB debugging
- Or use `adb connect` to connect to a device over network
- Or set up an Android emulator (requires additional setup)

### Desktop app fails with "HeadlessException"
- This is expected in a devcontainer without X11
- Use the Android build instead, or build a distributable and run on your host machine

### Gradle daemon issues
```bash
./gradlew --stop  # Stop all Gradle daemons
```
