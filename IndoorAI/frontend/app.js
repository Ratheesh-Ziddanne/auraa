/**
 * IndoorAI - Main Frontend Application Controller
 * Coordinates Map, Voice Assistant, Camera Vision, and REST APIs.
 */

class IndoorAiApp {
    constructor() {
        this.currentLocation = 'Block A Entrance';
        this.currentFloor = 0;
        this.currentPositionNorm = { x: 10, y: 10 };
        this.activeRoute = null;
        this.currentStepIndex = 0;
        this.activeNavigationId = null;
        this.navigationStatus = 'IDLE'; // IDLE, ACTIVE, ARRIVED, EMERGENCY

        this.currentEmotion = 'CALM';
        this.currentConfidence = 0.94;
        this.currentGuidanceStyle = 'STANDARD_GUIDANCE';
        this.latestObstacle = null;

        this.init();
    }

    async init() {
        console.log('Initializing IndoorAI frontend application...');

        // 1. Initialize Map Viewer
        this.mapViewer = new IndoorMapViewer('indoorMapCanvas');

        // 2. Initialize Camera & Vision
        this.obstacleCamera = new ObstacleCamera('cameraVideo', 'cameraOverlayCanvas', (obs) => {
            this.handleObstacleFromCamera(obs);
        });

        // 3. Initialize Voice Assistant
        this.voiceAssistant = new VoiceAssistant({
            onCommand: (text) => this.executeVoiceCommand(text),
            onTranscriptUpdate: (text) => this.setTranscript(`"${text}"`),
            onListeningChange: (listening) => this.updateListeningUI(listening)
        });

        // 4. Initialize Demonstration Scenario Runner
        this.scenarioRunner = new ScenarioRunner(this);

        // 5. Wire UI Event Listeners
        this.setupEventListeners();

        // 6. Start Camera / Simulation
        await this.obstacleCamera.startCamera();

        // 7. Initial voice welcome
        setTimeout(() => {
            this.speak("IndoorAI active. You are at Block A Entrance. Tap the orb or say Take me to the library to begin.");
        }, 800);
    }

