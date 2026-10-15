package com.aura.service.navigation;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * On-device Indoor AI Navigation & Positioning Service.
 * Implements topological graph routing, floor transitions (stairs/elevator),
 * dynamic obstacle recalculation, BLE beacon estimation, and optional sync with IndoorAI backend.
 */
public class IndoorAiService {
    private static final String TAG = "IndoorAiService";
    private static final String DEFAULT_BACKEND_URL = "http://10.0.2.2:3000/api"; // Android emulator localhost alias

    public static class IndoorNode {
        public final String id;
        public final String name;
        public final int floor;
        public final double x;
        public final double y;
        public final String type;
        public final boolean isSafeZone;

        public IndoorNode(String id, String name, int floor, double x, double y, String type, boolean isSafeZone) {
            this.id = id;
            this.name = name;
            this.floor = floor;
            this.x = x;
            this.y = y;
            this.type = type;
            this.isSafeZone = isSafeZone;
        }
    }

    public static class IndoorEdge {
        public final String from;
        public final String to;
        public final double weight;
        public final String description;

        public IndoorEdge(String from, String to, double weight, String description) {
            this.from = from;
            this.to = to;
            this.weight = weight;
            this.description = description;
        }
    }

    public static class IndoorRouteResult {
        public final boolean success;
        public final String source;
        public final String destination;
        public final double totalDistanceMeters;
        public final double estimatedTimeMinutes;
        public final boolean floorTransition;
        public final int startFloor;
        public final int endFloor;
        public final List<String> stepInstructions;
        public final String voiceSummary;
        public final boolean isRerouted;

        public IndoorRouteResult(boolean success, String source, String destination, double totalDistanceMeters,
                                  double estimatedTimeMinutes, boolean floorTransition, int startFloor, int endFloor,
                                  List<String> stepInstructions, String voiceSummary, boolean isRerouted) {
            this.success = success;
            this.source = source;
            this.destination = destination;
            this.totalDistanceMeters = totalDistanceMeters;
            this.estimatedTimeMinutes = estimatedTimeMinutes;
            this.floorTransition = floorTransition;
            this.startFloor = startFloor;
            this.endFloor = endFloor;
            this.stepInstructions = stepInstructions;
            this.voiceSummary = voiceSummary;
            this.isRerouted = isRerouted;
        }
    }

    private final Map<String, IndoorNode> nodes = new HashMap<>();
    private final List<IndoorEdge> edges = new ArrayList<>();
    private final Set<String> blockedEdges = new HashSet<>();

    public IndoorAiService() {
        initCampusGraph();
    }

    public IndoorAiService(android.content.Context context) {
        this();
    }

    private void initCampusGraph() {
        // Ground Floor (Floor 0)
        addNode(new IndoorNode("loc_1", "Block A Entrance", 0, 10, 10, "entrance", true));
        addNode(new IndoorNode("loc_2", "Main Corridor", 0, 30, 10, "corridor", false));
        addNode(new IndoorNode("loc_3", "Help Desk / Reception", 0, 20, 25, "help_desk", true));
        addNode(new IndoorNode("loc_4", "Central Staircase", 0, 50, 10, "stairs", false));
        addNode(new IndoorNode("loc_5", "Elevator GF", 0, 50, 25, "elevator", false));
        addNode(new IndoorNode("loc_6", "Cafeteria", 0, 75, 10, "cafeteria", false));
        addNode(new IndoorNode("loc_7", "Emergency Exit Ground Floor", 0, 95, 10, "exit", true));

        // First Floor (Floor 1)
        addNode(new IndoorNode("loc_8", "Staircase Landing 1F", 1, 50, 10, "stairs", false));
        addNode(new IndoorNode("loc_9", "Elevator 1F", 1, 50, 25, "elevator", false));
        addNode(new IndoorNode("loc_10", "First Floor Corridor", 1, 65, 10, "corridor", false));
        addNode(new IndoorNode("loc_11", "Computer Lab", 1, 85, 10, "lab", false));
        addNode(new IndoorNode("loc_12", "Library", 1, 85, 35, "library", false));
        addNode(new IndoorNode("loc_13", "Restroom 1F", 1, 30, 10, "restroom", false));
        addNode(new IndoorNode("loc_14", "Emergency Exit 1F", 1, 95, 10, "exit", true));

        // Ground Floor Edges
        addEdge("loc_1", "loc_2", 20, "Move straight along Main Corridor for 20 metres.");
        addEdge("loc_2", "loc_3", 15, "Turn right towards Reception Help Desk.");
        addEdge("loc_2", "loc_4", 20, "Continue straight along Main Corridor to Central Staircase.");
        addEdge("loc_3", "loc_5", 30, "Walk past reception along west corridor to Elevator.");
        addEdge("loc_4", "loc_5", 15, "Turn right to access the Elevator corridor.");
        addEdge("loc_4", "loc_6", 25, "Pass staircase heading east towards Cafeteria.");
        addEdge("loc_6", "loc_7", 20, "Proceed past cafeteria to Emergency Exit A.");

        // Floor Transition Edges
        addEdge("loc_4", "loc_8", 15, "Take central staircase on your left up to First Floor.");
        addEdge("loc_5", "loc_9", 10, "Take accessible elevator up to Floor 1.");

        // Floor 1 Edges
        addEdge("loc_8", "loc_9", 15, "Corridor connecting staircase and elevator.");
        addEdge("loc_8", "loc_13", 20, "Turn left along west corridor to First Floor Restroom.");
        addEdge("loc_8", "loc_10", 15, "Continue straight along First Floor Corridor for 15 metres.");
        addEdge("loc_9", "loc_10", 20, "Turn left from elevator into main First Floor corridor.");
        addEdge("loc_10", "loc_11", 15, "Proceed straight. The Computer Lab double doors are directly in front of you.");
        addEdge("loc_10", "loc_12", 30, "Turn right at junction. Walk 30 metres. The Library entrance is on your left.");
        addEdge("loc_11", "loc_14", 15, "Emergency corridor to First Floor Fire Exit.");
    }

