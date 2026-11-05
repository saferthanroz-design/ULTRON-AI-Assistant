# ULTRON Android AI Assistant (MVP v0.1)

Native Android app (Kotlin + Jetpack Compose) and Node.js backend proxy for OpenAI Responses API.

## Implemented
- Multi-turn AI chat with in-memory chat history (last 16 messages sent to API)
- Optional web search via Responses API `web_search_preview` tool
- Android speech recognition (device language) and optional text-to-speech responses
- Image attachment via Android photo picker and vision-capable AI model
- Dark neon UI; error handling, loading states, clear chat
- Backend API key kept off the Android device, request validation and rate limiting

## Prerequisites
- Android Studio with Android SDK 35, JDK 17, Android emulator or device running Android 8.0+
- Node.js 20+, npm
- OpenAI API key with billing/access for selected model and web search tool

## Start backend
```bash
cd backend
cp .env.example .env
# Edit .env and set OPENAI_API_KEY
npm install
npm start
```
Check `http://localhost:3000/health`. Do not commit .env.

## Run Android app
1. Open `android/` as an Android Studio project and let Gradle sync.
2. Start an Android emulator. Default backend URL is `http://10.0.2.2:3000` (Android emulator -> host machine).
3. Click **Run**. Accept microphone permission when prompted.
4. For a physical phone, put phone and computer on the same Wi-Fi and replace backend URL with your computer's LAN IP, e.g. add to `android/gradle.properties`:
   `ultronBackendUrl=http://192.168.1.100:3000`
   Ensure your firewall permits inbound TCP 3000. Never expose an unauthenticated backend publicly.
5. For production, deploy backend over HTTPS, remove cleartext traffic permission, add real authentication, and update backend URL to your HTTPS endpoint.

## Build debug APK
From Android Studio: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
Or, if Gradle 8.9 is installed:
```bash
cd android
gradle assembleDebug
```
The APK will be at `android/app/build/outputs/apk/debug/app-debug.apk`.
If Gradle is not installed, Android Studio can download/configure Gradle on import; this archive does not include a Gradle wrapper binary. You can generate one with `gradle wrapper --gradle-version 8.9` and then use `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`).

## Production security checklist
- Remove `android:usesCleartextTraffic="true"` before release; use HTTPS only.
- Implement authentication (the optional backend `APP_TOKEN` requires corresponding Android header support; currently not included in Android client).
- Add per-user quotas, cost limits, secure storage, abuse protection, and observability.
- Configure signed release builds and Play Store privacy disclosures.
- Avoid storing private data unless the user explicitly opts in.

## Scope limitations
- Chat history is in-memory and resets when the app process ends.
- Voice recognition uses the Android speech service; language follows device locale; speech output depends on installed TTS voices.
- The web toggle performs web-assisted model responses, not a full browser UI.
- WhatsApp, Gmail, Facebook, Instagram, YouTube integrations and autonomous workflows are NOT implemented. They require platform-specific OAuth, permissions, approved APIs, and user confirmation policies.
- This project is source code only: APK compilation and device testing are not verified in this environment.