    setupEventListeners() {
        // High Contrast Toggle
        const btnContrast = document.getElementById('btnContrastToggle');
        if (btnContrast) {
            btnContrast.addEventListener('click', () => {
                document.body.classList.toggle('high-contrast');
            });
        }

        // Voice Orb
        const voiceOrb = document.getElementById('voiceOrbContainer');
        if (voiceOrb) {
            voiceOrb.addEventListener('click', () => {
                this.voiceAssistant.toggleListening();
            });
            voiceOrb.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                    this.voiceAssistant.toggleListening();
                }
            });
        }

        // Start & Stop Navigation
        const btnStart = document.getElementById('btnStartNav');
        if (btnStart) {
            btnStart.addEventListener('click', () => {
                const dest = document.getElementById('selDestination').value;
                this.startNavigationTo(dest);
            });
        }

        const btnStop = document.getElementById('btnStopNav');
        if (btnStop) {
            btnStop.addEventListener('click', () => {
                this.stopNavigation();
            });
        }

        // Floor Toggle Buttons
        const btnF0 = document.getElementById('btnFloor0');
        const btnF1 = document.getElementById('btnFloor1');
        if (btnF0) {
            btnF0.addEventListener('click', () => {
                this.mapViewer.setFloor(0);
                this.speak('Switched view to Ground Floor.');
            });
        }
        if (btnF1) {
            btnF1.addEventListener('click', () => {
                this.mapViewer.setFloor(1);
                this.speak('Switched view to First Floor.');
            });
        }

        // Emergency SOS
        const btnSos = document.getElementById('btnEmergencySos');
        if (btnSos) {
            btnSos.addEventListener('click', () => {
                this.triggerEmergency();
            });
        }

        // Demonstration Scenario Runner
        const btnRunDemo = document.getElementById('btnRunScenario');
        if (btnRunDemo) {
            btnRunDemo.addEventListener('click', () => {
                if (this.scenarioRunner.isRunning) {
                    this.scenarioRunner.stopDemo();
                    btnRunDemo.innerText = '▶ Run Full Demo';
                } else {
                    this.scenarioRunner.startDemo();
                    btnRunDemo.innerText = '⏸ Pause Demo';
                }
            });
        }
    }

    updateListeningUI(isListening) {
        const orb = document.getElementById('voiceOrb');
        const statusText = document.getElementById('voiceStatusText');
        if (orb) {
            if (isListening) orb.classList.add('listening');
            else orb.classList.remove('listening');
        }
        if (statusText) {
            statusText.innerText = isListening ? 'Listening to your voice...' : 'Tap Orb or Say "AURA" to Speak';
        }
    }

    setTranscript(text) {
        const box = document.getElementById('voiceTranscriptBox');
        if (box) box.innerText = text;
    }

    speak(text, onDone = null) {
        this.setTranscript(`AURA: "${text}"`);
        this.voiceAssistant.speak(text, onDone);
    }

    triggerHaptic(pattern) {
        this.voiceAssistant.triggerHaptic(pattern);
    }

    updateLocation(name, floor, x, y) {
        this.currentLocation = name;
        this.currentFloor = floor;
        this.currentPositionNorm = { x, y };

        const lbl = document.getElementById('lblCurrentLoc');
        if (lbl) lbl.innerText = `${name} (Floor ${floor})`;

        this.mapViewer.setUserPosition(x, y, name);
        this.mapViewer.setFloor(floor);
    }

    async calculateRoute(source, destination) {
        try {
            const data = await window.apiClient.getRoute(source, destination);
            if (data.success) {
                this.activeRoute = data;
                this.currentStepIndex = 0;
                this.mapViewer.setRoute(data);

                const lblDist = document.getElementById('lblDistance');
                if (lblDist) lblDist.innerText = `${data.distance} metres`;

                const lblTime = document.getElementById('lblEstTime');
                if (lblTime) lblTime.innerText = data.estimatedTime;

                if (data.waypoints && data.waypoints[1]) {
                    this.updateStepInstruction(data.waypoints[1].instruction, 'Proceed forward along corridor.');
                }
            }
            return data;
        } catch (e) {
            console.error('Failed to calculate route:', e);
        }
    }

    async startNavigationTo(destination) {
        this.navigationStatus = 'ACTIVE';
        this.setTranscript(`Navigating to ${destination}...`);

        try {
            const res = await window.apiClient.startNavigation(this.currentLocation, destination);
            if (res.route) {
                this.activeNavigationId = res.navigation_id;
                this.activeRoute = res.route;
                this.mapViewer.setRoute(res.route);

                const lblDist = document.getElementById('lblDistance');
                if (lblDist) lblDist.innerText = `${res.route.distance} metres`;

                const lblTime = document.getElementById('lblEstTime');
                if (lblTime) lblTime.innerText = res.route.estimatedTime;

                this.speak(res.route.voiceSummary);
            }
        } catch (e) {
            console.error('Start navigation failed:', e);
            this.speak(`Could not calculate route to ${destination}.`);
        }
    }

    async stopNavigation() {
        this.navigationStatus = 'IDLE';
        if (this.activeNavigationId) {
            await window.apiClient.stopNavigation(this.activeNavigationId, 60, 'CANCELLED');
            this.activeNavigationId = null;
        }
        this.activeRoute = null;
        this.mapViewer.setRoute(null);
        this.updateStepInstruction('Navigation stopped.', 'You are at ' + this.currentLocation);
        this.speak('Navigation stopped.');
    }

    completeNavigation() {
        this.navigationStatus = 'ARRIVED';
        if (this.activeNavigationId) {
            window.apiClient.stopNavigation(this.activeNavigationId, 130, 'COMPLETED');
            this.activeNavigationId = null;
        }
        this.updateStepInstruction('Destination Arrived', 'You have arrived at your destination.');
    }

    updateStepInstruction(main, sub) {
        const text = document.getElementById('stepInstructionText');
        const subText = document.getElementById('stepInstructionSub');
        if (text) text.innerText = main;
        if (subText) subText.innerText = sub || '';
    }

    async handleObstacleFromCamera(rawObs) {
        try {
            const res = await window.apiClient.detectObstacle(rawObs);
            if (res.obstacle) {
                this.renderObstacleUI(res.obstacle);

                // Pass into AI Decision Engine
                const decision = await window.apiClient.decideAction({
                    userLocation: this.currentLocation,
                    destination: document.getElementById('selDestination').value,
                    currentRoute: this.activeRoute,
                    currentStepIndex: this.currentStepIndex,
                    obstacle: res.obstacle,
                    emotion: this.currentEmotion,
                    navigationStatus: this.navigationStatus
                });

                if (decision && decision.decision) {
                    this.applyDecision(decision.decision);
                }
            }
        } catch (e) {
            console.error('Obstacle detection error:', e);
        }
    }

    injectObstacle(type, distance, xCenterNorm = 0.5) {
        this.obstacleCamera.injectObstacle(type, distance, xCenterNorm);
        const obsObj = {
            type,
            distance,
            risk_level: distance < 1.5 ? 'DANGER' : (distance <= 3.2 ? 'CAUTION' : 'SAFE'),
            x: this.currentPositionNorm.x + (distance * 3),
            y: this.currentPositionNorm.y
        };
        this.mapViewer.setObstacles([obsObj]);
    }

    renderObstacleUI(obs) {
        this.latestObstacle = obs;
        const card = document.getElementById('obstacleCard');
        const badge = document.getElementById('obstacleRiskBadge');
        const typeEl = document.getElementById('obstacleType');
        const distEl = document.getElementById('obstacleDistance');
        const guideEl = document.getElementById('obstacleGuidance');

        if (card) card.className = `card obstacle-card ${obs.risk_level}`;
        if (badge) {
            badge.className = `obstacle-badge ${obs.risk_level}`;
            badge.innerText = obs.risk_level;
        }
        if (typeEl) typeEl.innerText = obs.type;
        if (distEl) distEl.innerText = `${obs.distance} metres`;
        if (guideEl) guideEl.innerText = obs.guidanceAdvice || 'Path clear.';
    }

    async analyzeUserSpeech(text) {
        try {
            const res = await window.apiClient.analyzeSentiment(text);
            if (res.emotion) {
                this.setEmotion(res.emotion, res.confidence, res.adaptedStyle, res.speechProsody);
            }
            return res;
        } catch (e) {
            console.error('Sentiment analysis error:', e);
        }
    }

    setEmotion(emotion, confidence, adaptedStyle, prosody) {
        this.currentEmotion = emotion;
        this.currentConfidence = confidence;
        this.currentGuidanceStyle = adaptedStyle;

        const iconEl = document.getElementById('emotionIcon');
        const labelEl = document.getElementById('emotionLabel');
        const confEl = document.getElementById('emotionConfidence');
        const styleEl = document.getElementById('guidanceStyleBadge');

        const icons = {
            CALM: '😊',
            HAPPY: '😄',
            CONFUSED: '🤔',
            ANXIOUS: '😰',
            FRUSTRATED: '😤',
            STRESSED: '😫',
            URGENT: '🚨'
        };

        if (iconEl) iconEl.innerText = icons[emotion] || '😐';
        if (labelEl) labelEl.innerText = `State: ${emotion}`;
        if (confEl) confEl.innerText = `Confidence: ${(confidence * 100).toFixed(0)}% | Model: DistilBERT`;
        if (styleEl) styleEl.innerText = `STYLE: ${adaptedStyle.replace('_', ' ')}`;

        this.voiceAssistant.setEmotionProsody(emotion, prosody);
    }

    applyDecision(decision) {
        if (decision.hapticPattern) {
            this.triggerHaptic(decision.hapticPattern);
        }
        if (decision.action === 'STOP') {
            this.updateStepInstruction('STOP! Collision Risk', decision.voiceMessage);
        }
    }

    async recalculateReroute(blockedLandmark) {
        const dest = document.getElementById('selDestination').value;
        const res = await window.apiClient.getRoute(this.currentLocation, dest, blockedLandmark, true);
        if (res.success) {
            this.activeRoute = res;
            this.mapViewer.setRoute(res);
            this.updateStepInstruction('Rerouted Path', res.voiceSummary);
        }
    }

    async triggerEmergency() {
        this.navigationStatus = 'EMERGENCY';
        this.setEmotion('URGENT', 0.99, 'SAFETY_PRIORITY', { rate: 1.1, pitch: 1.15 });
        this.triggerHaptic('RAPID_CONTINUOUS');

        try {
            const data = await window.apiClient.triggerEmergency(this.currentLocation, 'User pressed Emergency SOS Button');
            if (data.route) {
                this.activeRoute = data.route;
                this.mapViewer.setRoute(data.route);
                this.updateStepInstruction(`EMERGENCY: Proceed to ${data.safePoint}`, data.voicePrompt);
                this.speak(data.voicePrompt);
            }
        } catch (e) {
            console.error('Emergency trigger error:', e);
            this.speak('Emergency alert activated. Campus security has been notified.');
        }
    }

    async executeVoiceCommand(rawText) {
        if (!rawText) return;
        const lower = rawText.toLowerCase().trim();
        this.setTranscript(`User: "${rawText}"`);

        // First analyze user emotional sentiment in speech
        await this.analyzeUserSpeech(rawText);

        if (lower.includes('where am i')) {
            this.speak(`You are currently at ${this.currentLocation} on Floor ${this.currentFloor}. The nearest safe reception desk is 15 metres to your right.`);
            return;
        }

        if (lower.includes('library')) {
            document.getElementById('selDestination').value = 'Library';
            this.startNavigationTo('Library');
            return;
        }

        if (lower.includes('computer lab') || lower.includes('lab')) {
            document.getElementById('selDestination').value = 'Computer Lab';
            this.startNavigationTo('Computer Lab');
            return;
        }

        if (lower.includes('cafeteria')) {
            document.getElementById('selDestination').value = 'Cafeteria';
            this.startNavigationTo('Cafeteria');
            return;
        }

        if (lower.includes('nearest exit') || lower.includes('find exit') || lower.includes('exit')) {
            this.triggerEmergency();
            return;
        }

        if (lower.includes('stop navigation') || lower.includes('stop') || lower.includes('cancel')) {
            this.stopNavigation();
            return;
        }

        if (lower.includes('repeat')) {
            this.voiceAssistant.repeatLast();
            return;
        }

        if (lower.includes('in front') || lower.includes('obstacle') || lower.includes('anything in front')) {
            if (this.latestObstacle && this.latestObstacle.risk_level !== 'SAFE') {
                this.speak(`There is a ${this.latestObstacle.type} ${this.latestObstacle.distance} metres ahead. ${this.latestObstacle.guidanceAdvice}`);
            } else {
                this.speak('Your forward walking path is clear for the next 4 metres.');
            }
            return;
        }

        if (lower.includes('help me') || lower.includes('lost') || lower.includes("don't know where i am")) {
            this.speak("Don't worry, you are safe. You are in the main building corridor. Stay right where you are. I will guide you step by step.");
            return;
        }

        // Generic query fallback
        this.speak(`Received: "${rawText}". Say "Take me to the library" or "Where am I" to navigate.`);
    }
}

window.addEventListener('DOMContentLoaded', () => {
    window.app = new IndoorAiApp();
});
