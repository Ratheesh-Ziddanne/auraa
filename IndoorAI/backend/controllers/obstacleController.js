const db = require('../../database/db');
const ObstacleDetector = require('../../ai/obstacle_detection/obstacleDetector');

const detectorInstance = new ObstacleDetector();

class ObstacleController {
    async detectObstacle(req, res) {
        try {
            const rawObj = req.body || {};
            const evaluation = detectorInstance.evaluateObstacle(rawObj);

            // Record significant obstacle in database for audit & safety history
            if (evaluation.risk_level === 'DANGER' || evaluation.risk_level === 'CAUTION') {
                await db.run(`
                    INSERT INTO obstacles (type, distance, risk_level, x_pos, y_pos)
                    VALUES (?, ?, ?, ?, ?)
                `, [evaluation.type, evaluation.distance, evaluation.risk_level, rawObj.x_pos || null, rawObj.y_pos || null]);
            }

            return res.json({
                status: 'SUCCESS',
                ai_mode: req.body.ai_mode || 'ACTIVE',
                model: 'YOLOv8-Mobile / COCO-SSD',
                obstacle: evaluation
            });
        } catch (e) {
            console.error('detectObstacle error:', e);
            return res.status(500).json({ error: 'Obstacle evaluation failed.' });
        }
    }

    getDetector() {
        return detectorInstance;
    }
}

module.exports = new ObstacleController();
