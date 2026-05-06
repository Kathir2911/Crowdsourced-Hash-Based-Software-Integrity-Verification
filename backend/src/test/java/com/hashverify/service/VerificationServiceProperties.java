package com.hashverify.service;

import com.hashverify.model.ConsensusLifecycle;
import com.hashverify.model.ConsensusResult;
import com.hashverify.model.ConsensusStatus;
import com.hashverify.model.SoftwareIdentity;
import com.hashverify.model.SuspicionLevel;
import com.hashverify.model.TamperStatus;
import com.hashverify.model.VerificationResult;
import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for VerificationService
 * Tests requirements 5.1, 5.2, 5.3, 5.4, 5.5
 */
class VerificationServiceProperties {
    
    /**
     * Helper method to create VerificationService with a stub ConsensusService
     */
    private VerificationService createVerificationService(ConsensusResult consensusResult) {
        // Create a stub ConsensusService that returns the provided result
        ConsensusService consensusService = new ConsensusService(null, null) {
            @Override
            public ConsensusResult calculateConsensus(String identityHash) {
                return consensusResult;
            }
        };
        
        VerificationService verificationService = new VerificationService(consensusService);
        
        // Set configuration values
        ReflectionTestUtils.setField(verificationService, "confidenceHighThreshold", 0.70);
        ReflectionTestUtils.setField(verificationService, "confidenceMinimumSubmissions", 10);
        
        return verificationService;
    }
    
