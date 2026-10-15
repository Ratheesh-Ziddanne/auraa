package com.aura.infrastructure.intent;

import com.aura.application.confirmation.ConfirmationHandler;
import com.aura.core.JavaAiIntentClassifier;
import com.aura.domain.errors.AuraError;
import com.aura.domain.errors.RetryPolicyMap;
import com.aura.domain.validation.ValidationRules;
import com.aura.models.Contact;
import com.aura.models.Intent;
import com.aura.service.contact.PhoneticNormaliser;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AuraCoreLogicTest {

    @Test
    public void testIntentClassification() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        // 1. Text Mom saying I will be late
        Intent textIntent = classifier.classify("text mom saying I will be late");
        assertTrue(textIntent instanceof Intent.SendMessage);
        Intent.SendMessage msg = (Intent.SendMessage) textIntent;
        assertEquals("mom", msg.getRecipient());
        assertEquals("I will be late", msg.getBody());

        // 2. Tell John that the meeting is cancelled
        Intent tellIntent = classifier.classify("tell john that the meeting is cancelled");
        assertTrue(tellIntent instanceof Intent.SendMessage);
        Intent.SendMessage tellMsg = (Intent.SendMessage) tellIntent;
        assertEquals("john", tellMsg.getRecipient());
        assertEquals("the meeting is cancelled", tellMsg.getBody());

        // 3. Send message to Arjun saying I'll reach in ten minutes
        Intent arjunIntent = classifier.classify("send a message to Arjun saying I'll reach in ten minutes");
        assertTrue(arjunIntent instanceof Intent.SendMessage);
        Intent.SendMessage arjunMsg = (Intent.SendMessage) arjunIntent;
        assertEquals("Arjun", arjunMsg.getRecipient());
        assertEquals("I'll reach in ten minutes", arjunMsg.getBody());

        // 4. Call Dad
        Intent callIntent = classifier.classify("call Dad");
        assertTrue(callIntent instanceof Intent.CallContact);
        assertEquals("Dad", ((Intent.CallContact) callIntent).getContactName());

        // 5. Control intents
        Intent cancelIntent = classifier.classify("cancel");
        assertTrue(cancelIntent instanceof Intent.Cancel);

        // 6. Tool execution
        Intent jokeIntent = classifier.classify("tell me a joke");
        assertTrue(jokeIntent instanceof Intent.ExecuteTool);
        assertEquals("tell_joke", ((Intent.ExecuteTool) jokeIntent).getToolName());

        Intent weatherIntent = classifier.classify("what is the weather");
        assertTrue(weatherIntent instanceof Intent.ExecuteTool);
        assertEquals("get_weather", ((Intent.ExecuteTool) weatherIntent).getToolName());

        // 7. System Diagnostics
        Intent batteryIntent = classifier.classify("how much battery do I have");
        assertTrue(batteryIntent instanceof Intent.GetBatteryStatus);

        // 8. Vision & Navigation
        Intent visionIntent = classifier.classify("what is in front of me");
        assertTrue(visionIntent instanceof Intent.DescribeObstacles);

        Intent navIntent = classifier.classify("navigate to the kitchen");
        assertTrue(navIntent instanceof Intent.StartNavigation);
        assertEquals("the kitchen", ((Intent.StartNavigation) navIntent).getDestination());
        assertTrue(((Intent.StartNavigation) navIntent).isIndoor());

        // 9. Memory
        Intent remIntent = classifier.classify("remember that my exam is tomorrow");
        assertTrue(remIntent instanceof Intent.Remember);
        assertEquals("exam", ((Intent.Remember) remIntent).getKey());
        assertEquals("tomorrow", ((Intent.Remember) remIntent).getValue());

        // 10. Timers & Alarms
        Intent timerIntent = classifier.classify("set a timer for 10 minutes");
        assertTrue(timerIntent instanceof Intent.ExecuteTool);
        Intent.ExecuteTool timerTool = (Intent.ExecuteTool) timerIntent;
        assertEquals("set_timer", timerTool.getToolName());
        assertEquals(600, timerTool.getParameters().get("duration"));

        Intent alarmIntent = classifier.classify("set an alarm for 7:30 am");
        assertTrue(alarmIntent instanceof Intent.ExecuteTool);
        Intent.ExecuteTool alarmTool = (Intent.ExecuteTool) alarmIntent;
        assertEquals("set_alarm", alarmTool.getToolName());
        assertEquals(7, alarmTool.getParameters().get("hour"));
        assertEquals(30, alarmTool.getParameters().get("minute"));

        // 11. Math Calculations
        Intent calcIntent = classifier.classify("what is 25 plus 15");
        assertTrue(calcIntent instanceof Intent.Calculate);
        assertEquals("25 plus 15", ((Intent.Calculate) calcIntent).getExpression());

        // 12. Media Controls
        Intent mediaIntent = classifier.classify("pause music");
        assertTrue(mediaIntent instanceof Intent.MediaControl);
        assertEquals("pause", ((Intent.MediaControl) mediaIntent).getAction());

        // 13. Notifications
        Intent notifIntent = classifier.classify("read my notifications");
        assertTrue(notifIntent instanceof Intent.ReadNotifications);
    }

    @Test
    public void testPhoneticNormaliser() {
        int scoreExact = PhoneticNormaliser.score("Philip", "Filip");
        assertEquals(0, scoreExact);

        int scoreClose = PhoneticNormaliser.score("Sarah", "Sara");
        assertTrue(scoreClose <= 1);
    }

    @Test
    public void testValidationRules() {
        assertTrue(ValidationRules.message("Hello world"));
        assertFalse(ValidationRules.message(""));
        assertFalse(ValidationRules.message("   "));

        Contact validContact = new Contact("1", "Priya", "+919876543210");
        Contact invalidContact = new Contact("2", "Unknown", null, null);
        assertTrue(ValidationRules.contact(validContact));
        assertFalse(ValidationRules.contact(invalidContact));
    }

    @Test
    public void testRetryPolicyMap() {
        RetryPolicyMap.RetryPolicy policy1 = RetryPolicyMap.policy(AuraError.STTFailure);
        assertNotNull(policy1.getSpokenMessage());
        assertEquals(2, policy1.getMaxRetries());

        RetryPolicyMap.RetryPolicy policy2 = RetryPolicyMap.policy(new AuraError.PlatformFailure("SMS"));
        assertTrue(policy2.getSpokenMessage().contains("SMS"));
    }

    @Test
    public void testConfirmationHandler() {
        assertFalse(ConfirmationHandler.confirms(null));
        assertFalse(ConfirmationHandler.confirms(""));
        assertFalse(ConfirmationHandler.confirms("okay"));
        assertFalse(ConfirmationHandler.confirms("sure"));
        assertFalse(ConfirmationHandler.confirms("maybe"));
        assertFalse(ConfirmationHandler.confirms("yes please"));

        assertTrue(ConfirmationHandler.confirms("yes"));
        assertTrue(ConfirmationHandler.confirms("send it"));
        assertTrue(ConfirmationHandler.confirms("confirm"));
        assertTrue(ConfirmationHandler.confirms("go ahead"));
        assertTrue(ConfirmationHandler.confirms("do it"));

        // Cancellation priority over confirmation
        assertFalse(ConfirmationHandler.confirms("no"));
        assertFalse(ConfirmationHandler.confirms("cancel"));
        assertFalse(ConfirmationHandler.confirms("stop"));

        assertTrue(ConfirmationHandler.cancels("no"));
        assertTrue(ConfirmationHandler.cancels("cancel"));
        assertTrue(ConfirmationHandler.cancels("stop"));
        assertTrue(ConfirmationHandler.cancels("abort"));

        assertTrue(ConfirmationHandler.asksRepeat("repeat"));
        assertTrue(ConfirmationHandler.asksRepeat("say that again"));
    }

    @Test
    public void testSafetyConfirmationManagerSecurity() {
        com.aura.core.SafetyConfirmationManager manager = new com.aura.core.SafetyConfirmationManager();
        Intent criticalMsg = new Intent.SendMessage("Mom", "Need help", com.aura.models.Platform.SMS);

        // Verify high/medium safety intents require confirmation
        assertTrue(manager.needsConfirmation(criticalMsg));
        assertNotNull(manager.getPendingIntent());

        // Unclear response does not dispatch
        Intent unconfirmed = manager.handleResponse("maybe tomorrow");
        assertEquals(null, unconfirmed);
        assertNotNull(manager.getPendingIntent());

        // Explicit cancellation clears state
        Intent cancelled = manager.handleResponse("cancel");
        assertEquals(null, cancelled);
        assertEquals(null, manager.getPendingIntent());

        // Re-request and confirm
        manager.needsConfirmation(criticalMsg);
        Intent confirmed = manager.handleResponse("confirm");
        assertEquals(criticalMsg, confirmed);
        assertEquals(null, manager.getPendingIntent());
    }

    @Test
    public void testNavigationDestinationSanitization() {
        com.aura.service.navigation.HouseMapRepository repo = new com.aura.service.navigation.HouseMapRepository(null);
        com.aura.infrastructure.AuraNavigationEngine nav = new com.aura.infrastructure.AuraNavigationEngine(repo);

        // Blank/whitespace protection
        assertEquals("Where would you like to navigate to?", nav.getOutdoorInstruction("   "));
        assertEquals("Where would you like to navigate to?", nav.getOutdoorInstruction(null));

        // Sanitized instruction output
        String result = nav.getOutdoorInstruction("Central\nPark\t");
        assertTrue(result.contains("Central Park"));
        assertFalse(result.contains("\n"));
        assertFalse(result.contains("\t"));
    }

    @Test
    public void testCommunicationAndProductivityCommands() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        // 1. Send message with contact and body
        Intent sendMsg = classifier.classify("Send a message to Rahul saying I'll be late.");
        assertTrue(sendMsg instanceof Intent.SendMessage);
        Intent.SendMessage msg = (Intent.SendMessage) sendMsg;
        assertEquals("Rahul", msg.getRecipient());
        assertEquals("I'll be late.", msg.getBody());

        // 2. Call Amma
        Intent call = classifier.classify("Call Amma.");
        assertTrue(call instanceof Intent.CallContact);
        assertEquals("Amma", ((Intent.CallContact) call).getContactName());

        // 3. Read latest message
        Intent readMsg = classifier.classify("Read my latest message.");
        assertTrue(readMsg instanceof Intent.ReadLatestMessage);

        // 4. Set an alarm for 6 AM
        Intent alarm = classifier.classify("Set an alarm for 6 AM.");
        assertTrue(alarm instanceof Intent.ExecuteTool);
        Intent.ExecuteTool alarmTool = (Intent.ExecuteTool) alarm;
        assertEquals("set_alarm", alarmTool.getToolName());
        assertEquals(6, alarmTool.getParameters().get("hour"));
        assertEquals(0, alarmTool.getParameters().get("minute"));

        // 5. Set timer for 10 minutes
        Intent timer = classifier.classify("Set a timer for 10 minutes.");
        assertTrue(timer instanceof Intent.ExecuteTool);
        Intent.ExecuteTool timerTool = (Intent.ExecuteTool) timer;
        assertEquals("set_timer", timerTool.getToolName());
        assertEquals(600, timerTool.getParameters().get("duration"));

        // 6. Set reminder
        Intent reminder = classifier.classify("Remind me to submit my assignment at 5 PM.");
        assertTrue(reminder instanceof Intent.SetReminder);
        Intent.SetReminder r = (Intent.SetReminder) reminder;
        assertEquals("submit my assignment", r.getTitle());
        assertEquals("5 PM", r.getTimeText());

        // 7. Device controls: Bluetooth, Flashlight, Wi-Fi, Battery
        Intent btOn = classifier.classify("Turn on Bluetooth.");
        assertTrue(btOn instanceof Intent.ToggleBluetooth);
        assertTrue(((Intent.ToggleBluetooth) btOn).isEnable());

        Intent btOff = classifier.classify("Turn off Bluetooth.");
        assertTrue(btOff instanceof Intent.ToggleBluetooth);
        assertFalse(((Intent.ToggleBluetooth) btOff).isEnable());

        Intent flashOn = classifier.classify("Turn on the flashlight.");
        assertTrue(flashOn instanceof Intent.ToggleFlashlight);
        assertTrue(((Intent.ToggleFlashlight) flashOn).isEnable());

        Intent flashOff = classifier.classify("Turn off the flashlight.");
        assertTrue(flashOff instanceof Intent.ToggleFlashlight);
        assertFalse(((Intent.ToggleFlashlight) flashOff).isEnable());

        Intent wifiOff = classifier.classify("Turn off Wi-Fi.");
        assertTrue(wifiOff instanceof Intent.ToggleWifi);
        assertFalse(((Intent.ToggleWifi) wifiOff).isEnable());

        Intent battery = classifier.classify("What's my battery level?");
        assertTrue(battery instanceof Intent.GetBatteryStatus);
    }

    @Test
    public void testClarificationEngine() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        // Incomplete commands return ClarificationNeeded
        Intent needRecipient = classifier.classify("Send a message.");
        assertTrue(needRecipient instanceof Intent.ClarificationNeeded);
        assertEquals("recipient", ((Intent.ClarificationNeeded) needRecipient).getMissingField());

        Intent needAlarmTime = classifier.classify("Set an alarm.");
        assertTrue(needAlarmTime instanceof Intent.ClarificationNeeded);
        assertEquals("time", ((Intent.ClarificationNeeded) needAlarmTime).getMissingField());

        Intent needCallTarget = classifier.classify("Call.");
        assertTrue(needCallTarget instanceof Intent.ClarificationNeeded);
        assertEquals("contact", ((Intent.ClarificationNeeded) needCallTarget).getMissingField());

        Intent needTimerDuration = classifier.classify("Set a timer.");
        assertTrue(needTimerDuration instanceof Intent.ClarificationNeeded);
        assertEquals("duration", ((Intent.ClarificationNeeded) needTimerDuration).getMissingField());
    }

    @Test
    public void testContextualPronounAndModification() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();
        com.aura.core.ConversationalContextEngine context = new com.aura.core.ConversationalContextEngine();

        // 1. Seed context with last contact Priya
        context.updateContact(new Contact("101", "Priya", "+919876543210"));

        // 2. User says "Tell her I'll reach in 10 minutes" -> pronoun "her" resolves to Priya
        Intent resolvedMsg = classifier.classify("Tell her that I'll reach in 10 minutes.", context);
        assertTrue(resolvedMsg instanceof Intent.SendMessage);
        Intent.SendMessage sm = (Intent.SendMessage) resolvedMsg;
        assertEquals("Priya", sm.getRecipient());

        // 3. Alarm modification: "Set an alarm for 6", then "Actually make it 6:30"
        Intent modAlarm = classifier.classify("Actually make it 6:30.", context);
        assertTrue(modAlarm instanceof Intent.SetAlarm);
        Intent.SetAlarm a = (Intent.SetAlarm) modAlarm;
        assertEquals(6, a.getHour());
        assertEquals(30, a.getMinute());

        // 4. Data minimization: clear context
        context.clear();
        assertEquals(null, context.getLastContact());
    }

    @Test
    public void testPrivacyVoiceCommands() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        assertTrue(classifier.classify("Stop listening.") instanceof Intent.StopListening);
        assertTrue(classifier.classify("Clear my conversation.") instanceof Intent.ClearConversation);
        assertTrue(classifier.classify("Clear my memory.") instanceof Intent.ClearMemory);
        assertTrue(classifier.classify("Delete all stored AURA data.") instanceof Intent.DeleteAllData);
    }

    @Test
    public void testContactResolverDisambiguation() {
        com.aura.service.contact.ContactResolver resolver = new com.aura.service.contact.ContactResolver(null);

        List<Contact> rahuls = java.util.Arrays.asList(
                new Contact("1", "Rahul Kumar", "9876500001"),
                new Contact("2", "Rahul S", "9876500002")
        );

        String prompt = resolver.formatDisambiguationPrompt("Rahul", rahuls);
        assertTrue(prompt.contains("Rahul Kumar or Rahul S?"));

        // Resolve by name
        Contact chosen1 = resolver.resolveSelection("Rahul S", rahuls);
        assertEquals("Rahul S", chosen1.getDisplayName());

        // Resolve by ordinal
        Contact chosen2 = resolver.resolveSelection("the first one", rahuls);
        assertEquals("Rahul Kumar", chosen2.getDisplayName());
    }

    @Test
    public void testClockAndAlarmCommands() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        Intent wakeUp = classifier.classify("wake me up at 6 am");
        assertTrue(wakeUp instanceof Intent.ExecuteTool);
        Intent.ExecuteTool wakeTool = (Intent.ExecuteTool) wakeUp;
        assertEquals("set_alarm", wakeTool.getToolName());
        assertEquals(6, wakeTool.getParameters().get("hour"));
        assertEquals(0, wakeTool.getParameters().get("minute"));

        Intent showAlarms = classifier.classify("show my alarms");
        assertTrue(showAlarms instanceof Intent.ShowAlarms);

        Intent openClock = classifier.classify("open clock app");
        assertTrue(openClock instanceof Intent.OpenClock);

        Intent dismiss = classifier.classify("dismiss alarm");
        assertTrue(dismiss instanceof Intent.DismissAlarm);
    }

    @Test
    public void testCalendarAndReminderCommands() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        Intent remind1 = classifier.classify("remind me to take medicine at 8 pm");
        assertTrue(remind1 instanceof Intent.SetReminder);
        Intent.SetReminder r1 = (Intent.SetReminder) remind1;
        assertEquals("take medicine", r1.getTitle());
        assertEquals("8 pm", r1.getTimeText());

        Intent remind2 = classifier.classify("remind me at 5 pm to call dad");
        assertTrue(remind2 instanceof Intent.SetReminder);
        Intent.SetReminder r2 = (Intent.SetReminder) remind2;
        assertEquals("call dad", r2.getTitle());
        assertEquals("5 pm", r2.getTimeText());

        Intent openCal = classifier.classify("open calendar");
        assertTrue(openCal instanceof Intent.OpenCalendar);

        Intent listRem = classifier.classify("what are my reminders");
        assertTrue(listRem instanceof Intent.ListReminders);

        // Verify reminder epoch time parsing
        long ms = com.aura.core.JavaActionOrchestrator.parseReminderTimeMillis("tomorrow at 8 pm");
        assertTrue(ms > System.currentTimeMillis());
    }

    @Test
    public void testPersonalEncryptedMemoryService() {
        com.aura.service.memory.PersonalMemoryService memory = new com.aura.service.memory.PersonalMemoryService(null);
        memory.clear();

        // 1. Store singular item
        memory.remember("wallet", "on the bedside table");
        assertTrue(memory.hasMemory("wallet"));
        assertTrue(memory.hasMemory("where is my wallet"));
        String walletRecall = memory.recall("wallet");
        assertTrue(walletRecall.contains("wallet is on the bedside table"));

        // 2. Store plural item
        memory.remember("keys", "on the kitchen counter");
        assertTrue(memory.hasMemory("keys"));
        assertTrue(memory.hasMemory("key")); // Plural/singular matching
        String keysRecall = memory.recall("keys");
        assertTrue(keysRecall.contains("keys are on the kitchen counter"));

        // 3. Store item ending in 'es' like glasses
        memory.remember("glasses", "in the office drawer");
        assertTrue(memory.hasMemory("glasses"));
        String glassesRecall = memory.recall("where did I leave my glasses");
        assertTrue(glassesRecall.contains("glasses are in the office drawer"));

        // 4. Value search
        assertTrue(memory.hasMemory("kitchen counter"));
        String locRecall = memory.recall("kitchen counter");
        assertTrue(locRecall.contains("keys"));

        // 5. Listing and forget
        List<String> all = memory.listAll();
        assertEquals(3, all.size());

        assertTrue(memory.forget("wallet"));
        assertFalse(memory.hasMemory("wallet"));
        assertEquals(2, memory.listAll().size());

        memory.clear();
        assertEquals(0, memory.listAll().size());
    }

    @Test
    public void testObjectLocationIntentClassification() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        // 1. Left/Put memory storage
        Intent rem1 = classifier.classify("I left my keys on the dining table");
        assertTrue(rem1 instanceof Intent.Remember);
        Intent.Remember r1 = (Intent.Remember) rem1;
        assertEquals("keys", r1.getKey());
        assertEquals("on the dining table", r1.getValue());

        Intent rem2 = classifier.classify("remember I put my wallet in the drawer");
        assertTrue(rem2 instanceof Intent.Remember);
        Intent.Remember r2 = (Intent.Remember) rem2;
        assertEquals("wallet", r2.getKey());
        assertEquals("in the drawer", r2.getValue());

        // 2. Where did I put/leave object recall
        Intent rec1 = classifier.classify("where did I put my keys");
        assertTrue(rec1 instanceof Intent.Recall);
        assertEquals("keys", ((Intent.Recall) rec1).getQuery());

        Intent rec2 = classifier.classify("where did I leave my wallet");
        assertTrue(rec2 instanceof Intent.Recall);
        assertEquals("wallet", ((Intent.Recall) rec2).getQuery());

        // 3. Question-based object recall
        Intent rec3 = classifier.classify("where are my keys");
        assertTrue(rec3 instanceof Intent.Recall);
        assertEquals("keys", ((Intent.Recall) rec3).getQuery());

        Intent rec4 = classifier.classify("where's my walking stick");
        assertTrue(rec4 instanceof Intent.Recall);
        assertEquals("walking stick", ((Intent.Recall) rec4).getQuery());

        Intent rec5 = classifier.classify("find my glasses");
        assertTrue(rec5 instanceof Intent.Recall);
        assertEquals("glasses", ((Intent.Recall) rec5).getQuery());

        Intent rec6 = classifier.classify("where is my phone");
        assertTrue(rec6 instanceof Intent.Recall);
        assertEquals("phone", ((Intent.Recall) rec6).getQuery());
    }

    @Test
    public void testOrchestratorMemoryAndObjectRecallFlow() {
        com.aura.service.memory.PersonalMemoryService memory = new com.aura.service.memory.PersonalMemoryService(null);
        memory.clear();
        com.aura.service.tools.AuraToolbox toolbox = new com.aura.service.tools.AuraToolbox(null);
        toolbox.setMemoryService(memory);

        com.aura.core.JavaActionOrchestrator orchestrator = new com.aura.core.JavaActionOrchestrator(
                null, null, null, null, memory, toolbox
        );

        // 1. Remember keys
        com.aura.models.ActionResult res1 = orchestrator.executeAction(new Intent.Remember("keys", "on the dining table"));
        assertEquals(com.aura.models.ActionResult.ExecutionStatus.SUCCESS, res1.getStatus());
        assertTrue(res1.getDetail().contains("keys are on the dining table"));

        // 2. Recall keys found
        com.aura.models.ActionResult res2 = orchestrator.executeAction(new Intent.Recall("keys"));
        assertEquals(com.aura.models.ActionResult.ExecutionStatus.SUCCESS, res2.getStatus());
        assertTrue(res2.getDetail().contains("keys are on the dining table"));

        // 3. Recall unknown object -> prompts camera scan
        com.aura.models.ActionResult res3 = orchestrator.executeAction(new Intent.Recall("passport"));
        assertEquals(com.aura.models.ActionResult.ExecutionStatus.SUCCESS, res3.getStatus());
        assertTrue(res3.getDetail().contains("scan your surroundings with the camera"));

        // 4. Toolbox find_object with stored item
        java.util.HashMap<String, Object> p = new java.util.HashMap<>();
        p.put("objectName", "keys");
        String toolRes1 = toolbox.execute("find_object", p);
        assertTrue(toolRes1.contains("keys are on the dining table"));

        // 5. Toolbox find_object with unstored item
        p.put("objectName", "backpack");
        String toolRes2 = toolbox.execute("find_object", p);
        assertTrue(toolRes2.contains("Point your camera slowly around the room"));
    }

    @Test
    public void testVisionAndObstacleCommands() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        Intent vision1 = classifier.classify("What is in front of me?");
        assertTrue(vision1 instanceof Intent.DescribeObstacles);

        Intent vision2 = classifier.classify("Check for obstacles.");
        assertTrue(vision2 instanceof Intent.DescribeObstacles);

        Intent vision3 = classifier.classify("Is my path clear?");
        assertTrue(vision3 instanceof Intent.DescribeObstacles);

        Intent vision4 = classifier.classify("What do you see?");
        assertTrue(vision4 instanceof Intent.DescribeObstacles);

        Intent vision5 = classifier.classify("Scan for obstacles.");
        assertTrue(vision5 instanceof Intent.DescribeObstacles);

        com.aura.infrastructure.AuraVisionEngine visionEngine = new com.aura.infrastructure.AuraVisionEngine(null);
        assertNotNull(visionEngine.describeScene());
        assertNotNull(visionEngine.checkObstacles());
        assertNotNull(visionEngine.findObject("keys"));
    }

    @Test
    public void testIndoorAndHouseNavigationFlow() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        // 1. Indoor classification
        Intent nav1 = classifier.classify("Navigate to the kitchen");
        assertTrue(nav1 instanceof Intent.StartNavigation);
        Intent.StartNavigation sn1 = (Intent.StartNavigation) nav1;
        assertEquals("the kitchen", sn1.getDestination());
        assertTrue(sn1.isIndoor());

        Intent nav2 = classifier.classify("Directions to the bedroom");
        assertTrue(nav2 instanceof Intent.StartNavigation);
        assertTrue(((Intent.StartNavigation) nav2).isIndoor());

        Intent nav3 = classifier.classify("How do I get to the dining room");
        assertTrue(nav3 instanceof Intent.StartNavigation);
        assertTrue(((Intent.StartNavigation) nav3).isIndoor());

        // Outdoor classification
        Intent navOut = classifier.classify("Take me to Central Park");
        assertTrue(navOut instanceof Intent.StartNavigation);
        assertFalse(((Intent.StartNavigation) navOut).isIndoor());

        // Stop navigation
        Intent stopNav = classifier.classify("Stop navigation");
        assertTrue(stopNav instanceof Intent.StopNavigation);

        // Map house
        Intent mapHouse = classifier.classify("Map my house");
        assertTrue(mapHouse instanceof Intent.MapHouse);

        // 2. House mapping & indoor navigation flow
        com.aura.service.navigation.HouseMapRepository mapRepo = new com.aura.service.navigation.HouseMapRepository(null);
        com.aura.infrastructure.AuraNavigationEngine navEngine = new com.aura.infrastructure.AuraNavigationEngine(mapRepo);
        com.aura.service.memory.PersonalMemoryService memory = new com.aura.service.memory.PersonalMemoryService(null);

        com.aura.core.JavaActionOrchestrator orchestrator = new com.aura.core.JavaActionOrchestrator(
                null, null, null, navEngine, memory, null
        );
        orchestrator.setHouseMapRepository(mapRepo);

        // Initial map session query
        com.aura.models.ActionResult mapRes = orchestrator.executeAction(new Intent.MapHouse());
        assertTrue(mapRes.getDetail().contains("House mapping session started"));

        // Save room anchor via speech
        com.aura.models.ActionResult remRoom = orchestrator.executeAction(new Intent.Remember("this room", "the kitchen"));
        assertTrue(remRoom.getDetail().contains("kitchen"));
        assertEquals(1, mapRepo.getRoomCount());
        assertNotNull(mapRepo.getAnchor("kitchen"));
        assertNotNull(mapRepo.getAnchor("the kitchen")); // Resilient prefix matching

        // Navigate to the saved room
        com.aura.models.ActionResult navExec = orchestrator.executeAction(new Intent.StartNavigation("the kitchen", true));
        assertEquals(com.aura.models.ActionResult.ExecutionStatus.SUCCESS, navExec.getStatus());
        assertTrue(navExec.getDetail().contains("Navigating to kitchen"));
        assertTrue(navExec.getDetail().contains("doorway"));
    }

    @Test
    public void testSpatialPoseAndSteeringInstructions() {
        com.aura.infrastructure.AuraNavigationEngine nav = new com.aura.infrastructure.AuraNavigationEngine(null);

        // 1. Destination reached (< 0.6m)
        com.aura.infrastructure.AuraNavigationEngine.Pose origin = new com.aura.infrastructure.AuraNavigationEngine.Pose(0.0, 0.0, 0.0, 0.0);
        com.aura.infrastructure.AuraNavigationEngine.Pose close = new com.aura.infrastructure.AuraNavigationEngine.Pose(0.2, 0.0, 0.3, 0.0);
        String reached = nav.getSafetyAwareInstruction(origin, close);
        assertTrue(reached.contains("Destination reached"));

        // 2. Straight ahead walking
        com.aura.infrastructure.AuraNavigationEngine.Pose forward = new com.aura.infrastructure.AuraNavigationEngine.Pose(0.0, 0.0, 4.0, 0.0);
        String straight = nav.getSafetyAwareInstruction(origin, forward);
        assertTrue(straight.contains("Walk straight forward"));
        assertTrue(straight.contains("paces"));

        // 3. Right turn
        com.aura.infrastructure.AuraNavigationEngine.Pose rightTarget = new com.aura.infrastructure.AuraNavigationEngine.Pose(4.0, 0.0, 0.0, 0.0);
        String right = nav.getSafetyAwareInstruction(origin, rightTarget);
        assertTrue(right.contains("Turn"));
        assertTrue(right.contains("right"));

        // 4. Left turn
        com.aura.infrastructure.AuraNavigationEngine.Pose leftTarget = new com.aura.infrastructure.AuraNavigationEngine.Pose(-4.0, 0.0, 0.0, 0.0);
        String left = nav.getSafetyAwareInstruction(origin, leftTarget);
        assertTrue(left.contains("Turn"));
        assertTrue(left.contains("left"));

        // 5. Vertical floor hint (upstairs)
        com.aura.infrastructure.AuraNavigationEngine.Pose upstairs = new com.aura.infrastructure.AuraNavigationEngine.Pose(0.0, 2.5, 4.0, 0.0);
        String up = nav.getSafetyAwareInstruction(origin, upstairs);
        assertTrue(up.contains("up stairs"));
    }

    @Test
    public void testMinuteMovementAndIndoorNavigationFeatures() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        // 1. Voice classification of minute movements
        Intent nextStep = classifier.classify("next step");
        assertTrue(nextStep instanceof Intent.ExecuteTool);
        assertEquals("nav_next_step", ((Intent.ExecuteTool) nextStep).getToolName());

        Intent stepsLeft = classifier.classify("how many steps left");
        assertTrue(stepsLeft instanceof Intent.ExecuteTool);
        assertEquals("nav_steps_remaining", ((Intent.ExecuteTool) stepsLeft).getToolName());

        Intent checkHeading = classifier.classify("check my heading");
        assertTrue(checkHeading instanceof Intent.ExecuteTool);
        assertEquals("nav_check_alignment", ((Intent.ExecuteTool) checkHeading).getToolName());

        Intent locateDoor = classifier.classify("where is the doorway");
        assertTrue(locateDoor instanceof Intent.ExecuteTool);
        assertEquals("nav_locate_doorway", ((Intent.ExecuteTool) locateDoor).getToolName());

        Intent tookPaces = classifier.classify("i took 3 steps");
        assertTrue(tookPaces instanceof Intent.ExecuteTool);
        assertEquals("nav_record_steps", ((Intent.ExecuteTool) tookPaces).getToolName());
        assertEquals(3, ((Intent.ExecuteTool) tookPaces).getParameters().get("paces"));

        Intent scanPath = classifier.classify("scan path ahead");
        assertTrue(scanPath instanceof Intent.DescribeObstacles);

        Intent fridgeNav = classifier.classify("take me to the refrigerator");
        assertTrue(fridgeNav instanceof Intent.StartNavigation);
        assertTrue(((Intent.StartNavigation) fridgeNav).isIndoor());

        // 2. Active Session Micro-guidance execution
        com.aura.service.navigation.HouseMapRepository mapRepo = new com.aura.service.navigation.HouseMapRepository(null);
        mapRepo.saveAnchor("kitchen", 3.0, 4.0, 1, "near refrigerator");
        com.aura.infrastructure.AuraNavigationEngine navEngine = new com.aura.infrastructure.AuraNavigationEngine(mapRepo);
        com.aura.core.JavaActionOrchestrator orchestrator = new com.aura.core.JavaActionOrchestrator(
                null, null, null, navEngine, null, null
        );
        orchestrator.setHouseMapRepository(mapRepo);

        // Start indoor navigation to kitchen
        com.aura.models.ActionResult navStart = orchestrator.executeAction(new Intent.StartNavigation("kitchen", true));
        assertEquals(com.aura.models.ActionResult.ExecutionStatus.SUCCESS, navStart.getStatus());
        assertTrue(navStart.getDetail().contains("Navigating to kitchen"));
        assertTrue(navStart.getDetail().contains("doorway"));
        assertTrue(navEngine.isNavigatingIndoor());

        // Query next micro step
        com.aura.models.ActionResult step2 = orchestrator.executeAction(new Intent.ExecuteTool("nav_next_step", new java.util.HashMap<>()));
        assertTrue(step2.getDetail().contains("Step 2 of"));

        // Query paces remaining
        com.aura.models.ActionResult remaining = orchestrator.executeAction(new Intent.ExecuteTool("nav_steps_remaining", new java.util.HashMap<>()));
        assertTrue(remaining.getDetail().contains("paces"));
        assertTrue(remaining.getDetail().contains("meters"));

        // Query alignment
        com.aura.models.ActionResult alignment = orchestrator.executeAction(new Intent.ExecuteTool("nav_check_alignment", new java.util.HashMap<>()));
        assertTrue(alignment.getDetail().contains("12 o'clock") || alignment.getDetail().contains("Alignment"));

        // Query doorway locator
        com.aura.models.ActionResult doorway = orchestrator.executeAction(new Intent.ExecuteTool("nav_locate_doorway", new java.util.HashMap<>()));
        assertTrue(doorway.getDetail().contains("doorway") || doorway.getDetail().contains("door"));
        assertTrue(doorway.getDetail().contains("threshold"));

        // Record steps taken
        java.util.HashMap<String, Object> p = new java.util.HashMap<>();
        p.put("paces", 2);
        com.aura.models.ActionResult recorded = orchestrator.executeAction(new Intent.ExecuteTool("nav_record_steps", p));
        assertTrue(recorded.getDetail().contains("Recorded 2 paces"));

        // Stop navigation
        com.aura.models.ActionResult stopped = orchestrator.executeAction(new Intent.StopNavigation());
        assertEquals("Navigation has been stopped.", stopped.getDetail());
        assertFalse(navEngine.isNavigatingIndoor());
    }

    @Test
    public void testMediaAndVolumeIntentClassification() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        // Volume adjustments
        Intent volUp = classifier.classify("turn up the volume");
        assertTrue(volUp instanceof Intent.MediaControl);
        assertEquals("volume_up", ((Intent.MediaControl) volUp).getAction());

        Intent louder = classifier.classify("make it louder");
        assertTrue(louder instanceof Intent.MediaControl);
        assertEquals("volume_up", ((Intent.MediaControl) louder).getAction());

        Intent volDown = classifier.classify("turn down the volume");
        assertTrue(volDown instanceof Intent.MediaControl);
        assertEquals("volume_down", ((Intent.MediaControl) volDown).getAction());

        Intent quieter = classifier.classify("quieter");
        assertTrue(quieter instanceof Intent.MediaControl);
        assertEquals("volume_down", ((Intent.MediaControl) quieter).getAction());

        Intent mute = classifier.classify("mute music");
        assertTrue(mute instanceof Intent.MediaControl);
        assertEquals("mute", ((Intent.MediaControl) mute).getAction());

        Intent unmute = classifier.classify("unmute");
        assertTrue(unmute instanceof Intent.MediaControl);
        assertEquals("unmute", ((Intent.MediaControl) unmute).getAction());

        Intent maxVol = classifier.classify("max volume");
        assertTrue(maxVol instanceof Intent.MediaControl);
        assertEquals("max_volume", ((Intent.MediaControl) maxVol).getAction());

        Intent volLevel = classifier.classify("what is the volume");
        assertTrue(volLevel instanceof Intent.MediaControl);
        assertEquals("volume_status", ((Intent.MediaControl) volLevel).getAction());

        // Playback and media tools
        Intent fastFwd = classifier.classify("fast forward");
        assertTrue(fastFwd instanceof Intent.MediaControl);
        assertEquals("fast_forward", ((Intent.MediaControl) fastFwd).getAction());

        Intent rewind = classifier.classify("rewind song");
        assertTrue(rewind instanceof Intent.MediaControl);
        assertEquals("rewind", ((Intent.MediaControl) rewind).getAction());

        Intent openMus = classifier.classify("open music");
        assertTrue(openMus instanceof Intent.MediaControl);
        assertEquals("open_music", ((Intent.MediaControl) openMus).getAction());

        Intent resumeMus = classifier.classify("resume playback");
        assertTrue(resumeMus instanceof Intent.MediaControl);
        assertEquals("resume", ((Intent.MediaControl) resumeMus).getAction());
    }

    @Test
    public void testEntertainmentJokesRiddlesFunFacts() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        // Jokes
        Intent joke1 = classifier.classify("tell me a joke");
        assertTrue(joke1 instanceof Intent.ExecuteTool);
        assertEquals("tell_joke", ((Intent.ExecuteTool) joke1).getToolName());

        Intent joke2 = classifier.classify("make me laugh");
        assertTrue(joke2 instanceof Intent.ExecuteTool);
        assertEquals("tell_joke", ((Intent.ExecuteTool) joke2).getToolName());

        // Riddles
        Intent riddle1 = classifier.classify("tell me a riddle");
        assertTrue(riddle1 instanceof Intent.ExecuteTool);
        assertEquals("tell_riddle", ((Intent.ExecuteTool) riddle1).getToolName());

        Intent riddle2 = classifier.classify("ask me a riddle");
        assertTrue(riddle2 instanceof Intent.ExecuteTool);
        assertEquals("tell_riddle", ((Intent.ExecuteTool) riddle2).getToolName());

        // Fun facts & Trivia
        Intent fact1 = classifier.classify("tell me a fun fact");
        assertTrue(fact1 instanceof Intent.ExecuteTool);
        assertEquals("fun_fact", ((Intent.ExecuteTool) fact1).getToolName());

        Intent fact2 = classifier.classify("did you know");
        assertTrue(fact2 instanceof Intent.ExecuteTool);
        assertEquals("fun_fact", ((Intent.ExecuteTool) fact2).getToolName());

        Intent fact3 = classifier.classify("trivia");
        assertTrue(fact3 instanceof Intent.ExecuteTool);
        assertEquals("fun_fact", ((Intent.ExecuteTool) fact3).getToolName());
    }

    @Test
    public void testToolboxEntertainmentExecution() {
        com.aura.service.tools.AuraToolbox toolbox = new com.aura.service.tools.AuraToolbox(null);

        // Joke execution
        String joke = toolbox.execute("tell_joke", null);
        assertNotNull(joke);
        assertFalse(joke.trim().isEmpty());

        // Riddle execution
        String riddle = toolbox.execute("tell_riddle", null);
        assertNotNull(riddle);
        assertTrue(riddle.contains("Riddle:") && riddle.contains("Answer:"));

        // Fun fact execution
        String fact = toolbox.execute("fun_fact", null);
        assertNotNull(fact);
        assertTrue(fact.contains("Did you know?"));

        // Media control with null context graceful fallback
        String mediaRes = toolbox.controlMedia("volume_up");
        assertEquals("Audio service unavailable.", mediaRes);

        String nullActionRes = toolbox.controlMedia(null);
        assertEquals("No media action specified.", nullActionRes);
    }

    @Test
    public void testUniversalConversationalAiClassification() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        // 1. Astronomy / Science unseen question
        Intent q1 = classifier.classify("Hey AURA, why does the moon look bigger near the horizon?");
        assertTrue("Expected Intent.AskAi for moon illusion query", q1 instanceof Intent.AskAi);
        assertTrue(((Intent.AskAi) q1).getQuestion().toLowerCase().contains("moon look bigger"));

        // 2. Quantum Computing
        Intent q2 = classifier.classify("Explain quantum computing");
        assertTrue("Expected Intent.AskAi for quantum computing", q2 instanceof Intent.AskAi);
        assertTrue(((Intent.AskAi) q2).getQuestion().toLowerCase().contains("quantum computing"));

        // 3. Coding & Programming
        Intent q3 = classifier.classify("Write a Java program to sort an array");
        assertTrue("Expected Intent.AskAi for coding task", q3 instanceof Intent.AskAi);
        assertTrue(((Intent.AskAi) q3).getQuestion().toLowerCase().contains("sort an array"));

        // 4. Creative / Philosophical
        Intent q4 = classifier.classify("What is the meaning of life and happiness?");
        assertTrue("Expected Intent.AskAi for open-ended philosophy", q4 instanceof Intent.AskAi);

        // 5. AI Configuration Commands
        Intent keyIntent = classifier.classify("set gemini api key AIzaSyFakeTestKey12345");
        assertTrue("Expected Intent.SetAiKey", keyIntent instanceof Intent.SetAiKey);
        assertEquals("AIzaSyFakeTestKey12345", ((Intent.SetAiKey) keyIntent).getApiKey());

        Intent statusIntent = classifier.classify("what is the ai status");
        assertTrue("Expected Intent.GetAiStatus", statusIntent instanceof Intent.GetAiStatus);

        Intent clearIntent = classifier.classify("clear our conversation history");
        assertTrue("Expected Intent.ClearConversation", clearIntent instanceof Intent.ClearConversation);
    }

    @Test
    public void testConversationalFollowUpsClassifiedAsAskAi() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        // Follow-ups from user requirement scenario:
        // "Explain it more simply"
        Intent f1 = classifier.classify("Explain it more simply");
        assertTrue("Follow-up simplification should route to AskAi", f1 instanceof Intent.AskAi);
        assertEquals("Explain it more simply", ((Intent.AskAi) f1).getQuestion());

        // "Give me an example"
        Intent f2 = classifier.classify("Give me an example");
        assertTrue("Follow-up example should route to AskAi", f2 instanceof Intent.AskAi);
        assertEquals("Give me an example", ((Intent.AskAi) f2).getQuestion());

        // "Compare it with the previous one"
        Intent f3 = classifier.classify("Compare it with the previous one");
        assertTrue("Follow-up comparison should route to AskAi", f3 instanceof Intent.AskAi);
        assertEquals("Compare it with the previous one", ((Intent.AskAi) f3).getQuestion());

        // "Continue from where you stopped"
        Intent f4 = classifier.classify("Continue from where you stopped");
        assertTrue("Follow-up continuation should route to AskAi", f4 instanceof Intent.AskAi);

        // "What are its advantages?"
        Intent f5 = classifier.classify("What are its advantages?");
        assertTrue("Follow-up advantages question should route to AskAi", f5 instanceof Intent.AskAi);
    }

    @Test
    public void testAssistiveCommandsPreservedVersusAiQuestions() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        // 1. Scene / Obstacle detection
        Intent obs = classifier.classify("what is in front of me");
        assertTrue("Obstacle query must route to DescribeObstacles", obs instanceof Intent.DescribeObstacles);

        Intent doc = classifier.classify("read the document in front of me");
        assertTrue("Document reading must route to DescribeObstacles", doc instanceof Intent.DescribeObstacles);

        Intent obj = classifier.classify("what is this object");
        assertTrue("Object recognition must route to DescribeObstacles", obj instanceof Intent.DescribeObstacles);

        // 2. Navigation
        Intent nav = classifier.classify("guide me to the exit");
        assertTrue("Navigation request must route to StartNavigation", nav instanceof Intent.StartNavigation);

        // 3. Memory & Reminders
        Intent rem = classifier.classify("remind me to take my medicine at 8 PM");
        assertTrue("Medicine reminder must route to SetReminder", rem instanceof Intent.SetReminder);

        // 4. Phone Calling
        Intent call = classifier.classify("call Mom");
        assertTrue("Call contact must route to CallContact", call instanceof Intent.CallContact);

        // 5. SMS Messaging
        Intent sms = classifier.classify("send a message to Rahul saying I am home");
        assertTrue("SMS message must route to SendMessage", sms instanceof Intent.SendMessage);

        // 6. Device Hardware Tools
        Intent battery = classifier.classify("how much battery do I have");
        assertTrue("Battery status must route to GetBatteryStatus", battery instanceof Intent.GetBatteryStatus);

        Intent flash = classifier.classify("turn on the flashlight");
        assertTrue("Flashlight command must route to ToggleFlashlight", flash instanceof Intent.ToggleFlashlight);
    }

    @Test
    public void testConversationMemoryManagement() {
        com.aura.ai.ConversationMemoryManager memory = new com.aura.ai.ConversationMemoryManager(3); // 3 turns max

        assertTrue(memory.isEmpty());
        assertEquals(0, memory.getTurnCount());

        // Turn 1
        memory.addExchange("Why is the sky blue?", "Rayleigh scattering makes short blue light scatter more.");
        assertEquals(1, memory.getTurnCount());
        assertEquals("Why is the sky blue?", memory.getLastUserMessage());
        assertTrue(memory.getLastModelReply().contains("Rayleigh scattering"));

        // Turn 2
        memory.addExchange("Explain it more simply.", "Blue light bends and spreads everywhere in the air.");
        assertEquals(2, memory.getTurnCount());

        // Turn 3
        memory.addExchange("Give me an example.", "Think of small dust floating in sunlight.");
        assertEquals(3, memory.getTurnCount());

        // Turn 4 - should roll over and discard turn 1 to preserve max 3 turns (6 messages)
        memory.addExchange("What about sunset?", "Sunlight travels through more air, so only red passes through.");
        assertEquals(3, memory.getTurnCount());
        assertEquals(6, memory.getHistory().size());

        // Ensure Turn 1 was trimmed
        assertFalse(memory.getHistory().get(0).getContent().contains("Why is the sky blue?"));

        // Test Clear
        memory.clear();
        assertTrue(memory.isEmpty());
        assertEquals(0, memory.getTurnCount());
    }

    @Test
    public void testMarkdownStrippingForTextToSpeech() {
        String rawMarkdown = "### Moon Illusion\n\nThe **moon** appears *larger* due to:\n* Optical context\n* Horizon landmarks\n\nUse `System.out.println()` to log.";
        String cleaned = com.aura.ai.GeminiAiService.cleanForTextToSpeech(rawMarkdown);

        assertFalse("Cleaned speech should not contain bold markers", cleaned.contains("**"));
        assertFalse("Cleaned speech should not contain markdown hashes", cleaned.contains("###"));
        assertFalse("Cleaned speech should not contain backticks", cleaned.contains("`"));
        assertFalse("Cleaned speech should not contain asterisk bullets", cleaned.contains("* "));
        assertTrue("Cleaned speech should retain informative words", cleaned.contains("moon appears larger"));
    }

    @Test
    public void testZeroConfigAiServiceAnswersWithoutApiKey() {
        com.aura.ai.GeminiAiService service = new com.aura.ai.GeminiAiService(null);
        assertTrue("AI Service should always report configured (ready out-of-the-box)", service.isConfigured());

        // Test conversational prompt without any API key
        com.aura.ai.AiResponse resp = service.generateResponseSync("hello", null);
        assertNotNull(resp);
        assertTrue(resp.isSuccess());
        assertNotNull(resp.getReply());
        assertTrue(resp.getReply().toLowerCase().contains("hello") || resp.getReply().toLowerCase().contains("assist"));

        // Test identity prompt without any API key
        com.aura.ai.AiResponse idResp = service.generateResponseSync("who are you", null);
        assertTrue(idResp.isSuccess());
        assertTrue(idResp.getReply().toLowerCase().contains("aura"));
    }

    @Test
    public void testEmergencyContactVoiceCommands() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        // 1. Voice command to set emergency contact with phone number
        Intent setPhoneIntent = classifier.classify("my emergency contact is +19876543210");
        assertTrue("Intent should be Remember", setPhoneIntent instanceof Intent.Remember);
        Intent.Remember rPhone = (Intent.Remember) setPhoneIntent;
        assertEquals("emergency contact", rPhone.getKey());
        assertEquals("+19876543210", rPhone.getValue());

        // 2. Voice command to set emergency contact by name
        Intent setNameIntent = classifier.classify("set my emergency contact to Mom");
        assertTrue("Intent should be Remember", setNameIntent instanceof Intent.Remember);
        Intent.Remember rName = (Intent.Remember) setNameIntent;
        assertEquals("emergency contact", rName.getKey());
        assertEquals("Mom", rName.getValue());

        // 3. Voice query to recall emergency contact
        Intent whoIntent = classifier.classify("who is my emergency contact");
        assertTrue("Intent should be Recall", whoIntent instanceof Intent.Recall);
        Intent.Recall rWho = (Intent.Recall) whoIntent;
        assertEquals("emergency contact", rWho.getQuery());

        // 4. Voice commands to trigger emergency distress / SOS
        Intent sosIntent = classifier.classify("send sos");
        assertTrue("Intent should be Emergency", sosIntent instanceof Intent.Emergency);

        Intent call112Intent = classifier.classify("call 112");
        assertTrue("Intent should be Emergency", call112Intent instanceof Intent.Emergency);

        Intent distressIntent = classifier.classify("distress alert");
        assertTrue("Intent should be Emergency", distressIntent instanceof Intent.Emergency);
    }

    @Test
    public void testGuardianAlertManagerDataStructures() {
        // Universal 112 emergency fallback constant
        assertEquals("112", com.aura.service.safety.GuardianAlertManager.DEFAULT_EMERGENCY_SERVICE_NUMBER);

        // Emergency contact info model
        com.aura.service.safety.GuardianAlertManager.EmergencyContactInfo contact =
                new com.aura.service.safety.GuardianAlertManager.EmergencyContactInfo("Dr. Sarah", "+1234567890", "memory");
        assertEquals("Dr. Sarah", contact.getName());
        assertEquals("+1234567890", contact.getPhoneNumber());
        assertEquals("memory", contact.getSource());
        assertTrue(contact.toString().contains("Dr. Sarah"));
        assertTrue(contact.toString().contains("+1234567890"));

        // GuardianAlertManager failsafe execution with null dependencies
        com.aura.service.safety.GuardianAlertManager manager =
                new com.aura.service.safety.GuardianAlertManager(null, null, null);
        assertFalse("sendEmergencyAlert should fail safely when unconfigured", manager.sendEmergencyAlert());
        assertFalse("sendFallAlert should fail safely when unconfigured", manager.sendFallAlert());
        assertFalse("initiateEmergencyCall should fail safely when context is null", manager.initiateEmergencyCall(null));
        assertEquals("Guardian", manager.resolveGuardianName());
        assertNull(manager.resolveGuardianPhoneNumber());
    }

    @Test
    public void testEmergencyContactVoiceAndResolution() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        // 1. Voice classification for emergency contacts
        Intent intent1 = classifier.classify("my emergency contact is Rahul");
        assertTrue(intent1 instanceof Intent.Remember);
        assertEquals("emergency contact", ((Intent.Remember) intent1).getKey());
        assertEquals("Rahul", ((Intent.Remember) intent1).getValue());

        Intent intent2 = classifier.classify("set emergency number to +919876543210");
        assertTrue(intent2 instanceof Intent.Remember);
        assertEquals("emergency contact", ((Intent.Remember) intent2).getKey());
        assertEquals("+919876543210", ((Intent.Remember) intent2).getValue());

        Intent intent3 = classifier.classify("my guardian is Mom");
        assertTrue(intent3 instanceof Intent.Remember);
        assertEquals("guardian", ((Intent.Remember) intent3).getKey());
        assertEquals("Mom", ((Intent.Remember) intent3).getValue());

        Intent intent4 = classifier.classify("emergency");
        assertTrue(intent4 instanceof Intent.Emergency);

        Intent intent5 = classifier.classify("send sos");
        assertTrue(intent5 instanceof Intent.Emergency);

        // 2. PersonalMemoryService getRawValue verification
        com.aura.service.memory.PersonalMemoryService memory = new com.aura.service.memory.PersonalMemoryService(null);
        memory.remember("guardian", "+19876543210");
        assertEquals("+19876543210", memory.getRawValue("guardian"));

        memory.remember("emergency contact", "Rahul");
        assertEquals("Rahul", memory.getRawValue("emergency contact"));

        // 3. GuardianAlertManager resolution with direct phone number
        final java.util.concurrent.atomic.AtomicBoolean smsSent = new java.util.concurrent.atomic.AtomicBoolean(false);
        final String[] sentTo = new String[1];
        final String[] sentBody = new String[1];

        com.aura.infrastructure.SmsAdapter testAdapter = new com.aura.infrastructure.SmsAdapter(null) {
            @Override
            public com.aura.models.ActionResult send(String phoneNumber, String body) {
                smsSent.set(true);
                sentTo[0] = phoneNumber;
                sentBody[0] = body;
                return new com.aura.models.ActionResult(true, com.aura.models.Platform.SMS, "Sent");
            }
        };

        com.aura.service.safety.GuardianAlertManager alertManager =
                new com.aura.service.safety.GuardianAlertManager(testAdapter, null, memory);

        assertEquals("+19876543210", alertManager.resolveGuardianPhoneNumber());
        assertTrue(alertManager.sendEmergencyAlert());
        assertTrue(smsSent.get());
        assertEquals("+19876543210", sentTo[0]);
        assertTrue(sentBody[0].contains("EMERGENCY ALERT from AURA"));
    }

    @Test
    public void testOrchestratorExecuteEmergencyDispatchesSms() {
        final java.util.concurrent.atomic.AtomicBoolean smsFired = new java.util.concurrent.atomic.AtomicBoolean(false);
        final String[] targetNum = new String[1];

        com.aura.infrastructure.SmsAdapter adapter = new com.aura.infrastructure.SmsAdapter(null) {
            @Override
            public com.aura.models.ActionResult send(String phoneNumber, String body) {
                smsFired.set(true);
                targetNum[0] = phoneNumber;
                return new com.aura.models.ActionResult(true, com.aura.models.Platform.SMS, "Sent");
            }
        };

        com.aura.service.memory.PersonalMemoryService memory = new com.aura.service.memory.PersonalMemoryService(null);
        memory.remember("guardian", "+15559876543");

        com.aura.service.safety.GuardianAlertManager guardianManager =
                new com.aura.service.safety.GuardianAlertManager(adapter, null, memory);

        com.aura.core.JavaActionOrchestrator orchestrator = new com.aura.core.JavaActionOrchestrator(
                null, null, null, null, null, memory, null, adapter, null
        );
        orchestrator.setGuardianAlertManager(guardianManager);

        com.aura.models.ActionResult result = orchestrator.executeAction(new com.aura.models.Intent.Emergency());
        assertTrue(result.isSuccessful());
        assertTrue(smsFired.get());
        assertEquals("+15559876543", targetNum[0]);
        assertTrue(result.getDetail().contains("distress SMS with your live location has been sent"));
    }

    @Test
    public void testVisionEngineObstaclesAndFeatures() {
        com.aura.infrastructure.AuraVisionEngine vision = new com.aura.infrastructure.AuraVisionEngine(null);

        // Hardware / permission check with null context
        assertFalse(vision.hasCameraPermission());
        assertFalse(vision.isCameraHardwareAvailable());

        // Obstacle checking fallback returns safe guidance
        String obstacles = vision.checkObstacles();
        assertNotNull(obstacles);
        assertTrue(obstacles.toLowerCase().contains("camera"));

        // Describe scene
        String scene = vision.describeScene();
        assertNotNull(scene);
        assertTrue(scene.toLowerCase().contains("camera"));

        // Object search
        String findKeys = vision.findObject("keys");
        assertNotNull(findKeys);
        assertTrue(findKeys.toLowerCase().contains("keys") || findKeys.toLowerCase().contains("camera"));

        // Empty object query
        String findEmpty = vision.findObject("");
        assertEquals("What object are you looking for?", findEmpty);

        // Read text
        String text = vision.readText();
        assertNotNull(text);
        assertTrue(text.toLowerCase().contains("camera") || text.toLowerCase().contains("text"));

        // Detect color
        String color = vision.detectColor();
        assertNotNull(color);
        assertTrue(color.toLowerCase().contains("color") || color.toLowerCase().contains("camera"));

        // Check lighting
        String lighting = vision.checkLighting();
        assertNotNull(lighting);
        assertTrue(lighting.toLowerCase().contains("light"));

        // Mock AI Service with vision
        com.aura.ai.AiLanguageModelService mockAi = new com.aura.ai.AiLanguageModelService() {
            @Override
            public void generateResponse(String prompt, List<com.aura.ai.ChatMessage> history, Callback callback) {
                callback.onSuccess(com.aura.ai.AiResponse.success("AI response", "mock-model"));
            }

            @Override
            public com.aura.ai.AiResponse generateResponseSync(String prompt, List<com.aura.ai.ChatMessage> history) {
                return com.aura.ai.AiResponse.success("AI response", "mock-model");
            }

            @Override
            public com.aura.ai.AiResponse generateVisionResponseSync(String userPrompt, byte[] jpegData) {
                return com.aura.ai.AiResponse.success("Vision AI: Path is clear with a chair at 2 o'clock.", "gemini-1.5-flash");
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public String getProviderName() {
                return "MockProvider";
            }

            @Override
            public String getActiveModelName() {
                return "gemini-1.5-flash";
            }

            @Override
            public boolean isVisionSupported() {
                return true;
            }
        };

        vision.setAiService(mockAi);
        assertEquals(mockAi, vision.getAiService());
        assertTrue(vision.getAiService().isVisionSupported());
    }

    @Test
    public void testOrchestratorVisionActions() {
        com.aura.infrastructure.AuraVisionEngine vision = new com.aura.infrastructure.AuraVisionEngine(null);
        com.aura.core.JavaActionOrchestrator orchestrator = new com.aura.core.JavaActionOrchestrator(
                null, null, null, vision, null, null, null, null, null
        );

        // 1. Describe obstacles intent
        com.aura.models.ActionResult res1 = orchestrator.executeAction(new com.aura.models.Intent.DescribeObstacles());
        assertEquals(com.aura.models.ActionResult.ExecutionStatus.SUCCESS, res1.getStatus());
        assertNotNull(res1.getDetail());
        assertFalse(res1.getDetail().isEmpty());

        // 2. ExecuteTool for vision: detect_color
        com.aura.models.Intent.ExecuteTool colorTool = new com.aura.models.Intent.ExecuteTool("detect_color", new java.util.HashMap<>());
        com.aura.models.ActionResult res2 = orchestrator.executeAction(colorTool);
        assertEquals(com.aura.models.ActionResult.ExecutionStatus.SUCCESS, res2.getStatus());
        assertNotNull(res2.getDetail());

        // 3. ExecuteTool for vision: read_text_camera
        com.aura.models.Intent.ExecuteTool readTool = new com.aura.models.Intent.ExecuteTool("read_text_camera", new java.util.HashMap<>());
        com.aura.models.ActionResult res3 = orchestrator.executeAction(readTool);
        assertEquals(com.aura.models.ActionResult.ExecutionStatus.SUCCESS, res3.getStatus());
        assertNotNull(res3.getDetail());

        // 4. ExecuteTool for vision: check_lighting
        com.aura.models.Intent.ExecuteTool lightTool = new com.aura.models.Intent.ExecuteTool("check_lighting", new java.util.HashMap<>());
        com.aura.models.ActionResult res4 = orchestrator.executeAction(lightTool);
        assertEquals(com.aura.models.ActionResult.ExecutionStatus.SUCCESS, res4.getStatus());
        assertNotNull(res4.getDetail());

        // 5. ExecuteTool for vision: find_object
        java.util.HashMap<String, Object> params = new java.util.HashMap<>();
        params.put("objectName", "water bottle");
        com.aura.models.Intent.ExecuteTool findTool = new com.aura.models.Intent.ExecuteTool("find_object", params);
        com.aura.models.ActionResult res5 = orchestrator.executeAction(findTool);
        assertEquals(com.aura.models.ActionResult.ExecutionStatus.SUCCESS, res5.getStatus());
        assertNotNull(res5.getDetail());
        assertTrue(res5.getDetail().contains("water bottle"));
    }

    @Test
    public void testOneTimeVoiceRecognitionAndSpeakerProfile() {
        // 1. Test SpectralSpeakerRecognitionEngine one-time enrollment
        com.aura.infrastructure.SpectralSpeakerRecognitionEngine engine =
                new com.aura.infrastructure.SpectralSpeakerRecognitionEngine(null);

        assertFalse(engine.isRegistered());

        // Create sample voice PCM for User A (speech envelope with cadence)
        short[] userAudio1 = new short[1600];
        for (int i = 0; i < userAudio1.length; i++) {
            if ((i / 200) % 2 == 0) {
                userAudio1[i] = (short) (Math.sin(i * 0.2) * 8000);
            }
        }

        // Enroll once
        boolean firstEnroll = engine.enrollOnce(userAudio1);
        assertTrue(firstEnroll);
        assertTrue(engine.isRegistered());

        // Subsequent enrollOnce call should return false (only once!)
        short[] otherAudio = new short[1600];
        for (int i = 0; i < otherAudio.length; i++) {
            otherAudio[i] = (short) (Math.cos(i * 0.8) * 12000);
        }
        boolean secondEnroll = engine.enrollOnce(otherAudio);
        assertFalse(secondEnroll); // Already enrolled!

        // Same user's voice verifies (same cadence, lower volume)
        short[] userAudio2 = new short[1600];
        for (int i = 0; i < userAudio2.length; i++) {
            if ((i / 200) % 2 == 0) {
                userAudio2[i] = (short) (Math.sin(i * 0.2) * 5000);
            }
        }
        assertTrue(engine.verify(userAudio2));

        // Completely different cadence/speaker voice does not verify
        short[] imposterAudio = new short[1600];
        for (int i = 0; i < imposterAudio.length; i++) {
            if ((i / 200) % 2 == 1) {
                imposterAudio[i] = (short) (Math.sin(i * 0.5) * 8000);
            }
        }
        assertFalse(engine.verify(imposterAudio));

        // 2. Test Classifier for voice intents
        com.aura.core.JavaAiIntentClassifier classifier = new com.aura.core.JavaAiIntentClassifier();
        Intent regIntent = classifier.classify("register my voice");
        assertTrue(regIntent instanceof Intent.RegisterVoice);

        Intent resetIntent = classifier.classify("reset my voice");
        assertTrue(resetIntent instanceof Intent.ResetVoice);

        Intent statusIntent = classifier.classify("is my voice registered");
        assertTrue(statusIntent instanceof Intent.GetVoiceStatus);

        // 3. Test Orchestrator voice action execution
        com.aura.core.JavaActionOrchestrator orchestrator = new com.aura.core.JavaActionOrchestrator(
                null, null, null, null, null, null, null, null, null
        );
        orchestrator.setSpeakerEngine(engine);

        // Check status when registered
        com.aura.models.ActionResult statusRes = orchestrator.executeAction(new Intent.GetVoiceStatus());
        assertTrue(statusRes.isSuccessful());
        assertTrue(statusRes.getDetail().contains("currently registered and active"));

        // Register again when already registered informs user
        com.aura.models.ActionResult regRes = orchestrator.executeAction(new Intent.RegisterVoice());
        assertTrue(regRes.isSuccessful());
        assertTrue(regRes.getDetail().contains("already registered"));

        // Reset voice clears profile
        com.aura.models.ActionResult resetRes = orchestrator.executeAction(new Intent.ResetVoice());
        assertTrue(resetRes.isSuccessful());
        assertFalse(engine.isRegistered());

        // Check status after reset
        com.aura.models.ActionResult statusAfter = orchestrator.executeAction(new Intent.GetVoiceStatus());
        assertTrue(statusAfter.isSuccessful());
        assertTrue(statusAfter.getDetail().contains("not yet registered"));

        // 4. Test EnrollingVoice state phrase matching
        com.aura.application.fsm.ConversationState.EnrollingVoice enrollState =
                new com.aura.application.fsm.ConversationState.EnrollingVoice("Hello AURA, I am here");
        assertEquals("Hello AURA, I am here", enrollState.getPhrase());
        assertTrue(enrollState.toString().contains("EnrollingVoice"));
    }

    @Test
    public void testNotificationCommandsAndExecution() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        // 1. Classification tests for notification keywords
        Intent i1 = classifier.classify("notifications");
        assertTrue(i1 instanceof Intent.ReadNotifications);

        Intent i2 = classifier.classify("read notifications");
        assertTrue(i2 instanceof Intent.ReadNotifications);

        Intent i3 = classifier.classify("check notifications");
        assertTrue(i3 instanceof Intent.ReadNotifications);

        Intent i4 = classifier.classify("read my notifications");
        assertTrue(i4 instanceof Intent.ReadNotifications);

        Intent i5 = classifier.classify("check my notifications");
        assertTrue(i5 instanceof Intent.ReadNotifications);

        Intent i6 = classifier.classify("what are my notifications");
        assertTrue(i6 instanceof Intent.ReadNotifications);

        Intent i7 = classifier.classify("check alerts");
        assertTrue(i7 instanceof Intent.ReadNotifications);

        Intent i8 = classifier.classify("read alerts");
        assertTrue(i8 instanceof Intent.ReadNotifications);

        Intent i9 = classifier.classify("urgent notifications");
        assertTrue(i9 instanceof Intent.ReadImportantNotifications);

        Intent i10 = classifier.classify("important notifications");
        assertTrue(i10 instanceof Intent.ReadImportantNotifications);

        // 2. Orchestrator execution tests
        com.aura.core.JavaActionOrchestrator orchestrator = new com.aura.core.JavaActionOrchestrator(
                null, null, null, null, null, null
        );

        com.aura.models.ActionResult res1 = orchestrator.executeAction(new Intent.ReadNotifications());
        assertTrue(res1.isSuccessful());
        assertNotNull(res1.getDetail());
        assertTrue(res1.getDetail().contains("notifications"));

        com.aura.models.ActionResult res2 = orchestrator.executeAction(new Intent.ReadImportantNotifications());
        assertTrue(res2.isSuccessful());
        assertNotNull(res2.getDetail());
        assertTrue(res2.getDetail().contains("notifications"));

        // 3. AuraToolbox execution
        com.aura.service.tools.AuraToolbox toolbox = new com.aura.service.tools.AuraToolbox(null);
        String toolboxRes = toolbox.execute("summarize_notifications", null);
        assertNotNull(toolboxRes);
        assertTrue(toolboxRes.contains("notifications"));
    }

    @Test
    public void testIndoorAiNavigationAndObstacleSystem() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        // 1. Classification of indoor campus destinations
        Intent libIntent = classifier.classify("take me to the library");
        assertTrue(libIntent instanceof Intent.StartNavigation);
        Intent.StartNavigation navLib = (Intent.StartNavigation) libIntent;
        assertTrue(navLib.isIndoor());
        assertTrue(navLib.getDestination().toLowerCase().contains("library"));

        Intent labIntent = classifier.classify("navigate to the computer lab");
        assertTrue(labIntent instanceof Intent.StartNavigation);
        Intent.StartNavigation navLab = (Intent.StartNavigation) labIntent;
        assertTrue(navLab.isIndoor());
        assertTrue(navLab.getDestination().toLowerCase().contains("computer lab"));

        // 2. AuraNavigationEngine Campus Integration
        com.aura.infrastructure.AuraNavigationEngine navEngine = new com.aura.infrastructure.AuraNavigationEngine(null);
        String libRoute = navEngine.getIndoorInstruction("Library");
        assertNotNull(libRoute);
        assertTrue("Should contain distance or steps", libRoute.toLowerCase().contains("metre") || libRoute.toLowerCase().contains("meter") || libRoute.toLowerCase().contains("pace"));
        assertTrue("Should contain library destination", libRoute.toLowerCase().contains("library"));

        String labRoute = navEngine.getIndoorInstruction("Computer Lab");
        assertNotNull(labRoute);
        assertTrue(labRoute.toLowerCase().contains("computer lab") || labRoute.toLowerCase().contains("floor"));

        // 3. Dynamic Rerouting on Obstacle
        String rerouteMsg = navEngine.recalculateRouteDueToObstacle("chair blocking path");
        assertNotNull(rerouteMsg);
        assertTrue(rerouteMsg.toLowerCase().contains("recalculated") || rerouteMsg.toLowerCase().contains("detour") || rerouteMsg.toLowerCase().contains("alternate"));

        // 4. Nearest Emergency Exit
        String exitRoute = navEngine.findNearestEmergencyExit();
        assertNotNull(exitRoute);
        assertTrue(exitRoute.toLowerCase().contains("exit"));

        // 5. AuraVisionEngine Structured Risk Evaluation
        com.aura.infrastructure.AuraVisionEngine vision = new com.aura.infrastructure.AuraVisionEngine(null);
        com.aura.infrastructure.AuraVisionEngine.ObstacleRiskEvaluation dangerObs =
                vision.evaluateObstacleRisk("Chair", 0.9, 0.5);
        assertEquals("DANGER", dangerObs.riskLevel);
        assertTrue(dangerObs.shouldAlert);
        assertNotNull(dangerObs.guidanceAdvice);

        com.aura.infrastructure.AuraVisionEngine.ObstacleRiskEvaluation cautionObs =
                vision.evaluateObstacleRisk("Person", 1.8, 0.3);
        assertEquals("CAUTION", cautionObs.riskLevel);
        assertTrue(cautionObs.shouldAlert);
        assertTrue(cautionObs.guidanceAdvice.contains("right"));

        com.aura.infrastructure.AuraVisionEngine.ObstacleRiskEvaluation safeObs =
                vision.evaluateObstacleRisk("Signpost", 3.5, 0.5);
        assertEquals("SAFE", safeObs.riskLevel);
        assertFalse(safeObs.shouldAlert);

        // 6. Empathetic Navigation Style Adaptation
        com.aura.emotion.EmpatheticResponseEngine emotionEngine = new com.aura.emotion.EmpatheticResponseEngine();
        com.aura.emotion.EmotionResult confused = new com.aura.emotion.EmotionResult(
                com.aura.emotion.Emotion.CONFUSED, 0.85f, java.util.List.of()
        );
        String adaptedConfused = emotionEngine.adaptResponse(
                "Head forward for 20 meters.", navLib, null, confused, null
        );
        assertTrue("Confused response should contain landmarks or reassurance",
                adaptedConfused.contains("landmarks") || adaptedConfused.contains("step by step"));

        com.aura.emotion.EmotionResult anxious = new com.aura.emotion.EmotionResult(
                com.aura.emotion.Emotion.ANXIOUS, 0.85f, java.util.List.of()
        );
        String adaptedAnxious = emotionEngine.adaptResponse(
                "Head forward for 20 meters.", navLib, null, anxious, null
        );
        assertTrue("Anxious response should reassure safety",
                adaptedAnxious.contains("safe") || adaptedAnxious.contains("pace"));
    }

    @Test
    public void testEmergencyContactVoiceRegistrationAndCalling() {
        JavaAiIntentClassifier classifier = new JavaAiIntentClassifier();

        // 1. Classification of various emergency contact phrases
        Intent set1 = classifier.classify("my emergency contact is Rahul 9876543210");
        assertTrue(set1 instanceof Intent.Remember);
        Intent.Remember rem1 = (Intent.Remember) set1;
        assertEquals("emergency contact", rem1.getKey());
        assertTrue(rem1.getValue().contains("9876543210"));

        Intent set2 = classifier.classify("emergency contact is 9123456780");
        assertTrue(set2 instanceof Intent.Remember);

        Intent callEmergency1 = classifier.classify("call my emergency contact");
        assertTrue(callEmergency1 instanceof Intent.Emergency || callEmergency1 instanceof Intent.CallContact);

        Intent callEmergency2 = classifier.classify("emergency");
        assertTrue(callEmergency2 instanceof Intent.Emergency);

        // 2. Orchestrator registration and dialing flow
        final java.util.concurrent.atomic.AtomicBoolean smsFired = new java.util.concurrent.atomic.AtomicBoolean(false);
        final String[] dialTarget = new String[1];

        com.aura.infrastructure.SmsAdapter adapter = new com.aura.infrastructure.SmsAdapter(null) {
            @Override
            public com.aura.models.ActionResult send(String phoneNumber, String body) {
                smsFired.set(true);
                dialTarget[0] = phoneNumber;
                return new com.aura.models.ActionResult(true, com.aura.models.Platform.SMS, "Sent");
            }
        };

        com.aura.service.memory.PersonalMemoryService memory = new com.aura.service.memory.PersonalMemoryService(null);
        com.aura.service.safety.GuardianAlertManager guardianManager =
                new com.aura.service.safety.GuardianAlertManager(adapter, null, memory);

        com.aura.core.JavaActionOrchestrator orchestrator = new com.aura.core.JavaActionOrchestrator(
                null, null, null, null, null, memory, null, adapter, null
        );
        orchestrator.setGuardianAlertManager(guardianManager);

        // User sets emergency contact by voice
        com.aura.models.ActionResult regResult = orchestrator.executeAction(rem1);
        assertTrue(regResult.isSuccessful());
        assertTrue(regResult.getDetail().contains("Emergency contact registered"));

        // Verify second PersonalMemoryService instance shares the exact memory synchronously
        com.aura.service.memory.PersonalMemoryService memoryInstance2 = new com.aura.service.memory.PersonalMemoryService(null);
        assertNotNull(memoryInstance2.getRawValue("emergency contact"));
        assertEquals("9876543210", memoryInstance2.getRawValue("emergency number"));
        assertEquals("9876543210", guardianManager.resolveGuardianPhoneNumber());

        // User says "emergency" -> AURA alerts and calls Rahul at 9876543210
        com.aura.models.ActionResult emResult = orchestrator.executeAction(new Intent.Emergency());
        assertTrue(emResult.isSuccessful());
        assertTrue(smsFired.get());
        assertEquals("9876543210", dialTarget[0]);
        assertTrue(emResult.getDetail().contains("distress SMS with your live location has been sent"));
        assertFalse(emResult.getDetail().contains("No personal guardian was found"));

        // User says "call my emergency contact" -> Routes and dials registered contact
        smsFired.set(false);
        com.aura.models.ActionResult callResult = orchestrator.executeAction(new Intent.CallContact("emergency contact"));
        assertTrue(callResult.isSuccessful());
        assertTrue(smsFired.get());
        assertEquals("9876543210", dialTarget[0]);

        // Bracketed format [name/number] test
        Intent setBracket = classifier.classify("my emergency contact is [Rahul/9876543210]");
        assertTrue(setBracket instanceof Intent.Remember);
        Intent.Remember remBracket = (Intent.Remember) setBracket;
        com.aura.models.ActionResult bracketResult = orchestrator.executeAction(remBracket);
        assertTrue(bracketResult.isSuccessful());
        assertTrue(bracketResult.getDetail().contains("Emergency contact registered as Rahul"));

        // "it's an emergency" classification
        Intent itsEmergency = classifier.classify("it's an emergency");
        assertTrue(itsEmergency instanceof Intent.Emergency);
        Intent itsEmergencyClean = classifier.classify("it s an emergency");
        assertTrue(itsEmergencyClean instanceof Intent.Emergency);
    }
}



