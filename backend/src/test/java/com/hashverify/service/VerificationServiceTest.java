package com.hashverify.service;

import com.hashverify.model.ConsensusLifecycle;
import com.hashverify.model.ConsensusResult;
import com.hashverify.model.ConsensusStatus;
import com.hashverify.model.SoftwareIdentity;
import com.hashverify.model.SuspicionLevel;
import com.hashverify.model.TamperStatus;
import com.hashverify.model.VerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for VerificationService
 * Tests requirements 5.1, 5.2, 5.3, 5.4, 5.5
 */
class VerificationServiceTest {
    
    private SoftwareIdentity testIdentity;
    private String testHash;
    private String consensusHash;
    
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
        
        VerificationService service = new VerificationService(consensusService);
        
        // Set configuration values
        ReflectionTestUtils.setField(service, "confidenceHighThreshold", 0.70);
        ReflectionTestUtils.setField(service, "confidenceMinimumSubmissions", 10);
        
        return service;
    }
    
    @BeforeEach
    void setUp() {
        // Create test data
        testIdentity = new SoftwareIdentity("download.example.com", "setup.exe", 1048576L);
        testHash = "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd";
        consensusHash = "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd";
    }
    
    @Test
    void testVerifyHash_Verified_HighConfidence() {
        // Requirement 5.4: VERIFIED status when hash matches consensus with high confidence
        ConsensusResult consensus = createConsensusResult(
            consensusHash, 0.85, 15, ConsensusStatus.ESTABLISHED, ConsensusLifecycle.ESTABLISHED
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        VerificationResult result = verificationService.verifyHash(testHash, testIdentity);
        
        assertThat(result.getStatus()).isEqualTo(TamperStatus.VERIFIED);
        assertThat(result.getSuspicionLevel()).isEqualTo(SuspicionLevel.NONE);
        assertThat(result.getConfidence()).isEqualTo(0.85);
        assertThat(result.getSubmissionCount()).isEqualTo(15);
        assertThat(result.getMessage()).contains("verified");
    }
    
    @Test
    void testVerifyHash_Tampered_HighConfidence() {
        // Requirement 5.3: TAMPERED status when hash doesn't match consensus with high confidence
        String differentHash = "different1234567890123456789012345678901234567890123456789012";
        ConsensusResult consensus = createConsensusResult(
            consensusHash, 0.80, 20, ConsensusStatus.ESTABLISHED, ConsensusLifecycle.ESTABLISHED
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        VerificationResult result = verificationService.verifyHash(differentHash, testIdentity);
        
        assertThat(result.getStatus()).isEqualTo(TamperStatus.TAMPERED);
        assertThat(result.getSuspicionLevel()).isEqualTo(SuspicionLevel.HIGH);
        assertThat(result.getMessage()).contains("does not match");
    }
    
    @Test
    void testVerifyHash_Tampered_VeryHighConfidence_Critical() {
        // TAMPERED with very high confidence (>90%) should be CRITICAL suspicion
        String differentHash = "different1234567890123456789012345678901234567890123456789012";
        ConsensusResult consensus = createConsensusResult(
            consensusHash, 0.95, 50, ConsensusStatus.ESTABLISHED, ConsensusLifecycle.ESTABLISHED
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        VerificationResult result = verificationService.verifyHash(differentHash, testIdentity);
        
        assertThat(result.getStatus()).isEqualTo(TamperStatus.TAMPERED);
        assertThat(result.getSuspicionLevel()).isEqualTo(SuspicionLevel.CRITICAL);
        assertThat(result.getRecommendedAction()).contains("DO NOT USE");
    }
    
    @Test
    void testVerifyHash_SuspiciousLowConfidence_HighPercentageLowSubmissions() {
        // Requirement 5.2: SUSPICIOUS_LOW_CONFIDENCE when confidence >= 70% but < 10 submissions
        ConsensusResult consensus = createConsensusResult(
            consensusHash, 0.75, 8, ConsensusStatus.ESTABLISHED, ConsensusLifecycle.LOW_CONFIDENCE
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        VerificationResult result = verificationService.verifyHash(testHash, testIdentity);
        
        assertThat(result.getStatus()).isEqualTo(TamperStatus.SUSPICIOUS_LOW_CONFIDENCE);
        assertThat(result.getMessage()).contains("confidence is weak");
    }
    
    @Test
    void testVerifyHash_SuspiciousLowConfidence_LowPercentage() {
        // Requirement 5.2: SUSPICIOUS_LOW_CONFIDENCE when confidence < 70%
        ConsensusResult consensus = createConsensusResult(
            consensusHash, 0.60, 15, ConsensusStatus.NO_CONSENSUS, ConsensusLifecycle.LOW_CONFIDENCE
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        VerificationResult result = verificationService.verifyHash(testHash, testIdentity);
        
        assertThat(result.getStatus()).isEqualTo(TamperStatus.SUSPICIOUS_LOW_CONFIDENCE);
        assertThat(result.getConfidence()).isEqualTo(0.60);
    }
    
    @Test
    void testVerifyHash_Unknown_NoConsensus() {
        // Requirement 5.5: UNKNOWN status when no consensus available
        ConsensusResult consensus = createConsensusResult(
            null, 0.0, 0, ConsensusStatus.INSUFFICIENT_DATA, ConsensusLifecycle.UNKNOWN
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        VerificationResult result = verificationService.verifyHash(testHash, testIdentity);
        
        assertThat(result.getStatus()).isEqualTo(TamperStatus.UNKNOWN);
        assertThat(result.getSuspicionLevel()).isEqualTo(SuspicionLevel.LOW);
        assertThat(result.getMessage()).contains("No consensus data");
    }
    
    @Test
    void testVerifyHash_Unknown_Expired() {
        // Requirement 5.5: UNKNOWN status when consensus is expired
        ConsensusResult consensus = createConsensusResult(
            consensusHash, 0.80, 10, ConsensusStatus.EXPIRED, ConsensusLifecycle.EXPIRED
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        VerificationResult result = verificationService.verifyHash(testHash, testIdentity);
        
        assertThat(result.getStatus()).isEqualTo(TamperStatus.UNKNOWN);
    }
    
    @Test
    void testDetermineTamperStatus_HashMatches_HighConfidence() {
        // Requirement 5.4: Hash matches consensus with high confidence
        ConsensusResult consensus = createConsensusResult(
            testHash, 0.85, 15, ConsensusStatus.ESTABLISHED, ConsensusLifecycle.ESTABLISHED
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        TamperStatus status = verificationService.determineTamperStatus(testHash, consensus);
        
        assertThat(status).isEqualTo(TamperStatus.VERIFIED);
    }
    
    @Test
    void testDetermineTamperStatus_HashDiffers_HighConfidence() {
        // Requirement 5.3: Hash doesn't match consensus with high confidence
        String differentHash = "different1234567890123456789012345678901234567890123456789012";
        ConsensusResult consensus = createConsensusResult(
            testHash, 0.85, 15, ConsensusStatus.ESTABLISHED, ConsensusLifecycle.ESTABLISHED
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        TamperStatus status = verificationService.determineTamperStatus(differentHash, consensus);
        
        assertThat(status).isEqualTo(TamperStatus.TAMPERED);
    }
    
    @Test
    void testDetermineTamperStatus_NoConsensusHash() {
        // Requirement 5.5: No consensus hash available
        ConsensusResult consensus = createConsensusResult(
            null, 0.0, 2, ConsensusStatus.INSUFFICIENT_DATA, ConsensusLifecycle.UNKNOWN
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        TamperStatus status = verificationService.determineTamperStatus(testHash, consensus);
        
        assertThat(status).isEqualTo(TamperStatus.UNKNOWN);
    }
    
    @Test
    void testCalculateSuspicionLevel_Verified() {
        ConsensusResult consensus = createConsensusResult(
            testHash, 0.85, 15, ConsensusStatus.ESTABLISHED, ConsensusLifecycle.ESTABLISHED
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        SuspicionLevel level = verificationService.calculateSuspicionLevel(TamperStatus.VERIFIED, consensus);
        
        assertThat(level).isEqualTo(SuspicionLevel.NONE);
    }
    
    @Test
    void testCalculateSuspicionLevel_Tampered_High() {
        ConsensusResult consensus = createConsensusResult(
            testHash, 0.80, 15, ConsensusStatus.ESTABLISHED, ConsensusLifecycle.ESTABLISHED
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        SuspicionLevel level = verificationService.calculateSuspicionLevel(TamperStatus.TAMPERED, consensus);
        
        assertThat(level).isEqualTo(SuspicionLevel.HIGH);
    }
    
    @Test
    void testCalculateSuspicionLevel_Tampered_Critical() {
        ConsensusResult consensus = createConsensusResult(
            testHash, 0.95, 50, ConsensusStatus.ESTABLISHED, ConsensusLifecycle.ESTABLISHED
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        SuspicionLevel level = verificationService.calculateSuspicionLevel(TamperStatus.TAMPERED, consensus);
        
        assertThat(level).isEqualTo(SuspicionLevel.CRITICAL);
    }
    
    @Test
    void testCalculateSuspicionLevel_Suspicious_Low() {
        // Confidence between 50-70%
        ConsensusResult consensus = createConsensusResult(
            testHash, 0.60, 10, ConsensusStatus.NO_CONSENSUS, ConsensusLifecycle.LOW_CONFIDENCE
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        SuspicionLevel level = verificationService.calculateSuspicionLevel(
            TamperStatus.SUSPICIOUS_LOW_CONFIDENCE, consensus
        );
        
        assertThat(level).isEqualTo(SuspicionLevel.LOW);
    }
    
    @Test
    void testCalculateSuspicionLevel_Suspicious_Medium() {
        // Confidence between 30-50%
        ConsensusResult consensus = createConsensusResult(
            testHash, 0.40, 10, ConsensusStatus.NO_CONSENSUS, ConsensusLifecycle.LOW_CONFIDENCE
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        SuspicionLevel level = verificationService.calculateSuspicionLevel(
            TamperStatus.SUSPICIOUS_LOW_CONFIDENCE, consensus
        );
        
        assertThat(level).isEqualTo(SuspicionLevel.MEDIUM);
    }
    
    @Test
    void testCalculateSuspicionLevel_Unknown() {
        ConsensusResult consensus = createConsensusResult(
            null, 0.0, 0, ConsensusStatus.INSUFFICIENT_DATA, ConsensusLifecycle.UNKNOWN
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        SuspicionLevel level = verificationService.calculateSuspicionLevel(TamperStatus.UNKNOWN, consensus);
        
        assertThat(level).isEqualTo(SuspicionLevel.LOW);
    }
    
    @Test
    void testVerifyHash_ReturnsCompleteResult() {
        // Verify that all fields are populated in the result
        ConsensusResult consensus = createConsensusResult(
            consensusHash, 0.85, 15, ConsensusStatus.ESTABLISHED, ConsensusLifecycle.ESTABLISHED
        );
        
        VerificationService verificationService = createVerificationService(consensus);
        VerificationResult result = verificationService.verifyHash(testHash, testIdentity);
        
        assertThat(result.getStatus()).isNotNull();
        assertThat(result.getConfidence()).isNotNull();
        assertThat(result.getSubmissionCount()).isNotNull();
        assertThat(result.getConsensusHash()).isNotNull();
        assertThat(result.getMessage()).isNotBlank();
        assertThat(result.getSuspicionLevel()).isNotNull();
        assertThat(result.getRecommendedAction()).isNotBlank();
        assertThat(result.getTimestamp()).isNotNull();
    }
    
    /**
     * Helper method to create ConsensusResult for testing
     */
    private ConsensusResult createConsensusResult(String consensusHash, double confidence, 
                                                  int submissionCount, ConsensusStatus status,
                                                  ConsensusLifecycle lifecycle) {
        ConsensusResult result = new ConsensusResult(
            testIdentity, consensusHash, confidence, submissionCount, status, lifecycle
        );
        return result;
    }
}
