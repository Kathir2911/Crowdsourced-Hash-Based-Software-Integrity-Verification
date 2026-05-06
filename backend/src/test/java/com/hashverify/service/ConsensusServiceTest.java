package com.hashverify.service;

import com.hashverify.model.ConsensusLifecycle;
import com.hashverify.model.ConsensusResult;
import com.hashverify.model.ConsensusStatus;
import com.hashverify.model.HashSubmission;
import com.hashverify.model.SoftwareIdentity;
import com.hashverify.repository.ConsensusCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConsensusService
 * Tests requirements 4.1, 4.2, 4.3, 4.5
 */
@ExtendWith(MockitoExtension.class)
class ConsensusServiceTest {
    
    // Use stub instead of mock for concrete class
    private StubSubmissionStorageService submissionStorageService;
    
    @Mock
    private ConsensusCacheRepository consensusCacheRepository;
    
    private ConsensusService consensusService;
    
    /**
     * Stub implementation of SubmissionStorageService for testing
     */
    private static class StubSubmissionStorageService extends SubmissionStorageService {
        private final Map<String, List<HashSubmission>> submissionsByIdentityHash = new HashMap<>();
        private HashSubmission lastSaved;
        
        public StubSubmissionStorageService() {
            super(null); // Pass null for repository since we're stubbing
        }
        
        @Override
        public List<HashSubmission> findRecentByIdentityHash(String identityHash) {
            return submissionsByIdentityHash.getOrDefault(identityHash, new ArrayList<>());
        }
        
        @Override
        public HashSubmission saveSubmission(HashSubmission submission) {
            lastSaved = submission;
            return submission;
        }
        
        public void setSubmissionsForIdentityHash(String identityHash, List<HashSubmission> submissions) {
            submissionsByIdentityHash.put(identityHash, submissions);
        }
        
        public HashSubmission getLastSaved() {
            return lastSaved;
        }
    }
    
    private static final String TEST_IDENTITY_HASH = "test-identity-hash-123";
    private static final String CONSENSUS_HASH = "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd";
    private static final String MINORITY_HASH = "b2c3d4e5f6789012345678901234567890123456789012345678901234abcde";
    
    @BeforeEach
    void setUp() {
        submissionStorageService = new StubSubmissionStorageService();
        consensusService = new ConsensusService(submissionStorageService, consensusCacheRepository);
        
        // Set configuration values
        ReflectionTestUtils.setField(consensusService, "consensusThreshold", 0.70);
        ReflectionTestUtils.setField(consensusService, "minimumSubmissions", 3);
        ReflectionTestUtils.setField(consensusService, "submissionTtlDays", 90);
        ReflectionTestUtils.setField(consensusService, "cacheTtlHours", 24);
    }
    
