const http = require('http');
const app = require('../server');

const TEST_PORT = 3199;
let server;

function request(method, path, body = null) {
    return new Promise((resolve, reject) => {
        const payload = body ? JSON.stringify(body) : null;
        const req = http.request({
            hostname: '127.0.0.1',
            port: TEST_PORT,
            path: path,
            method: method,
            headers: {
                'Content-Type': 'application/json',
                ...(payload ? { 'Content-Length': Buffer.byteLength(payload) } : {})
            }
        }, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    const parsed = JSON.parse(data);
                    resolve({ statusCode: res.statusCode, body: parsed });
                } catch (e) {
                    resolve({ statusCode: res.statusCode, body: data });
                }
            });
        });

        req.on('error', reject);
        if (payload) req.write(payload);
        req.end();
    });
}

async function runTests() {
    console.log('--- Starting IndoorAI Automated Test Suite ---');
    server = app.listen(TEST_PORT);
    let passed = 0;
    let failed = 0;

    function assert(condition, message) {
        if (condition) {
            console.log(`  [PASS] ${message}`);
            passed++;
        } else {
            console.error(`  [FAIL] ${message}`);
            failed++;
        }
    }

    try {
        // 1. Healthcheck
        const health = await request('GET', '/api/health');
        assert(health.statusCode === 200 && health.body.status === 'UP', 'GET /api/health returns 200 UP');

        // 2. Auth: Login
        const login = await request('POST', '/api/login', { email: 'alex@aura.ai', password: 'securepass123' });
        assert(login.statusCode === 200 && login.body.token, 'POST /api/login succeeds for valid user');

        // 3. Buildings & Locations
        const buildings = await request('GET', '/api/buildings');
        assert(buildings.statusCode === 200 && buildings.body.buildings.length > 0, 'GET /api/buildings returns building list');

        const locations = await request('GET', '/api/locations');
        assert(locations.statusCode === 200 && locations.body.locations.length >= 7, 'GET /api/locations returns campus locations');

        // 4. Route Calculation
        const route = await request('GET', '/api/route?source=Block%20A%20Entrance&destination=Computer%20Lab');
        assert(route.statusCode === 200 && route.body.success === true && route.body.distance === 85, 'GET /api/route returns 85m route to Computer Lab');

        // 5. Navigation Start & Stop
        const startNav = await request('POST', '/api/navigation/start', {
            user_id: 1,
            source: 'Block A Entrance',
            destination: 'Library'
        });
        assert(startNav.statusCode === 201 && startNav.body.navigation_id, 'POST /api/navigation/start initiates session');

        const stopNav = await request('POST', '/api/navigation/stop', {
            navigation_id: startNav.body.navigation_id,
            duration: 120,
            status: 'COMPLETED'
        });
        assert(stopNav.statusCode === 200, 'POST /api/navigation/stop terminates session');

        // 6. Obstacle Detection & Risk
        const obsDanger = await request('POST', '/api/obstacle/detect', {
            type: 'Person',
            distance: 0.9,
            xCenterNorm: 0.5
        });
        assert(obsDanger.statusCode === 200 && obsDanger.body.obstacle.risk_level === 'DANGER', 'POST /api/obstacle/detect classifies <1.2m as DANGER');

        const obsCaution = await request('POST', '/api/obstacle/detect', {
            type: 'Chair',
            distance: 2.2,
            xCenterNorm: 0.5
        });
        assert(obsCaution.statusCode === 200 && obsCaution.body.obstacle.risk_level === 'CAUTION', 'POST /api/obstacle/detect classifies 2.2m as CAUTION');

        // 7. Sentiment & Emotion Analysis
        const sentConfused = await request('POST', '/api/sentiment/analyze', {
            text: 'I don\'t know where I am. Please help me.'
        });
        assert(sentConfused.statusCode === 200 && sentConfused.body.emotion === 'CONFUSED', 'POST /api/sentiment/analyze detects CONFUSED emotion');

        const sentAnxious = await request('POST', '/api/sentiment/analyze', {
            text: 'I am really scared and nervous about walking here'
        });
        assert(sentAnxious.statusCode === 200 && sentAnxious.body.emotion === 'ANXIOUS', 'POST /api/sentiment/analyze detects ANXIOUS emotion');

        // 8. Central AI Decision Engine
        const decision = await request('POST', '/api/decision/decide', {
            userLocation: 'Main Corridor',
            destination: 'Computer Lab',
            obstacle: { type: 'Chair', distance: 0.8, risk_level: 'DANGER', xCenterNorm: 0.5 },
            emotion: 'ANXIOUS'
        });
        assert(decision.statusCode === 200 && decision.body.decision.action === 'STOP', 'POST /api/decision/decide yields STOP when obstacle is DANGER');

        // 9. Emergency
        const emergency = await request('POST', '/api/emergency', {
            user_id: 1,
            currentLocation: 'Main Corridor',
            reason: 'Distress vocalization'
        });
        assert(emergency.statusCode === 200 && emergency.body.status === 'EMERGENCY_ACTIVATED', 'POST /api/emergency routes to nearest safe exit');

        // 10. History Retrieval
        const navHistory = await request('GET', '/api/navigation/history');
        assert(navHistory.statusCode === 200 && Array.isArray(navHistory.body.navigationHistory), 'GET /api/navigation/history returns array');

        const emoHistory = await request('GET', '/api/emotion/history');
        assert(emoHistory.statusCode === 200 && Array.isArray(emoHistory.body.emotionHistory), 'GET /api/emotion/history returns array');

        console.log(`\n========================================`);
        console.log(`Test Suite Finished: ${passed} Passed, ${failed} Failed`);
        console.log(`========================================`);

        if (failed > 0) process.exit(1);
    } catch (e) {
        console.error('Fatal test error:', e);
        process.exit(1);
    } finally {
        if (server) server.close();
    }
}

if (require.main === module) {
    runTests();
}

module.exports = runTests;
