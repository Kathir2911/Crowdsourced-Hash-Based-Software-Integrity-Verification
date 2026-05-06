package com.hashverify.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service for replay protection mechanism
 * Implements requirements 4.4, 4.7
 * 
 * Generates replay protection hashes to prevent duplicate submissions
 * from the same client within 24-hour windows
 */
@Service
public class ReplayProtectionService {
    
    private static final Logger logger = LoggerFactory.getLogger(ReplayProtectionService.class);
    private static final DateTimeFormatter TIME_WINDOW_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH");
    private static final int TIME_WINDOW_HOURS = 24;
    
    private final SubmissionStorageService submissionStorageService;
    
    public ReplayProtectionService(SubmissionStorageService submissionStorageService) {
        this.submissionStorageService = submissionStorageService;
    }
    
    /**
     * Generate replay protection hash from IP address, User Agent, and time window
     * 
     * Time window groups submissions into 24-hour periods to allow one submission
     * per client per day per software identity
     * 
     * @param ipAddress Client IP address
     * @param userAgent Client User-Agent string
     * @param timestamp Submission timestamp
     * @return SHA-256 hash of combined inputs
     */
    public String generateReplayProtectionHash(String ipAddress, String userAgent, LocalDateTime timestamp) {
        try {
            // Normalize inputs
            String normalizedIp = ipAddress != null ? ipAddress.trim() : "";
            String normalizedUserAgent = userAgent != null ? userAgent.trim() : "";
            
            // Calculate time window (24-hour bucket)
            String timeWindow = calculateTimeWindow(timestamp);
            
            // Combine inputs: IP + UserAgent + TimeWindow
            String combined = normalizedIp + normalizedUserAgent + timeWindow;
            
            // Generate SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            
            // Convert to hexadecimal string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            String replayHash = hexString.toString();
            logger.debug("Generated replay protection hash for time window: {}", timeWindow);
            
            return replayHash;
            
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not available: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate replay protection hash", e);
        }
    }
    
    /**
     * Calculate time window for replay protection
     * Groups timestamps into 24-hour periods
     * 
     * @param timestamp The submission timestamp
     * @return Time window string (e.g., "2024-01-15-00" for Jan 15, 2024)
     */
    private String calculateTimeWindow(LocalDateTime timestamp) {
        // Truncate to the start of the day (00:00:00)
        LocalDateTime windowStart = timestamp.toLocalDate().atStartOfDay();
        return windowStart.format(TIME_WINDOW_FORMATTER);
    }
    
    /**
     * Check if a submission is a duplicate within the replay protection window
     * 
     * @param replayProtectionHash The replay protection hash
     * @param softwareIdentityHash The software identity hash
     * @return true if duplicate exists, false otherwise
     */
    public boolean isDuplicateSubmission(String replayProtectionHash, String softwareIdentityHash) {
        try {
            boolean isDuplicate = submissionStorageService.isDuplicateSubmission(
                    replayProtectionHash, softwareIdentityHash);
            
            if (isDuplicate) {
                logger.warn("Duplicate submission detected for software identity: {} with replay hash: {}", 
                           softwareIdentityHash, replayProtectionHash);
            }
            
            return isDuplicate;
            
        } catch (Exception e) {
            logger.error("Failed to check for duplicate submission: {}", e.getMessage(), e);
            // In case of error, allow the submission to proceed (fail open)
            return false;
        }
    }
    
    /**
     * Validate that a submission is within the acceptable time range
     * Submissions older than the retention period should be rejected
     * 
     * @param timestamp The submission timestamp
     * @param retentionDays The retention period in days (default 90)
     * @return true if submission is within retention period, false otherwise
     */
    public boolean isWithinRetentionPeriod(LocalDateTime timestamp, int retentionDays) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        boolean isValid = timestamp.isAfter(cutoffDate);
        
        if (!isValid) {
            logger.warn("Submission timestamp {} is older than retention period of {} days", 
                       timestamp, retentionDays);
        }
        
        return isValid;
    }
    
    /**
     * Filter submissions to only include those within the retention period
     * Used by consensus calculation to exclude expired submissions
     * 
     * @param timestamp The submission timestamp
     * @param retentionDays The retention period in days
     * @return true if submission should be included in consensus, false otherwise
     */
    public boolean shouldIncludeInConsensus(LocalDateTime timestamp, int retentionDays) {
        return isWithinRetentionPeriod(timestamp, retentionDays);
    }
}
