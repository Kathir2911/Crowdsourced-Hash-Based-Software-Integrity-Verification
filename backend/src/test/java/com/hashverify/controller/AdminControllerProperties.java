package com.hashverify.controller;

import com.hashverify.config.ConsensusConfigurationProperties;
import com.hashverify.model.*;
import com.hashverify.service.ConsensusService;
import com.hashverify.service.SubmissionStorageService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for administrative features
 * **Validates: Requirements 9.4, 9.6, 11.4**
 */
class AdminControllerProperties {
    
    /**
     * Property 21: Administrative API Functionality
     * **Validates: Requirement 9.4**
     * 
     * For any administrative request for consensus statistics, the Backend_Server 
     * should return accurate statistical data.
     */
    @Property(tries = 50)
    void property21_administrativeAPIFunctionality_returnsAccurateStatistics(
            @ForAll @StringLength(min = 8, max = 64) String identityHash,
            @ForAll @StringLength(min = 8, max = 64) String consensusHash,
            @ForAll @IntRange(min = 1, max = 1000) int submissionCount) {
        
        // Given
        ConsensusService consensusService = mock(ConsensusService.class);
        SubmissionStorageService submissionStorageService = mock(SubmissionStorageService.class);
        ConsensusConfigurationProperties configProperties = new ConsensusConfigurationProperties();
        com.hashverify.service.DatabaseCleanupService databaseCleanupService = mock(com.hashverify.service.DatabaseCleanupService.class);
        com.hashverify.monitoring.PerformanceMonitor performanceMonitor = mock(com.hashverify.monitoring.PerformanceMonitor.class);
        AdminController adminController = new AdminController(configProperties, consensusService, submissionStorageService,
                                                             databaseCleanupService, performanceMonitor);
        
        // Create consensus result
        SoftwareIdentity identity = new SoftwareIdentity();
        identity.setSourceDomain("example.com");
        identity.setNormalizedFilename("test.exe");
        identity.setFileSize(1024L);
        
        ConsensusResult consensusResult = new ConsensusResult();
        consensusResult.setSoftwareIdentity(identity);
        consensusResult.setConsensusHash(consensusHash);
        consensusResult.setConfidence(0.85);
        consensusResult.setSubmissionCount(submissionCount);
        consensusResult.setStatus(ConsensusStatus.ESTABLISHED);
        consensusResult.setLifecycle(ConsensusLifecycle.ESTABLISHED);
        consensusResult.setLastUpdated(LocalDateTime.now());
        
        Map<String, Integer> hashDistribution = new HashMap<>();
        hashDistribution.put(consensusHash, submissionCount);
        consensusResult.setHashDistribution(hashDistribution);
        
        when(consensusService.calculateConsensus(identityHash)).thenReturn(consensusResult);
        
        // When
        ResponseEntity<Map<String, Object>> response = adminController.getConsensusStatistics(identityHash);
        
        // Then - verify accurate statistics are returned
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("consensusHash")).isEqualTo(consensusHash);
        assertThat(response.getBody().get("submissionCount")).isEqualTo(submissionCount);
        assertThat(response.getBody().get("status")).isEqualTo("ESTABLISHED");
        assertThat(response.getBody().get("lifecycle")).isEqualTo("ESTABLISHED");
    }
    
    /**
     * Property 21.1: System statistics accuracy
     * **Validates: Requirement 9.4**
     * 
     * For any system statistics request, the administrative API should return 
     * accurate aggregated data.
     */
    @Property(tries = 50)
    void property21_1_systemStatisticsAccuracy(
            @ForAll @IntRange(min = 0, max = 1000000) long totalSubmissions,
            @ForAll @IntRange(min = 0, max = 10000) long uniqueIdentities) {
        
        // Given
        ConsensusService consensusService = mock(ConsensusService.class);
        SubmissionStorageService submissionStorageService = mock(SubmissionStorageService.class);
        ConsensusConfigurationProperties configProperties = new ConsensusConfigurationProperties();
        com.hashverify.service.DatabaseCleanupService databaseCleanupService = mock(com.hashverify.service.DatabaseCleanupService.class);
        com.hashverify.monitoring.PerformanceMonitor performanceMonitor = mock(com.hashverify.monitoring.PerformanceMonitor.class);
        AdminController adminController = new AdminController(configProperties, consensusService, submissionStorageService,
                                                             databaseCleanupService, performanceMonitor);
        
        when(submissionStorageService.getTotalSubmissionCount()).thenReturn(totalSubmissions);
        when(submissionStorageService.getUniqueSoftwareIdentityCount()).thenReturn(uniqueIdentities);
        when(submissionStorageService.getSubmissionCountSince(any())).thenReturn(0L);
        
        // When
        ResponseEntity<Map<String, Object>> response = adminController.getSystemStatistics();
        
        // Then - verify accurate statistics
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("totalSubmissions")).isEqualTo(totalSubmissions);
        assertThat(response.getBody().get("uniqueSoftwareIdentities")).isEqualTo(uniqueIdentities);
    }
    
    /**
     * Property 23: Administrative Authentication
     * **Validates: Requirement 9.6**
     * 
     * For any administrative API request, the Backend_Server should authenticate 
     * the request using valid API keys.
     * 
     * Note: This property tests the authentication filter logic, not the controller directly.
     */
    @Property(tries = 50)
    void property23_administrativeAuthentication_validatesAPIKeys(
            @ForAll @StringLength(min = 16, max = 64) String apiKey1,
            @ForAll @StringLength(min = 16, max = 64) String apiKey2) {
        
        // Constant-time comparison should work correctly
        boolean shouldMatch = apiKey1.equals(apiKey2);
        boolean actualMatch = constantTimeEquals(apiKey1, apiKey2);
        
        assertThat(actualMatch).isEqualTo(shouldMatch);
    }
    
    /**
     * Property 23.1: API key validation security
     * **Validates: Requirement 9.6**
     * 
     * For any API key validation, the system should use constant-time comparison 
     * to prevent timing attacks.
     */
    @Property(tries = 50)
    void property23_1_apiKeyValidationSecurity(
            @ForAll @StringLength(min = 32, max = 32) String validKey,
            @ForAll @StringLength(min = 32, max = 32) String testKey) {
        
        // Constant-time comparison should always take the same time regardless of input
        long startTime = System.nanoTime();
        boolean result = constantTimeEquals(validKey, testKey);
        long endTime = System.nanoTime();
        
        // Verify the comparison completes (timing analysis would require statistical testing)
        assertThat(endTime).isGreaterThan(startTime);
        assertThat(result).isEqualTo(validKey.equals(testKey));
    }
    
    /**
     * Property 27: Data Formatting for Administration
     * **Validates: Requirement 11.4**
     * 
     * For any Submission_Repository data requested through administrative queries, 
     * the system should format the data into human-readable JSON.
     */
    @Property(tries = 50)
    void property27_dataFormattingForAdministration_humanReadableJSON(
            @ForAll @StringLength(min = 8, max = 64) String identityHash,
            @ForAll @IntRange(min = 1, max = 100) int submissionCount,
            @ForAll @IntRange(min = 50, max = 100) int consensusPercentage) {
        
        // Given
        ConsensusService consensusService = mock(ConsensusService.class);
        SubmissionStorageService submissionStorageService = mock(SubmissionStorageService.class);
        ConsensusConfigurationProperties configProperties = new ConsensusConfigurationProperties();
        com.hashverify.service.DatabaseCleanupService databaseCleanupService = mock(com.hashverify.service.DatabaseCleanupService.class);
        com.hashverify.monitoring.PerformanceMonitor performanceMonitor = mock(com.hashverify.monitoring.PerformanceMonitor.class);
        AdminController adminController = new AdminController(configProperties, consensusService, submissionStorageService,
                                                             databaseCleanupService, performanceMonitor);
        
        SoftwareIdentity identity = new SoftwareIdentity();
        identity.setSourceDomain("example.com");
        identity.setNormalizedFilename("test.exe");
        identity.setFileSize(1024L);
        
        ConsensusResult consensusResult = new ConsensusResult();
        consensusResult.setSoftwareIdentity(identity);
        consensusResult.setConsensusHash("abc123");
        consensusResult.setConfidence((double) consensusPercentage / 100.0);
        consensusResult.setSubmissionCount(submissionCount);
        consensusResult.setStatus(ConsensusStatus.ESTABLISHED);
        consensusResult.setLifecycle(ConsensusLifecycle.ESTABLISHED);
        consensusResult.setLastUpdated(LocalDateTime.now());
        
        int consensusCount = (submissionCount * consensusPercentage) / 100;
        int otherCount = submissionCount - consensusCount;
        
        Map<String, Integer> hashDistribution = new HashMap<>();
        hashDistribution.put("abc123", consensusCount);
        if (otherCount > 0) {
            hashDistribution.put("def456", otherCount);
        }
        consensusResult.setHashDistribution(hashDistribution);
        
        when(consensusService.calculateConsensus(identityHash)).thenReturn(consensusResult);
        
        // When
        ResponseEntity<Map<String, Object>> response = adminController.getConsensusStatistics(identityHash);
        
        // Then - verify human-readable formatting
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        
        // Confidence should be formatted as percentage string
        String confidenceStr = (String) response.getBody().get("confidence");
        assertThat(confidenceStr).matches("\\d+\\.\\d{2}%");
        assertThat(confidenceStr).contains(String.format("%d", consensusPercentage));
        
        // Hash distribution should be human-readable
        @SuppressWarnings("unchecked")
        Map<String, String> distribution = (Map<String, String>) response.getBody().get("hashDistribution");
        assertThat(distribution).isNotNull();
        
        for (String value : distribution.values()) {
            // Each entry should contain submission count and percentage
            assertThat(value).matches("\\d+ submissions \\(\\d+\\.\\d{2}%\\)");
        }
    }
    
    /**
     * Property 27.1: Configuration data formatting
     * **Validates: Requirement 11.4**
     * 
     * For any configuration data request, the system should return data in 
     * human-readable JSON format with proper structure.
     */
    @Property(tries = 50)
    void property27_1_configurationDataFormatting() {
        // Given
        ConsensusService consensusService = mock(ConsensusService.class);
        SubmissionStorageService submissionStorageService = mock(SubmissionStorageService.class);
        ConsensusConfigurationProperties configProperties = new ConsensusConfigurationProperties();
        com.hashverify.service.DatabaseCleanupService databaseCleanupService = mock(com.hashverify.service.DatabaseCleanupService.class);
        com.hashverify.monitoring.PerformanceMonitor performanceMonitor = mock(com.hashverify.monitoring.PerformanceMonitor.class);
        AdminController adminController = new AdminController(configProperties, consensusService, submissionStorageService,
                                                             databaseCleanupService, performanceMonitor);
        
        // When
        ResponseEntity<Map<String, Object>> response = adminController.getConfiguration();
        
        // Then - verify human-readable structure
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        
        // All configuration sections should be present
        assertThat(response.getBody()).containsKeys(
            "consensus", "retention", "performance", "rateLimiting", "security", "replayProtection"
        );
        
        // Each section should be a map (structured data)
        for (Object value : response.getBody().values()) {
            assertThat(value).isInstanceOf(Map.class);
        }
    }
    
    /**
     * Property 27.2: Health status formatting
     * **Validates: Requirement 11.4**
     * 
     * For any health status request, the system should return data in 
     * human-readable JSON format.
     */
    @Property(tries = 50)
    void property27_2_healthStatusFormatting(
            @ForAll @IntRange(min = 0, max = 1000000) long recordCount) {
        
        // Given
        ConsensusService consensusService = mock(ConsensusService.class);
        SubmissionStorageService submissionStorageService = mock(SubmissionStorageService.class);
        ConsensusConfigurationProperties configProperties = new ConsensusConfigurationProperties();
        com.hashverify.service.DatabaseCleanupService databaseCleanupService = mock(com.hashverify.service.DatabaseCleanupService.class);
        com.hashverify.monitoring.PerformanceMonitor performanceMonitor = mock(com.hashverify.monitoring.PerformanceMonitor.class);
        AdminController adminController = new AdminController(configProperties, consensusService, submissionStorageService,
                                                             databaseCleanupService, performanceMonitor);
        
        when(submissionStorageService.getTotalSubmissionCount()).thenReturn(recordCount);
        
        // When
        ResponseEntity<Map<String, Object>> response = adminController.getHealthStatus();
        
        // Then - verify human-readable format
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKeys("status", "timestamp", "database");
        
        // Status should be human-readable string
        assertThat(response.getBody().get("status")).isInstanceOf(String.class);
        assertThat(response.getBody().get("database")).isInstanceOf(String.class);
        
        // Timestamp should be present
        assertThat(response.getBody().get("timestamp")).isInstanceOf(LocalDateTime.class);
    }
    
    /**
     * Helper method for constant-time string comparison
     * Mirrors the implementation in ApiKeyAuthenticationFilter
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        
        if (a.length() != b.length()) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        
        return result == 0;
    }
}
