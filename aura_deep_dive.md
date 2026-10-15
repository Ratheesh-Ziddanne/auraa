# 🌟 AURA — Full Deep-Dive Analysis

## What is AURA?

**AURA** is a **blind-first, eyes-free voice assistant for Android** (API 30+, targeting SDK 37).  
It is designed **from the ground up for visually impaired users** — the phone screen is never required.  
The assistant lives entirely in voice: it listens, speaks, navigates, and acts, all without any touch interaction.

> **Package ID:** `com.aura`  
> **Version:** `0.1.0` (Sprint 1 scaffold)  
> **Language:** Kotlin + Jetpack Compose  
> **Min SDK:** Android 11 (API 30) · Target SDK: 37

---

## 🏗️ Architecture Overview

AURA uses a clean **Layered Architecture** with clear separation of concerns:

```
┌──────────────────────────────────────────────────────────┐
│                     Platform Layer                        │
│   LifecycleResilienceManager (Foreground Service)        │
│   AuraVoiceInteractionService (OS Assistant Integration) │
└────────────────────┬─────────────────────────────────────┘
                     │
┌────────────────────▼─────────────────────────────────────┐
│                  Presentation Layer                       │
│           VoiceInteractionController                     │
│           (Orchestrates the full voice loop)             │
└────────────────────┬─────────────────────────────────────┘
                     │
┌────────────────────▼─────────────────────────────────────┐
│                 Application Layer                         │
│   ConversationStateMachine (FSM) · ConfirmationHandler   │
│   IntentParser · ContextManager                          │
└────────────────────┬─────────────────────────────────────┘
                     │
┌────────────────────▼─────────────────────────────────────┐
│                   Service Layer                           │
│  ContactResolver · MessagingRouter · PersonalMemory      │
│  SafetyMonitor · HouseMapRepository · AuraToolbox        │
│  SecureTokenManager · TelemetryLogger · VoiceShortcuts   │
└────────────────────┬─────────────────────────────────────┘
                     │
┌────────────────────▼─────────────────────────────────────┐
│                Infrastructure Layer                       │
│  STT · TTS · AudioSession · WakeWord · SpeakerIdentity   │
│  Vision · Navigation · SMS Adapter                       │
└──────────────────────────────────────────────────────────┘
```

---

## 🧠 Core Subsystems — Detailed Breakdown

---

### 1. 🔊 Wake Word Engine (`infrastructure/wakeword/`)

**File:** [`WakeWordEngine.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/infrastructure/wakeword/WakeWordEngine.kt)

- Listens to a continuous raw PCM audio stream (as a Kotlin `SharedFlow<ShortArray>`)  
- Calculates the **RMS (Root Mean Square)** energy of each audio chunk  
- Triggers the wake callback when RMS exceeds threshold `5,000,000`  
- **Production plan:** Replace with **Porcupine** (a licensed, on-device keyword-spotting engine) using the word "AURA"  
- Prevents full STT (Speech-to-Text) from running until the wake word fires → massive battery savings

```
User says "AURA" → RMS spike detected → onWake() fires → Full voice loop starts
```

---

### 2. 🎙️ Speech-to-Text (STT) (`infrastructure/stt/`)

**File:** [`SpeechRecognitionEngine.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/infrastructure/stt/SpeechRecognitionEngine.kt)

- Uses **Android's native `SpeechRecognizer` API** (no cloud dependency for MVP)  
- Language: `en-US` (with multilingual router planned via Vosk for Tamil/Hindi)  
- Returns a `RecognitionResult` sealed interface: `Success(transcript, confidence)` or `Failure(reason)`  
- Implemented as a **suspending coroutine** — perfectly integrates with the async voice loop  
- `AuraRecognitionService` also registers AURA as a **system-level speech recognition provider**

---

### 3. 🔉 Text-to-Speech (TTS) (`infrastructure/tts/`)

**File:** [`TextToSpeechEngine.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/infrastructure/tts/TextToSpeechEngine.kt)

- Uses **Android's native TTS engine** — no network required  
- Speaks with **English (India) locale** (`en-IN`) for natural local pronunciation  
- Adjustable **speech rate** (used to slow down for error states: `0.8f`)  
- The entire UI is driven by TTS — no visual display is ever required

---

### 4. 🎛️ Audio Session Manager (`infrastructure/audio/`)

- Manages **Android AudioFocus** — politely pauses music/media before speaking  
- Releases focus when done, allowing other apps to resume  
- Handles **interruptions** (e.g., incoming calls) gracefully via the FSM's `Interrupted` state

---

### 5. 🪪 Speaker Recognition / Identity (`infrastructure/identity/`)

**File:** [`SpeakerRecognitionEngine.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/infrastructure/identity/SpeakerRecognitionEngine.kt)

