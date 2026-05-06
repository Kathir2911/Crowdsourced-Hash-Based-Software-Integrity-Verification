package com.hashverify.model;

/**
 * Enumeration representing the tamper detection status of a file
 */
public enum TamperStatus {
    /**
     * File hash matches consensus with high confidence
     */
    VERIFIED,
    
    /**
     * File hash differs from established consensus
     */
    TAMPERED,
    
    /**
     * Consensus exists but confidence is weak
     */
    SUSPICIOUS_LOW_CONFIDENCE,
    
    /**
     * No consensus data available for this software identity
     */
    UNKNOWN,
    
    /**
     * Verification service is unavailable
     */
    SERVICE_UNAVAILABLE
}