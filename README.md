# Beakan

**Your notifications, reimagined.**

<div align="center">
  <img src="app/src/main/ic_launcher-playstore.png" alt="Beakan Logo" width="120" style="border-radius: 20px"/>
  <br/>
  <br/>
  
  [![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-purple.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
  [![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-M3-green.svg?style=flat&logo=android)](https://developer.android.com/jetpack/compose)
  [![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
</div>

---

**Beakan** transforms the Android status bar into a dynamic "Live Activity" hub. It intercepts specific notification categories—media playback, OTP messages, downloads, and system events—and presents them as an interactive, pill-shaped overlay. This approach streamlines user interaction by providing context-aware controls directly at the top of the viewport.

## Key Features

### Dynamic Media Control
Integrates with system media sessions to provide a unified control interface.
- **Visualizer**: Renders a real-time waveform reacting to audio playback.
- **Metadata**: Displays scrolling track information and album artwork.
- **Smart Visibility**: Automatically hides on the lock screen or when the Quick Settings panel is active to maintain system consistency.

### Intelligent OTP Extraction
Automates the retrieval of One-Time Passwords from SMS notifications.
- **Regex Engine**: Utilizes a multi-stage extraction logic validated against a 1000-message dataset, achieving 100% accuracy in testing.
- **Quick Action**: Allows for single-tap copying of the extracted code.

### Torch Indicator
Provides a visual status indicator for the system flashlight, with toggle functionality accessible directly from the overlay.

### Download Tracking
Monitors active file downloads and displays a circular progress indicator, offering a non-intrusive status update outside the notification shade.

## Technical Overview

- **Language**: Kotlin
- **UI Toolkit**: Jetpack Compose (Material 3)
- **Architecture**: MVVM
- **Core Services**:
  - `NotificationListenerService`: Intercepts and parses incoming notifications.
  - `AccessibilityService`: Manages the window overlay injection.
  - `Canvas` API: Used for custom drawing of animations and background elements.

## Credits

This project builds upon concepts and architectural patterns established by **RossSihovsk**.

- **Reference**: [LiveMedia](https://github.com/RossSihovsk/LiveMedia) – Acknowledged for its foundational work on Android overlay implementations.

## License

Distributed under the MIT License. See `LICENSE` for more information.
