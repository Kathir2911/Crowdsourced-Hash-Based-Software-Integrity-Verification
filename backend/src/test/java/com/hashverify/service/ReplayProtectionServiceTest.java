package com.hashverify.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ReplayProtectionService
 * Tests replay protection hash generation and duplicate detection
 */
class ReplayProtectionServiceTest {
    
    private StubSubmissionStorageService submissionStorageService;
    private ReplayProtectionService replayProtectionService;
    
    /**
     * Stub implementation of SubmissionStorageService for testing
     */
    private static class StubSubmissionStorageService extends SubmissionStorageService {
        private final Map<String, Boolean> duplicateResults = new HashMap<>();
        private boolean throwException = false;
        
        public StubSubmissionStorageService() {
            super(null);
        }
        
        @Override
        public boolean isDuplicateSubmission(String replayProtectionHash, String identityHash) {
            if (throwException) {
                throw new RuntimeException("Database error");
            }
            String key = replayProtectionHash + ":" + identityHash;
            return duplicateResults.getOrDefault(key, false);
        }
        
        public void setDuplicateResult(String replayHash, String identityHash, boolean isDuplicate) {
            String key = replayHash + ":" + identityHash;
            duplicateResults.put(key, isDuplicate);
        }
        
        public void setThrowException(boolean throwException) {
            this.throwException = throwException;
        }
    }
    
    @BeforeEach
    void setUp() {
        submissionStorageService = new StubSubmissionStorageService();
        replayProtectionService = new ReplayProtectionService(submissionStorageService);
    }
    
