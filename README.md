# Axtor — Android AI Assistant

**Axtor** is an Android AI assistant project by **Alex77p**. This repository is the public home for the Axtor Android application, its releases, and related project information.

## Project

Axtor is designed as a personal AI assistant for Android, with support for local AI models and device-assistance capabilities.

### Keywords

Axtor, Android AI assistant, AI assistant for Android, local AI, offline AI, GGUF, Llama, Android automation, personal AI assistant, Alex77p.

## Releases

### Latest release

Use the repository's Releases page for the newest tested distribution. Versioned `v*` tags trigger the Phase 13 release workflow, which builds both an APK and Android App Bundle (AAB). A release is published only when the tagged build completes successfully.

### APK and AAB

The APK is intended for direct Android installation. The AAB is the package format intended for store distribution.

### Default GGUF Llama model

Axtor supports importing a compatible GGUF model through its Models interface. A model is not bundled into the Android APK/AAB because GGUF model files can be large and device-specific; a compatible model can be distributed separately as a release asset when available.

> Do not treat the existence of a release as proof that a model has been uploaded. Check the release Assets list for the exact model file.

## Validation

Phases 1–11 have passed the repository CI baseline. Phase 12 contains the real-device validation matrix for cold start, low RAM, screen-off operation, Bluetooth/headphones, microphone, voice, snap detection, model inference, automation, emergency stop, and battery/network behavior.

Real-device capabilities must be validated on an actual Android device; GitHub Actions alone cannot prove microphone, acoustic-distance, screen-off, or device-specific behavior.

## About the developer

**Alex sefer (Alex77p)** is the GitHub developer behind Axtor.

- GitHub: https://github.com/Alex77p
- Axtor: https://github.com/Alex77p/Axtor

## Search terms

This project may be relevant to people searching for **Axtor**, **Alex77p**, **Android AI assistant**, **offline Android AI**, **local AI assistant**, **GGUF Android**, **Llama Android**, and **Android AI automation**.

## License

See `LICENSE.md` for the applicable project terms.
