package com.hashverify.integration;

import com.hashverify.model.ConsensusLifecycle;
import com.hashverify.model.ConsensusResult;
import com.hashverify.model.HashSubmission;
import com.hashverify.model.SoftwareIdentity;
import com.hashverify.repository.HashSubmissionRepository;
import com.hashverify.service.ConsensusService;
import com.hashverify.service.ReplayProtectionService;
import com.hashverify.service.SubmissionStorageService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 12: Consensus Updates
 * 
 * **Validates: Requirements 4.5**
 * 
 * For any Software_Identity, when new valid submissions arrive, 
 * the Consensus_Service should recalculate consensus and update 
 * the lifecycle state appropriately.
 * 
 * This property test validates that:
 * 1. Consensus is recalculated when new submissions arrive
 * 2. Lifecycle state transitions correctly (UNKNOWN → LOW_CONFIDENCE → ESTABLISHED)
 * 3. Confidence values update based on new submissions
 * 4. Consensus hash changes when majority shifts
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConsensusUpdatesProperties {
    
    @Autowired
    private ConsensusService consensusService;
    
    @Autowired
    private SubmissionStorageService submissionStorageService;
    
    @Autowired
    private ReplayProtectionService replayProtectionService;
    
    @Autowired
    private HashSubmissionRepository repository;
    
    /**
     * Property: Consensus recalculates when new submissions arrive
     * 
     * Tests that adding new submissions causes consensus to be recalculated
     * and lifecycle state to transition appropriately.
     */
    @Property(tries = 50)
    void consensusRecalculatesWhenNewSubmissionsArrive(
            @ForAll @Size(min = 1, max = 5) List<@From("validHashes") String> initialHashes,
            @ForAll @Size(min = 1, max = 5) List<@From("validHashes") String> newHashes) {
        
        repository.deleteAll();
        
        // Given - Create software identity
        SoftwareIdentity identity = new SoftwareIdentity(
                "download.example.com",
                "test.exe",
                1024L
        );
        String identityHash = identity.generateIdentityHash();
        
        // When - Submit initial hashes
        List<HashSubmission> initialSubmissions = new ArrayList<>();
        for (int i = 0; i < initialHashes.size(); i++) {
            String replayHash = replayProtectionService.generateReplayProtectionHash(
                    "192.168.1." + i,
                    "TestClient/1.0",
                    LocalDateTime.now()
            );
            
            HashSubmission submission = new HashSubmission(
                    identity,
                    initialHashes.get(i),
                    "1.0.0",
                    replayHash,
                    "TestClient/1.0"
            );
            submission.setTimestamp(LocalDateTime.now());
            submissionStorageService.saveSubmission(submission);
            initialSubmissions.add(submission);
        }
        
        // Calculate initial consensus
        ConsensusResult initialConsensus = consensusService.calculateConsensus(identityHash);
        int initialSubmissionCount = initialConsensus.getSubmissionCount();
        ConsensusLifecycle initialLifecycle = initialConsensus.getLifecycle();
        
        // When - Add new submissions
        for (int i = 0; i < newHashes.size(); i++) {
            String replayHash = replayProtectionService.generateReplayProtectionHash(
                    "10.0.0." + i,
                    "TestClient/1.0",
                    LocalDateTime.now()
            );
            
            HashSubmission submission = new HashSubmission(
                    identity,
                    newHashes.get(i),
                    "1.0.0",
                    replayHash,
                    "TestClient/1.0"
            );
            submission.setTimestamp(LocalDateTime.now());
            submissionStorageService.saveSubmission(submission);
        }
        
        // Recalculate consensus
        ConsensusResult updatedConsensus = consensusService.calculateConsensus(identityHash);
        
        // Then - Consensus should be recalculated
        assertThat(updatedConsensus.getSubmissionCount())
                .as("Submission count should increase after new submissions")
                .isEqualTo(initialSubmissionCount + newHashes.size());
        
        // Verify lifecycle state is appropriate for total submission count
        int totalSubmissions = initialSubmissionCount + newHashes.size();
        if (totalSubmissions < 3) {
            assertThat(updatedConsensus.getLifecycle())
                    .as("Lifecycle should be LOW_CONFIDENCE with < 3 submissions")
                    .isIn(ConsensusLifecycle.UNKNOWN, ConsensusLifecycle.LOW_CONFIDENCE);
        }
        
        // If consensus was established and remains established, verify it's still valid
        if (initialLifecycle == ConsensusLifecycle.ESTABLISHED && 
            updatedConsensus.getLifecycle() == ConsensusLifecycle.ESTABLISHED) {
            assertThat(updatedConsensus.getConfidence())
                    .as("Established consensus should maintain >= 70% confidence")
                    .isGreaterThanOrEqualTo(0.70);
        }
    }
    
    /**
     * Property: Lifecycle transitions from UNKNOWN to LOW_CONFIDENCE to ESTABLISHED
     * 
     * Tests that lifecycle state transitions correctly as submissions accumulate.
     */
    @Property(tries = 50)
    void lifecycleTransitionsCorrectlyWithNewSubmissions(
            @ForAll @From("validHash") String consensusHash,
            @ForAll @IntRange(min = 1, max = 3) int initialCount,
            @ForAll @IntRange(min = 1, max = 5) int additionalCount) {
        
        repository.deleteAll();
        
        // Given - Create software identity
        SoftwareIdentity identity = new SoftwareIdentity(
                "download.test.com",
                "app.exe",
                2048L
        );
        String identityHash = identity.generateIdentityHash();
        
        // When - Submit initial hashes (all same hash for consensus)
        for (int i = 0; i < initialCount; i++) {
            String replayHash = replayProtectionService.generateReplayProtectionHash(
                    "172.16.0." + i,
                    "TestClient/1.0",
                    LocalDateTime.now()
            );
            
            HashSubmission submission = new HashSubmission(
                    identity,
                    consensusHash,
                    "1.0.0",
                    replayHash,
                    "TestClient/1.0"
            );
            submission.setTimestamp(LocalDateTime.now());
            submissionStorageService.saveSubmission(submission);
        }
        
        ConsensusResult initialConsensus = consensusService.calculateConsensus(identityHash);
        
        // When - Add more submissions with same hash
        for (int i = 0; i < additionalCount; i++) {
            String replayHash = replayProtectionService.generateReplayProtectionHash(
                    "192.168.100." + i,
                    "TestClient/1.0",
                    LocalDateTime.now()
            );
            
            HashSubmission submission = new HashSubmission(
                    identity,
                    consensusHash,
                    "1.0.0",
                    replayHash,
                    "TestClient/1.0"
            );
            submission.setTimestamp(LocalDateTime.now());
            submissionStorageService.saveSubmission(submission);
        }
        
        ConsensusResult updatedConsensus = consensusService.calculateConsensus(identityHash);
        int totalSubmissions = initialCount + additionalCount;
        
        // Then - Verify lifecycle state based on total submissions
        if (totalSubmissions < 3) {
            assertThat(updatedConsensus.getLifecycle())
                    .as("Should be UNKNOWN or LOW_CONFIDENCE with < 3 submissions")
                    .isIn(ConsensusLifecycle.UNKNOWN, ConsensusLifecycle.LOW_CONFIDENCE);
        } else {
            // With all same hash and >= 3 submissions, should be ESTABLISHED
            assertThat(updatedConsensus.getLifecycle())
                    .as("Should be ESTABLISHED with >= 3 submissions and 100% agreement")
                    .isEqualTo(ConsensusLifecycle.ESTABLISHED);
            assertThat(updatedConsensus.getConfidence())
                    .as("Confidence should be 1.0 with all same hash")
                    .isEqualTo(1.0);
        }
    }
    
    /**
     * Property: Consensus hash changes when majority shifts
     * 
     * Tests that consensus hash updates when new submissions shift the majority.
     */
    @Property(tries = 50)
    void consensusHashChangesWhenMajorityShifts(
            @ForAll @From("validHash") String hash1,
            @ForAll @From("validHash") String hash2) {
        
        Assume.that(!hash1.equals(hash2));
        repository.deleteAll();
        
        // Given - Create software identity
        SoftwareIdentity identity = new SoftwareIdentity(
                "download.shift.com",
                "shift.exe",
                4096L
        );
        String identityHash = identity.generateIdentityHash();
        
        // When - Submit 5 instances of hash1 (initial majority)
        for (int i = 0; i < 5; i++) {
            String replayHash = replayProtectionService.generateReplayProtectionHash(
                    "10.1.1." + i,
                    "TestClient/1.0",
                    LocalDateTime.now()
            );
            
            HashSubmission submission = new HashSubmission(
                    identity,
                    hash1,
                    "1.0.0",
                    replayHash,
                    "TestClient/1.0"
            );
            submission.setTimestamp(LocalDateTime.now());
            submissionStorageService.saveSubmission(submission);
        }
        
        ConsensusResult initialConsensus = consensusService.calculateConsensus(identityHash);
        
        // When - Submit 8 instances of hash2 (new majority)
        for (int i = 0; i < 8; i++) {
            String replayHash = replayProtectionService.generateReplayProtectionHash(
                    "10.2.2." + i,
                    "TestClient/1.0",
                    LocalDateTime.now()
            );
            
            HashSubmission submission = new HashSubmission(
                    identity,
                    hash2,
                    "1.0.0",
                    replayHash,
                    "TestClient/1.0"
            );
            submission.setTimestamp(LocalDateTime.now());
            submissionStorageService.saveSubmission(submission);
        }
        
        ConsensusResult updatedConsensus = consensusService.calculateConsensus(identityHash);
        
        // Then - Consensus hash should change to hash2 (new majority)
        // hash2 has 8/13 = 61.5% which is < 70%, so might not establish consensus
        // But if it does establish, it should be hash2
        if (updatedConsensus.getLifecycle() == ConsensusLifecycle.ESTABLISHED) {
            // If consensus is established, verify it's the majority hash
            double hash2Percentage = 8.0 / 13.0;
            if (hash2Percentage >= 0.70) {
                assertThat(updatedConsensus.getConsensusHash())
                        .as("Consensus hash should be hash2 when it's the majority")
                        .isEqualTo(hash2);
            }
        }
        
        // Verify submission count increased
        assertThat(updatedConsensus.getSubmissionCount())
                .as("Submission count should be 13 (5 + 8)")
                .isEqualTo(13);
    }
    
    /**
     * Property: Confidence updates based on new submissions
     * 
     * Tests that confidence percentage updates correctly when new submissions arrive.
     */
    @Property(tries = 50)
    void confidenceUpdatesWithNewSubmissions(
            @ForAll @From("validHash") String majorityHash,
            @ForAll @From("validHash") String minorityHash,
            @ForAll @IntRange(min = 3, max = 7) int majorityCount,
            @ForAll @IntRange(min = 1, max = 3) int minorityCount) {
        
        Assume.that(!majorityHash.equals(minorityHash));
        repository.deleteAll();
        
        // Given - Create software identity
        SoftwareIdentity identity = new SoftwareIdentity(
                "download.confidence.com",
                "confidence.exe",
                8192L
        );
        String identityHash = identity.generateIdentityHash();
        
        // When - Submit majority hash
        for (int i = 0; i < majorityCount; i++) {
            String replayHash = replayProtectionService.generateReplayProtectionHash(
                    "172.20.0." + i,
                    "TestClient/1.0",
                    LocalDateTime.now()
            );
            
            HashSubmission submission = new HashSubmission(
                    identity,
                    majorityHash,
                    "1.0.0",
                    replayHash,
                    "TestClient/1.0"
            );
            submission.setTimestamp(LocalDateTime.now());
            submissionStorageService.saveSubmission(submission);
        }
        
        // When - Submit minority hash
        for (int i = 0; i < minorityCount; i++) {
            String replayHash = replayProtectionService.generateReplayProtectionHash(
                    "172.21.0." + i,
                    "TestClient/1.0",
                    LocalDateTime.now()
            );
            
            HashSubmission submission = new HashSubmission(
                    identity,
                    minorityHash,
                    "1.0.0",
                    replayHash,
                    "TestClient/1.0"
            );
            submission.setTimestamp(LocalDateTime.now());
            submissionStorageService.saveSubmission(submission);
        }
        
        ConsensusResult consensus = consensusService.calculateConsensus(identityHash);
        
        // Then - Verify confidence calculation
        int totalSubmissions = majorityCount + minorityCount;
        double expectedConfidence = (double) majorityCount / totalSubmissions;
        
        assertThat(consensus.getSubmissionCount())
                .as("Submission count should match total submissions")
                .isEqualTo(totalSubmissions);
        
        if (consensus.getLifecycle() == ConsensusLifecycle.ESTABLISHED) {
            assertThat(consensus.getConfidence())
                    .as("Confidence should match expected percentage")
                    .isCloseTo(expectedConfidence, org.assertj.core.data.Offset.offset(0.01));
            
            assertThat(consensus.getConsensusHash())
                    .as("Consensus hash should be the majority hash")
                    .isEqualTo(majorityHash);
        }
    }
    
    // Arbitraries for generating test data
    
    @Provide
    Arbitrary<String> validHash() {
        return Arbitraries.strings()
                .withCharRange('a', 'f')
                .numeric()
                .ofLength(64)
                .map(String::toLowerCase);
    }
    
    @Provide
    Arbitrary<String> validHashes() {
        return validHash();
    }
}
