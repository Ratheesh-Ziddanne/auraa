const express = require('express');
const cors = require('cors');
const path = require('path');
const apiRoutes = require('./routes/apiRoutes');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true }));

// API Endpoints
app.use('/api', apiRoutes);

// Serve Frontend UI static files
const frontendDir = path.join(__dirname, '../frontend');
app.use(express.static(frontendDir));

// Fallback to index.html for SPA routes
app.get('*', (req, res, next) => {
    if (req.path.startsWith('/api')) {
        return next();
    }
    res.sendFile(path.join(frontendDir, 'pages/index.html'));
});

// Central error handler
app.use((err, req, res, next) => {
    console.error('Unhandled server error:', err);
    res.status(500).json({ error: 'Internal server error', details: err.message });
});

if (require.main === module) {
    app.listen(PORT, () => {
        console.log(`=======================================================`);
        console.log(`  IndoorAI Navigation & Safety Platform Running`);
        console.log(`  URL: http://localhost:${PORT}`);
        console.log(`  API Base: http://localhost:${PORT}/api`);
        console.log(`  Accessible UI: http://localhost:${PORT}/pages/index.html`);
        console.log(`=======================================================`);
    });
}

module.exports = app;
