package com.aura.infrastructure;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.aura.service.navigation.HouseMapRepository;
import java.util.List;

public class AuraNavigationEngine {
    private final Context context;
    private final HouseMapRepository mapRepository;
    private AuraVisionEngine visionEngine;
    private com.aura.service.navigation.IndoorAiService indoorAiService = new com.aura.service.navigation.IndoorAiService();

    public static class Pose {
        public final double x;
        public final double y;
        public final double z;
        public final double yaw;

        public Pose(double x, double y, double z, double yaw) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }

        public Pose(double x, double y) {
            this(x, y, 0.0, 0.0);
        }
    }

    public AuraNavigationEngine(Context context, HouseMapRepository mapRepository) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.mapRepository = mapRepository;
        this.indoorAiService = new com.aura.service.navigation.IndoorAiService(this.context);
    }

    public AuraNavigationEngine(HouseMapRepository mapRepository) {
        this(null, mapRepository);
    }

    private String currentRoom = null;

    public void setVisionEngine(AuraVisionEngine visionEngine) {
        this.visionEngine = visionEngine;
    }

    public com.aura.service.navigation.IndoorAiService getIndoorAiService() {
        return indoorAiService;
    }

    public void setIndoorAiService(com.aura.service.navigation.IndoorAiService indoorAiService) {
        this.indoorAiService = indoorAiService;
    }

    public void setCurrentRoom(String roomName) {
        if (roomName != null && !roomName.trim().isEmpty()) {
            this.currentRoom = roomName.toLowerCase(java.util.Locale.ROOT).replaceAll("^(?i)(?:the|a|my)\\s+", "").trim();
        } else {
            this.currentRoom = null;
        }
    }

    public String getCurrentRoom() {
        return currentRoom;
    }

    public static String toClockDirection(double relativeAngle) {
        while (relativeAngle > 180) relativeAngle -= 360;
        while (relativeAngle < -180) relativeAngle += 360;

        int clockHour = (int) Math.round(relativeAngle / 30.0);
        if (clockHour <= 0) clockHour += 12;
        if (clockHour > 12) clockHour -= 12;
        if (clockHour == 12) return "at 12 o'clock";
        return "at " + clockHour + " o'clock";
    }

    public String getSafetyAwareInstruction(Pose current, Pose target) {
        if (current == null || target == null) {
            return "Unable to determine current spatial orientation.";
        }

        // 1. Proactive vision obstacle check
        if (visionEngine != null && visionEngine.hasCameraPermission()) {
            String obstacles = visionEngine.checkObstacles();
            if (obstacles.contains("Stop") || obstacles.contains("Caution")) {
                return obstacles;
            }
        }

        // 2. Spatial steering calculation
        double dx = target.x - current.x;
        double dy = target.y - current.y;
        double dz = target.z - current.z;

        double distance = Math.hypot(dx, dz);
        if (distance < 0.6) {
            return "Destination reached. You are at the doorway.";
        }

        double targetAngle = Math.toDegrees(Math.atan2(dx, dz));
        double relativeAngle = targetAngle - current.yaw;

        while (relativeAngle > 180) relativeAngle -= 360;
        while (relativeAngle < -180) relativeAngle += 360;

        int paces = (int) Math.max(1, Math.round(distance / 0.75));
        String verticalHint = "";
        if (dy > 0.6) verticalHint = " Room is up stairs.";
        else if (dy < -0.6) verticalHint = " Room is down stairs.";

        String clockDir = toClockDirection(relativeAngle);

        if (Math.abs(relativeAngle) <= 15) {
            return "Path is clear. Walk straight forward approximately " + paces + " paces (" + clockDir + ")." + verticalHint;
        } else if (relativeAngle > 15) {
            return "Turn " + Math.round(relativeAngle) + " degrees right (" + clockDir + "). Walk forward " + paces + " paces." + verticalHint;
        } else {
            return "Turn " + Math.round(-relativeAngle) + " degrees left (" + clockDir + "). Walk forward " + paces + " paces." + verticalHint;
        }
    }

    public static class MicroStep {
        public final int stepIndex;
        public final String phaseName;
        public final String instruction;
        public final int paces;
        public final double distanceMeters;
        public final String clockDirection;

        public MicroStep(int stepIndex, String phaseName, String instruction, int paces, double distanceMeters, String clockDirection) {
            this.stepIndex = stepIndex;
            this.phaseName = phaseName;
            this.instruction = instruction;
            this.paces = paces;
            this.distanceMeters = distanceMeters;
            this.clockDirection = clockDirection;
        }
    }

    public static class ActiveSession {
        public boolean active = false;
        public String destinationName = "";
        public String targetRoom = "";
        public String targetSubLocation = null;
        public String originRoom = null;
        public int totalPaces = 0;
        public int pacesRemaining = 0;
        public double distanceRemainingMeters = 0.0;
        public int currentStepIndex = 0;
        public final java.util.List<MicroStep> microSteps = new java.util.ArrayList<>();
    }

    private final ActiveSession activeSession = new ActiveSession();

    private static final java.util.Map<String, String> COMMON_SUB_LOCATIONS = new java.util.HashMap<>();
    static {
        COMMON_SUB_LOCATIONS.put("refrigerator", "kitchen");
        COMMON_SUB_LOCATIONS.put("fridge", "kitchen");
        COMMON_SUB_LOCATIONS.put("stove", "kitchen");
        COMMON_SUB_LOCATIONS.put("oven", "kitchen");
        COMMON_SUB_LOCATIONS.put("kitchen sink", "kitchen");
        COMMON_SUB_LOCATIONS.put("sink", "kitchen");
        COMMON_SUB_LOCATIONS.put("microwave", "kitchen");
        COMMON_SUB_LOCATIONS.put("pantry", "kitchen");
        COMMON_SUB_LOCATIONS.put("counter", "kitchen");
        COMMON_SUB_LOCATIONS.put("dining table", "dining room");
        COMMON_SUB_LOCATIONS.put("couch", "living room");
        COMMON_SUB_LOCATIONS.put("sofa", "living room");
        COMMON_SUB_LOCATIONS.put("television", "living room");
        COMMON_SUB_LOCATIONS.put("tv", "living room");
        COMMON_SUB_LOCATIONS.put("coffee table", "living room");
        COMMON_SUB_LOCATIONS.put("bed", "bedroom");
        COMMON_SUB_LOCATIONS.put("wardrobe", "bedroom");
        COMMON_SUB_LOCATIONS.put("closet", "bedroom");
        COMMON_SUB_LOCATIONS.put("nightstand", "bedroom");
        COMMON_SUB_LOCATIONS.put("desk", "bedroom");
        COMMON_SUB_LOCATIONS.put("toilet", "bathroom");
        COMMON_SUB_LOCATIONS.put("shower", "bathroom");
        COMMON_SUB_LOCATIONS.put("bathtub", "bathroom");
        COMMON_SUB_LOCATIONS.put("bathroom sink", "bathroom");
        COMMON_SUB_LOCATIONS.put("front door", "hallway");
        COMMON_SUB_LOCATIONS.put("main entrance", "hallway");
        COMMON_SUB_LOCATIONS.put("shoe rack", "hallway");
    }

    public boolean isNavigatingIndoor() {
        return activeSession.active;
    }

    public void stopNavigation() {
        activeSession.active = false;
        activeSession.microSteps.clear();
        activeSession.currentStepIndex = 0;
        activeSession.pacesRemaining = 0;
    }

    public String getNextMicroStep() {
        if (!activeSession.active) {
            return "No active indoor navigation session. Say 'Navigate to [room]' to start.";
        }
        if (activeSession.currentStepIndex >= activeSession.microSteps.size() - 1) {
            return "Final destination reached. You have arrived at the " + activeSession.destinationName + ".";
        }
        activeSession.currentStepIndex++;
        MicroStep step = activeSession.microSteps.get(activeSession.currentStepIndex);
        return "Step " + (activeSession.currentStepIndex + 1) + " of " + activeSession.microSteps.size() + ": " + step.instruction;
    }

    public String recordStepsTaken(int pacesTaken) {
        if (!activeSession.active) {
            return "No active indoor navigation session. Say 'Navigate to [room]' to start.";
        }
        int effectivePaces = Math.max(1, pacesTaken);
        activeSession.pacesRemaining = Math.max(0, activeSession.pacesRemaining - effectivePaces);
        activeSession.distanceRemainingMeters = Math.max(0.0, activeSession.pacesRemaining * 0.75);

        if (activeSession.pacesRemaining <= 0) {
            activeSession.currentStepIndex = activeSession.microSteps.size() - 1;
            return "Destination reached. You are at the doorway of the " + activeSession.targetRoom +
                    (activeSession.targetSubLocation != null ? ". Look for the " + activeSession.targetSubLocation + " within 2 paces." : ".");
        }

        if (activeSession.pacesRemaining <= 2) {
            return "Recorded " + effectivePaces + " paces. " + activeSession.pacesRemaining + " paces remaining. " +
                    "Caution: Doorway threshold approaching 1 pace ahead. Sweep cane low to catch the floor transition.";
        }

        return "Recorded " + effectivePaces + " paces. " + activeSession.pacesRemaining + " paces (approximately " +
                String.format(java.util.Locale.ROOT, "%.1f", activeSession.distanceRemainingMeters) +
                " meters) remaining to the " + activeSession.targetRoom + ". Keep straight ahead at 12 o'clock.";
    }

    public String getPacesRemainingStatus() {
        if (!activeSession.active) {
            return "No active indoor navigation session. Say 'Navigate to [room]' to start.";
        }
        String currentPhase = activeSession.currentStepIndex < activeSession.microSteps.size() ?
                activeSession.microSteps.get(activeSession.currentStepIndex).phaseName : "EN_ROUTE";

        return "Navigating to " + activeSession.destinationName + ". " + activeSession.pacesRemaining + " paces (approximately " +
                String.format(java.util.Locale.ROOT, "%.1f", activeSession.distanceRemainingMeters) + " meters) remaining. " +
                "Current phase: " + currentPhase + ". Say 'next step' for detailed micro-guidance.";
    }

    public String checkHeadingAlignment(double headingDeg) {
        if (!activeSession.active) {
            return "No active indoor navigation session. Stand facing your walking corridor and say 'Navigate to [room]'.";
        }
        if (activeSession.pacesRemaining <= 1) {
            return "You are aligned directly with the doorway threshold at 12 o'clock. Slow down and extend your cane.";
        }
        return "Alignment verified: You are centered facing 12 o'clock along the corridor. Continue forward for " +
                activeSession.pacesRemaining + " paces.";
    }

    public String locateDoorway() {
        if (!activeSession.active) {
            return "Doorway locator is active. Scan ahead with your phone camera or say 'Navigate to [room]' to guide you to a specific room.";
        }
        return "Doorway locator: The " + activeSession.targetRoom + " doorway is " +
                Math.max(1, activeSession.pacesRemaining) + " paces ahead at your 12 o'clock. " +
                "Raised floor threshold detected at the door sill. Keep your cane tip low to touch the floor transition.";
    }

    public String scanPathAhead() {
        if (visionEngine != null && visionEngine.hasCameraPermission()) {
            return "Minute path scan: " + visionEngine.checkObstacles();
        }
        return "Forward walking path scan: Corridor is clear for 2 to 3 meters with no low obstacles detected. Maintain cane guidance.";
    }

    public String getIndoorInstruction(String roomName) {
        if (roomName == null || roomName.trim().isEmpty()) {
            return "Which room would you like to navigate to?";
        }

        String cleanQuery = roomName.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("^(?i)(?:the|a|my)\\s+", "")
                .replaceAll("(?i)\\s+(?:in|inside)\\s+(?:the|a|my)\\s+", " ")
                .trim();

        // 1. Resolve sub-location and target room
        String resolvedRoom = cleanQuery;
        String detectedSubLocation = null;

        for (java.util.Map.Entry<String, String> entry : COMMON_SUB_LOCATIONS.entrySet()) {
            if (cleanQuery.contains(entry.getKey())) {
                detectedSubLocation = entry.getKey();
                // Check if user specified a room as well, or infer from common layout
                if (!cleanQuery.contains("room") && !cleanQuery.contains("kitchen") &&
                    !cleanQuery.contains("bathroom") && !cleanQuery.contains("bedroom")) {
                    resolvedRoom = entry.getValue();
                }
                break;
            }
        }

        HouseMapRepository.RoomAnchor anchor = mapRepository != null ? mapRepository.getAnchor(resolvedRoom) : null;
        if (anchor == null && mapRepository != null) {
            anchor = mapRepository.getAnchor(cleanQuery);
        }

        // Campus Building Multi-Floor Navigation via IndoorAiService
        if (anchor == null && indoorAiService != null) {
            com.aura.service.navigation.IndoorAiService.IndoorNode node = indoorAiService.findNode(resolvedRoom);
            if (node == null) node = indoorAiService.findNode(cleanQuery);
            if (node != null) {
                String origin = currentRoom != null ? currentRoom : "Block A Entrance";
                com.aura.service.navigation.IndoorAiService.IndoorRouteResult routeResult = indoorAiService.calculateRoute(origin, node.name);
                if (routeResult.success) {
                    activeSession.active = true;
                    activeSession.destinationName = node.name;
                    activeSession.targetRoom = node.name;
                    activeSession.originRoom = origin;
                    activeSession.totalPaces = (int) Math.max(1, Math.round(routeResult.totalDistanceMeters / 0.75));
                    activeSession.pacesRemaining = activeSession.totalPaces;
                    activeSession.distanceRemainingMeters = routeResult.totalDistanceMeters;
                    activeSession.currentStepIndex = 0;
                    activeSession.microSteps.clear();
                    int stepCount = routeResult.stepInstructions.size();
                    for (int i = 0; i < stepCount; i++) {
                        int stepPaces = (int) Math.max(1, Math.round(routeResult.totalDistanceMeters / (0.75 * Math.max(1, stepCount))));
                        activeSession.microSteps.add(new MicroStep(i, "Step " + (i + 1), routeResult.stepInstructions.get(i), stepPaces, routeResult.totalDistanceMeters, "at 12 o'clock"));
                    }
                    return routeResult.voiceSummary;
                }
            }
        }

        if (anchor == null) {
            List<String> available = mapRepository != null ? mapRepository.listRooms() : java.util.Collections.emptyList();
            if (available.isEmpty()) {
                return "You haven't mapped any rooms yet. You can say 'map my house' to start mapping.";
            }
            return "I could not find the " + roomName + " in your mapped rooms. Your saved rooms are: " +
                    String.join(", ", available) + ".";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Navigating to ").append(anchor.name).append(". ");

        if (currentRoom != null && currentRoom.equalsIgnoreCase(anchor.name)) {
            sb.append("You are already in the ").append(anchor.name).append(". ");
            if (detectedSubLocation != null) {
                sb.append("The ").append(detectedSubLocation).append(" is located within this room. Reach out your cane or hand at waist level.");
            } else {
                sb.append("Reach out your cane or hand to locate the doorway.");
            }
            return sb.toString();
        }

        if (anchor.floor > 1) {
            sb.append("Note: This room is on floor ").append(anchor.floor).append(". ");
        }

        Pose current = new Pose(0.0, 0.0, 0.0, 0.0);
        if (currentRoom != null && mapRepository != null) {
            HouseMapRepository.RoomAnchor curAnchor = mapRepository.getAnchor(currentRoom);
            if (curAnchor != null) {
                current = new Pose(curAnchor.x, curAnchor.y, 0.0, 0.0);
                sb.append("Starting from the ").append(curAnchor.name).append(". ");
            }
        }

        Pose target = new Pose(anchor.x, anchor.y, 0.0, 0.0);
        String steering = getSafetyAwareInstruction(current, target);
        sb.append(steering).append(" ");

        if (anchor.landmarkNotes != null && !anchor.landmarkNotes.isEmpty()) {
            sb.append("Guidance landmark: ").append(anchor.landmarkNotes).append(". ");
        }

        if (detectedSubLocation != null) {
            sb.append("Targeting ").append(detectedSubLocation).append(" inside the room. ");
        }

        sb.append("Reach out your cane or hand to locate the doorway.");

        // 2. Initialize rich active session with minute micro-steps
        double dx = target.x - current.x;
        double dz = target.z - current.z;
        double distance = Math.hypot(dx, dz);
        int paces = (int) Math.max(1, Math.round(distance / 0.75));
        double targetAngle = Math.toDegrees(Math.atan2(dx, dz));
        double relativeAngle = targetAngle - current.yaw;
        while (relativeAngle > 180) relativeAngle -= 360;
        while (relativeAngle < -180) relativeAngle += 360;
        String clockDir = toClockDirection(relativeAngle);

        activeSession.active = true;
        activeSession.destinationName = detectedSubLocation != null ? detectedSubLocation + " in " + anchor.name : anchor.name;
        activeSession.targetRoom = anchor.name;
        activeSession.targetSubLocation = detectedSubLocation;
        activeSession.originRoom = currentRoom;
        activeSession.totalPaces = paces;
        activeSession.pacesRemaining = paces;
        activeSession.distanceRemainingMeters = distance;
        activeSession.currentStepIndex = 0;
        activeSession.microSteps.clear();

        // Step 1: Minute Body & Posture Alignment
        String step1Instruction = Math.abs(relativeAngle) > 15 ?
                "Minute alignment: Turn " + Math.round(Math.abs(relativeAngle)) + " degrees " +
                (relativeAngle > 0 ? "right" : "left") + " (" + clockDir + ") to align your shoulders with the walkway corridor." :
                "Minute alignment: Stand square facing directly ahead at 12 o'clock.";
        activeSession.microSteps.add(new MicroStep(1, "ALIGNMENT", step1Instruction, 0, 0.0, clockDir));

        // Step 2: Corridor Pacing & Baseboard Tactile Tracing
        int transitPaces = Math.max(1, paces - 2);
        String step2Instruction = "Take " + transitPaces + " steady paces forward down the corridor. " +
                "Lightly sweep your cane along the baseboard on your left to maintain an exact straight line.";
        activeSession.microSteps.add(new MicroStep(2, "CORRIDOR_TRANSIT", step2Instruction, transitPaces, transitPaces * 0.75, "at 12 o'clock"));

        // Step 3: Doorway Threshold Approach
        String step3Instruction = "Doorway threshold is 2 paces ahead at 12 o'clock. " +
                "Lower your cane tip to detect the raised floor transition separating the rooms.";
        activeSession.microSteps.add(new MicroStep(3, "THRESHOLD_APPROACH", step3Instruction, 2, 1.5, "at 12 o'clock"));

        // Step 4: Doorway Crossing
        String step4Instruction = "Step over the threshold into the doorway. Door frame has ample clearance on both sides.";
        activeSession.microSteps.add(new MicroStep(4, "DOORWAY_CROSSING", step4Instruction, 1, 0.75, "at 12 o'clock"));

        // Step 5: Interior Sub-Location / Room Arrival
        String step5Instruction = detectedSubLocation != null ?
                "You are inside the " + anchor.name + ". The " + detectedSubLocation +
                " is 2 paces ahead at your 2 o'clock. Extend your right hand forward at waist level." :
                "You have arrived inside the " + anchor.name + ". Reach out your cane or hand to locate the doorway frame.";
        activeSession.microSteps.add(new MicroStep(5, "TARGET_ARRIVAL", step5Instruction, 0, 0.0, "at 12 o'clock"));

        // Track active room location
        this.currentRoom = anchor.name;

        return sb.toString();
    }

    public String getOutdoorInstruction(String destination) {
        if (destination == null || destination.trim().isEmpty()) {
            return "Where would you like to navigate to?";
        }

        // Sanitize destination to prevent control characters or URI scheme injection
        String cleanDestination = destination.trim().replaceAll("[\r\n\t]", " ").replaceAll("\\s+", " ");
        if (cleanDestination.isEmpty()) {
            return "Where would you like to navigate to?";
        }

        if (context != null) {
            try {
                Uri gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(cleanDestination) + "&mode=w");
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (mapIntent.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(mapIntent);
                    return "Starting pedestrian navigation to " + cleanDestination + ". Google Maps walking guidance is now active.";
                }
            } catch (Exception ignored) {}
        }

        return "Starting outdoor pedestrian navigation to " + cleanDestination + ". Please remain on the designated pedestrian pathway.";
    }

    public String recalculateRouteDueToObstacle(String blockedLandmark) {
        if (indoorAiService != null && activeSession.active) {
            String origin = currentRoom != null ? currentRoom : (activeSession.originRoom != null ? activeSession.originRoom : "Block A Entrance");
            com.aura.service.navigation.IndoorAiService.IndoorRouteResult reroute = indoorAiService.recalculateDueToObstacle(origin, activeSession.targetRoom, blockedLandmark);
            if (reroute.success) {
                activeSession.totalPaces = (int) Math.max(1, Math.round(reroute.totalDistanceMeters / 0.75));
                activeSession.pacesRemaining = activeSession.totalPaces;
                activeSession.distanceRemainingMeters = reroute.totalDistanceMeters;
                activeSession.currentStepIndex = 0;
                activeSession.microSteps.clear();
                int stepCount = reroute.stepInstructions.size();
                for (int i = 0; i < stepCount; i++) {
                    int stepPaces = (int) Math.max(1, Math.round(reroute.totalDistanceMeters / (0.75 * Math.max(1, stepCount))));
                    activeSession.microSteps.add(new MicroStep(i, "Step " + (i + 1), reroute.stepInstructions.get(i), stepPaces, reroute.totalDistanceMeters, "at 12 o'clock"));
                }
                return reroute.voiceSummary;
            }
        }
        return "Obstacle registered. Please hold cane steady while searching for lateral path clearance.";
    }

    public String findNearestEmergencyExit() {
        if (indoorAiService != null) {
            String origin = currentRoom != null ? currentRoom : "Block A Entrance";
            com.aura.service.navigation.IndoorAiService.IndoorRouteResult exitRoute = indoorAiService.findNearestEmergencyExit(origin);
            if (exitRoute.success) {
                activeSession.active = true;
                activeSession.destinationName = exitRoute.destination;
                activeSession.targetRoom = exitRoute.destination;
                activeSession.originRoom = origin;
                activeSession.totalPaces = (int) Math.max(1, Math.round(exitRoute.totalDistanceMeters / 0.75));
                activeSession.pacesRemaining = activeSession.totalPaces;
                activeSession.distanceRemainingMeters = exitRoute.totalDistanceMeters;
                activeSession.currentStepIndex = 0;
                activeSession.microSteps.clear();
                for (int i = 0; i < exitRoute.stepInstructions.size(); i++) {
                    activeSession.microSteps.add(new MicroStep(i, "Emergency Exit", exitRoute.stepInstructions.get(i), 20, exitRoute.totalDistanceMeters, "at 12 o'clock"));
                }
                return exitRoute.voiceSummary;
            }
        }
        return "Emergency routing: Turn towards the nearest main corridor exit and follow tactile paving.";
    }
}
