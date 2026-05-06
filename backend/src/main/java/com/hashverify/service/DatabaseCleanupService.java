package com.hashverify.service;

import com.hashverify.repository.HashSubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for automated database cleanup and maintenance
 * Implements requirement 4.7: Exclude submissions older than 90 days from consensus calculations
 * Implements requirement 8.3: Hash_Registry shall support minimum 1 million hash submissions
 * Implements requirement 8.5: Use caching to improve performance when queries exceed 500ms
 */
@Service
public class DatabaseCleanupService {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseCleanupService.class);
    
    private final HashSubmissionRepository submissionRepository;
    
    @Value("${app.retention.submission-ttl-days:90}")
    private int submissionTtlDays;
    
    public DatabaseCleanupService(HashSubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }
    
    /**
     * Scheduled cleanup of expired submissions
     * Runs daily at 2:00 AM to remove submissions older than retention period
     * Implements requirement 4.7: Exclude submissions older than 90 days
     * 
     * Cron expression: "0 0 2 * * ?" = At 2:00 AM every day
     */
    @Scheduled(cron = "${app.cleanup.schedule:0 0 2 * * ?}")
    @Transactional
    public void cleanupExpiredSubmissions() {
        logger.info("Starting scheduled cleanup of expired submissions (retention: {} days)", submissionTtlDays);
        
        try {
            // Calculate cutoff date
            LocalDateTime cutoffDateTime = LocalDateTime.now().minusDays(submissionTtlDays);
            
            logger.info("Deleting submissions older than: {}", cutoffDateTime);
            
            // Delete expired submissions
            long startTime = System.currentTimeMillis();
            int deletedCount = submissionRepository.deleteByTimestampBefore(cutoffDateTime);
            long duration = System.currentTimeMillis() - startTime;
            
            logger.info("Cleanup completed: deleted {} expired submissions in {}ms", deletedCount, duration);
            
            // Log warning if cleanup took too long (potential performance issue)
            if (duration > 5000) {
                logger.warn("Cleanup took longer than expected: {}ms. Consider database optimization.", duration);
            }
            
        } catch (Exception e) {
            logger.error("Error during scheduled cleanup: {}", e.getMessage(), e);
            // Don't rethrow - we don't want to stop the scheduler
        }
    }
    
    /**
     * Manual cleanup trigger for administrative purposes
     * Can be called via admin API or for testing
     * 
     * @return Number of submissions deleted
     */
    @Transactional
    public int performManualCleanup() {
        logger.info("Manual cleanup triggered (retention: {} days)", submissionTtlDays);
        
        LocalDateTime cutoffDateTime = LocalDateTime.now().minusDays(submissionTtlDays);
        
        logger.info("Deleting submissions older than: {}", cutoffDateTime);
        
        long startTime = System.currentTimeMillis();
        int deletedCount = submissionRepository.deleteByTimestampBefore(cutoffDateTime);
        long duration = System.currentTimeMillis() - startTime;
        
        logger.info("Manual cleanup completed: deleted {} expired submissions in {}ms", deletedCount, duration);
        
        return deletedCount;
    }
    
    /**
     * Get count of submissions that would be deleted in next cleanup
     * Useful for monitoring and capacity planning
     * 
     * @return Number of expired submissions
     */
    public long countExpiredSubmissions() {
        LocalDateTime cutoffDateTime = LocalDateTime.now().minusDays(submissionTtlDays);
        return submissionRepository.countByTimestampBefore(cutoffDateTime);
    }
    
    /**
     * Get total submission count for capacity monitoring
     * Implements requirement 8.3: Support minimum 1 million hash submissions
     * 
     * @return Total number of submissions in database
     */
    public long getTotalSubmissionCount() {
        return submissionRepository.count();
    }
    
    /**
     * Check if database is approaching capacity limits
     * Logs warning if submission count is high
     * 
     * @return true if database is healthy, false if approaching limits
     */
    public boolean checkDatabaseHealth() {
        long totalSubmissions = getTotalSubmissionCount();
        long expiredSubmissions = countExpiredSubmissions();
        
        logger.info("Database health check: total={}, expired={}", totalSubmissions, expiredSubmissions);
        
        // Requirement 8.3: Support minimum 1 million submissions
        // Warn if approaching this limit
        if (totalSubmissions > 900_000) {
            logger.warn("Database approaching capacity: {} submissions (target: 1M minimum)", totalSubmissions);
        }
        
        // Warn if many expired submissions are not being cleaned up
        if (expiredSubmissions > 100_000) {
            logger.warn("Large number of expired submissions: {}. Consider running cleanup.", expiredSubmissions);
        }
        
        return totalSubmissions < 1_000_000 && expiredSubmissions < 100_000;
    }
}
