# ULTRON Android AI Assistant

ULTRON is a native Android app built with Kotlin and Jetpack Compose. It supports multi-turn chat, speech input, optional spoken replies, and image attachments with a vision-capable model. The Node.js backend supports hosted Gemini and local Ollama. Gemini can also ground replies with Google Search when enabled.

## Local backend

Install Node.js 20 or later, then run `npm install` from `backend/`. Put your server settings in `backend/.env`. Keep this file and your API keys out of Git.

For Gemini:

```dotenv
AI_PROVIDER=gemini
GEMINI_API_KEY=your-key-here
GEMINI_MODEL=gemini-3.5-flash-lite
ENABLE_WEB_SEARCH=false
```

Choose a model available to your Gemini project. Existing deployments default to `gemini-2.5-flash` if no model is set. Google Search may incur separate charges. Set `ENABLE_WEB_SEARCH=true` to enable it on Gemini.

For Ollama, install and start Ollama, pull a model such as `gemma3:4b`, then use:

```dotenv
AI_PROVIDER=ollama
OLLAMA_BASE_URL=http://127.0.0.1:11434
OLLAMA_MODEL=gemma3:4b
```

Keep Ollama's API port private. Ollama chat supports image attachments when the selected model supports vision. Turn off the app's web toggle when testing Ollama.

Run `npm start` from `backend/`. Open `http://localhost:3000/health` to check which provider the server selected. Send a chat request to verify that the provider is reachable and configured.

## Build and run Android

Open `android/` in Android Studio with JDK 17 and Android SDK 35. The app supports Android 8.0 and later. Debug and release builds default to the existing Render HTTPS endpoint.

- For an emulator talking to your local Node backend, set `ultronDebugBackendUrl=http://10.0.2.2:3000` in your local Gradle properties.
- For a physical test device, put your phone and computer on the same Wi-Fi and set `ultronDebugBackendUrl` to your computer's LAN address on port 3000. Allow access to the Node backend on the trusted network.
- For a release build targeting a different deployment, set `ultronBackendUrl` to its HTTPS URL. Cleartext traffic is allowed for debug builds only.

Use Android Studio or run `gradle assembleDebug` with Gradle 8.9 from `android/`. The APK is created at `android/app/build/outputs/apk/debug/app-debug.apk`.

## Deploying for public users

Deploy the Node service over HTTPS with `AI_PROVIDER=gemini` and the Gemini credentials set in the host's environment. Keep provider credentials on the server. Behind a reverse proxy, set `TRUST_PROXY_HOPS` only after verifying the number of trusted proxy hops for every route to the service. This lets the IP rate limiter identify clients correctly.

The current rate limit is IP-based and stored in the backend process. Before a public rollout, add user sign-in, persistent per-user quotas, and spending limits. The existing optional `APP_TOKEN` is for private API clients that send `X-App-Token`. The Android app does not send it. Sign the release build and complete the Play Store privacy disclosures before publishing.

## Current scope

Chat history is kept in memory and resets when the app process ends. Speech input and output depend on the device's Android speech and TTS services. Scheduled actions, reminders, and connected-account workflows such as Gmail or WhatsApp require separate implementation.
