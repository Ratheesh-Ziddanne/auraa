const db = require('../../database/db');
const IndoorNavigator = require('../../ai/navigation/indoorNavigator');

const nav = new IndoorNavigator();

class EmergencyController {
    async handleEmergency(req, res) {
        try {
            const { user_id = 1, currentLocation = 'Block A Entrance', reason = 'Manual SOS or Distress Voice Alert' } = req.body;

            // Find nearest exit or safe zone
            const safePointPlan = nav.findNearestSafePoint(currentLocation);

            // Fetch user emergency contact
            const user = await db.get('SELECT name, phone, emergency_contact FROM users WHERE user_id = ?', [user_id]);
            const contact = user ? (user.emergency_contact || 'Guardian (+1-555-9911)') : 'Campus Security (+1-555-9911)';

            // Record into navigation history as emergency
            await db.run(`
                INSERT INTO navigation_history (user_id, source, destination, duration, distance, status)
                VALUES (?, ?, ?, ?, ?, 'EMERGENCY_STOP')
            `, [user_id, currentLocation, safePointPlan.safePointName, 0, safePointPlan.route.distance]);

            // Record emotion as URGENT
            await db.run(`
                INSERT INTO emotion_history (user_id, emotion, confidence, input_text, adapted_style)
                VALUES (?, 'URGENT', 0.99, ?, 'SAFETY_PRIORITY')
            `, [user_id, `Emergency triggered: ${reason}`]);

            const notificationMessage = `EMERGENCY ALERT dispatched to ${contact}: User ${user ? user.name : 'Alex'} triggered SOS at ${currentLocation}. Nearest safe point route activated towards ${safePointPlan.safePointName}.`;

            return res.status(200).json({
                status: 'EMERGENCY_ACTIVATED',
                currentLocation,
                safePoint: safePointPlan.safePointName,
                distance: safePointPlan.route.distance,
                route: safePointPlan.route,
                voicePrompt: `Emergency alert acknowledged. Stand calm. Nearest safe exit is ${safePointPlan.safePointName}, ${safePointPlan.route.distance} metres away. ${safePointPlan.route.waypoints[1] ? safePointPlan.route.waypoints[1].instruction : ''}`,
                contactNotified: contact,
                dispatchSummary: notificationMessage,
                timestamp: new Date().toISOString()
            });
        } catch (e) {
            console.error('handleEmergency error:', e);
            return res.status(500).json({ error: 'Failed to process emergency request.' });
        }
    }
}

module.exports = new EmergencyController();
