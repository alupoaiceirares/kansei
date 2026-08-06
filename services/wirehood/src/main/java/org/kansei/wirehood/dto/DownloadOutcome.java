package org.kansei.wirehood.dto;

public enum DownloadOutcome {
    /** Track was already READY - added straight to the requester's library, no worker call */
    ALREADY_READY,
    /** Someone else already triggered this download - requester is now tracked in download_requests too */
    ALREADY_PENDING,
    /** New track (or a retry of a previously FAILED one) - inserted PENDING and queued for the worker */
    QUEUED
}
