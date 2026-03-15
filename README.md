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

### Option 2: Desktop App

#### One-time host setup

**Linux** (X11 or Wayland — required for devcontainer GUI forwarding):
```bash
xhost +local:
```

**macOS**: Install [XQuartz](https://www.xquartz.org/), enable "Allow connections from network clients" in Preferences > Security, then log out and back in.

#### Running

```bash
./run_desktop.sh
```

The script auto-detects your display environment (X11 forwarding, XWayland, or headless via Xvfb) and launches the app.

### Option 3: Build Distributable Package

To create a distributable package (requires additional dependencies):
```bash
./gradlew packageDistributionForCurrentOS
```

Note: This requires `fakeroot` and other packaging tools. On the host machine:
```bash
sudo apt install fakeroot
```

## 🧪 Testing

Karakept uses a multi-layered testing strategy to ensure reliability across platforms.

### 1. Fast Unit Tests (`commonTest`)
These tests run without any external dependencies (mocked) and provide sub-second feedback for core business logic.
```bash
./gradlew :composeApp:test
```

### 2. Integration Tests (`desktopTest`)
These tests orchestrate a local Karakeep environment using Docker Compose to verify full end-to-end flows.
*   **Requirements**: Docker and `docker-compose` must be installed.
*   **Authentication**: The test automatically registers a unique test user and provisions an API key via tRPC.

Run integration tests:
```bash
./gradlew :composeApp:desktopTest
```

### 3. Tips for Testing
*   **Incremental Builds**: Gradle marks tests as `UP-TO-DATE` if nothing changed. To force a fresh run, use:
    ```bash
    ./gradlew desktopTest --rerun-tasks
    ```
*   **Detailed Output**: To see registration steps and Docker logs in the terminal:
    ```bash
    ./gradlew desktopTest --info
    ```
*   **HTML Reports**: View detailed reports in your browser:
    `composeApp/build/reports/tests/desktopTest/index.html`

## 📋 Other Useful Commands

```bash
# List all available tasks
./gradlew tasks

# Build without running
./gradlew build

# Clean build
./gradlew clean

# Run tests (alias for all targets)
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

## Roadmap

### Native macOS Build

Goal: ship Karakept as a native macOS app (Kotlin/Native, no JVM dependency).

**Library migrations required:**

| Library | Current (JVM) | KMP Replacement | Status |
|---------|--------------|-----------------|--------|
| HTML parsing | JSoup | [Ksoup](https://github.com/fleeksoft/ksoup) | Done |
| Database | Room (alpha KMP) | SQLDelight | Planned |
| HTTP engine | OkHttp | Ktor Darwin engine | Planned |
| HTML rendering | JavaFX WebView | WKWebView (Kotlin/Native interop) | Planned |
| DataStore | AndroidX DataStore | File-based / NSUserDefaults | Planned |
| File I/O | `java.io.File` | Kotlin/Native Foundation APIs | Planned |

**Platform implementations needed (expect/actual):**
- Database builder (`Database.kt`)
- Preferences storage (`DataStoreFactory.kt`)
- Platform detection & cache dir (`Platform.kt`)
- File operations (`FileUtils.kt`)
- Share functionality (`ShareUtils.kt`)
- Haptic feedback (`HapticUtils.kt`)
- HTML display (`HtmlRenderer.kt`)
- Back navigation (`BackHandler.kt`)
- URL opening (`CustomTabOpener.kt`)
- Dynamic theme colors (`PlatformTheme.kt`)
- Notification permissions (`NotificationPermissionRequest.kt`)

## 📄 License

[Add your license here]

## 🤝 Contributing

[Add contribution guidelines here]
