package com.hashverify.model;

/**
 * Enumeration representing the status of consensus calculation
 */
public enum ConsensusStatus {
    /**
     * Consensus has been established with sufficient confidence
     */
    ESTABLISHED,
    
    /**
     * Insufficient data to establish consensus (< 3 submissions)
     */
    INSUFFICIENT_DATA,
    
    /**
     * No clear consensus among submissions (< 70% agreement)
     */
    NO_CONSENSUS,
    
    /**
     * Consensus has expired due to lack of recent submissions (> 90 days)
     */
    EXPIRED
}