- `SpectralSpeakerRecognitionEngine` — extracts a **16-bucket spectral energy fingerprint** from raw PCM  
- **Registration:** User repeats 3 phrases; prints are averaged to build a robust voice template  
- **Verification:** Computes Manhattan distance between live and stored prints; accepts if `< 0.15`  
- Prevents AURA from responding to other people's voices — critical privacy/security feature  
- **Production plan:** Replace with TFLite or Kaldi voiceprint model

---

### 6. 🤖 Conversation State Machine (FSM) (`application/fsm/`)

**File:** [`ConversationStateMachine.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/application/fsm/ConversationStateMachine.kt)

The heart of AURA. A **pure, deterministic reducer** with **11 states**:

| State | Description | Timeout |
|---|---|---|
| `Idle` | Waiting for wake word | None |
| `ListeningCommand` | Actively listening after wake | 5 sec |
| `ResolvingContact` | Looking up contact in phonebook | 8 sec |
| `Disambiguating` | Multiple contacts found, asking user to pick | 10 sec |
| `DictatingMessage` | User is composing the message | 15 sec |
| `ConfirmingSend` | AURA reads back the message for approval | 12 sec |
| `Sending` | Actively sending the message | 30 sec |
| `Enrolling` | First-time speaker voice registration | 15 sec |
| `Mapping` | Room-by-room house mapping session | 20 sec |
| `Navigating` | Guiding user to a room | 30 sec |
| `FaultRecovery` | Error handling with retry logic | None |
| `Interrupted` | Paused (e.g. incoming call) — stores resume state | None |

Key design principle: **No message is ever sent until the FSM reaches `Sending` state via explicit affirmative confirmation**

---

### 7. ✅ Confirmation Handler (`application/confirmation/`)

**File:** [`ConfirmationHandler.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/application/confirmation/ConfirmationHandler.kt)

**Deliberately strict.** Only these exact phrases confirm a send:

| Type | Accepted Phrases |
|---|---|
| **Confirm** | `"yes"`, `"send it"`, `"confirm"`, `"go ahead"` |
| **Repeat** | `"say that again"`, `"repeat"`, `"repeat that"` |
| **Cancel** | `"no"`, `"cancel"`, `"stop"`, `"don't send"`, `"do not send"` |

`"okay"`, `"sure"`, `"fine"`, silence → **all rejected**. This is an explicit safety design for visually impaired users who cannot see what they're confirming.

---

### 8. 📞 Contact Resolver (`service/contact/`)

**File:** [`ContactResolver.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/service/contact/ContactResolver.kt)

- Queries the **Android Contacts Provider** (requires `READ_CONTACTS` permission)  
- Supports **nickname overrides** (e.g., say "Mum" → maps to "Mother's real name")  
- Uses **phonetic fuzzy matching** (`PhoneticNormaliser`) — handles speech recognition errors like "Sumana" vs "Suman"  
- Returns **at most 3 candidates** — if >1, FSM enters `Disambiguating` state to let user pick

---

### 9. 📤 SMS Adapter (`infrastructure/adapters/`)

**File:** [`SmsAdapter.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/infrastructure/adapters/SmsAdapter.kt)

- Sends SMS using Android's `SmsManager`  
- Returns a `SendResult(successful, platform, detail)` model  
- Supports validation and fallback routing via `MessagingRouter`  
- **Future platforms:** WhatsApp, Email (via OAuth)

---

### 10. 👁️ Vision Engine (`infrastructure/vision/`)

**File:** [`AuraVisionEngine.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/infrastructure/vision/AuraVisionEngine.kt)

- Bridges **CameraX** with **Gemini Nano Vision** (on-device LLM)  
- `describeScene()` → headlessly captures an image and returns a text description spoken aloud  
- Triggered by: `"What do you see?"` or `"Describe what's around me"`  
- MVP returns a simulated description; production wires to real Gemini Nano  
- Also used by Navigation Engine for **proactive obstacle detection**

---

### 11. 🧭 Navigation Engine (`infrastructure/navigation/`)

**File:** [`AuraNavigationEngine.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/infrastructure/navigation/AuraNavigationEngine.kt)

- **Spatial navigation** inside the user's home  
- Works with a `Pose(x, y, z, yaw)` coordinate system  
- `getSafetyAwareInstruction()`:
  1. Calls Vision Engine first — checks for obstacles in camera frame
  2. If obstacle detected → speaks warning: *"Stop. There is an obstacle directly in front of you."*
  3. Otherwise, calculates turn direction and distance to target  
