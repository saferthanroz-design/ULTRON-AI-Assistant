# FitLog

FitLog is a standalone Kotlin and Jetpack Compose prototype for ULTRON's Android build workflow. It logs workouts offline and displays a weekly summary. The app supports Android 8.0 and later.

Open this directory in Android Studio, or run `gradle :app:assembleDebug` from this directory with Gradle 8.9, JDK 17 or later, and Android SDK 35. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

For a device smoke test, add a workout dated today, verify the weekly totals, close and reopen the app, and check that you can edit and delete the saved workout.
