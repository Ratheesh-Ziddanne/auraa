const db = require('./db');

async function seed() {
    console.log('Seeding IndoorAI database with realistic campus navigation data...');

    // Wait 500ms for schema creation if needed
    await new Promise(r => setTimeout(r, 600));

    // Clear existing tables
    await db.run('DELETE FROM users');
    await db.run('DELETE FROM buildings');
    await db.run('DELETE FROM locations');
    await db.run('DELETE FROM routes');
    await db.run('DELETE FROM obstacles');
    await db.run('DELETE FROM navigation_history');
    await db.run('DELETE FROM emotion_history');

    // 1. Users
    await db.run(`
        INSERT INTO users (user_id, name, email, password, phone, emergency_contact, preferences)
        VALUES (1, 'Alex Mercer', 'alex@aura.ai', 'securepass123', '+1-555-0199', '+1-555-9911 (Guardian Amma)', '{"contrast":"high","speechRate":1.0,"haptic":true,"theme":"dark_accessible"}')
    `);

    // 2. Buildings
    await db.run(`
        INSERT INTO buildings (building_id, building_name, number_of_floors, description)
        VALUES (1, 'Science & Engineering Complex (Block A)', 2, 'Main engineering and computer science complex with tactile paving, BLE beacons, and ADA-compliant indoor ramps.')
    `);

    // 3. Locations (Ground Floor & First Floor)
    const locations = [
        // Floor 0 (Ground Floor)
        { id: 1, bid: 1, floor: 0, name: 'Block A Entrance', type: 'entrance', x: 10, y: 10, safe: 1, uuid: 'fda50693-a4e2-4fb1-afcf-c6eb07647825', major: 1, minor: 1 },
        { id: 2, bid: 1, floor: 0, name: 'Main Corridor', type: 'corridor', x: 30, y: 10, safe: 0, uuid: 'fda50693-a4e2-4fb1-afcf-c6eb07647825', major: 1, minor: 2 },
        { id: 3, bid: 1, floor: 0, name: 'Help Desk / Reception', type: 'help_desk', x: 20, y: 25, safe: 1, uuid: 'fda50693-a4e2-4fb1-afcf-c6eb07647825', major: 1, minor: 3 },
        { id: 4, bid: 1, floor: 0, name: 'Central Staircase', type: 'stairs', x: 50, y: 10, safe: 0, uuid: 'fda50693-a4e2-4fb1-afcf-c6eb07647825', major: 1, minor: 4 },
        { id: 5, bid: 1, floor: 0, name: 'Elevator GF', type: 'elevator', x: 50, y: 25, safe: 0, uuid: 'fda50693-a4e2-4fb1-afcf-c6eb07647825', major: 1, minor: 5 },
        { id: 6, bid: 1, floor: 0, name: 'Cafeteria', type: 'cafeteria', x: 75, y: 10, safe: 0, uuid: 'fda50693-a4e2-4fb1-afcf-c6eb07647825', major: 1, minor: 6 },
        { id: 7, bid: 1, floor: 0, name: 'Emergency Exit Ground Floor', type: 'exit', x: 95, y: 10, safe: 1, uuid: 'fda50693-a4e2-4fb1-afcf-c6eb07647825', major: 1, minor: 7 },

        // Floor 1 (First Floor)
        { id: 8, bid: 1, floor: 1, name: 'Staircase Landing 1F', type: 'stairs', x: 50, y: 10, safe: 0, uuid: 'fda50693-a4e2-4fb1-afcf-c6eb07647825', major: 2, minor: 1 },
        { id: 9, bid: 1, floor: 1, name: 'Elevator 1F', type: 'elevator', x: 50, y: 25, safe: 0, uuid: 'fda50693-a4e2-4fb1-afcf-c6eb07647825', major: 2, minor: 2 },
        { id: 10, bid: 1, floor: 1, name: 'First Floor Corridor', type: 'corridor', x: 65, y: 10, safe: 0, uuid: 'fda50693-a4e2-4fb1-afcf-c6eb07647825', major: 2, minor: 3 },
        { id: 11, bid: 1, floor: 1, name: 'Computer Lab', type: 'lab', x: 85, y: 10, safe: 0, uuid: 'fda50693-a4e2-4fb1-afcf-c6eb07647825', major: 2, minor: 4 },
        { id: 12, bid: 1, floor: 1, name: 'Library', type: 'library', x: 85, y: 35, safe: 0, uuid: 'fda50693-a4e2-4fb1-afcf-c6eb07647825', major: 2, minor: 5 },
        { id: 13, bid: 1, floor: 1, name: 'Restroom 1F', type: 'restroom', x: 30, y: 10, safe: 0, uuid: 'fda50693-a4e2-4fb1-afcf-c6eb07647825', major: 2, minor: 6 },
        { id: 14, bid: 1, floor: 1, name: 'Emergency Exit 1F', type: 'exit', x: 95, y: 10, safe: 1, uuid: 'fda50693-a4e2-4fb1-afcf-c6eb07647825', major: 2, minor: 7 }
    ];

    for (const loc of locations) {
        await db.run(`
            INSERT INTO locations (location_id, building_id, floor, location_name, type, x_coordinate, y_coordinate, is_safe_zone, beacon_uuid, beacon_major, beacon_minor)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `, [loc.id, loc.bid, loc.floor, loc.name, loc.type, loc.x, loc.y, loc.safe, loc.uuid, loc.major, loc.minor]);
    }

    // 4. Precalculated Routes
    const sampleRoutes = [
        {
            bid: 1,
            source: 'Block A Entrance',
            dest: 'Computer Lab',
            dist: 85,
            time: 2.0,
            waypoints: JSON.stringify([
                { step: 1, instruction: 'Move straight along Main Corridor for 20 metres.', floor: 0, x: 30, y: 10 },
                { step: 2, instruction: 'The central staircase is 20 metres ahead. Take the stairs up to the First Floor.', floor: 0, x: 50, y: 10 },
                { step: 3, instruction: 'Arrived on First Floor. Continue straight along First Floor Corridor for 15 metres.', floor: 1, x: 65, y: 10 },
                { step: 4, instruction: 'Turn slightly right. The Computer Lab double doors are directly in front of you.', floor: 1, x: 85, y: 10 }
            ])
        },
        {
            bid: 1,
            source: 'Block A Entrance',
            dest: 'Library',
            dist: 95,
            time: 2.2,
            waypoints: JSON.stringify([
                { step: 1, instruction: 'Move straight along Main Corridor for 20 metres.', floor: 0, x: 30, y: 10 },
                { step: 2, instruction: 'Take the central staircase on your left up to the First Floor.', floor: 0, x: 50, y: 10 },
                { step: 3, instruction: 'Walk along the First Floor Corridor for 15 metres towards the east wing.', floor: 1, x: 65, y: 10 },
                { step: 4, instruction: 'Turn right at the junction and walk 25 metres. The Quiet Study Library entrance is on your left.', floor: 1, x: 85, y: 35 }
            ])
        },
        {
            bid: 1,
            source: 'Block A Entrance',
            dest: 'Emergency Exit Ground Floor',
            dist: 60,
            time: 1.2,
            isEmergency: 1,
            waypoints: JSON.stringify([
                { step: 1, instruction: 'Emergency Route: Move straight east along the unobstructed main corridor.', floor: 0, x: 50, y: 10 },
                { step: 2, instruction: 'Emergency exit push-bar door is directly ahead at 12 o\'clock.', floor: 0, x: 95, y: 10 }
            ])
        }
    ];

    for (const r of sampleRoutes) {
        await db.run(`
            INSERT INTO routes (building_id, source, destination, distance, estimated_time, waypoints, is_emergency)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        `, [r.bid, r.source, r.dest, r.dist, r.time, r.waypoints, r.isEmergency || 0]);
    }

    // 5. Initial Obstacles
    await db.run(`
        INSERT INTO obstacles (type, distance, risk_level, x_pos, y_pos)
        VALUES 
            ('Chair', 3.2, 'CAUTION', 42.0, 10.5),
            ('Person', 1.1, 'DANGER', 60.0, 10.0),
            ('Dustbin', 4.5, 'SAFE', 25.0, 12.0)
    `);

    // 6. Navigation History
    await db.run(`
        INSERT INTO navigation_history (user_id, source, destination, duration, distance, status)
        VALUES
            (1, 'Block A Entrance', 'Cafeteria', 95, 65, 'COMPLETED'),
            (1, 'Block A Entrance', 'Help Desk / Reception', 40, 25, 'COMPLETED')
    `);

    // 7. Emotion History
    await db.run(`
        INSERT INTO emotion_history (user_id, emotion, confidence, input_text, adapted_style)
        VALUES
            (1, 'CALM', 0.94, 'Take me to the cafeteria', 'STANDARD_GUIDANCE'),
            (1, 'CONFUSED', 0.89, 'I don''t know where I am. Please help me.', 'SUPPORTIVE_DETAILED')
    `);

    console.log('Seed completed successfully!');
}

if (require.main === module) {
    seed().then(() => {
        console.log('Done');
        process.exit(0);
    }).catch(err => {
        console.error('Seed failed:', err);
        process.exit(1);
    });
}

module.exports = seed;
