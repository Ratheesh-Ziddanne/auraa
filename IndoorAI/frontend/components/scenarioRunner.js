/**
 * Interactive Demonstration Scenario Runner (Section 17)
 * Executes the complete 17-step end-to-end user navigation journey
 * with audio, visual map transitions, camera obstacle injection, and emotion adaptation.
 */

class ScenarioRunner {
    constructor(appContext) {
        this.ctx = appContext;
        this.isRunning = false;
        this.currentStep = 0;
        this.timer = null;

        this.steps = [
            {
                num: 1,
                title: 'User Enters Building',
                desc: 'BLE beacon triangulation identifies user location as "Block A Entrance".',
                action: async () => {
                    this.ctx.updateLocation('Block A Entrance', 0, 10, 10);
                    this.ctx.speak('Welcome to the Science and Engineering Complex. Indoor positioning active via Bluetooth beacons. You are at Block A Entrance.');
                }
            },
            {
                num: 2,
                title: 'User Voice Request: "Take me to the library"',
                desc: 'User speaks navigation goal to assistant.',
                action: async () => {
                    this.ctx.setTranscript('Take me to the library');
                    this.ctx.setEmotion('CALM', 0.95, 'STANDARD_GUIDANCE');
                }
            },
            {
                num: 3,
                title: 'Speech-to-Text & Intent Recognition',
                desc: 'System maps speech transcript to Destination: "Library".',
                action: async () => {
                    await this.ctx.calculateRoute('Block A Entrance', 'Library');
                }
            },
            {
                num: 4,
                title: 'Navigation Engine Calculates Route',
                desc: 'Route: Block A Entrance -> Main Corridor -> Staircase -> First Floor -> Library (95m, 2.2 min).',
                action: async () => {
                    this.ctx.speak('Route to Library calculated. Distance is 95 metres, estimated walking time 2.2 minutes. Move straight along Main Corridor for 20 metres.');
                }
            },
            {
                num: 5,
                title: 'Voice Guidance Begins',
                desc: 'User starts walking along the main corridor. Cane sweeping active.',
                action: async () => {
                    this.ctx.updateLocation('Main Corridor', 0, 25, 10);
                }
            },
            {
                num: 6,
                title: 'Camera Continuously Scans Surroundings',
                desc: 'Computer vision scanner scans forward 12 o\'clock path.',
                action: async () => {
                    this.ctx.injectObstacle('Dustbin', 4.5, 0.8);
                }
            },
            {
                num: 7,
                title: 'Obstacle Detected: Chair',
                desc: 'AI vision model identifies chair in corridor at 2.8 metres.',
                action: async () => {
                    this.ctx.injectObstacle('Chair', 2.8, 0.4);
                }
            },
            {
                num: 8,
                title: 'Risk Evaluation: CAUTION',
                desc: 'AI estimates distance (2.8m) and assigns CAUTION risk level.',
                action: async () => {
                    this.ctx.triggerHaptic('DOUBLE_PULSE');
                }
            },
            {
                num: 9,
                title: 'Voice & Haptic Warning Generated',
                desc: 'Voice advises lateral adjustment: "Caution. Chair detected 2.8 metres ahead. Move slightly to your right."',
                action: async () => {
                    this.ctx.speak('Caution. Chair detected 2.8 metres ahead on your left. Move slightly to your right.');
                }
            },
            {
                num: 10,
                title: 'User Distress: "I don\'t know where I am. Please help me."',
                desc: 'User experiences confusion or anxiety in unfamiliar space.',
                action: async () => {
                    this.ctx.setTranscript("I don't know where I am. Please help me.");
                }
            },
            {
                num: 11,
                title: 'Sentiment Analysis: CONFUSED / ANXIOUS (89%)',
                desc: 'NLP engine identifies emotional vulnerability and high anxiety.',
                action: async () => {
                    await this.ctx.analyzeUserSpeech("I don't know where I am. Please help me.");
                }
            },
            {
                num: 12,
                title: 'AI Adapts Communication Style',
                desc: 'Switches to supportive landmark guidance with slower, reassuring prosody.',
                action: async () => {
                    this.ctx.speak("Don't worry. You are safe. You are currently in the main corridor near the reception desk. I will guide you step by step. Pause and feel the tactile floor guide beneath your feet.");
                }
            },
            {
                num: 13,
                title: 'Obstacle Blocks Original Corridor',
                desc: 'Central staircase is blocked by maintenance/heavy obstruction.',
                action: async () => {
                    this.ctx.injectObstacle('Barrier / Person', 0.9, 0.5);
                    this.ctx.triggerHaptic('RAPID_CONTINUOUS');
                }
            },
            {
                num: 14,
                title: 'Dynamic Route Recalculation',
                desc: 'AI detects blockage and reroutes via accessible Elevator to Floor 1.',
                action: async () => {
                    await this.ctx.recalculateReroute('Central Staircase');
                    this.ctx.speak("Obstacle directly ahead. Route recalculated. Turn right into the accessible elevator corridor.");
                    this.ctx.updateLocation('Elevator GF', 0, 50, 25);
                }
            },
            {
                num: 15,
                title: 'Floor Transition to 1F',
                desc: 'User ascends via Elevator to First Floor corridor.',
                action: async () => {
                    this.ctx.mapViewer.setFloor(1);
                    this.ctx.updateLocation('Elevator 1F', 1, 50, 25);
                    this.ctx.speak("Arrived on First Floor. Step out of elevator and continue 20 metres east along First Floor Corridor.");
                }
            },
            {
                num: 16,
                title: 'Final Approach to Destination',
                desc: 'Approaching Quiet Study Library double doors.',
                action: async () => {
                    this.ctx.updateLocation('Library', 1, 85, 35);
                    this.ctx.injectObstacle('Doorway', 2.0, 0.5);
                }
            },
            {
                num: 17,
                title: 'Arrival & Navigation Completed',
                desc: 'Target destination reached safely. Session logged in database.',
                action: async () => {
                    this.ctx.setEmotion('HAPPY', 0.96, 'WARM_INTERACTIVE');
                    this.ctx.triggerHaptic('TAP');
                    this.ctx.speak("You have arrived at the Library entrance on your left. Navigation completed successfully. Have a wonderful day.");
                    this.ctx.completeNavigation();
                }
            }
        ];
    }

