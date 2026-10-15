package com.aura.models;

import java.util.Objects;

public class Contact {
    private String id;
    private String displayName;
    private String phoneNumber;
    private String email;
    private int interactionFrequency;
    private long lastInteractionTimestamp;

    public Contact(String id, String displayName, String phoneNumber) {
        this(id, displayName, phoneNumber, null, 0, 0L);
    }

    public Contact(String id, String displayName, String phoneNumber, String email) {
        this(id, displayName, phoneNumber, email, 0, 0L);
    }

    public Contact(String id, String displayName, String phoneNumber, String email, int interactionFrequency, long lastInteractionTimestamp) {
        this.id = id;
        this.displayName = displayName;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.interactionFrequency = interactionFrequency;
        this.lastInteractionTimestamp = lastInteractionTimestamp;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getName() { return displayName; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getEmail() { return email; }
    public int getInteractionFrequency() { return interactionFrequency; }
    public long getLastInteractionTimestamp() { return lastInteractionTimestamp; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Contact contact = (Contact) o;
        return Objects.equals(id, contact.id) &&
               Objects.equals(displayName, contact.displayName) &&
               Objects.equals(phoneNumber, contact.phoneNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName, phoneNumber);
    }

    @Override
    public String toString() {
        return "Contact{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                '}';
    }
}
