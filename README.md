# Karakept - Kotlin Multiplatform Bookmark Manager

A Kotlin Multiplatform application for managing bookmarks with support for Android and Desktop (JVM).

## 🚀 Running the Application

### Prerequisites

- The project uses Gradle wrapper, so no need to install Gradle separately
- For Android: Android SDK (automatically configured in devcontainer)
- For Desktop: X11 or Wayland display (see GUI forwarding setup below)

### Option 1: Android (Recommended for Devcontainer)

#### Debug Build (Development)
Build the Android APK:
```bash
./gradlew assembleDebug
```

The APK will be located at: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`

Install on a connected device or emulator:
```bash
./gradlew installDebug
```

#### Release Build (Optimized)
Build the release APK:
```bash
./gradlew assembleRelease
```

The APK will be located at: `composeApp/build/outputs/apk/release/composeApp-release.apk`

> **Note**: The release build uses debug signing for convenience. For production deployment, configure proper release signing in `composeApp/build.gradle.kts`.

Install release version:
```bash
./gradlew installRelease
```

### Option 2: Desktop with Wayland/X11 Forwarding

#### Initial Setup (One-time)

1. **Update devcontainer configuration** (already done if using this repo):
   - The `.devcontainer/devcontainer.json` includes Wayland socket mounts
   - Rebuild the container: Press `F1` → **"Dev Containers: Rebuild Container"**

2. **On your host machine**, allow container access:
   ```bash
   xhost +local:
   ```

#### Running the Desktop App

After the devcontainer is rebuilt:
```bash
./gradlew run
```

#### Troubleshooting Desktop GUI

**If you get permission errors:**
```bash
# On host machine
xhost +local:docker
```

**If Wayland socket is not found:**

Check your socket location:
```bash
echo $XDG_RUNTIME_DIR/$WAYLAND_DISPLAY
```

**Alternative: Use X11 fallback:**
```bash
export DISPLAY=:0
./gradlew run
```

### Option 3: Build Distributable Package

To create a distributable package (requires additional dependencies):
```bash
./gradlew packageDistributionForCurrentOS
```

Note: This requires `fakeroot` and other packaging tools. On the host machine:
```bash
sudo apt install fakeroot
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

# Stop Gradle daemon
./gradlew --stop
```

## 🛠️ Development Setup

### Using Devcontainer (Recommended)

This project includes a complete devcontainer configuration with:
- Java 17
- Android SDK with NDK
- Gradle 8.9
- All required development tools

Simply open the project in VS Code and select **"Reopen in Container"**.

### Manual Setup

If not using devcontainer:

1. Install Java 17 or later
2. Install Android SDK
3. Set environment variables:
   ```bash
   export ANDROID_HOME=/path/to/android-sdk
   export ANDROID_SDK_ROOT=$ANDROID_HOME
   ```

## 🏗️ Project Structure

```
karakept-kmp/
├── composeApp/          # Main application module
│   ├── src/
│   │   ├── commonMain/  # Shared code
│   │   ├── androidMain/ # Android-specific code
│   │   └── desktopMain/ # Desktop-specific code
│   └── build.gradle.kts
├── .devcontainer/       # Devcontainer configuration
└── gradle/              # Gradle wrapper and version catalog
```

## 📦 Dependencies

- **Kotlin Multiplatform**: 2.1.0
- **Compose Multiplatform**: 1.7.0
- **Ktor**: 3.3.2 (HTTP client)
- **Room**: 2.7.0-alpha11 (Database)
- **Voyager**: 1.1.0-beta03 (Navigation)
- **Koin**: 4.0.0 (Dependency injection)
- **Coil**: 3.3.0 (Image loading)

## 🐛 Known Issues

- **Desktop app in devcontainer**: Requires GUI forwarding setup (see above)
- **compileSdk warning**: Using compileSdk 35 with AGP 8.5.2 (warning can be suppressed)

## 📄 License

[Add your license here]

## 🤝 Contributing

[Add contribution guidelines here]
