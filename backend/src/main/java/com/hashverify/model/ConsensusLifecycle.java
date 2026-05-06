package com.hashverify.model;

/**
 * Enumeration representing the lifecycle state of consensus for a software identity
 */
public enum ConsensusLifecycle {
    /**
     * No submissions have been received for this software identity
     */
    UNKNOWN,
    
    /**
     * Fewer than 3 submissions or less than 70% agreement among submissions
     */
    LOW_CONFIDENCE,
    
    /**
     * 3 or more submissions with 70% or greater agreement on a single hash
     */
    ESTABLISHED,
    
    /**
     * Previously established consensus now has conflicting submissions that reduce confidence
     */
    DEGRADED,
    
    /**
     * No submissions received within the retention period (90 days)
     */
    EXPIRED
}