    @Test
    void testCalculateConsensus_NoSubmissions_ReturnsUnknown() {
        // Given
        when(consensusCacheRepository.findBySoftwareIdentityHash(TEST_IDENTITY_HASH))
            .thenReturn(Optional.empty());
        submissionStorageService.setSubmissionsForIdentityHash(TEST_IDENTITY_HASH, new ArrayList<>());
        when(consensusCacheRepository.save(any(ConsensusResult.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        ConsensusResult result = consensusService.calculateConsensus(TEST_IDENTITY_HASH);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ConsensusStatus.INSUFFICIENT_DATA);
        assertThat(result.getLifecycle()).isEqualTo(ConsensusLifecycle.UNKNOWN);
        assertThat(result.getConfidence()).isEqualTo(0.0);
        assertThat(result.getSubmissionCount()).isEqualTo(0);
        assertThat(result.getConsensusHash()).isNull();
        
        verify(consensusCacheRepository).save(any(ConsensusResult.class));
    }
    
    @Test
    void testCalculateConsensus_BelowMinimumThreshold_ReturnsInsufficientData() {
        // Given - only 2 submissions (below minimum of 3)
        List<HashSubmission> submissions = createSubmissions(2, CONSENSUS_HASH);
        
        when(consensusCacheRepository.findBySoftwareIdentityHash(TEST_IDENTITY_HASH))
            .thenReturn(Optional.empty());
        submissionStorageService.setSubmissionsForIdentityHash(TEST_IDENTITY_HASH, submissions);
        when(consensusCacheRepository.save(any(ConsensusResult.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        ConsensusResult result = consensusService.calculateConsensus(TEST_IDENTITY_HASH);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ConsensusStatus.INSUFFICIENT_DATA);
        assertThat(result.getLifecycle()).isEqualTo(ConsensusLifecycle.LOW_CONFIDENCE);
        assertThat(result.getSubmissionCount()).isEqualTo(2);
        assertThat(result.getConfidence()).isEqualTo(1.0); // All submissions agree
    }
    
    @Test
    void testCalculateConsensus_MeetsThreshold_ReturnsEstablished() {
        // Given - 5 submissions, 4 with same hash (80% consensus)
        List<HashSubmission> submissions = new ArrayList<>();
        submissions.addAll(createSubmissions(4, CONSENSUS_HASH));
        submissions.addAll(createSubmissions(1, MINORITY_HASH));
        
        when(consensusCacheRepository.findBySoftwareIdentityHash(TEST_IDENTITY_HASH))
            .thenReturn(Optional.empty());
        submissionStorageService.setSubmissionsForIdentityHash(TEST_IDENTITY_HASH, submissions);
        when(consensusCacheRepository.save(any(ConsensusResult.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        ConsensusResult result = consensusService.calculateConsensus(TEST_IDENTITY_HASH);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ConsensusStatus.ESTABLISHED);
        assertThat(result.getLifecycle()).isEqualTo(ConsensusLifecycle.ESTABLISHED);
        assertThat(result.getConsensusHash()).isEqualTo(CONSENSUS_HASH);
        assertThat(result.getConfidence()).isEqualTo(0.8);
        assertThat(result.getSubmissionCount()).isEqualTo(5);
    }
    
    @Test
    void testCalculateConsensus_BelowConsensusThreshold_ReturnsNoConsensus() {
        // Given - 5 submissions, 3 with same hash (60% - below 70% threshold)
        List<HashSubmission> submissions = new ArrayList<>();
        submissions.addAll(createSubmissions(3, CONSENSUS_HASH));
        submissions.addAll(createSubmissions(2, MINORITY_HASH));
        
        when(consensusCacheRepository.findBySoftwareIdentityHash(TEST_IDENTITY_HASH))
            .thenReturn(Optional.empty());
        submissionStorageService.setSubmissionsForIdentityHash(TEST_IDENTITY_HASH, submissions);
        when(consensusCacheRepository.save(any(ConsensusResult.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        ConsensusResult result = consensusService.calculateConsensus(TEST_IDENTITY_HASH);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ConsensusStatus.NO_CONSENSUS);
        assertThat(result.getLifecycle()).isEqualTo(ConsensusLifecycle.LOW_CONFIDENCE);
        assertThat(result.getConfidence()).isEqualTo(0.6);
        assertThat(result.getSubmissionCount()).isEqualTo(5);
    }
    
    @Test
    void testCalculateConsensus_ExactlyAtThreshold_ReturnsEstablished() {
        // Given - 10 submissions, 7 with same hash (exactly 70%)
        List<HashSubmission> submissions = new ArrayList<>();
        submissions.addAll(createSubmissions(7, CONSENSUS_HASH));
        submissions.addAll(createSubmissions(3, MINORITY_HASH));
        
        when(consensusCacheRepository.findBySoftwareIdentityHash(TEST_IDENTITY_HASH))
            .thenReturn(Optional.empty());
        submissionStorageService.setSubmissionsForIdentityHash(TEST_IDENTITY_HASH, submissions);
        when(consensusCacheRepository.save(any(ConsensusResult.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        ConsensusResult result = consensusService.calculateConsensus(TEST_IDENTITY_HASH);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ConsensusStatus.ESTABLISHED);
        assertThat(result.getLifecycle()).isEqualTo(ConsensusLifecycle.ESTABLISHED);
        assertThat(result.getConfidence()).isEqualTo(0.7);
    }
    
    @Test
    void testCalculateConsensus_UsesCachedResult_WhenNotStale() {
        // Given
        ConsensusResult cachedResult = createCachedResult();
        cachedResult.setExpiresAt(LocalDateTime.now().plusHours(1)); // Not expired
        
        when(consensusCacheRepository.findBySoftwareIdentityHash(TEST_IDENTITY_HASH))
            .thenReturn(Optional.of(cachedResult));
        
        // When
        ConsensusResult result = consensusService.calculateConsensus(TEST_IDENTITY_HASH);
        
        // Then
        assertThat(result).isEqualTo(cachedResult);
        // Verify stub was not called (no submissions set)
        verify(consensusCacheRepository, never()).save(any(ConsensusResult.class));
    }
    
    @Test
    void testCalculateConsensus_RecalculatesWhenCacheStale() {
        // Given
        ConsensusResult staleResult = createCachedResult();
        staleResult.setExpiresAt(LocalDateTime.now().minusHours(1)); // Expired
        
        List<HashSubmission> submissions = createSubmissions(5, CONSENSUS_HASH);
        
        when(consensusCacheRepository.findBySoftwareIdentityHash(TEST_IDENTITY_HASH))
            .thenReturn(Optional.of(staleResult));
        submissionStorageService.setSubmissionsForIdentityHash(TEST_IDENTITY_HASH, submissions);
        when(consensusCacheRepository.save(any(ConsensusResult.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        ConsensusResult result = consensusService.calculateConsensus(TEST_IDENTITY_HASH);
        
        // Then
        assertThat(result).isNotNull();
        verify(consensusCacheRepository).save(any(ConsensusResult.class));
    }
    
    @Test
    void testCalculateConfidence_CorrectlyCalculatesPercentage() {
        // Given
        List<HashSubmission> submissions = new ArrayList<>();
        submissions.addAll(createSubmissions(8, CONSENSUS_HASH));
        submissions.addAll(createSubmissions(2, MINORITY_HASH));
        
        // When
        double confidence = consensusService.calculateConfidence(submissions, CONSENSUS_HASH);
        
        // Then
        assertThat(confidence).isEqualTo(0.8);
    }
    
    @Test
    void testCalculateConfidence_EmptySubmissions_ReturnsZero() {
        // When
        double confidence = consensusService.calculateConfidence(new ArrayList<>(), CONSENSUS_HASH);
        
        // Then
        assertThat(confidence).isEqualTo(0.0);
    }
    
    @Test
    void testCalculateConfidence_NullConsensusHash_ReturnsZero() {
        // Given
        List<HashSubmission> submissions = createSubmissions(5, CONSENSUS_HASH);
        
        // When
        double confidence = consensusService.calculateConfidence(submissions, null);
        
        // Then
        assertThat(confidence).isEqualTo(0.0);
    }
    
    @Test
    void testInvalidateCache_SetsExpirationInPast() {
        // Given
        ConsensusResult cachedResult = createCachedResult();
        cachedResult.setExpiresAt(LocalDateTime.now().plusHours(1));
        
        when(consensusCacheRepository.findBySoftwareIdentityHash(TEST_IDENTITY_HASH))
            .thenReturn(Optional.of(cachedResult));
        when(consensusCacheRepository.save(any(ConsensusResult.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        consensusService.invalidateCache(TEST_IDENTITY_HASH);
        
        // Then
        verify(consensusCacheRepository).save(argThat(result -> 
            result.getExpiresAt().isBefore(LocalDateTime.now())
        ));
    }
    
    @Test
    void testGetConsensus_ReturnsCachedWhenAvailable() {
        // Given
        ConsensusResult cachedResult = createCachedResult();
        cachedResult.setExpiresAt(LocalDateTime.now().plusHours(1));
        
        when(consensusCacheRepository.findBySoftwareIdentityHash(TEST_IDENTITY_HASH))
            .thenReturn(Optional.of(cachedResult));
        
        // When
        Optional<ConsensusResult> result = consensusService.getConsensus(TEST_IDENTITY_HASH);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(cachedResult);
    }
    
    @Test
    void testHashDistribution_CorrectlyCounts() {
        // Given - 10 submissions with 3 different hashes
        List<HashSubmission> submissions = new ArrayList<>();
        submissions.addAll(createSubmissions(6, CONSENSUS_HASH));
        submissions.addAll(createSubmissions(3, MINORITY_HASH));
        submissions.addAll(createSubmissions(1, "c3d4e5f6789012345678901234567890123456789012345678901234abcdef"));
        
        when(consensusCacheRepository.findBySoftwareIdentityHash(TEST_IDENTITY_HASH))
            .thenReturn(Optional.empty());
        submissionStorageService.setSubmissionsForIdentityHash(TEST_IDENTITY_HASH, submissions);
        when(consensusCacheRepository.save(any(ConsensusResult.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        ConsensusResult result = consensusService.calculateConsensus(TEST_IDENTITY_HASH);
        
        // Then
        assertThat(result.getHashDistribution()).hasSize(3);
        assertThat(result.getHashDistribution().get(CONSENSUS_HASH)).isEqualTo(6);
        assertThat(result.getHashDistribution().get(MINORITY_HASH)).isEqualTo(3);
    }
    
    // Helper methods
    
    private List<HashSubmission> createSubmissions(int count, String hash) {
        List<HashSubmission> submissions = new ArrayList<>();
        SoftwareIdentity identity = createSoftwareIdentity();
        
        for (int i = 0; i < count; i++) {
            HashSubmission submission = new HashSubmission();
            submission.setSoftwareIdentity(identity);
            submission.setHash(hash);
            submission.setTimestamp(LocalDateTime.now().minusDays(i));
            submission.setClientVersion("1.0.0");
            submission.setReplayProtectionHash("replay-hash-" + i);
            submissions.add(submission);
        }
        
        return submissions;
    }
    
    private SoftwareIdentity createSoftwareIdentity() {
        SoftwareIdentity identity = new SoftwareIdentity();
        identity.setSourceDomain("example.com");
        identity.setNormalizedFilename("test.exe");
        identity.setFileSize(1024L);
        return identity;
    }
    
    private ConsensusResult createCachedResult() {
        SoftwareIdentity identity = createSoftwareIdentity();
        ConsensusResult result = new ConsensusResult(
            identity,
            CONSENSUS_HASH,
            0.8,
            5,
            ConsensusStatus.ESTABLISHED,
            ConsensusLifecycle.ESTABLISHED
        );
        result.setExpiresAt(LocalDateTime.now().plusHours(24));
        return result;
    }
}
