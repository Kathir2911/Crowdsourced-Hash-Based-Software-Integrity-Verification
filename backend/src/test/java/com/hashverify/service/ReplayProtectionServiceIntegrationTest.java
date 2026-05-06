package com.hashverify.service;

import com.hashverify.model.HashSubmission;
import com.hashverify.model.SoftwareIdentity;
import com.hashverify.repository.HashSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ReplayProtectionService
 * Tests replay protection with real database interactions
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReplayProtectionServiceIntegrationTest {
    
    @Autowired
    private ReplayProtectionService replayProtectionService;
    
    @Autowired
    private SubmissionStorageService submissionStorageService;
    
    @Autowired
    private HashSubmissionRepository repository;
    
    @BeforeEach
    void setUp() {
        repository.deleteAll();
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
    void testGenerateReplayProtectionHash_Idempotent() {
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
    void testIsDuplicateSubmission_WithRealDatabase() {
        // Given
        String ipAddress = "192.168.1.1";
        String userAgent = "Mozilla/5.0";
        LocalDateTime timestamp = LocalDateTime.now();
        
        String replayHash = replayProtectionService.generateReplayProtectionHash(ipAddress, userAgent, timestamp);
        
        SoftwareIdentity identity = new SoftwareIdentity("example.com", "test.exe", 1024L);
        HashSubmission submission = new HashSubmission(
            identity,
            "a".repeat(64),
            "1.0.0",
            replayHash,
            userAgent
        );
        // Set timestamp manually since @CreationTimestamp only works on persist
        submission.setTimestamp(timestamp);
        
        // When - First submission
        submissionStorageService.saveSubmission(submission);
        boolean isDuplicate1 = replayProtectionService.isDuplicateSubmission(replayHash, identity.generateIdentityHash());
        
        // Then - Should detect duplicate
        assertThat(isDuplicate1).isTrue();
        
        // When - Different replay hash
        String differentReplayHash = replayProtectionService.generateReplayProtectionHash("192.168.1.2", userAgent, timestamp);
        boolean isDuplicate2 = replayProtectionService.isDuplicateSubmission(differentReplayHash, identity.generateIdentityHash());
        
        // Then - Should not detect duplicate
        assertThat(isDuplicate2).isFalse();
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
