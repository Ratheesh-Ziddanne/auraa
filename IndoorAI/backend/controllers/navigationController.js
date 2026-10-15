const db = require('../../database/db');
const IndoorNavigator = require('../../ai/navigation/indoorNavigator');

const navigatorInstance = new IndoorNavigator();

class NavigationController {
    async getRoute(req, res) {
        try {
            const { source, destination, blockedNear, recalculate } = req.query;
            if (!source || !destination) {
                return res.status(400).json({ error: 'Both "source" and "destination" query parameters are required.' });
            }

            let routeResult;
            if (recalculate === 'true' && blockedNear) {
                routeResult = navigatorInstance.recalculateDueToObstacle(source, destination, blockedNear);
            } else {
                routeResult = navigatorInstance.calculateRoute(source, destination);
            }

            if (!routeResult.success) {
                return res.status(404).json(routeResult);
            }

            // Also check if an identical precomputed route exists in DB
            const dbRoute = await db.get(
                'SELECT route_id FROM routes WHERE LOWER(source) = ? AND LOWER(destination) = ?',
                [source.toLowerCase().trim(), destination.toLowerCase().trim()]
            );

            return res.json({
                ...routeResult,
                route_id: dbRoute ? dbRoute.route_id : null
            });
        } catch (e) {
            console.error('getRoute error:', e);
            return res.status(500).json({ error: 'Failed to calculate indoor route.' });
        }
    }

    async startNavigation(req, res) {
        try {
            const { user_id = 1, source, destination } = req.body;
            if (!source || !destination) {
                return res.status(400).json({ error: 'Both source and destination are required to start navigation.' });
            }

            const routeResult = navigatorInstance.calculateRoute(source, destination);
            if (!routeResult.success) {
                return res.status(404).json(routeResult);
            }

            // Insert initial active record in navigation_history
            const insertResult = await db.run(`
                INSERT INTO navigation_history (user_id, source, destination, duration, distance, status)
                VALUES (?, ?, ?, ?, ?, 'IN_PROGRESS')
            `, [user_id, source, destination, 0, routeResult.distance]);

            return res.status(201).json({
                message: 'Navigation started.',
                navigation_id: insertResult.lastID,
                status: 'ACTIVE',
                route: routeResult
            });
        } catch (e) {
            console.error('startNavigation error:', e);
            return res.status(500).json({ error: 'Failed to start navigation session.' });
        }
    }

    async stopNavigation(req, res) {
        try {
            const { navigation_id, duration = 60, status = 'COMPLETED' } = req.body;
            if (navigation_id) {
                await db.run(`
                    UPDATE navigation_history
                    SET status = ?, duration = ?
                    WHERE navigation_id = ?
                `, [status, duration, navigation_id]);
            }

            navigatorInstance.clearObstacleBlocks();

            return res.json({
                message: 'Navigation session terminated.',
                status: status || 'COMPLETED'
            });
        } catch (e) {
            console.error('stopNavigation error:', e);
            return res.status(500).json({ error: 'Failed to stop navigation session.' });
        }
    }

    getNavigator() {
        return navigatorInstance;
    }
}

module.exports = new NavigationController();
