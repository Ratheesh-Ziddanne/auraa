/**
 * Real-Time Obstacle Detection & Risk Evaluation Module
 * Supports COCO-SSD / MobileNet object classes, distance estimation,
 * risk classification (SAFE, CAUTION, DANGER), and alert debouncing.
 */

class ObstacleDetector {
    constructor() {
        // Known typical physical heights of obstacles in metres for pinhole distance estimation
        this.OBJECT_HEIGHTS = {
            'person': 1.7,
            'chair': 0.85,
            'table': 0.75,
            'wall': 2.5,
            'door': 2.0,
            'staircase': 1.5,
            'bag': 0.4,
            'backpack': 0.45,
            'bicycle': 1.0,
            'dustbin': 0.7,
            'trash can': 0.7,
            'pole': 2.2,
            'vehicle': 1.5,
            'bench': 0.5,
            'potted plant': 0.8,
            'box': 0.5
        };

        // Track active obstacles to avoid spamming the user on every frame (Section 13)
        // Key: label + approx position, Value: { lastAlertTime, lastRiskLevel, lastDistance }
        this.trackedObstacles = new Map();
        this.ALERT_COOLDOWN_MS = 3500; // Minimum delay before repeating identical alert
    }

    /**
     * Estimates distance from camera frame bounding box.
     * Uses pinhole camera model: Distance = (RealHeight * FocalLength) / PixelHeight
     * @param {string} label 
     * @param {number} boxHeightNorm Normalized box height [0, 1]
     * @param {number} boxWidthNorm Normalized box width [0, 1]
     * @returns {number} Distance in metres, rounded to 1 decimal place
     */
    estimateDistance(label, boxHeightNorm, boxWidthNorm = 0.5) {
        const cleanLabel = (label || '').toLowerCase().trim();
        const realHeight = this.OBJECT_HEIGHTS[cleanLabel] || 1.0;
        
        // Approximate calibrated mobile camera focal constant (norm height of 1.0 at 1.0m)
        const focalConstant = 0.58;
        const normH = Math.max(0.05, Math.min(boxHeightNorm, 1.0));
        
        const estDist = (realHeight * focalConstant) / normH;
        return Math.max(0.3, Math.min(parseFloat(estDist.toFixed(1)), 15.0));
    }

    /**
     * Classifies risk level into SAFE, CAUTION, or DANGER.
     * @param {number} distance Metres
     * @param {number} xCenterNorm Normalized horizontal center [0=left, 0.5=center, 1=right]
     * @returns {'SAFE' | 'CAUTION' | 'DANGER'}
     */
    classifyRisk(distance, xCenterNorm = 0.5) {
        const inDirectPath = xCenterNorm >= 0.25 && xCenterNorm <= 0.75;
        
        if (distance < 1.2 && inDirectPath) {
            return 'DANGER';
        }
        if (distance < 1.8) {
            return 'DANGER';
        }
        if (distance <= 3.2) {
            return inDirectPath ? 'CAUTION' : 'SAFE';
        }
        return 'SAFE';
    }

    /**
     * Evaluates a detected object, updates tracking state, and determines if an alert is needed.
     * Implements Section 13 alert debounce rule:
     * "Don't repeat alert every frame. Alert again only if risk increases or distance drops significantly."
     */
    evaluateObstacle(rawObj) {
        const type = (rawObj.type || rawObj.label || 'Obstacle').trim();
        const boxHeight = rawObj.boxHeightNorm || (rawObj.distance ? (1.0 / rawObj.distance) : 0.35);
        const boxCenter = rawObj.xCenterNorm !== undefined ? rawObj.xCenterNorm : 0.5;
        
        const distance = rawObj.distance !== undefined ? 
            parseFloat(rawObj.distance) : 
            this.estimateDistance(type, boxHeight, rawObj.boxWidthNorm);

        const riskLevel = rawObj.risk_level || this.classifyRisk(distance, boxCenter);
        const trackerKey = `${type.toLowerCase()}`;
        const now = Date.now();

        const tracked = this.trackedObstacles.get(trackerKey);
        let shouldAlert = false;
        let alertMessage = '';
        let guidanceAdvice = '';

        // Formulate spatial guidance advice
        if (boxCenter < 0.35) {
            guidanceAdvice = 'Obstacle on your left. Stay centered or step right.';
        } else if (boxCenter > 0.65) {
            guidanceAdvice = 'Obstacle on your right. Stay centered or step left.';
        } else {
            guidanceAdvice = 'Directly blocking your path. Stop or move to your right.';
        }

        if (riskLevel === 'DANGER') {
            alertMessage = `Stop! ${type} detected directly ahead, ${distance} metres away. ${guidanceAdvice}`;
        } else if (riskLevel === 'CAUTION') {
            alertMessage = `Warning. ${type} ${distance} metres ahead. Move slightly to your ${boxCenter > 0.5 ? 'left' : 'right'}.`;
        } else {
            alertMessage = `${type} detected ${distance} metres ahead. Path is safe.`;
        }

        if (!tracked) {
            // New obstacle detected
            shouldAlert = (riskLevel === 'DANGER' || riskLevel === 'CAUTION');
            this.trackedObstacles.set(trackerKey, {
                lastAlertTime: now,
                lastRiskLevel: riskLevel,
                lastDistance: distance,
                type: type
            });
        } else {
            const riskEscalated = (tracked.lastRiskLevel === 'SAFE' && riskLevel !== 'SAFE') ||
                                  (tracked.lastRiskLevel === 'CAUTION' && riskLevel === 'DANGER');
            const significantlyCloser = (tracked.lastDistance - distance) >= 0.8;
            const cooldownExpired = (now - tracked.lastAlertTime) > this.ALERT_COOLDOWN_MS;

            if (riskEscalated || (significantlyCloser && riskLevel !== 'SAFE') || (cooldownExpired && riskLevel === 'DANGER')) {
                shouldAlert = true;
                tracked.lastAlertTime = now;
                tracked.lastRiskLevel = riskLevel;
                tracked.lastDistance = distance;
            }
        }

        return {
            type,
            distance,
            risk_level: riskLevel,
            xCenterNorm: boxCenter,
            shouldAlert,
            alertMessage,
            guidanceAdvice,
            timestamp: new Date().toISOString()
        };
    }

    /**
     * Resets tracked obstacles (e.g., when changing rooms or rerouting).
     */
    resetTracking() {
        this.trackedObstacles.clear();
    }
}

module.exports = ObstacleDetector;
