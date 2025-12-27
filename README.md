# Beakan

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-purple.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg?style=flat&logo=android)](https://android-arsenal.com/api?level=26)
[![Service](https://img.shields.io/badge/Architecture-Service%20Layer-orange.svg?style=flat&logo=android)](https://developer.android.com/guide/components/services)

**Beakan** brings iOS-style **Live Activities** to Android via the **Live Updates API** introduced in Android 16. It's a background service that catches specific notifications—like 2FA codes, downloads, and music—and promotes them into a unified, persistent status chip at the top of the screen.

---

## How It Works

Beakan runs a `NotificationListenerService` in the background. It watches the notification stream, filters for the stuff that matters, and decides what to show based on priority. The UI is completely decoupled from this service.

```mermaid
flowchart TD
    System[System Notification Stream] -->|Intercept| Listener[MediaNotificationListener]
    
    Listener -->|Route| Extractor{Extraction Engine}
    
    Extractor -->|Regex| OTP[OtpExtractor]
    Extractor -->|Bundle Analysis| DL[DownloadTracker]
    Extractor -->|Session Bound| Media[MediaController]
    
    OTP -->|P0 Signal| Manager[LiveActivityManager]
    DL -->|P1 Signal| Manager
    Media -->|P2 Signal| Manager
    
    Manager -->|Arbitrate| Publisher[NotificationPublisher]
    Publisher -->|Render| SystemBar[System Status Bar]
```

## Core Modules

### 1. The Listener (`MediaNotificationListener`)
This service connects to Android's notification stream. It filters noise efficiently:
-   **Debouncing**: Ignores rapid updates (like download progress firing every 10ms) to keep the UI smooth.
-   **Classification**: Identifies what kind of notification came in—was it an SMS? A download manager update? A music app?

### 2. OTP Extraction (`OtpExtractor`)
For 2FA codes, we run a local parser on notification text.
-   **Keywords**: Looks for words like "code", "pin", or "login".
-   **Regex**: Uses a few precise patterns to find 4-8 digit numbers. It's smart enough to ignore phone numbers or support ticket IDs.
-   **Privacy**: Everything happens in memory. No data is saved or sent anywhere.

### 3. State Manager (`LiveActivityManager`)
This component decides what to show when multiple things happen at once.

```mermaid
stateDiagram-v2
    [*] --> Idle
    
    Idle --> Media: Playback Started (P2)
    Media --> Download: Download Started (P1)
    
    Download --> OTP: 2FA Code Received (P0)
    Media --> OTP: 2FA Code Received (P0)
    
    OTP --> Download: 30s Timeout / Dismiss
    OTP --> Media: 30s Timeout / Dismiss
    
    Download --> Media: Download Complete
    Media --> Idle: Session End
```

| Priority | Type | Behavior |
| :--- | :--- | :--- |
| **P0** | **OTP / 2FA** | Highest priority. Shows up immediately, then auto-dismisses after 30s. |
| **P1** | **Downloads** | Shows while a file is downloading. Hides when done. |
| **P2** | **Media** | Default state. Shows whenever music or audio is playing. |

### 4. Media Binding
We don't just scrape the notification title. Beakan looks for a `MediaSession` token to talk directly to the music app.
-   **Fast Updates**: Play/Pause buttons work instantly because they use proper system callbacks.
-   **Metadata**: Gets clean album art and track details directly from the session.

## Tech Stack

| Component | Details |
| :--- | :--- |
| **Service** | `NotificationListenerService` (Foreground) |
| **Media** | `MediaSessionCompat` / `MediaController` |
| **Parsing** | Standard Java Regex |
| **State** | Kotlin Singletons |
| **Min SDK** | Android 8.0 (API 26) |

## Setup

### Prerequisites
-   Android 8.0+ device or emulator.
-   **Manual Step**: You must grant "Notification Access" in system settings when the app asks.

### Install

```bash
git clone https://github.com/yourusername/beakan.git
./gradlew installDebug
```

> **Testing Tip**: Use a real device or a Play Store emulator. You need real apps (like Messages or Spotify) generating notifications to see it work.