    private void addNode(IndoorNode node) {
        nodes.put(node.id, node);
    }

    private void addEdge(String from, String to, double weight, String desc) {
        edges.add(new IndoorEdge(from, to, weight, desc));
    }

    public IndoorNode findNode(String nameOrQuery) {
        if (nameOrQuery == null || nameOrQuery.trim().isEmpty()) return null;
        String lower = nameOrQuery.toLowerCase(Locale.ROOT).trim()
                .replaceAll("^(?i)(?:the|a|my)\\s+", "")
                .replaceAll("(?i)\\s+(?:in|inside)\\s+(?:the|a|my)\\s+", " ")
                .trim();

        for (IndoorNode node : nodes.values()) {
            String nodeLower = node.name.toLowerCase(Locale.ROOT);
            if (nodeLower.equals(lower) || nodeLower.contains(lower) || lower.contains(nodeLower)) {
                return node;
            }
        }

        // Common synonyms
        if (lower.contains("lab") || lower.contains("computer")) return nodes.get("loc_11");
        if (lower.contains("library") || lower.contains("book")) return nodes.get("loc_12");
        if (lower.contains("cafeteria") || lower.contains("canteen") || lower.contains("food")) return nodes.get("loc_6");
        if (lower.contains("reception") || lower.contains("help desk") || lower.contains("desk")) return nodes.get("loc_3");
        if (lower.contains("entrance") || lower.contains("gate") || lower.contains("lobby")) return nodes.get("loc_1");
        if (lower.contains("exit") || lower.contains("fire exit") || lower.contains("emergency exit")) return nodes.get("loc_7");
        if (lower.contains("restroom") || lower.contains("washroom") || lower.contains("toilet")) return nodes.get("loc_13");
        if (lower.contains("elevator") || lower.contains("lift")) return nodes.get("loc_5");
        if (lower.contains("stairs") || lower.contains("staircase")) return nodes.get("loc_4");

        return null;
    }

    /**
     * Calculates optimal route between source and destination using Dijkstra pathfinding.
     */
    public IndoorRouteResult calculateRoute(String sourceName, String destName) {
        return calculateRouteInternal(sourceName, destName, false);
    }

    /**
     * Dynamically recalculates route when an obstacle blocks a corridor.
     */
    public IndoorRouteResult recalculateDueToObstacle(String sourceName, String destName, String blockedLandmark) {
        IndoorNode blocked = findNode(blockedLandmark);
        if (blocked != null) {
            for (IndoorEdge edge : edges) {
                if (edge.from.equals(blocked.id) || edge.to.equals(blocked.id)) {
                    blockedEdges.add(edge.from + "->" + edge.to);
                    blockedEdges.add(edge.to + "->" + edge.from);
                }
            }
        }
        return calculateRouteInternal(sourceName, destName, true);
    }

    public void clearBlockedEdges() {
        blockedEdges.clear();
    }

