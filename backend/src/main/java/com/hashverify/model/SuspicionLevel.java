package com.hashverify.model;

/**
 * Enumeration representing the suspicion level for verification results
 */
public enum SuspicionLevel {
    /**
     * No suspicion - file is verified with high confidence
     */
    NONE(0),
    
    /**
     * Low suspicion - minor confidence issues
     */
    LOW(1),
    
    /**
     * Medium suspicion - moderate confidence issues
     */
    MEDIUM(2),
    
    /**
     * High suspicion - significant confidence issues
     */
    HIGH(3),
    
    /**
     * Critical suspicion - file likely tampered
     */
    CRITICAL(4);
    
    private final int level;
    
    SuspicionLevel(int level) {
        this.level = level;
    }
    
    public int getLevel() {
        return level;
    }
    
    /**
     * Get suspicion level from numeric value
     */
    public static SuspicionLevel fromLevel(int level) {
        for (SuspicionLevel suspicionLevel : values()) {
            if (suspicionLevel.level == level) {
                return suspicionLevel;
            }
        }
        throw new IllegalArgumentException("Invalid suspicion level: " + level);
    }
}