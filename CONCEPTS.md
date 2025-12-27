# Beakan: Creative Feature Registry

This document outlines potential "Killer Features" that leverage the **Live Update** core technology (Notification Interception -> Status Bar Promotion) in creative ways beyond standard music playback.

---

## 🎨 Aesthetics & Immersion

### 1. Chameleon Mode (Dynamic Media Coloring)
**Concept:**
Abandon the static "Spotify Green" for a dynamic theme that adapts to the currently playing album art.

**Implementation Strategy:**
*   **Trigger:** On `MetadataChanged` (Album Art load).
*   **Tech:** Use Android's `Palette` API to extract the `Vibrant` or `Dominant` swatch from the bitmap.
*   **Performance:** Run asynchronously on a background thread. Cache the `Int` color value.
*   **Rendering:** The `NotificationPublisher` uses this cached color to tint the progress bar and notification background.
*   **Result:** A jazz album makes the status bar moody/dark; a pop album makes it vibrant pink.

### 2. Immersive Dashboard
**Concept:**
Transform the main app UI from a settings list into a "Now Playing" visualizer.

**Implementation Strategy:**
*   **Trigger:** When the app is opened while media is active.
*   **Tech:** `MainActivity` subscribes to the service state.
*   **UI:** Render a full-screen, heated-blur background of the album art. Overlay large metadata and custom controls.
*   **Result:** The app feels like a dedicated "Station" rather than a utility.

---

## 🛠️ Utility & Productivity

### 3. Flash Codes (OTP Pinner)
**Concept:**
Solve the friction of 2FA by pinning the code to the system status bar, visible even when you switch to your banking app.

**Implementation Strategy:**
*   **Trigger:** Intercept `Notification.CATEGORY_MESSAGE` or standard SMS.
*   **Logic:** Regex parser scans for patterns like `Code: \d{4,6}` or `OTP is \d{6}`.
*   **Mapping:**
    *   **Song Title:** "Security Code"
    *   **Artist:** "123-456" (The Code)
    *   **Duration:** 30 seconds (Countdown timer).
*   **Result:** A "playing" notification that actually holds your temporary password.

### 4. Universal Progress Bar
**Concept:**
Monitor file downloads, uploads, or updates from *any* app without pulling down the notification shade.

**Implementation Strategy:**
*   **Trigger:** Intercept notifications with `Notification.EXTRA_PROGRESS`.
*   **Logic:** Filter out music apps to avoid conflicts.
*   **Mapping:**
    *   **Song Title:** App Name (e.g., "Chrome")
    *   **Artist:** "Downloading..."
    *   **Duration:** `progressMax`
    *   **Current Position:** `progressCurrent`
*   **Result:** A live ring in the status bar showing the download status.

### 5. Meeting Ticker
**Concept:**
A subtle countdown to your next meeting, preventing "Zoom Fatigue" and checking the clock.

**Implementation Strategy:**
*   **Trigger:** Intercept Calendar "Upcoming Event" notifications (usually 10 min before).
*   **Logic:** Parse the event time.
*   **Mapping:**
    *   **Song Title:** "Daily Standup"
    *   **Artist:** "In 5 min"
    *   **Duration:** Total time until start.
    *   **Position:** Time elapsed since notification.
*   **Result:** A ticker that counts down `04:59`, `04:58`... right in the status bar.

### 6. Ride Radar
**Concept:**
Track your Uber/Lyft/Food Delivery without opening the app.

**Implementation Strategy:**
*   **Trigger:** Intercept text updates from ride-sharing apps.
*   **Logic:** Regex search for `"(\d+) min.*away"`.
*   **Mapping:**
    *   **Song Title:** "Driver Arriving"
    *   **Artist:** "4 min"
    *   **Album Art:** Car Icon.
*   **Result:** Constant visibility of your ride's status.

---

## ⚡ Interaction

### 7. Pocket DJ (Shake-to-Skip)
**Concept:**
Skip tracks without taking your phone out or looking at the screen.

**Implementation Strategy:**
*   **Trigger:** Accelerometer sensor event.
*   **Logic:** Detect a "Clean Shake" gesture (high acceleration on X-axis, followed by reversal).
*   **Action:** Fire `transportControls.skipToNext()`.
*   **Safety:** Debounce to prevent accidental double-skips.
*   **Result:** Physical control over your digital media.

### 8. Keyword Watcher (VIP Pager)
**Concept:**
A "Do Not Disturb" bypass for specific people or words.

**Implementation Strategy:**
*   **Configuration:** User sets a list: ["Mom", "Urgent", "Server Down"].
*   **Trigger:** Text match in any incoming notification.
*   **Action:** Promote to "Media" status immediately.
*   **Result:** Urgent messages take over the music slot to ensure they are seen.
