package com.aura.domain.errors;

public abstract class AuraError {
    public static final AuraError STTFailure = new STTFailureError();
    public static final AuraError NetworkUnavailable = new NetworkUnavailableError();
    public static final AuraError Timeout = new TimeoutError();

    public static class STTFailureError extends AuraError {
        @Override
        public String toString() { return "STTFailure"; }
    }

    public static class NetworkUnavailableError extends AuraError {
        @Override
        public String toString() { return "NetworkUnavailable"; }
    }

    public static class TimeoutError extends AuraError {
        @Override
        public String toString() { return "Timeout"; }
    }

    public static class PlatformFailure extends AuraError {
        private final String platformName;
        public PlatformFailure(String platformName) {
            this.platformName = platformName != null ? platformName : "Unknown";
        }
        public String getPlatformName() { return platformName; }
        @Override
        public String toString() { return "PlatformFailure(" + platformName + ")"; }
    }

    public static class ContactNotFound extends AuraError {
        private final String query;
        public ContactNotFound(String query) { this.query = query; }
        public String getQuery() { return query; }
        @Override
        public String toString() { return "ContactNotFound(" + query + ")"; }
    }

    public static class PermissionDenied extends AuraError {
        private final String permission;
        public PermissionDenied(String permission) { this.permission = permission; }
        public String getPermission() { return permission; }
        @Override
        public String toString() { return "PermissionDenied(" + permission + ")"; }
    }
}
