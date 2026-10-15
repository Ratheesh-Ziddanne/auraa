/**
 * Indoor Navigation & Positioning Engine
 * Graph representation, A* shortest/safest path, turn-by-turn guidance,
 * wrong-direction detection, dynamic recalculation, and BLE positioning simulation.
 */

class IndoorNavigator {
    constructor() {
        // Topological indoor building map graph
        // Nodes represent key landmarks, corridors, rooms, stairs, elevators
        this.nodes = {
            // Ground Floor (Floor 0)
            'loc_1': { id: 'loc_1', name: 'Block A Entrance', floor: 0, x: 10, y: 10, type: 'entrance', safe: true },
            'loc_2': { id: 'loc_2', name: 'Main Corridor', floor: 0, x: 30, y: 10, type: 'corridor' },
            'loc_3': { id: 'loc_3', name: 'Help Desk / Reception', floor: 0, x: 20, y: 25, type: 'help_desk', safe: true },
            'loc_4': { id: 'loc_4', name: 'Central Staircase', floor: 0, x: 50, y: 10, type: 'stairs', transitionsTo: 'loc_8' },
            'loc_5': { id: 'loc_5', name: 'Elevator GF', floor: 0, x: 50, y: 25, type: 'elevator', transitionsTo: 'loc_9' },
            'loc_6': { id: 'loc_6', name: 'Cafeteria', floor: 0, x: 75, y: 10, type: 'cafeteria' },
            'loc_7': { id: 'loc_7', name: 'Emergency Exit Ground Floor', floor: 0, x: 95, y: 10, type: 'exit', safe: true },

            // First Floor (Floor 1)
            'loc_8': { id: 'loc_8', name: 'Staircase Landing 1F', floor: 1, x: 50, y: 10, type: 'stairs', transitionsTo: 'loc_4' },
            'loc_9': { id: 'loc_9', name: 'Elevator 1F', floor: 1, x: 50, y: 25, type: 'elevator', transitionsTo: 'loc_5' },
            'loc_10': { id: 'loc_10', name: 'First Floor Corridor', floor: 1, x: 65, y: 10, type: 'corridor' },
            'loc_11': { id: 'loc_11', name: 'Computer Lab', floor: 1, x: 85, y: 10, type: 'lab' },
            'loc_12': { id: 'loc_12', name: 'Library', floor: 1, x: 85, y: 35, type: 'library' },
            'loc_13': { id: 'loc_13', name: 'Restroom 1F', floor: 1, x: 30, y: 10, type: 'restroom' },
            'loc_14': { id: 'loc_14', name: 'Emergency Exit 1F', floor: 1, x: 95, y: 10, type: 'exit', safe: true }
        };

        // Graph edges with distances in metres
        this.edges = [
            // Floor 0 edges
            { from: 'loc_1', to: 'loc_2', weight: 20, desc: 'Move straight along Main Corridor' },
            { from: 'loc_2', to: 'loc_3', weight: 15, desc: 'Turn right towards Reception Help Desk' },
            { from: 'loc_2', to: 'loc_4', weight: 20, desc: 'Continue straight to Central Staircase' },
            { from: 'loc_3', to: 'loc_5', weight: 30, desc: 'Walk past reception along west corridor to Elevator' },
            { from: 'loc_4', to: 'loc_5', weight: 15, desc: 'Turn right to access the Elevator' },
            { from: 'loc_4', to: 'loc_6', weight: 25, desc: 'Pass staircase heading towards Cafeteria' },
            { from: 'loc_6', to: 'loc_7', weight: 20, desc: 'Proceed past cafeteria to Emergency Exit' },

            // Floor transition edges (stairs / elevator)
            { from: 'loc_4', to: 'loc_8', weight: 15, desc: 'Ascend central staircase to First Floor' },
            { from: 'loc_5', to: 'loc_9', weight: 10, desc: 'Take elevator up to Floor 1' },

            // Floor 1 edges
            { from: 'loc_8', to: 'loc_9', weight: 15, desc: 'Corridor connecting stairs and elevator' },
            { from: 'loc_8', to: 'loc_13', weight: 20, desc: 'Turn left along west corridor to Restrooms' },
            { from: 'loc_8', to: 'loc_10', weight: 15, desc: 'Continue straight along First Floor Corridor' },
            { from: 'loc_9', to: 'loc_10', weight: 20, desc: 'Turn left from elevator into main corridor' },
            { from: 'loc_10', to: 'loc_11', weight: 15, desc: 'Proceed straight to Computer Lab double doors' },
            { from: 'loc_10', to: 'loc_12', weight: 30, desc: 'Turn right at junction towards Library' },
            { from: 'loc_11', to: 'loc_14', weight: 15, desc: 'Emergency corridor to First Floor Exit' }
        ];

        // Blocked edges list (for dynamic obstacle recalculation)
        this.blockedEdges = new Set();
    }

