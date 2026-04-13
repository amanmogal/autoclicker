# Repository Guidelines

## Project Structure & Module Organization
This repository is a single-module Android app (`:app`) built with Gradle and Kotlin.
- App code: `app/src/main/kotlin/com/autoride/accepter/`
- Manifest: `app/src/main/AndroidManifest.xml`
- UI resources: `app/src/main/res/layout`, `res/drawable`, `res/values`, `res/xml`
- Release copy output: `release_apk/`
- Build artifacts (generated): `app/build/`, `build/` (do not edit manually)

Place new business logic with existing domain classes (for example, alongside `FilterEngine.kt`) and keep Android service/UI code in focused files.

## Build, Test, and Development Commands
Use the Gradle wrapper from repo root:
- `./gradlew app:assembleDebug` — builds debug APK (`app/build/outputs/apk/debug/AutoRideAccepter_Latest.apk`)
- `./gradlew app:copyApk` — builds debug APK and copies it into `release_apk/`
- `./gradlew app:lint` — runs Android lint checks
- `./gradlew app:testDebugUnitTest` — runs local JVM unit tests
- `./gradlew app:connectedDebugAndroidTest` — runs instrumentation tests on a connected device/emulator
- `./gradlew clean` — removes build outputs

## Coding Style & Naming Conventions
- Language/toolchain: Kotlin + Android Gradle Plugin, Java/Kotlin target 17.
- Indentation: 4 spaces; no tabs.
- Naming:
  - Classes/objects: `PascalCase` (`RideAcceptService`)
  - Functions/variables: `camelCase` (`updateSettings`)
  - Constants: `UPPER_SNAKE_CASE` (`TARGET_PACKAGES`)
  - Android resources/files: `snake_case` (`overlay_status.xml`)
- Keep packages under `com.autoride.accepter` unless a clear module boundary is introduced.

## Testing Guidelines
- Unit tests: `app/src/test/kotlin/...`
- Instrumentation tests: `app/src/androidTest/kotlin/...`
- Test naming: `ClassNameTest` with descriptive methods (for example, `shouldAccept_whenFarePerKmIsValid`).
- Before opening a PR, run at minimum: `app:lint` and `app:testDebugUnitTest`.

## Commit & Pull Request Guidelines
This snapshot does not include `.git` history, so use this convention moving forward:
- Commit format: Conventional Commits (`feat:`, `fix:`, `chore:`) + Lore trailers when relevant (`Constraint:`, `Tested:`, `Not-tested:`).
- Keep commits small and scoped to one concern.
- PRs should include:
  - concise problem/solution summary
  - linked issue/ticket
  - verification commands + results
  - screenshots/video for UI or overlay behavior changes
