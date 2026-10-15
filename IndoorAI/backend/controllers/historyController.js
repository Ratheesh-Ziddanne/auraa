const db = require('../../database/db');

class HistoryController {
    async getNavigationHistory(req, res) {
        try {
            const { user_id, limit = 50 } = req.query;
            let sql = 'SELECT * FROM navigation_history';
            const params = [];

            if (user_id) {
                sql += ' WHERE user_id = ?';
                params.push(user_id);
            }
            sql += ' ORDER BY timestamp DESC LIMIT ?';
            params.push(parseInt(limit, 10));

            const history = await db.all(sql, params);
            return res.json({ navigationHistory: history });
        } catch (e) {
            console.error('getNavigationHistory error:', e);
            return res.status(500).json({ error: 'Failed to retrieve navigation history.' });
        }
    }

    async getEmotionHistory(req, res) {
        try {
            const { user_id, limit = 50 } = req.query;
            let sql = 'SELECT * FROM emotion_history';
            const params = [];

            if (user_id) {
                sql += ' WHERE user_id = ?';
                params.push(user_id);
            }
            sql += ' ORDER BY timestamp DESC LIMIT ?';
            params.push(parseInt(limit, 10));

            const history = await db.all(sql, params);
            return res.json({ emotionHistory: history });
        } catch (e) {
            console.error('getEmotionHistory error:', e);
            return res.status(500).json({ error: 'Failed to retrieve emotion history.' });
        }
    }
}

module.exports = new HistoryController();