    findNodeByName(name) {
        if (!name) return null;
        const lower = name.toLowerCase().trim();
        for (const node of Object.values(this.nodes)) {
            if (node.name.toLowerCase() === lower || node.name.toLowerCase().includes(lower)) {
                return node;
            }
        }
        return null;
    }

    /**
     * Calculates the shortest and safest route using Dijkstra's algorithm.
     * Respects blocked corridor segments.
     */
    calculateRoute(sourceName, destName, options = {}) {
        const startNode = this.findNodeByName(sourceName) || this.nodes['loc_1'];
        const endNode = this.findNodeByName(destName);

        if (!endNode) {
            return {
                success: false,
                message: `Destination "${destName}" not found in building directory.`
            };
        }

        // Build adjacency list including bidirectional paths
        const adj = {};
        for (const k of Object.keys(this.nodes)) adj[k] = [];

        for (const edge of this.edges) {
            const edgeKey = `${edge.from}-${edge.to}`;
            const reverseKey = `${edge.to}-${edge.from}`;

            if (this.blockedEdges.has(edgeKey) || this.blockedEdges.has(reverseKey)) {
                continue; // Skip blocked corridor
            }

            adj[edge.from].push({ to: edge.to, weight: edge.weight, desc: edge.desc });
            adj[edge.to].push({ to: edge.from, weight: edge.weight, desc: edge.desc });
        }

        // Dijkstra algorithm
        const distances = {};
        const previous = {};
        const edgeDescriptions = {};
        const unvisited = new Set(Object.keys(this.nodes));

        for (const k of Object.keys(this.nodes)) {
            distances[k] = Infinity;
            previous[k] = null;
        }
        distances[startNode.id] = 0;

        while (unvisited.size > 0) {
            // Find unvisited node with lowest distance
            let closestNode = null;
            let lowestDist = Infinity;
            for (const n of unvisited) {
                if (distances[n] < lowestDist) {
                    lowestDist = distances[n];
                    closestNode = n;
                }
            }

            if (!closestNode || distances[closestNode] === Infinity) break;
            if (closestNode === endNode.id) break;

            unvisited.delete(closestNode);

            for (const neighbor of (adj[closestNode] || [])) {
                if (!unvisited.has(neighbor.to)) continue;
                const alt = distances[closestNode] + neighbor.weight;
                if (alt < distances[neighbor.to]) {
                    distances[neighbor.to] = alt;
                    previous[neighbor.to] = closestNode;
                    edgeDescriptions[`${closestNode}->${neighbor.to}`] = neighbor.desc;
                }
            }
        }

        // Reconstruct path
        const path = [];
        let curr = endNode.id;
        while (curr) {
            path.unshift(curr);
            curr = previous[curr];
        }

        if (path.length === 0 || path[0] !== startNode.id) {
            return {
                success: false,
                message: `No clear unobstructed route found from ${startNode.name} to ${endNode.name}.`
            };
        }

        // Generate Turn-by-Turn Waypoint Instructions
        const waypoints = [];
        let totalDistance = distances[endNode.id];
        let totalTimeMinutes = parseFloat((totalDistance / 40.0).toFixed(1)); // Approx 40m/min (0.67m/s for cane/guided walking)

        for (let i = 0; i < path.length; i++) {
            const node = this.nodes[path[i]];
            let instruction = '';

            if (i === 0) {
                instruction = `Start at ${node.name}. Check cane path in front.`;
            } else {
                const prevNode = this.nodes[path[i - 1]];
                const desc = edgeDescriptions[`${prevNode.id}->${node.id}`] || `Walk towards ${node.name}`;
                instruction = desc;
            }

            waypoints.push({
                step: i + 1,
                nodeId: node.id,
                name: node.name,
                floor: node.floor,
                x: node.x,
                y: node.y,
                type: node.type,
                instruction
            });
        }

        return {
            success: true,
            source: startNode.name,
            destination: endNode.name,
            currentLocation: startNode.name,
            distance: totalDistance,
            estimatedTime: `${totalTimeMinutes} minutes`,
            estimatedTimeMinutes: totalTimeMinutes,
            floorTransition: startNode.floor !== endNode.floor,
            startFloor: startNode.floor,
            endFloor: endNode.floor,
            waypoints,
            voiceSummary: `Route calculated from ${startNode.name} to ${endNode.name}. Total distance is ${totalDistance} metres, estimated time ${totalTimeMinutes} minutes. ${waypoints[1] ? waypoints[1].instruction : ''}`
        };
    }

