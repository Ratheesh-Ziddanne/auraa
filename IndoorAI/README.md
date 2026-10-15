# IndoorAI - AI-Powered Indoor Navigation & Safety System

> A complete AI-powered indoor navigation system designed specifically to assist visually impaired and blind users in navigating complex buildings safely.

---

## 🌟 Major AI Capabilities

1. **Indoor Navigation & Positioning**:
   - Multi-floor navigation (Ground Floor & 1st Floor) with stairs & elevator routing.
   - Topological A* / Dijkstra shortest and safest route calculation.
   - Micro-guidance turn-by-turn auditory instructions with clock directions.
   - BLE Beacon RSSI positioning triangulation simulation ($RSSI = -10 \cdot n \cdot \log_{10}(d) + A$).
   - Dynamic route recalculation when corridors are blocked by obstacles.
   - Wrong-direction detection and automated heading correction.

2. **Real-Time Obstacle Detection & Risk Evaluation**:
   - Computer vision object classification (Person, Chair, Table, Wall, Door, Staircase, Bag, Bicycle, Dustbin, Pole, Vehicle).
   - Optical pinhole camera model distance estimation ($D = \frac{F \times H_{real}}{h_{pixel}}$).
   - 3-Tier Risk Classification:
     - `SAFE` (> 3.0m)
     - `CAUTION` (1.5m – 3.0m)
     - `DANGER` (< 1.5m or directly in path)
   - Debounced object tracking engine preventing repetitive alert fatigue.

3. **Sentiment & Emotion Analysis**:
   - NLP speech sentiment and vocal pattern analyzer.
   - Detects 7 emotional states: `CALM`, `HAPPY`, `CONFUSED`, `ANXIOUS`, `FRUSTRATED`, `STRESSED`, `URGENT`.
   - Empathetic adaptive communication:
     - **Confused**: Provides supportive landmark cues (walls, doors, tactile floor guides).
     - **Anxious**: Provides short, reassuring, step-by-step instructions.
     - **Frustrated**: Skips extra narrative; provides concise direct turns only.
     - **Emergency / Urgent**: Immediate safety halt and exit path guidance.
     - **Calm**: Standard turn-by-turn guidance.

4. **Central AI Decision Engine**:
   - Synthesizes user location + destination + planned route + detected obstacles + distances + user emotion.
   - Recommends coordinated multi-modal actions: Voice instructions + Haptic vibrations + UI safety badges.

---

## 🏗️ Project Structure

```text
IndoorAI/
│
├── frontend/
│   ├── pages/
│   │   └── index.html                # Accessible high-contrast HTML interface
│   ├── components/
│   │   ├── mapViewer.js              # HTML5 Canvas 2D indoor floorplan renderer
│   │   ├── obstacleCamera.js         # Camera stream & bounding box overlay
│   │   ├── voiceAssistant.js         # Web Speech STT/TTS with prosody adaptation
│   │   └── scenarioRunner.js         # 17-step end-to-end demonstration runner
│   ├── services/
│   │   └── apiClient.js              # REST API Client
│   ├── styles/
│   │   └── main.css                  # Accessible high-contrast CSS design tokens
│   └── app.js                        # Main frontend application coordinator
│
├── backend/
│   ├── controllers/
│   │   ├── authController.js         # /login & /register
│   │   ├── buildingController.js     # /buildings & /locations
│   │   ├── navigationController.js   # /route, /navigation/start, /navigation/stop
│   │   ├── obstacleController.js     # /obstacle/detect
│   │   ├── sentimentController.js    # /sentiment/analyze
│   │   ├── decisionController.js     # /decision/decide
│   │   ├── emergencyController.js    # /emergency
│   │   └── historyController.js      # /navigation/history & /emotion/history
│   ├── routes/
│   │   └── apiRoutes.js              # Express API router
│   ├── tests/
│   │   └── test_api.js               # Automated 15-test test suite
│   └── server.js                     # Express server & static asset host
│
├── ai/
│   ├── obstacle_detection/
│   │   └── obstacleDetector.js       # CV detection, distance, & tracking
│   ├── sentiment_analysis/
│   │   └── sentimentAnalyzer.js      # NLP emotion analysis & adaptive styles
│   ├── navigation/
│   │   └── indoorNavigator.js        # Graph routing, floor transitions, BLE
│   └── decision_engine/
│       └── decisionEngine.js         # Central AI decision synthesis
│
├── database/
│   ├── schema.sql                    # SQLite DDL schema (7 tables)
│   ├── db.js                         # Promisified SQLite wrapper
│   ├── seed.js                       # Realistic campus building map seed data
│   └── indoor_ai.db                  # Persistent SQLite database file
│
├── package.json                      # Node.js dependencies
└── README.md                         # Full documentation
```

---

## 🗄️ Database Tables

| Table | Description |
|---|---|
| `users` | User credentials, emergency guardian phone, accessible preferences |
| `buildings` | Building metadata and floor counts |
| `locations` | Rooms, corridors, stairs, elevators, BLE beacon UUIDs, coordinates |
| `routes` | Precomputed & dynamic routes with distances, times, and waypoints |
| `obstacles` | Detected obstacles, distances, risk levels, and timestamps |
| `navigation_history` | Historical sessions (source, destination, duration, distance, status) |
| `emotion_history` | Logged emotional states, confidence scores, and adapted styles |

---

## 🚀 Quick Start Guide

### 1. Install Dependencies
```bash
cd IndoorAI
npm install
```

### 2. Seed Database
```bash
npm run seed
```

### 3. Run Automated Tests
```bash
npm test
```
All 15 tests will execute and verify health, authentication, routing, obstacle risk classification, emotion detection, and emergency routing.

### 4. Start the Application
```bash
npm start
```
Open **[http://localhost:3000](http://localhost:3000)** in any modern web browser.

---

## 🎙️ Accessible Voice Commands

- *"Where am I?"* &rarr; Announces current room and nearest safe zone.
- *"Take me to the library"* &rarr; Calculates route and begins turn-by-turn guidance.
- *"Take me to the computer lab"* &rarr; Floor-to-floor guidance via stairs.
- *"Is there anything in front of me?"* &rarr; Reports forward obstacle distance.
- *"Find nearest exit"* or *"Help me"* &rarr; Triggers immediate emergency route.
- *"Stop navigation"* &rarr; Halts guidance session safely.
- *"Repeat that"* &rarr; Repeats the last auditory instruction.

---

## 🎬 Final Demonstration Scenario (Section 17)

Click **"▶ Run Full Demo"** on the dashboard to trigger the automated 17-step end-to-end scenario:
1. User enters building (BLE position identified as "Block A Entrance").
2. User requests: *"Take me to the library"*.
3. Speech-to-text converts command.
4. Route to Library calculated (95m, 2.2 min).
5. Voice guidance begins along Main Corridor.
6. Camera continuously scans path.
7. Obstacle detected: Chair.
8. AI classifies risk: `CAUTION` at 2.8m.
9. Voice warning advises stepping slightly right.
10. User distress: *"I don't know where I am. Please help me."*
11. Sentiment analysis detects `CONFUSED` / `ANXIOUS` (89% confidence).
12. AI adapts tone to supportive landmark guidance.
13. Corridor blocked by heavy obstruction.
14. Dynamic recalculation switches to accessible Elevator path.
15. Floor transition to 1F corridor.
16. Approach to Library double doors.
17. Arrival confirmed and session completed.
