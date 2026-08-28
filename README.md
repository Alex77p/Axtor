# MyAiAgent Android 1.2.0

MyAiAgent is an offline-first Android assistant created by **Ayush Debbarma**.

## Real local GGUF inference

This version integrates a real llama.cpp-based Android runtime through the Maven Central AAR `dev.ffmpegkit-maintained:llama-android:0.1.1`. The library exposes GGUF model loading and local completion APIs; no cloud API key or remote inference service is required. The AAR is arm64-v8a and CPU/NEON based in its free build. See the upstream documentation for licensing and build details.

The app:

- Imports a `.gguf` file through Android's document picker.
- Copies the model into app-private storage.
- Validates the GGUF magic header.
- Selects the imported model as the active model.
- Runs prompts locally with a 4096-token context and bounded CPU thread count.
- Releases the native model after each completion to avoid retaining large native allocations.
- Exports the active GGUF file and a model manifest.
- Uses the same local model from the voice assistant when available.
- Prefers offline Android speech recognition and keeps voice command processing local after speech recognition.

## Download and import

**Download an AI agent or model from a website and import it into the app.**

After downloading a supported model, use the app's model import option to select the local file. The app currently supports GGUF model files for local inference.

## Model sizing

A Q4_K_M model needs substantial RAM in addition to the file size. Start with small models such as 0.5B–3B on phones with limited memory. Large 7B/8B models may fail to load or be killed on devices with insufficient RAM.

## Build

Open this directory in Android Studio, sync Gradle, and build the debug or release APK. Internet access is required during the first Gradle sync if the llama-android AAR is not already cached; model inference itself does not require internet access.

The upstream llama.cpp Android documentation also documents native NDK builds and the official Android binding. It recommends keeping context size reasonable because memory can spike with larger contexts.

## Voice

The voice service runs as an Android microphone foreground service and prefers offline recognition. The service recognizes wake phrases such as `Hey MyAI`, `Hey My AI`, `MyAI`, and `My AI`, then routes the command to the local model or local device-action handlers.

A true low-power hardware wake-word DSP is device-dependent; this build uses Android's speech recognizer rather than pretending to provide a universal hardware hotword engine.

## Security

Android permissions and security boundaries remain enforced. The app does not bypass the lock screen, credentials, or protected system permissions.