- Provides turn-by-turn audio directions: *"Turn 45 degrees right. Walk 3 steps."*

---

### 12. 🗺️ House Map Repository (`service/navigation/`)

**File:** [`HouseMapRepository.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/service/navigation/HouseMapRepository.kt)

- Stores named room anchors (e.g., `"kitchen"` → `Pose(1.0, 0, 2.0, 90°)`)  
- User builds the map by walking room-to-room and saying *"This is the kitchen"*  
- Navigation engine queries this map to plot paths  
- **Production plan:** Persist in encrypted SQLCipher Room database

---

### 13. 🧠 Personal Memory Service (`service/memory/`)

**File:** [`PersonalMemoryService.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/service/memory/PersonalMemoryService.kt)

- Stores personal facts encrypted via **AES-256-GCM + AES-256-SIV** (Android Keystore)  
- User says: *"Remember that my doctor's appointment is Tuesday"*  
- Recalled with natural temporal context: *"You told me 3 hours ago that..."*  
- Uses `EncryptedSharedPreferences` — zero plaintext data written to disk

---

### 14. 🛡️ Safety Monitor (`service/safety/`)

**File:** [`SafetyMonitor.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/service/safety/SafetyMonitor.kt)

Always-on background safety features:

| Trigger | Detection | AURA's Response |
|---|---|---|
| **Fall/Impact** | Accelerometer `> 45 m/s²` | *"I detected a hard impact. Are you okay?"* |
| **Low Battery** | 15% | *"Battery is low. Please charge soon."* |
| **Critical Battery** | 5% | *"Emergency: Battery at 5%. I may shut down."* |
| **Fire Alarm** | Audio Classifier (TFLite) | *"Emergency! I hear a fire alarm. Evacuate!"* |
| **Doorbell** | Audio Classifier | *"Someone is at the door."* |
| **Baby Crying** | Audio Classifier | *"I hear a baby crying nearby."* |

---

### 15. 🔐 Secure Token Manager (`service/security/`)

**File:** [`SecureTokenManager.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/service/security/SecureTokenManager.kt)

- Stores OAuth tokens and API keys in **Android Keystore**  
- Uses hardware-backed key storage where available  
- Required for: Email OAuth (Phase 2), future API integrations

---

### 16. 🧰 AuraToolbox & AppFunctions (`service/tools/`)

**File:** [`AuraToolbox.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/service/tools/AuraToolbox.kt) | [`AuraAppFunctions.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/service/tools/AuraAppFunctions.kt)

`AuraToolbox` — the internal tool executor. Handles:
- `tell_joke` — tells a joke
- `set_timer` — starts a countdown
- `get_weather` — fetches weather
- `get_news` — fetches headlines

`AuraAppFunctions` — exposes AURA to **Android's AppFunctions API**:  
This makes AURA's capabilities available to the **system-level Gemini assistant** — so even Gemini on the device can delegate tasks like "Send a message for me" to AURA. Exposed functions:
- `sendMessage(targetName, message)` → sends SMS
- `setAlarm(time, label)` → alarm
- `findMyRoom(roomName)` → navigation
- `checkAuraStatus()` → system check
- `tellJoke()`, `setTimer(seconds)`, `summarizeNotifications()`, `getBatteryLevel()`

---

### 17. 🔄 VoiceInteractionController (`presentation/`)

**File:** [`VoiceInteractionController.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/presentation/VoiceInteractionController.kt)

The **central orchestrator** of the entire voice loop:

1. `start()` → plays a beep, acquires audio focus, speaks *"AURA is listening"*, waits for STT result
2. Routes command to the right handler:
   - `"What do you see?"` → Vision Engine
   - `"Remember that..."` → Memory Service
   - `"Take me to the kitchen"` → Navigation
   - `"Map my house"` → Mapping session
   - `"Joke"` / `"Timer"` / `"Weather"` → Toolbox
   - Messaging commands → FSM flow
3. `enroll()` → runs the 3-phrase voice registration session
4. `startMapping()` → interactive room-by-room mapping session
5. `startNavigation(room)` → real-time navigation with vision-aware obstacle detection

---

### 18. ⚙️ LifecycleResilienceManager (`platform/`)

**File:** [`LifecycleResilienceManager.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/platform/LifecycleResilienceManager.kt)

The **always-on foreground service** — the backbone of AURA. It:

- Starts as a `START_STICKY` foreground service with a persistent notification
- Wires together **every subsystem** on startup: audio streamer, wake word, TTS/STT, vision, navigation, memory, toolbox, safety monitor
- Wakes the screen (via WakeLock) when the wake word fires
- Updates the notification text and Compose UI in real-time with status (Listening / Thinking / Speaking)
- Exposes a `serviceStatus: StateFlow<String>` so the UI reacts without polling

