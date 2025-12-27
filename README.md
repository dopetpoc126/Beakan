# Beakan

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-purple.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4.svg?style=flat&logo=android)](https://developer.android.com/jetpack/compose)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg?style=flat&logo=android)](https://android-arsenal.com/api?level=26)

**Beakan** is a proof-of-concept Android application that demonstrates **Live Updates**, a new system-level notification paradigm introduced in Android 16.

This project intercepts active media sessions from any compatible application (Spotify, YouTube Music, Audible, etc.) and surfaces playback information directly within the system status bar using rich, animated Promoted Notifications.

---

## Technical Overview

Android 16 introduces `PromotedNotifications`, a mechanism that allows high-priority, contextually-relevant data to be rendered in the system's status bar area. This provides users with glanceable, real-time information without needing to expand the notification shade.

Beakan leverages this by:

1.  **Binding to System Notification Stream**: A `NotificationListenerService` intercepts all incoming notifications.
2.  **Filtering for Media Sessions**: The service identifies notifications containing `Notification.EXTRA_MEDIA_SESSION` or `MediaStyle` templates.
3.  **Extracting Live Metadata**: Using the `MediaController` API, it queries `MediaMetadata` (Title, Artist, Duration, Album Art) and `PlaybackState` (Position, Play/Pause state) in real-time.
4.  **Publishing Promoted Notifications**: The extracted data is rendered into a custom notification with `CATEGORY_TRANSPORT` and posted with `FOREGROUND_SERVICE_MEDIA_PLAYBACK` to ensure system-level visibility.

## Core Features

-   **System Status Bar Integration**: Displays current track information as a compact, persistent chip in the status bar.
-   **Real-Time Playback Synchronization**: Progress indicators update every second, with instant reflection of play/pause/skip actions.
-   **Universal Media App Compatibility**: Operates via the standard Android MediaSession API, ensuring compatibility across all well-behaved media applications.
-   **Album Art & Action Passthrough**: Extracts and displays album artwork; passes through native playback actions (Previous, Play/Pause, Next) from the source app.
-   **Low Latency Source Switching**: Automatically detects and switches to the currently active media source with sub-300ms latency.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Application Layer                            │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │   MainActivity.kt (Jetpack Compose UI, Permissions Flow)   │    │
│  └─────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────┐
│                          Service Layer                              │
│  ┌───────────────────────────┐   ┌────────────────────────────┐    │
│  │ MediaNotificationListener │──▶│   NotificationPublisher    │    │
│  │ (NotificationListenerSvc) │   │   (Promoted Notification)  │    │
│  └───────────────────────────┘   └────────────────────────────┘    │
│           │                                                         │
│           ▼                                                         │
│  ┌───────────────────────────┐                                      │
│  │     MediaController       │                                      │
│  │  (Metadata & Playback)    │                                      │
│  └───────────────────────────┘                                      │
└─────────────────────────────────────────────────────────────────────┘
```

-   **`MediaNotificationListener.kt`**: The core service. Listens for media notifications, manages `MediaController` callbacks, debounces updates, and handles source-switching logic.
-   **`NotificationPublisher.kt`**: Responsible for constructing and posting the promoted notification with `MediaStyle`, remote views, and action intents.
-   **`MainActivity.kt`**: Provides the user-facing configuration screen, permission management, and in-app animations demonstrating the feature.

## Tech Stack

| Component          | Technology                                                                 |
| :----------------- | :------------------------------------------------------------------------- |
| Language           | Kotlin 1.9 (JVM Target 1.8)                                                |
| UI Framework       | Jetpack Compose (Material 3)                                               |
| Build System       | Gradle (Kotlin DSL)                                                        |
| Min SDK            | 26 (Android 8.0 Oreo)                                                      |
| Target SDK         | 36 (Android 16)                                                            |
| Core APIs          | `NotificationListenerService`, `MediaController`, `MediaSession`, `Notification.MediaStyle` |

## Getting Started

### Prerequisites

-   Android Studio Ladybug (or newer).
-   A physical device or emulator running Android 8.0+ (API 26).

### Build & Run

```bash
# Clone the repository
git clone https://github.com/yourusername/beakan.git
cd beakan

# Build and install via Gradle
./gradlew installDebug
```

### Required Permissions

On first launch, the application will guide you to grant **Notification Listener Access**.

> This permission is mandatory for the `NotificationListenerService` to function. The application only reads metadata from `MediaStyle` notifications; no other notification content is accessed or stored.

<!--## Demonstration

| Configuration Screen | Live Update in Action |
|:--------------------:|:---------------------:|
| *(Screenshot)*       | *(Recording)*         |-->

## Contributing

Contributions, issues, and feature requests are welcome.

1.  Fork the repository.
2.  Create a feature branch: `git checkout -b feature/your-feature-name`
3.  Commit your changes: `git commit -m 'feat: Add new feature'`
4.  Push to the branch: `git push origin feature/your-feature-name`
5.  Open a Pull Request.

## License

Distributed under the MIT License. See `LICENSE` for more information.
