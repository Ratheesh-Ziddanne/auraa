-- Database Schema for IndoorAI Navigation System

CREATE TABLE IF NOT EXISTS users (
    user_id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    email TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL,
    phone TEXT,
    emergency_contact TEXT,
    preferences TEXT DEFAULT '{"contrast":"high","speechRate":1.0,"haptic":true}',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS buildings (
    building_id INTEGER PRIMARY KEY AUTOINCREMENT,
    building_name TEXT NOT NULL,
    number_of_floors INTEGER NOT NULL,
    description TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS locations (
    location_id INTEGER PRIMARY KEY AUTOINCREMENT,
    building_id INTEGER NOT NULL,
    floor INTEGER NOT NULL,
    location_name TEXT NOT NULL,
    type TEXT NOT NULL DEFAULT 'room',
    x_coordinate REAL NOT NULL,
    y_coordinate REAL NOT NULL,
    is_safe_zone INTEGER DEFAULT 0,
    beacon_uuid TEXT,
    beacon_major INTEGER,
    beacon_minor INTEGER,
    FOREIGN KEY(building_id) REFERENCES buildings(building_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS routes (
    route_id INTEGER PRIMARY KEY AUTOINCREMENT,
    building_id INTEGER NOT NULL,
    source TEXT NOT NULL,
    destination TEXT NOT NULL,
    distance REAL NOT NULL,
    estimated_time REAL NOT NULL,
    waypoints TEXT NOT NULL,
    is_accessible INTEGER DEFAULT 1,
    is_emergency INTEGER DEFAULT 0,
    FOREIGN KEY(building_id) REFERENCES buildings(building_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS obstacles (
    obstacle_id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL,
    distance REAL NOT NULL,
    risk_level TEXT NOT NULL CHECK(risk_level IN ('SAFE', 'CAUTION', 'DANGER')),
    x_pos REAL,
    y_pos REAL,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS navigation_history (
    navigation_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER,
    source TEXT NOT NULL,
    destination TEXT NOT NULL,
    duration INTEGER NOT NULL,
    distance REAL NOT NULL,
    status TEXT NOT NULL CHECK(status IN ('COMPLETED', 'CANCELLED', 'REROUTED', 'EMERGENCY_STOP', 'IN_PROGRESS')),
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS emotion_history (
    emotion_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER,
    emotion TEXT NOT NULL CHECK(emotion IN ('CALM', 'HAPPY', 'CONFUSED', 'ANXIOUS', 'FRUSTRATED', 'STRESSED', 'URGENT')),
    confidence REAL NOT NULL,
    input_text TEXT,
    adapted_style TEXT,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(user_id) REFERENCES users(user_id)
);
