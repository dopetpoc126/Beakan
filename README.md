# Beakan

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-purple.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg?style=flat&logo=android)](https://android-arsenal.com/api?level=26)
[![Service](https://img.shields.io/badge/Architecture-Service%20Layer-orange.svg?style=flat&logo=android)](https://developer.android.com/guide/components/services)

**Beakan** is a backend-driven notification interception engine designed to demonstrate **Live Activities** on Android. It functions as a system-level broker that intercepts high-priority event streams—authentication codes, download progress, and media sessions—and re-surfaces them as unified, persistent status indicators.

---

## Architecture Overview

The core of Beakan is a background `NotificationListenerService` that operates independently of the UI. It functions as a state machine that ingests raw system notifications, parses them for actionable data, and arbitrates their display priority.

```mermaid
graph TD
    System[System Notification Stream] -->|Intercept| Listener[MediaNotificationListener]
    
    Listener -->|Route| Extractor{Extraction Engine}
    
    Extractor -->|Regex/ NLP| OTP[OtpExtractor]
    Extractor -->|Bundle Analysis| DL[DownloadTracker]
    Extractor -->|Session Token| Media[MediaController]
    
    OTP -->|State Update| Manager[LiveActivityManager]
    DL -->|State Update| Manager
    Media -->|State Update| Manager
    
    Manager -->|Arbitration (Priority Logic)| Publisher[NotificationPublisher]
    Publisher -->|Post| SystemBar[System Status Bar]
```

## Core Engineering Modules

### 1. Interception & Filtering (`MediaNotificationListener`)
The service binds to the Android System via `bindService`, gaining read access to the notification stream. It implements a highly optimized filtering pipeline:
-   **Debouncing**: Prevents rapid-fire updates (e.g., from high-frequency download progress) from flooding the main thread.
-   **Source Classification**: Determining notification type (SMS vs System Download vs Third-party App) via package parsing and `Notification.Category` analysis.

### 2. Multi-Stage Regex Extraction (`OtpExtractor`)
For parsing 2FA codes, Beakan utilizes a local, non-networked extraction engine:
-   **Heuristic Keyword Matching**: Scans `tickerText` and `extras` for high-entropy tokens indicative of authentication (e.g., "code", "pin", "login").
-   **Pattern Recognition**: Applies a staggered set of Regex patterns (Anchor-based vs. Floating) to isolate numeric sequences (4-8 digits) while ignoring phone numbers and tracking IDs.
-   **Security**: All processing occurs in-memory; no notification content is persisted to disk.

### 3. State Arbitration Engine (`LiveActivityManager`)
Beakan manages multiple concurrent data streams using a priority-based state machine. The `LiveActivityManager` ensures that the most critical information is prioritized without user intervention:

| Priority | Activity Type | Logic |
| :--- | :--- | :--- |
| **P0 (Critical)** | **OTP / 2FA** | Overrides all other states. Self-dismisses after 30s expiry. |
| **P1 (Active)** | **Downloads** | Active only while progress < 100%. Interrupts media. |
| **P2 (Passive)** | **Media** | Default state. Persists as long as a session is active. |

### 4. Low-Latency Media Binding
Instead of relying solely on notification extras, Beakan extracts the `MediaSession.Token` to bind directly to the underlying `MediaController`. This enables:
-   **Sub-ms Latency**: Playback state changes (Play/Pause) are reflected instantly via IPC callbacks (`onPlaybackStateChanged`).
-   **Metadata Sync**: Album art and track titles are fetched directly from the session transport controls.

## Tech Stack

| Component | Implementation Detail |
| :--- | :--- |
| **Service Layer** | `NotificationListenerService` (Foreground Service) |
| **IPC** | `MediaSessionCompat` / `MediaController` |
| **Parsing** | `java.util.regex` (Optimized for Android Runtime) |
| **State Management** | Kotlin Data Classes & Synchronized Singletons |
| **Min SDK** | API 26 (Android 8.0) |

## Getting Started

### Prerequisites
-   Android API 26+ Device/Emulator.
-   **Permission**: `BIND_NOTIFICATION_LISTENER_SERVICE` (Must be granted manually via System Settings).

### Installation

```bash
git clone https://github.com/yourusername/beakan.git
./gradlew installDebug
```

> **Note**: As this project relies heavily on system service binding, it is best tested on a physical device or a full Google Play ecosystem emulator to simulate real-world notification traffic (SMS, Spotify, Chrome Downloads).
