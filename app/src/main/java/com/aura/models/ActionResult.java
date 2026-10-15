package com.aura.models;

public class ActionResult {
    public enum ExecutionStatus {
        SUCCESS,
        FAILED,
        CANCELLED,
        PERMISSION_REQUIRED,
        AMBIGUOUS,
        UNSUPPORTED
    }

    private final boolean successful;
    private final ExecutionStatus status;
    private final Platform platform;
    private final String detail;

    public ActionResult(boolean successful, Platform platform, String detail) {
        this.successful = successful;
        this.status = successful ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED;
        this.platform = platform;
        this.detail = detail;
    }

    public ActionResult(ExecutionStatus status, Platform platform, String detail) {
        this.successful = (status == ExecutionStatus.SUCCESS);
        this.status = status;
        this.platform = platform;
        this.detail = detail;
    }

    public boolean isSuccessful() { return successful; }
    public ExecutionStatus getStatus() { return status; }
    public Platform getPlatform() { return platform; }
    public String getDetail() { return detail; }
}