---

### 19. 🎨 UI (MainActivity + Theme)

**File:** [`MainActivity.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/MainActivity.kt)

The UI is minimal by design (the app is blind-first):

- A **pulsing animated circle** that changes color based on state:
  - 🔵 **Cyan** → Listening
  - 🟣 **Purple** → Thinking / Processing
  - 🟢 **Green** → Speaking
- Displays the current status text (e.g. *"Listening"*, *"Processing"*)
- Shows `"Say 'AURA' to begin."`
- A **Stop Assistant** button (accessibility fallback)

On startup, it requests all needed permissions: `RECORD_AUDIO`, `SEND_SMS`, `ACCESS_FINE_LOCATION`, `CAMERA`, `POST_NOTIFICATIONS`.

---

### 20. 🤝 OS Assistant Integration (`platform/assistant/`)

- `AuraVoiceInteractionService` — registers AURA as an **Android default assistant**
- When user long-presses the power button → OS calls AURA's session
- `AuraVoiceInteractionSession.onShow()` → signals the background service to start the voice loop; hides any UI (eyes-free)
- `AuraRecognitionService` — registers AURA as a **system-level speech recognition provider**

---

## 📦 Domain Models

**File:** [`Models.kt`](file:///c:/Users/sowmi/OneDrive/Desktop/auraa/app/src/main/java/com/aura/models/Models.kt)

| Model | Description |
|---|---|
| `Contact` | id, displayName, phoneNumber, email |
| `Platform` | `SMS`, `EMAIL`, `WHATSAPP` |
| `SendResult` | successful, platform, detail |
| `Intent.SendMessage` | recipient, body, platform |
| `Intent.ExecuteTool` | toolName, parameters map |
| `Intent.Cancel/Repeat/Unknown` | Control intents |
| `ConversationContext` | lastContact, lastPlatform, lastMessage, turnCount, timestamp |

**Error types** (`AuraError`):  
`STTFailure`, `IntentLowConfidence`, `ContactNotFound`, `AmbiguousContact`, `NetworkUnavailable`, `AuthExpired`, `PlatformFailure`, `AudioFocusLost`, `PermissionDenied`, `DraftSaveFailed`

---

## 🔒 Privacy & Security Design

- **Zero message content written to disk** in this sprint
- Memory stored in **hardware-backed AES-256 encrypted** shared preferences  
- No audio, contact, draft, or token persisted in plaintext — ever
- Speaker verification prevents AURA from acting on a stranger's voice
- Confirmation handler requires **explicit spoken affirmative** — no passive "okay" triggers a send

---

## 🗺️ Feature Roadmap

### ✅ Implemented (Sprint 1)
- Wake word detection, TTS/STT, audio focus management
- Speaker enrollment and spectral voice verification
- 11-state deterministic FSM for messaging
- Strict confirmation gate (4 phrases only)
- Contact lookup with phonetic fuzzy matching
- SMS sending adapter
- Vision engine (CameraX + Gemini Nano skeleton)
- Indoor navigation with obstacle awareness
- House mapping via voice
- Personal memory (encrypted, with temporal context)
- Safety monitoring (fall, battery, audio events)
- Toolbox (jokes, timers, weather, news)
- AppFunctions integration (Gemini can call AURA)
- Android OS default assistant integration

### 🔲 Planned (Sprint 2+)
- **Porcupine** licensed wake-word model file
- **Vosk** on-device Tamil/Hindi/English models
- **SQLCipher** Room DB for encrypted drafts, shortcuts, schedules
- **Email OAuth** (client ID + redirect URI)
- WhatsApp integration via Accessibility Service
- Caregiver companion app
- Incoming SMS reader
- Boot receiver + WorkManager scheduled tasks
- Signed foreground-service notification

---

## 🧱 Tech Stack Summary

| Component | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| Async | Kotlin Coroutines + Flow |
| STT | Android SpeechRecognizer |
| TTS | Android TextToSpeech (en-IN) |
| Wake Word | Porcupine (stub) |
| Vision | CameraX + Gemini Nano Vision |
| Navigation | Custom spatial engine |
| Speaker ID | Spectral energy fingerprint |
| Memory | EncryptedSharedPreferences (AES-256) |
| AppFunctions | `androidx.appfunctions` |
| OS Assistant | `VoiceInteractionService` |
| Safety | Accelerometer + Audio Classifier |
| SMS | Android SmsManager |
| Build | Gradle KTS + KSP |
