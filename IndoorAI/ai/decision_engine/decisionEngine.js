/**
 * Central AI Decision Engine (Section 6)
 * Synthesizes:
 * - User Location & Heading
 * - Planned Navigation Route
 * - Real-Time Obstacle & Distance
 * - User Emotional State
 * - System Safety Constraints
 * 
 * Generates:
 * - Recommended Action (CONTINUE, SLOW_DOWN, STOP, EVADE_RIGHT, EVADE_LEFT, REVIER, RECALCULATE, EMERGENCY_HALT)
 * - Empathetic Voice Instruction
 * - Haptic Feedback Pattern (TAP, PULSE, CONTINUOUS_RAPID)
 * - UI Safety Badges
 */

class DecisionEngine {
    constructor(navigator, obstacleDetector, sentimentAnalyzer) {
        this.navigator = navigator;
        this.obstacleDetector = obstacleDetector;
        this.sentimentAnalyzer = sentimentAnalyzer;
    }

    /**
     * Central decision synthesis
     */
    decide(inputState) {
        const {
            userLocation = 'Block A Entrance',
            destination = 'Computer Lab',
            currentRoute = null,
            currentStepIndex = 0,
            obstacle = null, // { type, distance, risk_level, xCenterNorm }
            emotion = 'CALM',
            emotionConfidence = 0.9,
            navigationStatus = 'ACTIVE' // IDLE, ACTIVE, REROUTING, PAUSED, ARRIVED
        } = inputState;

        const currentWaypoint = currentRoute && currentRoute.waypoints ? 
            currentRoute.waypoints[currentStepIndex] : null;

        let action = 'CONTINUE';
        let voiceMessage = '';
        let hapticPattern = 'TAP';
        let riskBadge = 'SAFE';
        let shouldRecalculate = false;

        // 1. Critical Obstacle Collision Risk Evaluation
        if (obstacle && obstacle.risk_level === 'DANGER') {
            action = 'STOP';
            riskBadge = 'DANGER';
            hapticPattern = 'RAPID_CONTINUOUS';

            if (obstacle.distance < 1.0) {
                // Immediate collision risk
                voiceMessage = `Stop immediately! Obstacle directly ahead, less than 1 metre away.`;
                shouldRecalculate = true;
            } else {
                const evadeDirection = (obstacle.xCenterNorm !== undefined && obstacle.xCenterNorm < 0.5) ? 'right' : 'left';
                voiceMessage = `Stop! ${obstacle.type || 'Obstacle'} detected directly ahead at ${obstacle.distance} metres. Please step slightly to your ${evadeDirection}.`;
                action = evadeDirection === 'right' ? 'EVADE_RIGHT' : 'EVADE_LEFT';
                shouldRecalculate = obstacle.distance < 1.5;
            }

            // Adapt voice tone if user is already anxious
            if (emotion === 'ANXIOUS' || emotion === 'CONFUSED') {
                voiceMessage = `Pause and breathe. You're completely safe. ${voiceMessage} Wait for my signal before stepping forward.`;
            }

            return {
                action,
                riskBadge,
                hapticPattern,
                voiceMessage,
                shouldRecalculate,
                obstacleType: obstacle.type,
                obstacleDistance: obstacle.distance,
                emotion,
                guidanceStyle: 'SAFETY_PRIORITY'
            };
        }

        // 2. Caution Obstacle
        if (obstacle && obstacle.risk_level === 'CAUTION') {
            action = 'SLOW_DOWN';
            riskBadge = 'CAUTION';
            hapticPattern = 'DOUBLE_PULSE';

            const side = (obstacle.xCenterNorm !== undefined && obstacle.xCenterNorm < 0.5) ? 'left' : 'right';
            voiceMessage = `Caution. ${obstacle.type || 'Object'} ${obstacle.distance} metres ahead on your ${side}. Continue slowly.`;

            if (emotion === 'CONFUSED') {
                voiceMessage += ` Keep your cane sweeping along the tactile floor tiles.`;
            }

            return {
                action,
                riskBadge,
                hapticPattern,
                voiceMessage,
                shouldRecalculate: false,
                obstacleType: obstacle.type,
                obstacleDistance: obstacle.distance,
                emotion,
                guidanceStyle: 'CAUTIONARY_AWARE'
            };
        }

        // 3. Normal / Sentiment-Adapted Navigation Guidance
        const rawStepInstruction = currentWaypoint ? 
            currentWaypoint.instruction : 
            `Continue navigating towards ${destination}.`;

        const distanceRemaining = currentRoute ? currentRoute.distance : 0;

        switch (emotion) {
            case 'CONFUSED':
                action = 'ORIENT_LANDMARK';
                hapticPattern = 'PULSE';
                riskBadge = 'SAFE';
                voiceMessage = `Don't worry. You are near ${userLocation}. I will guide you with landmark cues. ${rawStepInstruction} Feel the wall or door frame on your side for orientation.`;
                break;

            case 'ANXIOUS':
                action = 'REASSURE_STEP';
                hapticPattern = 'GENTLE_PULSE';
                riskBadge = 'SAFE';
                voiceMessage = `You are safe. Stay steady. ${rawStepInstruction} I am scanning every metre in front of you.`;
                break;

            case 'FRUSTRATED':
                action = 'DIRECT_STEP';
                hapticPattern = 'TAP';
                riskBadge = 'SAFE';
                voiceMessage = `${rawStepInstruction} Destination ${distanceRemaining} metres.`;
                break;

            case 'URGENT':
                action = 'EXPEDITE_SAFE';
                hapticPattern = 'DOUBLE_PULSE';
                riskBadge = 'CAUTION';
                voiceMessage = `Direct path to nearest safe point: ${rawStepInstruction}. Walk briskly.`;
                break;

            case 'HAPPY':
                action = 'CONTINUE';
                hapticPattern = 'TAP';
                riskBadge = 'SAFE';
                voiceMessage = `Great progress! ${rawStepInstruction}`;
                break;

            case 'CALM':
            default:
                action = 'CONTINUE';
                hapticPattern = 'TAP';
                riskBadge = 'SAFE';
                voiceMessage = rawStepInstruction;
                break;
        }

        return {
            action,
            riskBadge,
            hapticPattern,
            voiceMessage,
            shouldRecalculate: false,
            obstacleType: obstacle ? obstacle.type : null,
            obstacleDistance: obstacle ? obstacle.distance : null,
            emotion,
            guidanceStyle: emotion === 'CALM' ? 'STANDARD_GUIDANCE' : 'EMOTION_ADAPTED'
        };
    }
}

module.exports = DecisionEngine;
