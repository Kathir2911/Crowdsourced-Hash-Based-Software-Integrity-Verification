package com.hashverify.controller;

import com.hashverify.config.ConsensusConfigurationProperties;
import com.hashverify.model.ConsensusResult;
import com.hashverify.service.ConsensusService;
import com.hashverify.service.SubmissionStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Administrative API controller for system configuration and statistics
 * Implements requirements 9.4, 9.6, 11.4
 */
@RestController
@RequestMapping("/admin")
public class AdminController {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);
    
    private final ConsensusConfigurationProperties configProperties;
    private final ConsensusService consensusService;
    private final SubmissionStorageService submissionStorageService;
    private final com.hashverify.service.DatabaseCleanupService databaseCleanupService;
    private final com.hashverify.monitoring.PerformanceMonitor performanceMonitor;
    
    public AdminController(ConsensusConfigurationProperties configProperties,
                          ConsensusService consensusService,
                          SubmissionStorageService submissionStorageService,
                          com.hashverify.service.DatabaseCleanupService databaseCleanupService,
                          com.hashverify.monitoring.PerformanceMonitor performanceMonitor) {
        this.configProperties = configProperties;
        this.consensusService = consensusService;
        this.submissionStorageService = submissionStorageService;
        this.databaseCleanupService = databaseCleanupService;
        this.performanceMonitor = performanceMonitor;
    }
    
    /**
     * Get current system configuration
     * Requirement 9.4: Provide administrative API for viewing consensus statistics
     * 
     * @return Current configuration settings
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfiguration() {
        logger.info("Admin request: Get system configuration");
        
        Map<String, Object> config = new HashMap<>();
        
        // Consensus configuration
        Map<String, Object> consensus = new HashMap<>();
        consensus.put("thresholdPercentage", configProperties.getConsensus().getThresholdPercentage());
        consensus.put("minimumSubmissions", configProperties.getConsensus().getMinimumSubmissions());
        consensus.put("confidenceHighThreshold", configProperties.getConsensus().getConfidenceHighThreshold());
        consensus.put("confidenceMinimumSubmissions", configProperties.getConsensus().getConfidenceMinimumSubmissions());
        consensus.put("cacheTtlHours", configProperties.getConsensus().getCacheTtlHours());
        config.put("consensus", consensus);
        
        // Retention configuration
        Map<String, Object> retention = new HashMap<>();
        retention.put("submissionTtlDays", configProperties.getRetention().getSubmissionTtlDays());
        config.put("retention", retention);
        
        // Performance configuration
        Map<String, Object> performance = new HashMap<>();
        performance.put("maxConcurrentRequests", configProperties.getPerformance().getMaxConcurrentRequests());
        performance.put("apiTimeoutSeconds", configProperties.getPerformance().getApiTimeoutSeconds());
        performance.put("hashComputationTimeoutSeconds", configProperties.getPerformance().getHashComputationTimeoutSeconds());
        config.put("performance", performance);
        
        // Rate limiting configuration
        Map<String, Object> rateLimiting = new HashMap<>();
        rateLimiting.put("submissionsPerIpPerHour", configProperties.getRateLimiting().getSubmissionsPerIpPerHour());
        rateLimiting.put("verificationsPerIpPerMinute", configProperties.getRateLimiting().getVerificationsPerIpPerMinute());
        rateLimiting.put("adminApiRequestsPerMinute", configProperties.getRateLimiting().getAdminApiRequestsPerMinute());
        config.put("rateLimiting", rateLimiting);
        
        // Security configuration (excluding sensitive data)
        Map<String, Object> security = new HashMap<>();
        security.put("requireHttps", configProperties.getSecurity().getRequireHttps());
        security.put("corsAllowedOrigins", configProperties.getSecurity().getCorsAllowedOrigins());
        config.put("security", security);
        
        // Replay protection configuration
        Map<String, Object> replayProtection = new HashMap<>();
        replayProtection.put("timeWindowHours", configProperties.getReplayProtection().getTimeWindowHours());
        replayProtection.put("hashAlgorithm", configProperties.getReplayProtection().getHashAlgorithm());
        config.put("replayProtection", replayProtection);
        
        return ResponseEntity.ok(config);
    }
    
    /**
     * Get consensus statistics for a specific software identity
     * Requirement 9.4: Provide administrative API for viewing consensus statistics
     * Requirement 11.4: Format Hash_Registry data into human-readable JSON
     * 
     * @param identityHash The software identity hash
     * @return Consensus statistics in human-readable JSON format
     */
    @GetMapping("/consensus/{identityHash}")
    public ResponseEntity<Map<String, Object>> getConsensusStatistics(@PathVariable String identityHash) {
        logger.info("Admin request: Get consensus statistics for identity hash: {}", identityHash);
        
        ConsensusResult consensus = consensusService.calculateConsensus(identityHash);
        
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("softwareIdentityHash", identityHash);
        statistics.put("consensusHash", consensus.getConsensusHash());
        statistics.put("confidence", String.format("%.2f%%", consensus.getConfidence() * 100));
        statistics.put("submissionCount", consensus.getSubmissionCount());
        statistics.put("status", consensus.getStatus().toString());
        statistics.put("lifecycle", consensus.getLifecycle().toString());
        statistics.put("lastUpdated", consensus.getLastUpdated());
        statistics.put("oldestSubmission", consensus.getOldestSubmission());
        statistics.put("newestSubmission", consensus.getNewestSubmission());
        
        // Format hash distribution in human-readable format
        Map<String, String> hashDistribution = new HashMap<>();
        if (consensus.getHashDistribution() != null) {
            consensus.getHashDistribution().forEach((hash, count) -> {
                double percentage = (double) count / consensus.getSubmissionCount() * 100;
                hashDistribution.put(hash, String.format("%d submissions (%.2f%%)", count, percentage));
            });
        }
        statistics.put("hashDistribution", hashDistribution);
        
        return ResponseEntity.ok(statistics);
    }
    
    /**
     * Get overall system statistics
     * Requirement 9.4: Provide administrative API for viewing consensus statistics
     * Requirement 11.4: Format Hash_Registry data into human-readable JSON
     * 
     * @return System-wide statistics in human-readable JSON format
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getSystemStatistics() {
        logger.info("Admin request: Get system statistics");
        
        Map<String, Object> statistics = new HashMap<>();
        
        // Get total submission count
        long totalSubmissions = submissionStorageService.getTotalSubmissionCount();
        statistics.put("totalSubmissions", totalSubmissions);
        
        // Get submission count by time period
        LocalDateTime now = LocalDateTime.now();
        long submissionsLast24Hours = submissionStorageService.getSubmissionCountSince(now.minusHours(24));
        long submissionsLast7Days = submissionStorageService.getSubmissionCountSince(now.minusDays(7));
        long submissionsLast30Days = submissionStorageService.getSubmissionCountSince(now.minusDays(30));
        
        Map<String, Long> submissionsByPeriod = new HashMap<>();
        submissionsByPeriod.put("last24Hours", submissionsLast24Hours);
        submissionsByPeriod.put("last7Days", submissionsLast7Days);
        submissionsByPeriod.put("last30Days", submissionsLast30Days);
        statistics.put("submissionsByPeriod", submissionsByPeriod);
        
        // Get unique software identities count
        long uniqueSoftwareIdentities = submissionStorageService.getUniqueSoftwareIdentityCount();
        statistics.put("uniqueSoftwareIdentities", uniqueSoftwareIdentities);
        
        // System configuration summary
        Map<String, Object> configSummary = new HashMap<>();
        configSummary.put("consensusThreshold", String.format("%.0f%%", configProperties.getConsensus().getThresholdPercentage() * 100));
        configSummary.put("minimumSubmissions", configProperties.getConsensus().getMinimumSubmissions());
        configSummary.put("retentionPeriod", configProperties.getRetention().getSubmissionTtlDays() + " days");
        statistics.put("configuration", configSummary);
        
        // Timestamp
        statistics.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(statistics);
    }
    
    /**
     * Clean up expired data
     * Requirement 9.4: Administrative functionality
     * Requirement 4.7: Exclude submissions older than 90 days
     * 
     * @return Cleanup results
     */
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupExpiredData() {
        logger.info("Admin request: Clean up expired data");
        
        // Perform manual cleanup using DatabaseCleanupService
        int deletedSubmissions = databaseCleanupService.performManualCleanup();
        int expiredCache = consensusService.cleanupExpiredCache();
        
        Map<String, Object> result = new HashMap<>();
        result.put("expiredSubmissionsDeleted", deletedSubmissions);
        result.put("expiredCacheEntriesDeleted", expiredCache);
        result.put("timestamp", LocalDateTime.now());
        
        logger.info("Cleanup completed: {} submissions, {} cache entries deleted", 
                   deletedSubmissions, expiredCache);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Get database health and capacity status
     * Requirement 8.3: Hash_Registry shall support minimum 1 million hash submissions
     * Requirement 9.4: Administrative functionality
     * 
     * @return Database health and capacity information
     */
    @GetMapping("/database/health")
    public ResponseEntity<Map<String, Object>> getDatabaseHealth() {
        logger.info("Admin request: Get database health");
        
        Map<String, Object> health = new HashMap<>();
        
        // Get submission counts
        long totalSubmissions = databaseCleanupService.getTotalSubmissionCount();
        long expiredSubmissions = databaseCleanupService.countExpiredSubmissions();
        long activeSubmissions = totalSubmissions - expiredSubmissions;
        
        health.put("totalSubmissions", totalSubmissions);
        health.put("activeSubmissions", activeSubmissions);
        health.put("expiredSubmissions", expiredSubmissions);
        
        // Calculate capacity metrics (Requirement 8.3: minimum 1M submissions)
        double capacityUsedPercent = (totalSubmissions / 1_000_000.0) * 100;
        health.put("capacityUsedPercent", String.format("%.2f%%", capacityUsedPercent));
        health.put("capacityTarget", "1,000,000 submissions");
        
        // Check health status
        boolean isHealthy = databaseCleanupService.checkDatabaseHealth();
        health.put("status", isHealthy ? "HEALTHY" : "WARNING");
        
        // Add warnings if needed
        if (totalSubmissions > 900_000) {
            health.put("warning", "Database approaching capacity limit");
        }
        if (expiredSubmissions > 100_000) {
            health.put("warning", "Large number of expired submissions - consider running cleanup");
        }
        
        health.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * Get performance metrics
     * Requirement 8.2: Backend_Server shall respond to verification requests within 200 milliseconds
     * Requirement 9.4: Administrative functionality
     * 
     * @return Performance metrics
     */
    @GetMapping("/performance")
    public ResponseEntity<Map<String, Object>> getPerformanceMetrics() {
        logger.info("Admin request: Get performance metrics");
        
        Map<String, Object> metrics = new HashMap<>();
        
        // Overall metrics
        metrics.put("totalRequests", performanceMonitor.getTotalRequests());
        metrics.put("slowRequests", performanceMonitor.getSlowRequests());
        metrics.put("performanceTargetMet", String.format("%.2f%%", performanceMonitor.getPerformanceTargetPercentage()));
        metrics.put("responseTimeTarget", "200ms");
        
        // Endpoint-specific metrics
        Map<String, Map<String, Object>> endpointMetrics = new HashMap<>();
        
        // Check verification endpoint
        var verifyStats = performanceMonitor.getEndpointStats("POST /api/v1/verify");
        if (verifyStats != null) {
            Map<String, Object> verifyMetrics = new HashMap<>();
            verifyMetrics.put("averageResponseTime", verifyStats.getAverageResponseTime() + "ms");
            verifyMetrics.put("minResponseTime", verifyStats.getMinResponseTime() + "ms");
            verifyMetrics.put("maxResponseTime", verifyStats.getMaxResponseTime() + "ms");
            verifyMetrics.put("requestCount", verifyStats.getRequestCount());
            endpointMetrics.put("POST /api/v1/verify", verifyMetrics);
        }
        
        // Check submission endpoint
        var submitStats = performanceMonitor.getEndpointStats("POST /api/v1/submissions");
        if (submitStats != null) {
            Map<String, Object> submitMetrics = new HashMap<>();
            submitMetrics.put("averageResponseTime", submitStats.getAverageResponseTime() + "ms");
            submitMetrics.put("minResponseTime", submitStats.getMinResponseTime() + "ms");
            submitMetrics.put("maxResponseTime", submitStats.getMaxResponseTime() + "ms");
            submitMetrics.put("requestCount", submitStats.getRequestCount());
            endpointMetrics.put("POST /api/v1/submissions", submitMetrics);
        }
        
        metrics.put("endpointMetrics", endpointMetrics);
        metrics.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(metrics);
    }
    
    /**
     * Get health status
     * Requirement 9.4: Administrative functionality
     * 
     * @return System health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        logger.debug("Admin request: Get health status");
        
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        
        // Check database connectivity
        try {
            long count = submissionStorageService.getTotalSubmissionCount();
            health.put("database", "UP");
            health.put("databaseRecordCount", count);
        } catch (Exception e) {
            health.put("database", "DOWN");
            health.put("databaseError", e.getMessage());
            logger.error("Database health check failed", e);
        }
        
        return ResponseEntity.ok(health);
    }
}
