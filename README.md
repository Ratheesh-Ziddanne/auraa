# AURA

Blind-first voice messaging assistant for Android 8.0+.

This initial Sprint 1 scaffold contains the safe audio/session foundation: microphone permission narration, Android TTS, English (India) speech recognition, audio-focus interruption handling, and an accessible fallback button. It intentionally **cannot send any message** yet; the mandatory read-back and explicit confirmation FSM is Sprint 2.

## Open and run

Open this directory in Android Studio (JDK 17, Android SDK 35), allow Gradle to sync, then run on a physical Android device. Grant microphone permission after the narrated explanation finishes. The Gradle wrapper is not included because this workspace did not contain one; Android Studio will offer to create/sync the project using its installed Gradle.

## Privacy status

No message content, tokens, drafts, contacts, or audio are persisted in this sprint.
