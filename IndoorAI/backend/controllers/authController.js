const db = require('../../database/db');

class AuthController {
    async register(req, res) {
        try {
            const { name, email, password, phone, emergency_contact, preferences } = req.body;
            if (!name || !email || !password) {
                return res.status(400).json({ error: 'Name, email, and password are required.' });
            }

            const existing = await db.get('SELECT user_id FROM users WHERE email = ?', [email.toLowerCase()]);
            if (existing) {
                return res.status(409).json({ error: 'A user with this email already exists.' });
            }

            const prefString = typeof preferences === 'object' ? JSON.stringify(preferences) : preferences;
            const result = await db.run(`
                INSERT INTO users (name, email, password, phone, emergency_contact, preferences)
                VALUES (?, ?, ?, ?, ?, ?)
            `, [name, email.toLowerCase(), password, phone || null, emergency_contact || null, prefString || '{}']);

            const user = await db.get('SELECT user_id, name, email, phone, emergency_contact, preferences FROM users WHERE user_id = ?', [result.lastID]);
            return res.status(201).json({
                message: 'User registered successfully.',
                user,
                token: `indoor_ai_token_${user.user_id}_${Date.now()}`
            });
        } catch (e) {
            console.error('Registration error:', e);
            return res.status(500).json({ error: 'Registration failed due to server error.' });
        }
    }

    async login(req, res) {
        try {
            const { email, password } = req.body;
            if (!email || !password) {
                return res.status(400).json({ error: 'Email and password are required.' });
            }

            const user = await db.get('SELECT * FROM users WHERE email = ?', [email.toLowerCase().trim()]);
            if (!user || user.password !== password) {
                return res.status(401).json({ error: 'Invalid email or password.' });
            }

            delete user.password;
            return res.json({
                message: 'Login successful.',
                user,
                token: `indoor_ai_token_${user.user_id}_${Date.now()}`
            });
        } catch (e) {
            console.error('Login error:', e);
            return res.status(500).json({ error: 'Login failed due to server error.' });
        }
    }
}

module.exports = new AuthController();
