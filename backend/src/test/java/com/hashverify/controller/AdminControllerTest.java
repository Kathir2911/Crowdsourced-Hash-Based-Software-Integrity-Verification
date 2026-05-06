package com.hashverify.controller;

import com.hashverify.config.ConsensusConfigurationProperties;
import com.hashverify.model.*;
import com.hashverify.service.ConsensusService;
import com.hashverify.service.SubmissionStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AdminController
 * Tests requirements 9.4, 9.6, 11.4
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerTest {
    
    @Mock
    private ConsensusService consensusService;
    
    @Mock
    private SubmissionStorageService submissionStorageService;
    
    @Mock
    private com.hashverify.service.DatabaseCleanupService databaseCleanupService;
    
    @Mock
    private com.hashverify.monitoring.PerformanceMonitor performanceMonitor;
    
    private ConsensusConfigurationProperties configProperties;
    private AdminController adminController;
    
    @BeforeEach
    void setUp() {
        configProperties = new ConsensusConfigurationProperties();
        adminController = new AdminController(configProperties, consensusService, submissionStorageService, 
                                             databaseCleanupService, performanceMonitor);
    }
    
    /**
     * Test: Get configuration endpoint returns current settings
     * Requirement 9.4: Provide administrative API for viewing consensus statistics
     */
    @Test
    void shouldReturnCurrentConfiguration() {
        // When
        ResponseEntity<Map<String, Object>> response = adminController.getConfiguration();
        
        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKeys("consensus", "retention", "performance", "rateLimiting", "security", "replayProtection");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> consensus = (Map<String, Object>) response.getBody().get("consensus");
        assertThat(consensus).containsKeys("thresholdPercentage", "minimumSubmissions");
        assertThat(consensus.get("thresholdPercentage")).isEqualTo(0.70);
        assertThat(consensus.get("minimumSubmissions")).isEqualTo(3);
    }
    
    /**
     * Test: Get consensus statistics returns formatted data
     * Requirement 9.4: Provide administrative API for viewing consensus statistics
     * Requirement 11.4: Format Hash_Registry data into human-readable JSON
     */
    @Test
    void shouldReturnConsensusStatistics() {
        // Given
        String identityHash = "test-identity-hash";
        
        SoftwareIdentity identity = new SoftwareIdentity();
        identity.setSourceDomain("example.com");
        identity.setNormalizedFilename("test.exe");
        identity.setFileSize(1024L);
        
        ConsensusResult consensusResult = new ConsensusResult();
        consensusResult.setSoftwareIdentity(identity);
        consensusResult.setConsensusHash("abc123");
        consensusResult.setConfidence(0.85);
        consensusResult.setSubmissionCount(10);
        consensusResult.setStatus(ConsensusStatus.ESTABLISHED);
        consensusResult.setLifecycle(ConsensusLifecycle.ESTABLISHED);
        consensusResult.setLastUpdated(LocalDateTime.now());
        
        Map<String, Integer> hashDistribution = new HashMap<>();
        hashDistribution.put("abc123", 8);
        hashDistribution.put("def456", 2);
        consensusResult.setHashDistribution(hashDistribution);
        
        when(consensusService.calculateConsensus(identityHash)).thenReturn(consensusResult);
        
        // When
        ResponseEntity<Map<String, Object>> response = adminController.getConsensusStatistics(identityHash);
        
        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKeys("softwareIdentityHash", "consensusHash", "confidence", "submissionCount", "status", "lifecycle", "hashDistribution");
        assertThat(response.getBody().get("consensusHash")).isEqualTo("abc123");
        assertThat(response.getBody().get("confidence")).isEqualTo("85.00%");
        assertThat(response.getBody().get("submissionCount")).isEqualTo(10);
        
        @SuppressWarnings("unchecked")
        Map<String, String> distribution = (Map<String, String>) response.getBody().get("hashDistribution");
        assertThat(distribution).containsKeys("abc123", "def456");
        assertThat(distribution.get("abc123")).contains("8 submissions").contains("80.00%");
    }
    
    /**
     * Test: Get system statistics returns aggregated data
     * Requirement 9.4: Provide administrative API for viewing consensus statistics
     * Requirement 11.4: Format Hash_Registry data into human-readable JSON
     */
    @Test
    void shouldReturnSystemStatistics() {
        // Given
        when(submissionStorageService.getTotalSubmissionCount()).thenReturn(1000L);
        when(submissionStorageService.getSubmissionCountSince(org.mockito.ArgumentMatchers.any())).thenReturn(50L, 200L, 500L);
        when(submissionStorageService.getUniqueSoftwareIdentityCount()).thenReturn(100L);
        
        // When
        ResponseEntity<Map<String, Object>> response = adminController.getSystemStatistics();
        
        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKeys("totalSubmissions", "submissionsByPeriod", "uniqueSoftwareIdentities", "configuration", "timestamp");
        assertThat(response.getBody().get("totalSubmissions")).isEqualTo(1000L);
        assertThat(response.getBody().get("uniqueSoftwareIdentities")).isEqualTo(100L);
        
        @SuppressWarnings("unchecked")
        Map<String, Long> submissionsByPeriod = (Map<String, Long>) response.getBody().get("submissionsByPeriod");
        assertThat(submissionsByPeriod).containsKeys("last24Hours", "last7Days", "last30Days");
    }
    
    /**
     * Test: Cleanup endpoint removes expired data
     * Requirement 9.4: Administrative functionality
     */
    @Test
    void shouldCleanupExpiredData() {
        // Given
        when(submissionStorageService.deleteExpiredSubmissions()).thenReturn(50);
        when(consensusService.cleanupExpiredCache()).thenReturn(10);
        
        // When
        ResponseEntity<Map<String, Object>> response = adminController.cleanupExpiredData();
        
        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKeys("expiredSubmissionsDeleted", "expiredCacheEntriesDeleted", "timestamp");
        assertThat(response.getBody().get("expiredSubmissionsDeleted")).isEqualTo(50);
        assertThat(response.getBody().get("expiredCacheEntriesDeleted")).isEqualTo(10);
    }
    
    /**
     * Test: Health endpoint returns system status
     * Requirement 9.4: Administrative functionality
     */
    @Test
    void shouldReturnHealthStatus() {
        // Given
        when(submissionStorageService.getTotalSubmissionCount()).thenReturn(1000L);
        
        // When
        ResponseEntity<Map<String, Object>> response = adminController.getHealthStatus();
        
        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKeys("status", "timestamp", "database", "databaseRecordCount");
        assertThat(response.getBody().get("status")).isEqualTo("UP");
        assertThat(response.getBody().get("database")).isEqualTo("UP");
        assertThat(response.getBody().get("databaseRecordCount")).isEqualTo(1000L);
    }
    
    /**
     * Test: Health endpoint handles database errors gracefully
     * Requirement 9.4: Administrative functionality
     */
    @Test
    void shouldHandleDatabaseErrorsInHealthCheck() {
        // Given
        when(submissionStorageService.getTotalSubmissionCount()).thenThrow(new RuntimeException("Database connection failed"));
        
        // When
        ResponseEntity<Map<String, Object>> response = adminController.getHealthStatus();
        
        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKeys("status", "timestamp", "database", "databaseError");
        assertThat(response.getBody().get("status")).isEqualTo("UP");
        assertThat(response.getBody().get("database")).isEqualTo("DOWN");
        assertThat(response.getBody().get("databaseError")).isEqualTo("Database connection failed");
    }
}