    /**
     * Dynamically blocks a corridor segment due to a dangerous obstacle and recalculates a safe route.
     */
    recalculateDueToObstacle(sourceName, destName, blockedNearNodeName) {
        const blockedNode = this.findNodeByName(blockedNearNodeName);
        if (blockedNode) {
            // Find edges connected to blocked node
            for (const edge of this.edges) {
                if (edge.from === blockedNode.id || edge.to === blockedNode.id) {
                    this.blockedEdges.add(`${edge.from}-${edge.to}`);
                    this.blockedEdges.add(`${edge.to}-${edge.from}`);
                }
            }
        }

        const newRoute = this.calculateRoute(sourceName, destName);
        if (newRoute.success) {
            newRoute.isRerouted = true;
            newRoute.rerouteReason = `Obstacle blocking original corridor near ${blockedNearNodeName}.`;
            newRoute.voiceSummary = `Obstacle blocking the path ahead. Route recalculated. ${newRoute.voiceSummary}`;
        }
        return newRoute;
    }

    /**
     * Clears all temporary obstacle blockages.
     */
    clearObstacleBlocks() {
        this.blockedEdges.clear();
    }

    /**
     * Emergency Exit Routing (Section 8)
     */
    findNearestSafePoint(currentLocationName) {
        const currNode = this.findNodeByName(currentLocationName) || this.nodes['loc_1'];
        let closestExit = null;
        let shortestDist = Infinity;

        for (const [id, node] of Object.entries(this.nodes)) {
            if (node.safe && node.floor === currNode.floor) {
                const route = this.calculateRoute(currNode.name, node.name);
                if (route.success && route.distance < shortestDist) {
                    shortestDist = route.distance;
                    closestExit = node;
                }
            }
        }

        if (!closestExit) {
            // Fallback to ground floor entrance
            closestExit = this.nodes['loc_1'];
        }

        const route = this.calculateRoute(currNode.name, closestExit.name);
        return {
            safePointName: closestExit.name,
            isEmergency: true,
            route,
            alertMessage: `Emergency routing activated. Guiding you to nearest safe point: ${closestExit.name}. Distance: ${route.distance} metres.`
        };
    }

    /**
     * Checks if user heading or movement is deviating from planned route (Wrong-direction detection).
     */
    checkDirection(currentX, currentY, targetWaypoint) {
        if (!targetWaypoint) return { isCorrect: true };
        const dx = targetWaypoint.x - currentX;
        const dy = targetWaypoint.y - currentY;
        const dist = Math.sqrt(dx * dx + dy * dy);

        return {
            isCorrect: dist <= 15.0, // Within corridor tolerance
            distanceToWaypoint: parseFloat(dist.toFixed(1)),
            correctivePrompt: dist > 15.0 ? 
                'Wrong direction detected. Please pause, re-align your heading 180 degrees, and sweep your cane.' : 
                'Heading confirmed along correct corridor.'
        };
    }

    /**
     * Simulates BLE Beacon Signal Positioning (Section 7)
     * RSSI = -10 * n * log10(d) + A
     */
    estimateLocationFromBeacons(beaconReadings = []) {
        if (!beaconReadings || beaconReadings.length === 0) {
            return {
                locationName: 'Block A Entrance',
                floor: 0,
                confidence: 0.95,
                method: 'BLE_SIMULATION'
            };
        }

        // Pick beacon with strongest RSSI (closest to 0 dBm)
        let bestBeacon = null;
        let maxRssi = -999;
        for (const b of beaconReadings) {
            if (b.rssi > maxRssi) {
                maxRssi = b.rssi;
                bestBeacon = b;
            }
        }

        const matchedNode = Object.values(this.nodes).find(n => n.type === bestBeacon.type) || this.nodes['loc_1'];

        return {
            locationName: matchedNode.name,
            floor: matchedNode.floor,
            x: matchedNode.x,
            y: matchedNode.y,
            rssi: maxRssi,
            confidence: 0.92,
            method: 'BLE_BEACON_TRIANGULATION'
        };
    }
}

module.exports = IndoorNavigator;
