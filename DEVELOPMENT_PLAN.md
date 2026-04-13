# Development Plan: Auto-Ride Accepter App

This document outlines the detailed research, feature specifications, and execution phases for developing an Android automation utility similar to "Auto Ride Accepter 3.2 Pro".

## 1. Feature Research & Reverse Engineering Analysis
Before starting implementation, we must define the exact feature set of the "Pro" version to ensure parity.

### Core Feature Breakdown:
1.  **Multi-Platform Support:** 
    *   *Research:* Identify the package names and UI structures for Uber Driver, Ola Driver, Rapido, and Indriver.
    *   *Requirement:* The app must handle different "Accept" button layouts and text for each platform.
2.  **Advanced Filtering Logic:**
    *   *Minimum Fare:* Flat rate threshold (e.g., Accept only if > ₹100).
    *   *Fare per KM:* Intelligent calculation based on (Fare / Distance).
    *   *Distance to Pickup:* Limit pickup distance to avoid long unpaid travel.
    *   *Ride Type:* Filtering based on "Auto", "Bike", or "Car" categories.
3.  **Accessibility-Based Automation:**
    *   *Screen Scraping:* Extracting text from the active window without root access.
    *   *Simulated Taps:* Clicking the "Accept" button programmatically.
4.  **Human-Likeness (Stealth Mode):**
    *   *Variable Delay:* Random wait times (e.g., 200ms - 800ms) before clicking.
    *   *Coordinate Randomization:* Tapping different parts of the button instead of the exact center.
5.  **Overlay UI & Control:**
    *   *Floating Dashboard:* A transparent/movable overlay allowing drivers to toggle settings without switching apps.
    *   *Visual Feedback:* Highlighting the "Accept" button when it is being clicked.
6.  **Persistence & Reliability:**
    *   *Auto-Restart:* Ensuring the service restarts if the OS kills it for battery saving.
    *   *Stay Awake:* Keeping the screen on while the "Auto-Accept" is active.

## 2. Advanced Research: Latency & Detection Avoidance

### A. Latency Reduction
*   **Event Filtering:** Configure `AccessibilityServiceInfo` to only listen to `TYPE_WINDOW_CONTENT_CHANGED` and `TYPE_WINDOW_STATE_CHANGED` for specific packages to reduce overhead.
*   **Efficient Node Searching:** Use `findAccessibilityNodeInfosByViewId` for direct access instead of recursive tree traversal. Enable `FLAG_REPORT_VIEW_IDS`.
*   **Concurrency:** Offload heavy filtering logic to a background thread to keep the main Accessibility thread responsive.
*   **Pre-emptive Parsing:** Start parsing the screen as soon as a window state change is detected, even before the full UI is rendered if possible.

### B. Detection Avoidance (Stealth)
*   **Service Obfuscation:** Use generic or misleading names for the Accessibility Service (e.g., "System Helper" or "Notification Optimizer") to avoid manual inspection or simple string-based detection.
*   **Overlay Cloaking:** Use `FLAG_NOT_TOUCH_MODAL` and `FLAG_NOT_FOCUSABLE`. Hide the overlay during sensitive app transitions.
*   **Behavioral Entropy:** Implement non-linear delays and "jitter" in click coordinates. Mimic "swipe-to-accept" if the target app uses it, rather than a single tap.
*   **Anti-Detection of Development Environment:** Advice to disable ADB and Developer Options when the app is in use, as target apps often scan for these.
*   **Package Name Randomization:** (Advanced) Dynamically change the package name or use a random one during build to avoid static blacklisting.

## 3. Implementation Roadmap

### Phase 1: Foundation (Week 1) - **IN PROGRESS**
*   Setup Project (Kotlin, Gradle).
*   Implement `AccessibilityService` boilerplate.
*   Feature: App Detection (detecting when Uber/Ola is in the foreground).

### Phase 2: Screen Parsing & Logic (Week 2)
*   Feature: Text Extraction (Reading price and distance).
*   Feature: Custom Filter Engine (The math behind accepting or rejecting a ride).

### Phase 3: Automation & UX (Week 3)
*   Feature: Click Simulation.
*   Feature: Floating "Pro" Dashboard.

### Phase 4: Stealth & Optimization (Week 4)
*   Feature: Anti-detection (Randomized gestures).
*   Beta Testing: Simulating real ride flows.

## 4. Documentation & Compliance
*   **Terms of Use:** Clearly stating the app is for accessibility and automation.
*   **Developer Guide:** Internal docs on how to update UI IDs when target apps update.