    @Test
    void testGenerateReplayProtectionHash_ValidInputs() {
        // Given
        String ipAddress = "192.168.1.1";
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        
        // When
        String hash = replayProtectionService.generateReplayProtectionHash(ipAddress, userAgent, timestamp);
        
        // Then
        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64); // SHA-256 produces 64 hex characters
        assertThat(hash).matches("^[a-f0-9]{64}$"); // Lowercase hexadecimal
    }
    
    @Test
    void testGenerateReplayProtectionHash_SameInputsSameHash() {
        // Given
        String ipAddress = "192.168.1.1";
        String userAgent = "Mozilla/5.0";
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        
        // When
        String hash1 = replayProtectionService.generateReplayProtectionHash(ipAddress, userAgent, timestamp);
        String hash2 = replayProtectionService.generateReplayProtectionHash(ipAddress, userAgent, timestamp);
        
        // Then
        assertThat(hash1).isEqualTo(hash2);
    }
    
    @Test
    void testGenerateReplayProtectionHash_SameDay24HourWindow() {
        // Given - Same day, different times
        String ipAddress = "192.168.1.1";
        String userAgent = "Mozilla/5.0";
        LocalDateTime morning = LocalDateTime.of(2024, 1, 15, 8, 0);
        LocalDateTime evening = LocalDateTime.of(2024, 1, 15, 20, 0);
        
        // When
        String hashMorning = replayProtectionService.generateReplayProtectionHash(ipAddress, userAgent, morning);
        String hashEvening = replayProtectionService.generateReplayProtectionHash(ipAddress, userAgent, evening);
        
        // Then - Should be the same hash (same 24-hour window)
        assertThat(hashMorning).isEqualTo(hashEvening);
    }
    
    @Test
    void testGenerateReplayProtectionHash_DifferentDaysDifferentHash() {
        // Given - Different days
        String ipAddress = "192.168.1.1";
        String userAgent = "Mozilla/5.0";
        LocalDateTime day1 = LocalDateTime.of(2024, 1, 15, 10, 0);
        LocalDateTime day2 = LocalDateTime.of(2024, 1, 16, 10, 0);
        
        // When
        String hash1 = replayProtectionService.generateReplayProtectionHash(ipAddress, userAgent, day1);
        String hash2 = replayProtectionService.generateReplayProtectionHash(ipAddress, userAgent, day2);
        
        // Then - Should be different hashes (different 24-hour windows)
        assertThat(hash1).isNotEqualTo(hash2);
    }
    
    @Test
    void testGenerateReplayProtectionHash_DifferentIpDifferentHash() {
        // Given
        String ip1 = "192.168.1.1";
        String ip2 = "192.168.1.2";
        String userAgent = "Mozilla/5.0";
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 0);
        
        // When
        String hash1 = replayProtectionService.generateReplayProtectionHash(ip1, userAgent, timestamp);
        String hash2 = replayProtectionService.generateReplayProtectionHash(ip2, userAgent, timestamp);
        
        // Then
        assertThat(hash1).isNotEqualTo(hash2);
    }
    
    @Test
    void testGenerateReplayProtectionHash_DifferentUserAgentDifferentHash() {
        // Given
        String ipAddress = "192.168.1.1";
        String userAgent1 = "Mozilla/5.0 (Windows)";
        String userAgent2 = "Mozilla/5.0 (Mac)";
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 0);
        
        // When
        String hash1 = replayProtectionService.generateReplayProtectionHash(ipAddress, userAgent1, timestamp);
        String hash2 = replayProtectionService.generateReplayProtectionHash(ipAddress, userAgent2, timestamp);
        
        // Then
        assertThat(hash1).isNotEqualTo(hash2);
    }
    
    @Test
    void testGenerateReplayProtectionHash_NullInputsHandled() {
        // Given
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 0);
        
        // When
        String hash = replayProtectionService.generateReplayProtectionHash(null, null, timestamp);
        
        // Then - Should not throw exception and produce valid hash
        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("^[a-f0-9]{64}$");
    }
    
    @Test
    void testIsDuplicateSubmission_DuplicateExists() {
        // Given
        String replayHash = "abc123";
        String identityHash = "def456";
        submissionStorageService.setDuplicateResult(replayHash, identityHash, true);
        
        // When
        boolean isDuplicate = replayProtectionService.isDuplicateSubmission(replayHash, identityHash);
        
        // Then
        assertThat(isDuplicate).isTrue();
    }
    
    @Test
    void testIsDuplicateSubmission_NoDuplicate() {
        // Given
        String replayHash = "abc123";
        String identityHash = "def456";
        submissionStorageService.setDuplicateResult(replayHash, identityHash, false);
        
        // When
        boolean isDuplicate = replayProtectionService.isDuplicateSubmission(replayHash, identityHash);
        
        // Then
        assertThat(isDuplicate).isFalse();
    }
    
    @Test
    void testIsDuplicateSubmission_ExceptionHandled() {
        // Given
        String replayHash = "abc123";
        String identityHash = "def456";
        submissionStorageService.setThrowException(true);
        
        // When
        boolean isDuplicate = replayProtectionService.isDuplicateSubmission(replayHash, identityHash);
        
        // Then - Should fail open (return false) on error
        assertThat(isDuplicate).isFalse();
    }
    
    @Test
    void testIsWithinRetentionPeriod_RecentSubmission() {
        // Given
        LocalDateTime recentTimestamp = LocalDateTime.now().minusDays(30);
        int retentionDays = 90;
        
        // When
        boolean isValid = replayProtectionService.isWithinRetentionPeriod(recentTimestamp, retentionDays);
        
        // Then
        assertThat(isValid).isTrue();
    }
    
    @Test
    void testIsWithinRetentionPeriod_ExpiredSubmission() {
        // Given
        LocalDateTime oldTimestamp = LocalDateTime.now().minusDays(100);
        int retentionDays = 90;
        
        // When
        boolean isValid = replayProtectionService.isWithinRetentionPeriod(oldTimestamp, retentionDays);
        
        // Then
        assertThat(isValid).isFalse();
    }
    
    @Test
    void testIsWithinRetentionPeriod_ExactBoundary() {
        // Given
        LocalDateTime boundaryTimestamp = LocalDateTime.now().minusDays(90).plusMinutes(1);
        int retentionDays = 90;
        
        // When
        boolean isValid = replayProtectionService.isWithinRetentionPeriod(boundaryTimestamp, retentionDays);
        
        // Then
        assertThat(isValid).isTrue();
    }
    
    @Test
    void testShouldIncludeInConsensus_RecentSubmission() {
        // Given
        LocalDateTime recentTimestamp = LocalDateTime.now().minusDays(45);
        int retentionDays = 90;
        
        // When
        boolean shouldInclude = replayProtectionService.shouldIncludeInConsensus(recentTimestamp, retentionDays);
        
        // Then
        assertThat(shouldInclude).isTrue();
    }
    
    @Test
    void testShouldIncludeInConsensus_ExpiredSubmission() {
        // Given
        LocalDateTime oldTimestamp = LocalDateTime.now().minusDays(120);
        int retentionDays = 90;
        
        // When
        boolean shouldInclude = replayProtectionService.shouldIncludeInConsensus(oldTimestamp, retentionDays);
        
        // Then
        assertThat(shouldInclude).isFalse();
    }
}