    /**
     * **Validates: Requirements 5.1, 5.4**
     * 
     * Property: For any hash that matches consensus with high confidence (>= 70% and >= 10 submissions),
     * the verification status should be VERIFIED
     */
    @Property(tries = 50)
    void verifiedStatus_WhenHashMatchesWithHighConfidence(
        @ForAll @Size(64) String hash,
        @ForAll @DoubleRange(min = 0.70, max = 1.0) double confidence,
        @ForAll @IntRange(min = 10, max = 1000) int submissionCount
    ) {
        // Arrange
        SoftwareIdentity identity = createTestIdentity();
        ConsensusResult consensus = createConsensusResult(
            hash, confidence, submissionCount, ConsensusStatus.ESTABLISHED, ConsensusLifecycle.ESTABLISHED
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        
        // Act
        VerificationResult result = verificationService.verifyHash(hash, identity);
        
        // Assert
        assertThat(result.getStatus()).isEqualTo(TamperStatus.VERIFIED);
        assertThat(result.getSuspicionLevel()).isEqualTo(SuspicionLevel.NONE);
    }
    
    /**
     * **Validates: Requirements 5.1, 5.3**
     * 
     * Property: For any hash that differs from consensus with high confidence (>= 70% and >= 10 submissions),
     * the verification status should be TAMPERED
     */
    @Property(tries = 50)
    void tamperedStatus_WhenHashDiffersWithHighConfidence(
        @ForAll @Size(64) String submittedHash,
        @ForAll @Size(64) String consensusHash,
        @ForAll @DoubleRange(min = 0.70, max = 1.0) double confidence,
        @ForAll @IntRange(min = 10, max = 1000) int submissionCount
    ) {
        Assume.that(!submittedHash.equals(consensusHash));
        
        // Arrange
        SoftwareIdentity identity = createTestIdentity();
        ConsensusResult consensus = createConsensusResult(
            consensusHash, confidence, submissionCount, ConsensusStatus.ESTABLISHED, ConsensusLifecycle.ESTABLISHED
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        
        // Act
        VerificationResult result = verificationService.verifyHash(submittedHash, identity);
        
        // Assert
        assertThat(result.getStatus()).isEqualTo(TamperStatus.TAMPERED);
        assertThat(result.getSuspicionLevel()).isIn(SuspicionLevel.HIGH, SuspicionLevel.CRITICAL);
    }
    
    /**
     * **Validates: Requirements 5.2, 5.3**
     * 
     * Property: For any consensus with confidence < 70% OR submission count < 10,
     * the verification status should be SUSPICIOUS_LOW_CONFIDENCE
     */
    @Property(tries = 50)
    void suspiciousStatus_WhenLowConfidenceOrFewSubmissions(
        @ForAll @Size(64) String hash,
        @ForAll @DoubleRange(min = 0.0, max = 1.0) double confidence,
        @ForAll @IntRange(min = 1, max = 100) int submissionCount
    ) {
        // Only test cases where confidence < 70% OR submissions < 10
        boolean isLowConfidence = confidence < 0.70;
        boolean isFewSubmissions = submissionCount < 10;
        Assume.that(isLowConfidence || isFewSubmissions);
        
        // Arrange
        SoftwareIdentity identity = createTestIdentity();
        ConsensusStatus status = (confidence >= 0.70) ? ConsensusStatus.ESTABLISHED : ConsensusStatus.NO_CONSENSUS;
        ConsensusResult consensus = createConsensusResult(
            hash, confidence, submissionCount, status, ConsensusLifecycle.LOW_CONFIDENCE
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        
        // Act
        VerificationResult result = verificationService.verifyHash(hash, identity);
        
        // Assert
        assertThat(result.getStatus()).isEqualTo(TamperStatus.SUSPICIOUS_LOW_CONFIDENCE);
    }
    
    /**
     * **Validates: Requirements 5.5**
     * 
     * Property: For any verification request when no consensus exists,
     * the verification status should be UNKNOWN
     */
    @Property(tries = 50)
    void unknownStatus_WhenNoConsensus(
        @ForAll @Size(64) String hash
    ) {
        // Arrange
        SoftwareIdentity identity = createTestIdentity();
        ConsensusResult consensus = createConsensusResult(
            null, 0.0, 0, ConsensusStatus.INSUFFICIENT_DATA, ConsensusLifecycle.UNKNOWN
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        
        // Act
        VerificationResult result = verificationService.verifyHash(hash, identity);
        
        // Assert
        assertThat(result.getStatus()).isEqualTo(TamperStatus.UNKNOWN);
        assertThat(result.getSuspicionLevel()).isEqualTo(SuspicionLevel.LOW);
    }
    
    /**
     * **Validates: Requirements 5.3**
     * 
     * Property: Suspicion level calculation should be consistent with tamper status
     */
    @Property(tries = 50)
    void suspicionLevel_ConsistentWithTamperStatus(
        @ForAll @Size(64) String hash,
        @ForAll @DoubleRange(min = 0.0, max = 1.0) double confidence,
        @ForAll @IntRange(min = 0, max = 1000) int submissionCount
    ) {
        // Arrange
        SoftwareIdentity identity = createTestIdentity();
        ConsensusStatus status = determineStatus(confidence, submissionCount);
        ConsensusLifecycle lifecycle = determineLifecycle(confidence, submissionCount);
        
        ConsensusResult consensus = createConsensusResult(
            hash, confidence, submissionCount, status, lifecycle
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        
        // Act
        VerificationResult result = verificationService.verifyHash(hash, identity);
        
        // Assert - verify suspicion level is appropriate for status
        switch (result.getStatus()) {
            case VERIFIED:
                assertThat(result.getSuspicionLevel()).isEqualTo(SuspicionLevel.NONE);
                break;
            case TAMPERED:
                assertThat(result.getSuspicionLevel()).isIn(SuspicionLevel.HIGH, SuspicionLevel.CRITICAL);
                break;
            case SUSPICIOUS_LOW_CONFIDENCE:
                assertThat(result.getSuspicionLevel()).isIn(SuspicionLevel.LOW, SuspicionLevel.MEDIUM);
                break;
            case UNKNOWN:
                assertThat(result.getSuspicionLevel()).isEqualTo(SuspicionLevel.LOW);
                break;
        }
    }
    
    /**
     * **Validates: Requirements 3.3, 5.6, 6.6**
     * 
     * Property 6: Submission Data Completeness
     * 
     * Verification result should always contain complete and valid data including:
     * - Verification status (Requirement 6.6)
     * - Confidence percentage (Requirement 5.6)
     * - Submission count (Requirement 5.6)
     * - Timestamp (Requirement 3.3)
     * - Message and recommended action
     */
    @Property(tries = 50)
    void verificationResult_AlwaysComplete(
        @ForAll @Size(64) String hash,
        @ForAll @DoubleRange(min = 0.0, max = 1.0) double confidence,
        @ForAll @IntRange(min = 0, max = 1000) int submissionCount
    ) {
        // Arrange
        SoftwareIdentity identity = createTestIdentity();
        ConsensusStatus status = determineStatus(confidence, submissionCount);
        ConsensusLifecycle lifecycle = determineLifecycle(confidence, submissionCount);
        String consensusHash = (submissionCount > 0) ? hash : null;
        
        ConsensusResult consensus = createConsensusResult(
            consensusHash, confidence, submissionCount, status, lifecycle
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        
        // Act
        VerificationResult result = verificationService.verifyHash(hash, identity);
        
        // Assert - all fields should be populated
        assertThat(result.getStatus()).isNotNull();
        assertThat(result.getConfidence()).isNotNull();
        assertThat(result.getConfidence()).isBetween(0.0, 1.0);
        assertThat(result.getSubmissionCount()).isNotNull();
        assertThat(result.getSubmissionCount()).isGreaterThanOrEqualTo(0);
        assertThat(result.getMessage()).isNotBlank();
        assertThat(result.getSuspicionLevel()).isNotNull();
        assertThat(result.getRecommendedAction()).isNotBlank();
        assertThat(result.getTimestamp()).isNotNull();
    }
    
    /**
     * **Validates: Requirements 5.3**
     * 
     * Property: Critical suspicion level should only occur for TAMPERED status with very high confidence
     */
    @Property(tries = 50)
    void criticalSuspicion_OnlyForHighConfidenceTampered(
        @ForAll @Size(64) String submittedHash,
        @ForAll @Size(64) String consensusHash,
        @ForAll @DoubleRange(min = 0.90, max = 1.0) double confidence,
        @ForAll @IntRange(min = 10, max = 1000) int submissionCount
    ) {
        Assume.that(!submittedHash.equals(consensusHash));
        
        // Arrange
        SoftwareIdentity identity = createTestIdentity();
        ConsensusResult consensus = createConsensusResult(
            consensusHash, confidence, submissionCount, ConsensusStatus.ESTABLISHED, ConsensusLifecycle.ESTABLISHED
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        
        // Act
        VerificationResult result = verificationService.verifyHash(submittedHash, identity);
        
        // Assert
        assertThat(result.getStatus()).isEqualTo(TamperStatus.TAMPERED);
        assertThat(result.getSuspicionLevel()).isEqualTo(SuspicionLevel.CRITICAL);
    }
    
    /**
     * **Validates: Requirements 5.1**
     * 
     * Property: Hash comparison should be case-sensitive and exact
     */
    @Property(tries = 50)
    void hashComparison_IsCaseSensitiveAndExact(
        @ForAll("hexHashWithLetters") String hash,
        @ForAll @DoubleRange(min = 0.70, max = 1.0) double confidence,
        @ForAll @IntRange(min = 10, max = 1000) int submissionCount
    ) {
        // Arrange
        SoftwareIdentity identity = createTestIdentity();
        String uppercaseHash = hash.toUpperCase();
        
        ConsensusResult consensus = createConsensusResult(
            hash, confidence, submissionCount, ConsensusStatus.ESTABLISHED, ConsensusLifecycle.ESTABLISHED
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        
        // Act
        VerificationResult result = verificationService.verifyHash(uppercaseHash, identity);
        
        // Assert - uppercase hash should not match lowercase consensus
        assertThat(result.getStatus()).isEqualTo(TamperStatus.TAMPERED);
    }
    
    /**
     * Provider for hex hashes that contain at least one letter
     */
    @Provide
    Arbitrary<String> hexHashWithLetters() {
        return Arbitraries.strings()
            .withCharRange('a', 'f')
            .ofLength(1)
            .flatMap(letter -> 
                Arbitraries.strings()
                    .withChars("0123456789abcdef")
                    .ofLength(63)
                    .map(rest -> letter + rest)
            );
    }
    
    // Helper methods
    
    private SoftwareIdentity createTestIdentity() {
        return new SoftwareIdentity("download.example.com", "setup.exe", 1048576L);
    }
    
    private ConsensusResult createConsensusResult(String consensusHash, double confidence,
                                                  int submissionCount, ConsensusStatus status,
                                                  ConsensusLifecycle lifecycle) {
        SoftwareIdentity identity = createTestIdentity();
        ConsensusResult result = new ConsensusResult(
            identity, consensusHash, confidence, submissionCount, status, lifecycle
        );
        return result;
    }
    
    private ConsensusStatus determineStatus(double confidence, int submissionCount) {
        if (submissionCount == 0) {
            return ConsensusStatus.INSUFFICIENT_DATA;
        }
        if (submissionCount < 3) {
            return ConsensusStatus.INSUFFICIENT_DATA;
        }
        if (confidence >= 0.70) {
            return ConsensusStatus.ESTABLISHED;
        }
        return ConsensusStatus.NO_CONSENSUS;
    }
    
    private ConsensusLifecycle determineLifecycle(double confidence, int submissionCount) {
        if (submissionCount == 0) {
            return ConsensusLifecycle.UNKNOWN;
        }
        if (submissionCount < 3 || confidence < 0.70) {
            return ConsensusLifecycle.LOW_CONFIDENCE;
        }
        return ConsensusLifecycle.ESTABLISHED;
    }
}