    startDemo() {
        this.isRunning = true;
        this.currentStep = 0;
        this.runNextStep();
    }

    stopDemo() {
        this.isRunning = false;
        if (this.timer) {
            clearTimeout(this.timer);
            this.timer = null;
        }
    }

    async runNextStep() {
        if (!this.isRunning) return;
        if (this.currentStep >= this.steps.length) {
            this.isRunning = false;
            console.log('Final demonstration scenario completed!');
            return;
        }

        const step = this.steps[this.currentStep];
        this.renderStepUI(this.currentStep);

        try {
            await step.action();
        } catch (e) {
            console.error(`Step ${step.num} error:`, e);
        }

        this.currentStep++;

        // Delay between scenario steps for realistic voice/audio demonstration
        const stepDelay = (this.currentStep === 4 || this.currentStep === 12 || this.currentStep === 14) ? 5500 : 3800;

        if (this.isRunning) {
            this.timer = setTimeout(() => this.runNextStep(), stepDelay);
        }
    }

    renderStepUI(index) {
        const list = document.getElementById('scenarioStepsList');
        if (!list) return;

        const items = list.querySelectorAll('.scenario-step-item');
        items.forEach((item, i) => {
            if (i < index) {
                item.className = 'scenario-step-item completed';
            } else if (i === index) {
                item.className = 'scenario-step-item active';
                item.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            } else {
                item.className = 'scenario-step-item';
            }
        });
    }
}

window.ScenarioRunner = ScenarioRunner;
