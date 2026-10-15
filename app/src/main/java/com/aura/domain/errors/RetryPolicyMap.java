package com.aura.domain.errors;

public final class RetryPolicyMap {
    private RetryPolicyMap() {}

    public static class RetryPolicy {
        private final String spokenMessage;
        private final int maxRetries;

        public RetryPolicy(String spokenMessage, int maxRetries) {
            this.spokenMessage = spokenMessage;
            this.maxRetries = maxRetries;
        }

        public String getSpokenMessage() { return spokenMessage; }
        public int getMaxRetries() { return maxRetries; }
    }

    public static RetryPolicy policy(AuraError error) {
        if (error instanceof AuraError.STTFailureError) {
            return new RetryPolicy("I didn't quite catch that. Please speak again.", 2);
        } else if (error instanceof AuraError.PlatformFailure) {
            String platform = ((AuraError.PlatformFailure) error).getPlatformName();
            return new RetryPolicy("Failed to communicate via " + platform + ". Would you like to retry?", 1);
        } else if (error instanceof AuraError.ContactNotFound) {
            String query = ((AuraError.ContactNotFound) error).getQuery();
            return new RetryPolicy("I couldn't find contact " + query + " in your contacts.", 0);
        } else if (error instanceof AuraError.NetworkUnavailableError) {
            return new RetryPolicy("Network is currently unavailable. Please check your connection.", 1);
        } else if (error instanceof AuraError.TimeoutError) {
            return new RetryPolicy("The request timed out. Please try again.", 1);
        }
        return new RetryPolicy("An unexpected error occurred. Please try again.", 1);
    }
}
