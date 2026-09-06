# Axtor Feature & Setup Guide

This guide describes the current Axtor Android interface and the main features available from it.

## 1. First launch

1. Install the latest tested Axtor APK.
2. Open Axtor.
3. Grant **Microphone** permission when Android asks.
4. Axtor opens directly on its ChatGPT-style chat interface.
5. Tap **🎙** if you want to start/restart voice mode.

Axtor does not require a wake/calling phrase. Speak the command itself.

## 2. Chat

The main screen is the default ChatGPT-style interface.

- Type a request and tap **➤**.
- Tap **＋** to start a new local conversation.
- Conversation history is stored locally and limited to the most recent messages.
- Tap **⋮** for AI, voice, device/file, diagnostics, and guide controls.

Approved commands are routed through Axtor's agent and security layer.

## 3. AI & Models

Open **⋮ → AI & Models**.

### Recommended model for the test phone

The test device model is **24048RN6CI (Redmi A3x India)**. For the 3 GB RAM configuration, the recommended exact local model is:

**Qwen2.5-0.5B-Instruct-GGUF — Q4_K_M**

Recommended filename: `qwen2.5-0.5b-instruct-q4_k_m.gguf`.

The Q4_K_M file is roughly 491 MB in the official Qwen GGUF release. This is the preferred starting point for this low-memory phone. Avoid starting with 3B/7B models. If RAM pressure occurs, use Q3_K_M instead.

### Import GGUF

Choose **Import GGUF model**, select a GGUF file, and Axtor copies it into its private model directory after validating the GGUF header and file size. The imported model becomes the active local model.

### Choose active model

Use **Choose active model** to switch between imported local GGUF models.

### Model status

**Model status** reports local-model readiness, whether the model is loaded, and whether online AI is configured.

## 4. Online AI

Open **⋮ → AI & Models → Configure online AI**.

The user supplies their own provider API key. Axtor stores the key using Android Keystore encryption. Online AI is optional and provider quotas/terms apply; it is not guaranteed to be unlimited or permanently free.

## 5. Direct voice commands

Tap **🎙**, or open **⋮ → Voice & Snaps → Start voice service**.

Speak the command directly. There is no "Hey Axtor" or other calling phrase.

If Android shows a microphone error:

**Android Settings → Apps → Axtor → Permissions → Microphone → Allow**

Then reopen Axtor and tap **🎙**.

## 6. Personal snap control

Open **⋮ → Voice & Snaps → Enroll personal snaps**.

Axtor records three clear snap samples. Perform them in a reasonably quiet room with the microphone unobstructed.

Then use **Enable snap commands**.

| Pattern | Action |
|---|---|
| 1 snap | Hand the microphone to voice recognition, then speak a command |
| 2 snaps | Execute the configured safe double-snap command (default: volume down) |
| 3 snaps | Emergency-stop safety action |
| 4 snaps | Execute the configured safe quad-snap command (default: open notification settings) |

**Toggle extended-range snaps** can improve detection from farther away, but higher sensitivity can also increase false triggers in noisy environments.

Use **Disable snap commands** to turn pattern mode off.

## 7. Device & Files

Open **⋮ → Device & Files**.

- **All files access** opens Android's system-controlled **Allow access to manage all files** screen for Axtor on Android versions that support it.
- After returning, Axtor also opens the controlled workspace picker so a specific folder can be persisted for the file agent.
- **File access status** reports whether a workspace has been granted.
- **Open Android accessibility settings** takes you to Android's own accessibility settings.
- **Open app settings** opens Axtor's Android app settings.

The file agent supports controlled operations such as listing, searching, reading, writing, appending, renaming, and creating directories within the user-granted workspace.

Android/Google Play may restrict broad `MANAGE_EXTERNAL_STORAGE` access unless the app's core function qualifies, so Axtor retains the safer system folder-workspace route as a fallback.

## 8. Diagnostics

Open **⋮ → Diagnostics** to check:

- microphone permission;
- speech-recognition availability;
- voice-service state;
- local GGUF readiness;
- online AI configuration;
- snap enrollment;
- accessibility-service status.

## 9. Safety

Axtor checks commands before device execution. Dangerous operations are intentionally restricted, including arbitrary shell/root/ADB commands, unlocking or bypassing security, destructive device operations, credential/secret access, and disabling Android protections.

The repair, self-coding, self-modification, verification, activation, rollback, and component-lifecycle code provides an engineering foundation; it does **not** mean Axtor has unrestricted self-modifying or root access to Android.

## 10. If Axtor appears to close

1. Reopen Axtor.
2. Check **Android Settings → Apps → Axtor → Permissions → Microphone**.
3. Tap **🎙**.
4. Open **⋮ → Diagnostics**.
5. If voice fails, disable snap commands temporarily and test normal voice mode.

## Quick phone-test checklist

- [ ] App launches into ChatGPT-style UI
- [ ] Chat message sends successfully
- [ ] New chat clears local conversation
- [ ] Microphone permission works
- [ ] 🎙 starts voice mode
- [ ] Direct voice command works without a wake phrase
- [ ] GGUF import validates and selects a model
- [ ] **Qwen2.5-0.5B-Instruct Q4_K_M** loads on the test phone
- [ ] Active-model chooser works
- [ ] Model status reports correctly
- [ ] Optional online AI setup works
- [ ] Personal snaps enroll successfully
- [ ] 1/2/3/4-snap patterns behave as documented
- [ ] All files access screen works when supported
- [ ] File workspace can be granted
- [ ] Diagnostics reports expected state
- [ ] Android accessibility settings can be opened
- [ ] App remains stable after background/foreground transitions