    private IndoorRouteResult calculateRouteInternal(String sourceName, String destName, boolean isRerouted) {
        IndoorNode start = findNode(sourceName);
        if (start == null) start = nodes.get("loc_1"); // Default entrance

        IndoorNode end = findNode(destName);
        if (end == null) {
            return new IndoorRouteResult(false, sourceName, destName, 0, 0, false, 0, 0,
                    Collections.emptyList(), "Destination '" + destName + "' was not found in building directory.", false);
        }

        Map<String, List<IndoorEdge>> adj = new HashMap<>();
        for (String id : nodes.keySet()) adj.put(id, new ArrayList<>());

        for (IndoorEdge edge : edges) {
            String forwardKey = edge.from + "->" + edge.to;
            String reverseKey = edge.to + "->" + edge.from;
            if (blockedEdges.contains(forwardKey) || blockedEdges.contains(reverseKey)) {
                continue;
            }
            adj.get(edge.from).add(new IndoorEdge(edge.from, edge.to, edge.weight, edge.description));
            adj.get(edge.to).add(new IndoorEdge(edge.to, edge.from, edge.weight, edge.description));
        }

        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        Map<String, String> edgeDesc = new HashMap<>();
        Set<String> unvisited = new HashSet<>(nodes.keySet());

        for (String id : nodes.keySet()) {
            dist.put(id, Double.POSITIVE_INFINITY);
            prev.put(id, null);
        }
        dist.put(start.id, 0.0);

        while (!unvisited.isEmpty()) {
            String closest = null;
            double minD = Double.POSITIVE_INFINITY;
            for (String u : unvisited) {
                if (dist.get(u) < minD) {
                    minD = dist.get(u);
                    closest = u;
                }
            }

            if (closest == null || minD == Double.POSITIVE_INFINITY || closest.equals(end.id)) break;
            unvisited.remove(closest);

            for (IndoorEdge neighbor : adj.get(closest)) {
                if (!unvisited.contains(neighbor.to)) continue;
                double alt = dist.get(closest) + neighbor.weight;
                if (alt < dist.get(neighbor.to)) {
                    dist.put(neighbor.to, alt);
                    prev.put(neighbor.to, closest);
                    edgeDesc.put(closest + "->" + neighbor.to, neighbor.description);
                }
            }
        }

        List<String> path = new ArrayList<>();
        String curr = end.id;
        while (curr != null) {
            path.add(0, curr);
            curr = prev.get(curr);
        }

        if (path.isEmpty() || !path.get(0).equals(start.id)) {
            return new IndoorRouteResult(false, start.name, end.name, 0, 0, false, 0, 0,
                    Collections.emptyList(), "No unobstructed path found to " + end.name + ".", isRerouted);
        }

        double totalDist = dist.get(end.id);
        double estTimeMin = Math.round((totalDist / 42.5) * 10.0) / 10.0; // ~0.7 m/s visually guided pace

        List<String> instructions = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            IndoorNode n = nodes.get(path.get(i));
            if (i == 0) {
                instructions.add("Start at " + n.name + ". Check cane path in front.");
            } else {
                IndoorNode prevNode = nodes.get(path.get(i - 1));
                String d = edgeDesc.get(prevNode.id + "->" + n.id);
                instructions.add(d != null ? d : "Move towards " + n.name + ".");
            }
        }

        boolean floorTrans = start.floor != end.floor;
        String summary;
        if (isRerouted) {
            summary = "Path obstruction detected ahead. Route recalculated to " + end.name +
                    ". Total distance is " + (int) totalDist + " metres, estimated time " + estTimeMin + " minutes. " +
                    (instructions.size() > 1 ? instructions.get(1) : "");
        } else {
            summary = "Navigating to " + end.name + ". Route: " + start.name + " to " + end.name +
                    ". Distance: " + (int) totalDist + " metres, estimated time: " + estTimeMin + " minutes. " +
                    (instructions.size() > 1 ? instructions.get(1) : "");
        }

        return new IndoorRouteResult(true, start.name, end.name, totalDist, estTimeMin,
                floorTrans, start.floor, end.floor, instructions, summary, isRerouted);
    }

    /**
     * Finds nearest safe zone or emergency exit.
     */
    public IndoorRouteResult findNearestEmergencyExit(String currentLocation) {
        IndoorNode curr = findNode(currentLocation);
        if (curr == null) curr = nodes.get("loc_1");

        IndoorNode bestExit = null;
        double minD = Double.POSITIVE_INFINITY;

        for (IndoorNode n : nodes.values()) {
            if (n.isSafeZone && n.floor == curr.floor) {
                IndoorRouteResult r = calculateRoute(curr.name, n.name);
                if (r.success && r.totalDistanceMeters < minD) {
                    minD = r.totalDistanceMeters;
                    bestExit = n;
                }
            }
        }

        if (bestExit == null) bestExit = nodes.get("loc_1");
        IndoorRouteResult r = calculateRoute(curr.name, bestExit.name);
        return new IndoorRouteResult(true, curr.name, bestExit.name, r.totalDistanceMeters, r.estimatedTimeMinutes,
                false, curr.floor, bestExit.floor, r.stepInstructions,
                "Emergency exit route activated. Nearest safe point is " + bestExit.name + ", " +
                        (int) r.totalDistanceMeters + " metres away. " + (r.stepInstructions.size() > 1 ? r.stepInstructions.get(1) : ""),
                false);
    }
}
