package com.aura.service.contact;

import com.aura.models.Contact;
import org.junit.Before;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ContactResolverTest {

    private ContactResolver resolver;

    @Before
    public void setUp() {
        resolver = new ContactResolver(null);
    }

    @Test
    public void testExactMatchPrioritizedOverPartialMatches() {
        List<Contact> pool = Arrays.asList(
                new Contact("1", "Rahul Kumar", "9876500001"),
                new Contact("2", "Rahul", "9876500002"),
                new Contact("3", "Rahul Sharma", "9876500003")
        );

        List<Contact> results = resolver.find("Rahul", pool);
        assertEquals(1, results.size());
        assertEquals("Rahul", results.get(0).getDisplayName());
        assertEquals("9876500002", results.get(0).getPhoneNumber());
    }

    @Test
    public void testTokenFirstNameMatching() {
        List<Contact> pool = Arrays.asList(
                new Contact("1", "John Doe", "+15551234567"),
                new Contact("2", "Alice Smith", "+15559876543")
        );

        List<Contact> results = resolver.find("John", pool);
        assertEquals(1, results.size());
        assertEquals("John Doe", results.get(0).getDisplayName());
    }

    @Test
    public void testTokenLastNameMatching() {
        List<Contact> pool = Arrays.asList(
                new Contact("1", "John Doe", "+15551234567"),
                new Contact("2", "Alice Smith", "+15559876543")
        );

        List<Contact> results = resolver.find("Smith", pool);
        assertEquals(1, results.size());
        assertEquals("Alice Smith", results.get(0).getDisplayName());
    }

    @Test
    public void testBidirectionalAliasesMother() {
        List<Contact> pool = Arrays.asList(
                new Contact("1", "Mom", "+919876543210"),
                new Contact("2", "Office", "+919876543211")
        );

        // Query "mother" should match "Mom"
        List<Contact> resMother = resolver.find("mother", pool);
        assertEquals(1, resMother.size());
        assertEquals("Mom", resMother.get(0).getDisplayName());

        // Query "amma" should match "Mom"
        List<Contact> resAmma = resolver.find("amma", pool);
        assertEquals(1, resAmma.size());
        assertEquals("Mom", resAmma.get(0).getDisplayName());

        // Query "mom" should also match "Mom"
        List<Contact> resMom = resolver.find("mom", pool);
        assertEquals(1, resMom.size());
        assertEquals("Mom", resMom.get(0).getDisplayName());
    }

    @Test
    public void testBidirectionalAliasesFatherAndGuardian() {
        List<Contact> pool = Arrays.asList(
                new Contact("1", "Father", "+919876543210"),
                new Contact("2", "Caretaker Ravi", "+919876543211")
        );

        // Query "dad" matches "Father"
        List<Contact> resDad = resolver.find("dad", pool);
        assertEquals(1, resDad.size());
        assertEquals("Father", resDad.get(0).getDisplayName());

        // Query "appa" matches "Father"
        List<Contact> resAppa = resolver.find("appa", pool);
        assertEquals(1, resAppa.size());
        assertEquals("Father", resAppa.get(0).getDisplayName());

        // Query "guardian" matches "Caretaker Ravi"
        List<Contact> resGuard = resolver.find("guardian", pool);
        assertEquals(1, resGuard.size());
        assertEquals("Caretaker Ravi", resGuard.get(0).getDisplayName());
    }

    @Test
    public void testPhoneNumberSuffixMatching() {
        List<Contact> pool = Arrays.asList(
                new Contact("1", "Priya", "+919876543210"),
                new Contact("2", "Ravi", "+919123456789")
        );

        // Query with local 10 digits matches contact with +91 country code
        List<Contact> results = resolver.find("9876543210", pool);
        assertEquals(1, results.size());
        assertEquals("Priya", results.get(0).getDisplayName());

        // Query with international format matches
        List<Contact> resultsIntl = resolver.find("+919123456789", pool);
        assertEquals(1, resultsIntl.size());
        assertEquals("Ravi", resultsIntl.get(0).getDisplayName());
    }

    @Test
    public void testSanitizePhoneNumber() {
        assertEquals("+15551234567", ContactResolver.sanitizePhoneNumber("+1 (555) 123-4567"));
        assertEquals("9876543210", ContactResolver.sanitizePhoneNumber("98765 43210"));
        assertEquals("+919876543210", ContactResolver.sanitizePhoneNumber("+91-98765-43210"));
    }

    @Test
    public void testPhoneticMatching() {
        List<Contact> pool = Arrays.asList(
                new Contact("1", "Philip", "+15550001"),
                new Contact("2", "Sarah", "+15550002")
        );

        List<Contact> resPhilip = resolver.find("Filip", pool);
        assertEquals(1, resPhilip.size());
        assertEquals("Philip", resPhilip.get(0).getDisplayName());

        List<Contact> resSarah = resolver.find("Sara", pool);
        assertEquals(1, resSarah.size());
        assertEquals("Sarah", resSarah.get(0).getDisplayName());
    }

    @Test
    public void testDisambiguationResolution() {
        List<Contact> rahuls = Arrays.asList(
                new Contact("1", "Rahul Kumar", "9876500001"),
                new Contact("2", "Rahul Sharma", "9876500002"),
                new Contact("3", "Rahul Verma", "9876500003")
        );

        String prompt = resolver.formatDisambiguationPrompt("Rahul", rahuls);
        assertTrue(prompt.contains("multiple contacts matching Rahul"));
        assertTrue(prompt.contains("Rahul Kumar"));
        assertTrue(prompt.contains("Rahul Sharma"));
        assertTrue(prompt.contains("Rahul Verma"));

        Contact chosen1 = resolver.resolveSelection("the first one", rahuls);
        assertNotNull(chosen1);
        assertEquals("Rahul Kumar", chosen1.getDisplayName());

        Contact chosen2 = resolver.resolveSelection("second", rahuls);
        assertNotNull(chosen2);
        assertEquals("Rahul Sharma", chosen2.getDisplayName());

        Contact chosen3 = resolver.resolveSelection("option 3", rahuls);
        assertNotNull(chosen3);
        assertEquals("Rahul Verma", chosen3.getDisplayName());

        Contact chosenName = resolver.resolveSelection("Rahul Sharma", rahuls);
        assertNotNull(chosenName);
        assertEquals("Rahul Sharma", chosenName.getDisplayName());
    }
}
