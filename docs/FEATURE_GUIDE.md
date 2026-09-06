# Axtor Feature & Setup Guide

This guide describes the features currently available in Axtor and how to enable them from the Android app.

## 1. First launch

1. Install the latest Axtor APK.
2. Open Axtor.
3. Grant **Microphone** permission when Android asks.
4. Axtor opens on its default ChatGPT-style chat screen.
5. Tap **🎙** to start voice mode if it is not already active.

Axtor does not require a wake/calling phrase. Speak the command itself.

## 2. Chat / AI

The main screen is the default ChatGPT-style interface.

- Type a request in the message box and tap **➤**.
- Tap **🎙** for hands-free voice operation.
- Axtor routes approved commands through its agent/security layer.
- Local GGUF inference is the offline AI path when a compatible model is installed.
- The configured online AI router is the online path when network/API configuration is available.

Do not assume that an online provider is unlimited or permanently free; provider quotas and terms can change.

## 3. Enable direct voice commands

Voice mode is enabled by tapping **🎙**. Axtor listens for the command directly; there is no "Hey Axtor" or other calling phrase.

If Android shows a microphone permission error:

**Android Settings → Apps → Axtor → Permissions → Microphone → Allow**

Then reopen Axtor and tap **🎙**.

## 4. Personal snap control

Open the **⋮** menu and choose **Enroll personal snaps**.

Axtor records three clear snap samples. Perform them in a reasonably quiet room and keep the microphone unobstructed.

After enrollment:

1. Open **⋮**.
2. Choose **Enable snap commands**.
3. Leave Axtor's voice service running.

Current patterns:

| Pattern | Action |
|---|---|
| 1 snap | Hand microphone to voice recognition, then speak a command |
| 2 snaps | Execute the configured double-snap safe command (default: volume down) |
| 3 snaps | Emergency-stop safety action |
| 4 snaps | Execute the configured quad-snap safe command (default: open notification settings) |

Use **Toggle extended-range snap detection** when detection needs to work from a greater distance or with weaker snaps. It can increase sensitivity, so false triggers are possible in noisy environments.

To turn the pattern mode off, open **⋮ → Disable snap commands**.

## 5. Device automation

Voice or typed commands can request supported device actions. Axtor checks every command against its security policy before execution.

Examples depend on the installed build and Android permissions. Dangerous operations are intentionally blocked, including arbitrary shell/root/ADB commands, unlocking/bypass actions, factory reset/wipe, credential access, and disabling security protections.

## 6. File agent

The file agent supports controlled operations such as listing, searching, reading, writing, appending, renaming, creating directories, and deleting files.

When the app provides file/workspace setup, grant access only to a directory you want Axtor to manage. Axtor is not intended to have unrestricted filesystem or root access.

## 7. Local GGUF model

A compatible GGUF model can be imported through the model-management path provided by the current build.

Recommended approach for low-memory Android devices:

- use a small quantized GGUF model;
- keep context size moderate;
- close other memory-heavy apps during inference;
- keep sufficient free storage for the model.

The APK does not automatically contain a large GGUF model unless that exact release lists one as an asset.

## 8. Online AI

The online router can use a configured API provider. The API key must be supplied by the user and is not hard-coded into the app.

Internet AI is optional; local GGUF inference remains the offline path.

## 9. Diagnostics

Open **⋮ → Run voice diagnostics** to check microphone permission, speech-recognition availability, direct-command mode, and whether the voice service is running.

The app also records its latest voice/service error in its local diagnostic preferences for troubleshooting.

## 10. Safety and autonomy

Axtor contains an agent, repair, self-coding, self-modification, verification, activation, rollback, and component-lifecycle architecture. These components are not equivalent to unrestricted self-modifying/root access on Android.

Changes that could compromise Android security, credentials, or device protections are restricted by the command-security layer.

## 11. If Axtor appears to close

1. Reopen Axtor.
2. Check **Settings → Apps → Axtor → Permissions → Microphone**.
3. Make sure the microphone permission is allowed.
4. Tap **🎙**.
5. Open **⋮ → Run voice diagnostics**.
6. If voice still fails, disable snap commands temporarily and test normal voice mode.

## Quick setup checklist

- [ ] Microphone permission granted
- [ ] ChatGPT-style interface opens
- [ ] 🎙 starts voice mode
- [ ] Direct command works without a wake phrase
- [ ] Optional personal snaps enrolled
- [ ] Optional snap command mode enabled
- [ ] Optional GGUF model imported
- [ ] Optional online AI/API configured
- [ ] Optional file workspace granted
- [ ] Voice diagnostics reports the expected state
