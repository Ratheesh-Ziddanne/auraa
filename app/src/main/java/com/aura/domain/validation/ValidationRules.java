package com.aura.domain.validation;

import com.aura.models.Contact;

public final class ValidationRules {
    private ValidationRules() {}

    public static boolean message(String body) {
        return body != null && !body.trim().isEmpty();
    }

    public static boolean contact(Contact contact) {
        if (contact == null) return false;
        boolean hasPhone = contact.getPhoneNumber() != null && !contact.getPhoneNumber().trim().isEmpty();
        boolean hasEmail = contact.getEmail() != null && !contact.getEmail().trim().isEmpty();
        return hasPhone || hasEmail;
    }

    public static boolean smsWarning(String body) {
        return body != null && body.length() > 160;
    }
}
