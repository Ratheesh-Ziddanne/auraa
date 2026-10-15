const db = require('../../database/db');

class BuildingController {
    async getBuildings(req, res) {
        try {
            const buildings = await db.all('SELECT * FROM buildings ORDER BY building_name ASC');
            return res.json({ buildings });
        } catch (e) {
            console.error('getBuildings error:', e);
            return res.status(500).json({ error: 'Failed to retrieve buildings.' });
        }
    }

    async getLocations(req, res) {
        try {
            const { building_id, floor } = req.query;
            let sql = 'SELECT * FROM locations';
            const params = [];
            const conditions = [];

            if (building_id) {
                conditions.push('building_id = ?');
                params.push(building_id);
            }
            if (floor !== undefined) {
                conditions.push('floor = ?');
                params.push(floor);
            }

            if (conditions.length > 0) {
                sql += ' WHERE ' + conditions.join(' AND ');
            }
            sql += ' ORDER BY floor ASC, location_name ASC';

            const locations = await db.all(sql, params);
            return res.json({ locations });
        } catch (e) {
            console.error('getLocations error:', e);
            return res.status(500).json({ error: 'Failed to retrieve locations.' });
        }
    }
}

module.exports = new BuildingController();
