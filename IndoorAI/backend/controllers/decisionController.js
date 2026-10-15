const DecisionEngine = require('../../ai/decision_engine/decisionEngine');
const NavigationController = require('./navigationController');
const ObstacleController = require('./obstacleController');
const SentimentController = require('./sentimentController');

const decisionEngine = new DecisionEngine(
    NavigationController.getNavigator(),
    ObstacleController.getDetector(),
    SentimentController.getAnalyzer()
);

class DecisionController {
    async decide(req, res) {
        try {
            const inputState = req.body || {};
            const decision = decisionEngine.decide(inputState);
            return res.json({
                status: 'SUCCESS',
                decision
            });
        } catch (e) {
            console.error('Decision error:', e);
            return res.status(500).json({ error: 'Decision engine processing failed.' });
        }
    }
}

module.exports = new DecisionController();
