# Android App Analysis: Auto Ride Accepter (3.2 Pro)

This document provides a detailed overview of the application's structure, technology stack, and a guide on how to build a similar automation application.

## 1. Project Overview
The current project directory contains a compiled Android application package (**APK**). It functions as an **automation utility** for ride-hailing drivers (e.g., Uber, Ola, Rapido) to automatically accept rides based on user-defined limits.

## 2. Technology Stack
*   **Language:** Kotlin (Recommended) or Java.
*   **Core API:** **Android Accessibility Services** (allows the app to "read" other apps and simulate clicks).
*   **UI Framework:** XML or Jetpack Compose for the settings; `WindowManager` for floating overlays.
*   **Build System:** Gradle.

## 3. How to Build a Similar "Auto-Acceptor" App
Creating an app like this from scratch involves using Android's automation and accessibility APIs. Here is the technical roadmap:

### Step 1: Create an Accessibility Service
This is the "engine" that interacts with other apps.
1.  **Define the Service:** Create a class extending `AccessibilityService`.
2.  **Manifest Declaration:** 
    ```xml
    <service android:name=".RideAcceptService"
             android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
        <intent-filter>
            <action android:name="android.accessibilityservice.AccessibilityService" />
        </intent-filter>
        <meta-data android:name="android.accessibilityservice"
                   android:resource="@xml/accessibility_service_config" />
    </service>
    ```
3.  **Config (`accessibility_service_config.xml`):** Set `accessibilityEventTypes` to `typeWindowStateChanged` and `typeWindowContentChanged`. Set `packageNames` to target apps (e.g., `com.ubercab.driver`).

### Step 2: Screen Scraping Logic
In your `onAccessibilityEvent(event: AccessibilityEvent)`, you inspect the UI tree:
*   **Search for Text:** Use `rootInActiveWindow.findAccessibilityNodeInfosByText("Accept")`.
*   **Check Filters:** Extract price or distance text from the screen and compare it with user settings.
*   **Perform Action:** If conditions are met, call `nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)`.

### Step 3: Overlay UI (Floating Button)
To allow drivers to toggle the auto-acceptor without leaving the Uber/Ola app:
*   Use `TYPE_APPLICATION_OVERLAY` permission.
*   Use the `WindowManager` to add a floating view.

### Step 4: Battery & Persistence
*   Request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` to prevent the OS from killing the service.
*   Use a **Foreground Service** with a persistent notification.

## 4. Challenges & Risks
*   **Anti-Bot Detection:** Many apps detect rapid or static-coordinate clicks. A professional app (like this Pro version) likely uses **randomized delays** and **offset click coordinates** to mimic human behavior.
*   **Policy:** Using such apps can lead to driver account suspension if detected by the ride-hailing platform.

## 5. Summary of APK Components
| Component | Function in Auto-Accepter |
| :--- | :--- |
| **Accessibility Service** | The main logic for detecting and clicking "Accept" buttons. |
| **Broadcast Receivers** | Listening for "Boot Completed" to start the service automatically. |
| **SharedPreferences** | Storing your "Min Fare" or "Max Distance" settings. |
| **DEX Files** | The compiled logic for all the above. |
