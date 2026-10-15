const express = require('express');
const router = express.Router();

const authController = require('../controllers/authController');
const buildingController = require('../controllers/buildingController');
const navigationController = require('../controllers/navigationController');
const obstacleController = require('../controllers/obstacleController');
const sentimentController = require('../controllers/sentimentController');
const emergencyController = require('../controllers/emergencyController');
const historyController = require('../controllers/historyController');
const decisionController = require('../controllers/decisionController');

// 1. Authentication
router.post('/login', (req, res) => authController.login(req, res));
router.post('/register', (req, res) => authController.register(req, res));

// 2. Buildings & Locations
router.get('/buildings', (req, res) => buildingController.getBuildings(req, res));
router.get('/locations', (req, res) => buildingController.getLocations(req, res));

// 3. Navigation & Routing
router.get('/route', (req, res) => navigationController.getRoute(req, res));
router.post('/navigation/start', (req, res) => navigationController.startNavigation(req, res));
router.post('/navigation/stop', (req, res) => navigationController.stopNavigation(req, res));

// 4. Real-Time Obstacle Detection
router.post('/obstacle/detect', (req, res) => obstacleController.detectObstacle(req, res));

// 5. Sentiment & Emotion Analysis
router.post('/sentiment/analyze', (req, res) => sentimentController.analyzeSentiment(req, res));

// 6. Central Decision Engine
router.post('/decision/decide', (req, res) => decisionController.decide(req, res));

// 7. Safety & Emergency
router.post('/emergency', (req, res) => emergencyController.handleEmergency(req, res));

// 8. History & Analytics
router.get('/navigation/history', (req, res) => historyController.getNavigationHistory(req, res));
router.get('/emotion/history', (req, res) => historyController.getEmotionHistory(req, res));

// Healthcheck
router.get('/health', (req, res) => {
    res.json({
        status: 'UP',
        system: 'IndoorAI Navigation & Safety Platform',
        version: '1.0.0',
        timestamp: new Date().toISOString()
    });
});

module.exports = router;
