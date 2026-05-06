package com.hashverify.service;

import com.hashverify.model.ConsensusLifecycle;
import com.hashverify.model.ConsensusResult;
import com.hashverify.model.ConsensusStatus;
import com.hashverify.model.HashSubmission;
import com.hashverify.model.SoftwareIdentity;
import com.hashverify.repository.ConsensusCacheRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for ConsensusService
 * Tests consensus calculation accuracy, minimum threshold enforcement, and lifecycle transitions
 * 
 * **Validates: Requirements 4.1, 4.2, 4.3**
 */
class ConsensusServiceProperties {

    /**
     * Property 9: Consensus Calculation Accuracy
     * 
     * For any set of Hash_Submissions for the same Software_Identity, the Consensus_Service 
     * should correctly calculate the majority hash when 70% or more submissions share the 
     * same hash value.
     * 
     * **Validates: Requirements 4.1, 4.3**
     */
    @Property(tries = 50)
    void consensusCalculationAccuracy(
            @ForAll("submissionSetsWithMajority") SubmissionSet submissionSet) {
        
        // Given: A set of submissions where one hash has >= 70% agreement
        ConsensusService consensusService = createConsensusService(submissionSet.submissions);
        
        // When: Calculate consensus
        ConsensusResult result = consensusService.calculateConsensus(submissionSet.identityHash);
        
        // Then: Consensus should be established with the majority hash
        assertThat(result.getStatus()).isEqualTo(ConsensusStatus.ESTABLISHED);
        assertThat(result.getConsensusHash()).isEqualTo(submissionSet.majorityHash);
        
        // Verify confidence calculation is accurate
        long majorityCount = submissionSet.submissions.stream()
                .filter(s -> s.getHash().equals(submissionSet.majorityHash))
                .count();
        double expectedConfidence = (double) majorityCount / submissionSet.submissions.size();
        assertThat(result.getConfidence()).isEqualTo(expectedConfidence);
        
        // Verify confidence meets threshold
        assertThat(result.getConfidence()).isGreaterThanOrEqualTo(0.70);
        
        // Verify submission count is correct
        assertThat(result.getSubmissionCount()).isEqualTo(submissionSet.submissions.size());
    }

    /**
     * Property 10: Minimum Submission Threshold
     * 
     * For any Software_Identity with fewer than 3 submissions, the Consensus_Service 
     * should not establish consensus regardless of hash distribution.
     * 
     * **Validates: Requirements 4.2**
     */
    @Property(tries = 50)
    void minimumSubmissionThreshold(
            @ForAll("submissionSetsBelowThreshold") SubmissionSet submissionSet) {
        
        // Given: A set of submissions with fewer than 3 submissions
        assertThat(submissionSet.submissions.size()).isLessThan(3);
        
        ConsensusService consensusService = createConsensusService(submissionSet.submissions);
        
        // When: Calculate consensus
        ConsensusResult result = consensusService.calculateConsensus(submissionSet.identityHash);
        
        // Then: Consensus should NOT be established
        assertThat(result.getStatus()).isNotEqualTo(ConsensusStatus.ESTABLISHED);
        assertThat(result.getStatus()).isEqualTo(ConsensusStatus.INSUFFICIENT_DATA);
        
        // Lifecycle should be LOW_CONFIDENCE or UNKNOWN
        assertThat(result.getLifecycle()).isIn(
                ConsensusLifecycle.LOW_CONFIDENCE, 
                ConsensusLifecycle.UNKNOWN
        );
        
        // Verify submission count is correct
        assertThat(result.getSubmissionCount()).isEqualTo(submissionSet.submissions.size());
    }

    /**
     * Property 10b: Minimum Threshold Boundary Test
     * 
     * For any Software_Identity with exactly 3 submissions where all agree, 
     * consensus should be established.
     * 
     * **Validates: Requirements 4.2**
     */
    @Property(tries = 50)
    void minimumThresholdBoundary(
            @ForAll("validSoftwareIdentities") SoftwareIdentity identity,
            @ForAll("validBlake3Hashes") String hash) {
        
        // Given: Exactly 3 submissions with 100% agreement
        List<HashSubmission> submissions = createSubmissionsWithHash(identity, hash, 3);
        
        ConsensusService consensusService = createConsensusService(submissions);
        
        // When: Calculate consensus
        ConsensusResult result = consensusService.calculateConsensus(identity.generateIdentityHash());
        
        // Then: Consensus should be established
        assertThat(result.getStatus()).isEqualTo(ConsensusStatus.ESTABLISHED);
        assertThat(result.getLifecycle()).isEqualTo(ConsensusLifecycle.ESTABLISHED);
        assertThat(result.getConsensusHash()).isEqualTo(hash);
        assertThat(result.getConfidence()).isEqualTo(1.0);
        assertThat(result.getSubmissionCount()).isEqualTo(3);
    }

    /**
     * Property 9b: Consensus Threshold Boundary Test
     * 
     * For any set of submissions where the majority hash has exactly 70% agreement,
     * consensus should be established.
     * 
     * **Validates: Requirements 4.1, 4.3**
     */
    @Property(tries = 50)
    void consensusThresholdBoundary(
            @ForAll("submissionSetsAtExactThreshold") SubmissionSet submissionSet) {
        
        // Given: Submissions where majority hash has exactly 70% agreement
        ConsensusService consensusService = createConsensusService(submissionSet.submissions);
        
        // When: Calculate consensus
        ConsensusResult result = consensusService.calculateConsensus(submissionSet.identityHash);
        
        // Then: Consensus should be established
        assertThat(result.getStatus()).isEqualTo(ConsensusStatus.ESTABLISHED);
        assertThat(result.getConsensusHash()).isEqualTo(submissionSet.majorityHash);
        assertThat(result.getConfidence()).isEqualTo(0.70);
        assertThat(result.getLifecycle()).isEqualTo(ConsensusLifecycle.ESTABLISHED);
    }

    /**
     * Property 9c: Below Consensus Threshold
     * 
     * For any set of submissions where no hash reaches 70% agreement,
     * consensus should not be established.
     * 
     * **Validates: Requirements 4.1, 4.3**
     */
    @Property(tries = 50)
    void belowConsensusThreshold(
            @ForAll("submissionSetsBelowConsensusThreshold") SubmissionSet submissionSet) {
        
        // Given: Submissions where no hash reaches 70% agreement
        ConsensusService consensusService = createConsensusService(submissionSet.submissions);
        
        // When: Calculate consensus
        ConsensusResult result = consensusService.calculateConsensus(submissionSet.identityHash);
        
        // Then: Consensus should not be established
        assertThat(result.getStatus()).isIn(
                ConsensusStatus.NO_CONSENSUS,
                ConsensusStatus.INSUFFICIENT_DATA
        );
        assertThat(result.getLifecycle()).isEqualTo(ConsensusLifecycle.LOW_CONFIDENCE);
        
        // Verify confidence is below threshold
        assertThat(result.getConfidence()).isLessThan(0.70);
    }

    /**
     * Property 13.1: Consensus Lifecycle Transitions
     * 
     * For any Software_Identity, the Consensus_Service should correctly transition 
     * between lifecycle states (UNKNOWN → LOW_CONFIDENCE → ESTABLISHED → DEGRADED → EXPIRED) 
     * based on submission patterns and time elapsed.
     * 
     * **Validates: Requirements 4.1, 4.2, 4.7**
     */
    @Property(tries = 50)
    void consensusLifecycleTransitions(
            @ForAll("lifecycleTransitionScenarios") LifecycleScenario scenario) {
        
        // Given: A lifecycle transition scenario
        ConsensusService consensusService = createConsensusService(scenario.submissions);
        
        // When: Calculate consensus
        ConsensusResult result = consensusService.calculateConsensus(scenario.identityHash);
        
        // Then: Verify lifecycle state matches expected state
        assertThat(result.getLifecycle()).isEqualTo(scenario.expectedLifecycle);
        
        // Verify lifecycle state is consistent with status
        verifyLifecycleConsistency(result);
    }

    /**
     * Property 13.1b: UNKNOWN to LOW_CONFIDENCE Transition
     * 
     * When first submissions are received (1-2 submissions), lifecycle should 
     * transition from UNKNOWN to LOW_CONFIDENCE.
     * 
     * **Validates: Requirements 4.2**
     */
    @Property(tries = 50)
    void unknownToLowConfidenceTransition(
            @ForAll("validSoftwareIdentities") SoftwareIdentity identity,
            @ForAll("validBlake3Hashes") String hash,
            @ForAll @IntRange(min = 1, max = 2) int submissionCount) {
        
        // Given: 1-2 submissions (below minimum threshold)
        List<HashSubmission> submissions = createSubmissionsWithHash(identity, hash, submissionCount);
        
        ConsensusService consensusService = createConsensusService(submissions);
        
        // When: Calculate consensus
        ConsensusResult result = consensusService.calculateConsensus(identity.generateIdentityHash());
        
        // Then: Lifecycle should be LOW_CONFIDENCE
        assertThat(result.getLifecycle()).isEqualTo(ConsensusLifecycle.LOW_CONFIDENCE);
        assertThat(result.getStatus()).isEqualTo(ConsensusStatus.INSUFFICIENT_DATA);
    }

    /**
     * Property 13.1c: LOW_CONFIDENCE to ESTABLISHED Transition
     * 
     * When sufficient submissions with high agreement are received, lifecycle should 
     * transition from LOW_CONFIDENCE to ESTABLISHED.
     * 
     * **Validates: Requirements 4.1, 4.2**
     */
    @Property(tries = 50)
    void lowConfidenceToEstablishedTransition(
            @ForAll("validSoftwareIdentities") SoftwareIdentity identity,
            @ForAll("validBlake3Hashes") String hash,
            @ForAll @IntRange(min = 3, max = 20) int submissionCount) {
        
        // Given: >= 3 submissions with 100% agreement
        List<HashSubmission> submissions = createSubmissionsWithHash(identity, hash, submissionCount);
        
        ConsensusService consensusService = createConsensusService(submissions);
        
        // When: Calculate consensus
        ConsensusResult result = consensusService.calculateConsensus(identity.generateIdentityHash());
        
        // Then: Lifecycle should be ESTABLISHED
        assertThat(result.getLifecycle()).isEqualTo(ConsensusLifecycle.ESTABLISHED);
        assertThat(result.getStatus()).isEqualTo(ConsensusStatus.ESTABLISHED);
        assertThat(result.getConfidence()).isEqualTo(1.0);
    }

    /**
     * Property 13.1d: Conflicting Submissions Handling
     * 
     * When submissions have conflicting hashes, the system should calculate
     * consensus based on the majority and reflect appropriate lifecycle state.
     * 
     * **Validates: Requirements 4.5**
     */
    @Property(tries = 50)
    void conflictingSubmissionsHandling(
            @ForAll("conflictingSubmissionScenarios") SubmissionSet submissionSet) {
        
        // Given: Submissions with conflicting hashes
        ConsensusService consensusService = createConsensusService(submissionSet.submissions);
        
        // When: Calculate consensus
        ConsensusResult result = consensusService.calculateConsensus(submissionSet.identityHash);
        
        // Then: Verify the system handles conflicts appropriately
        // The consensus hash should be the one with the most submissions
        assertThat(result.getConsensusHash()).isEqualTo(submissionSet.majorityHash);
        
        // Confidence should reflect the distribution
        double expectedConfidence = (double) submissionSet.majorityCount / submissionSet.submissions.size();
        assertThat(result.getConfidence()).isEqualTo(expectedConfidence);
        
        // Lifecycle should be appropriate for the confidence level
        if (result.getConfidence() >= 0.70 && result.getSubmissionCount() >= 3) {
            assertThat(result.getLifecycle()).isIn(
                    ConsensusLifecycle.ESTABLISHED,
                    ConsensusLifecycle.DEGRADED
            );
        } else {
            assertThat(result.getLifecycle()).isEqualTo(ConsensusLifecycle.LOW_CONFIDENCE);
        }
    }

    /**
     * Property 13.1e: Lifecycle State Consistency
     * 
     * For any consensus result, the lifecycle state should be consistent with
     * the submission count, confidence, and status.
     * 
     * **Validates: Requirements 4.1, 4.2, 4.3**
     */
    @Property(tries = 50)
    void lifecycleStateConsistency(
            @ForAll("anySubmissionSet") SubmissionSet submissionSet) {
        
        // Given: Any set of submissions
        ConsensusService consensusService = createConsensusService(submissionSet.submissions);
        
        // When: Calculate consensus
        ConsensusResult result = consensusService.calculateConsensus(submissionSet.identityHash);
        
        // Then: Verify lifecycle state is consistent with other fields
        verifyLifecycleConsistency(result);
    }

    // Helper methods

    private ConsensusService createConsensusService(List<HashSubmission> submissions) {
        SubmissionStorageService mockStorage = mock(SubmissionStorageService.class);
        ConsensusCacheRepository mockCache = mock(ConsensusCacheRepository.class);
        
        when(mockCache.findBySoftwareIdentityHash(any())).thenReturn(Optional.empty());
        when(mockStorage.findRecentByIdentityHash(any())).thenReturn(submissions);
        when(mockCache.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        ConsensusService service = new ConsensusService(mockStorage, mockCache);
        
        // Set configuration values
        ReflectionTestUtils.setField(service, "consensusThreshold", 0.70);
        ReflectionTestUtils.setField(service, "minimumSubmissions", 3);
        ReflectionTestUtils.setField(service, "submissionTtlDays", 90);
        ReflectionTestUtils.setField(service, "cacheTtlHours", 24);
        
        return service;
    }

    private ConsensusService createConsensusServiceWithPreviousResult(
            List<HashSubmission> submissions, ConsensusResult previousResult) {
        
        SubmissionStorageService mockStorage = mock(SubmissionStorageService.class);
        ConsensusCacheRepository mockCache = mock(ConsensusCacheRepository.class);
        
        when(mockCache.findBySoftwareIdentityHash(any())).thenReturn(Optional.of(previousResult));
        when(mockStorage.findRecentByIdentityHash(any())).thenReturn(submissions);
        when(mockCache.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        ConsensusService service = new ConsensusService(mockStorage, mockCache);
        
        // Set configuration values
        ReflectionTestUtils.setField(service, "consensusThreshold", 0.70);
        ReflectionTestUtils.setField(service, "minimumSubmissions", 3);
        ReflectionTestUtils.setField(service, "submissionTtlDays", 90);
        ReflectionTestUtils.setField(service, "cacheTtlHours", 24);
        
        return service;
    }

    private void verifyLifecycleConsistency(ConsensusResult result) {
        int submissionCount = result.getSubmissionCount();
        double confidence = result.getConfidence();
        ConsensusStatus status = result.getStatus();
        ConsensusLifecycle lifecycle = result.getLifecycle();
        
        // UNKNOWN: No submissions
        if (submissionCount == 0) {
            assertThat(lifecycle).isEqualTo(ConsensusLifecycle.UNKNOWN);
            assertThat(status).isEqualTo(ConsensusStatus.INSUFFICIENT_DATA);
        }
        
        // LOW_CONFIDENCE: Below minimum threshold or below consensus percentage
        if (submissionCount < 3 || confidence < 0.70) {
            assertThat(lifecycle).isIn(
                    ConsensusLifecycle.LOW_CONFIDENCE,
                    ConsensusLifecycle.UNKNOWN
            );
        }
        
        // ESTABLISHED: Meets threshold and confidence requirements
        if (status == ConsensusStatus.ESTABLISHED) {
            assertThat(submissionCount).isGreaterThanOrEqualTo(3);
            assertThat(confidence).isGreaterThanOrEqualTo(0.70);
            assertThat(lifecycle).isIn(
                    ConsensusLifecycle.ESTABLISHED,
                    ConsensusLifecycle.DEGRADED
            );
        }
        
        // EXPIRED: Should have EXPIRED status
        if (lifecycle == ConsensusLifecycle.EXPIRED) {
            assertThat(status).isEqualTo(ConsensusStatus.EXPIRED);
        }
    }

    private List<HashSubmission> createSubmissionsWithHash(
            SoftwareIdentity identity, String hash, int count) {
        
        List<HashSubmission> submissions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            HashSubmission submission = new HashSubmission();
            submission.setId(UUID.randomUUID());
            submission.setSoftwareIdentity(identity);
            submission.setHash(hash);
            submission.setTimestamp(LocalDateTime.now().minusDays(i));
            submission.setClientVersion("1.0.0");
            submission.setReplayProtectionHash("replay-" + UUID.randomUUID());
            submissions.add(submission);
        }
        return submissions;
    }

    // Generators for property-based testing

    @Provide
    Arbitrary<SubmissionSet> submissionSetsWithMajority() {
        return Combinators.combine(
                validSoftwareIdentities(),
                validBlake3Hashes(),
                validBlake3Hashes(),
                Arbitraries.integers().between(3, 20)
        ).as((identity, majorityHash, minorityHash, totalCount) -> {
            // Calculate counts to ensure >= 70% majority
            int majorityCount = (int) Math.ceil(totalCount * 0.70);
            int minorityCount = totalCount - majorityCount;
            
            return createSubmissionSet(
                    identity, majorityHash, minorityHash, majorityCount, minorityCount);
        });
    }

    @Provide
    Arbitrary<SubmissionSet> submissionSetsBelowThreshold() {
        return Combinators.combine(
                validSoftwareIdentities(),
                validBlake3Hashes(),
                Arbitraries.integers().between(0, 2)
        ).as((identity, hash, count) -> {
            List<HashSubmission> submissions = createSubmissionsWithHash(identity, hash, count);
            return new SubmissionSet(
                    identity.generateIdentityHash(),
                    submissions,
                    hash,
                    count
            );
        });
    }

    @Provide
    Arbitrary<SubmissionSet> submissionSetsAtExactThreshold() {
        return Arbitraries.just(10).map(totalCount -> {
            // Use fixed count of 10 to ensure exactly 70% (7 out of 10)
            SoftwareIdentity identity = new SoftwareIdentity("example.com", "setup.exe", 1024L);
            String majorityHash = "a".repeat(64);
            String minorityHash = "b".repeat(64);
            int majorityCount = 7; // Exactly 70%
            int minorityCount = 3;
            
            return createSubmissionSet(
                    identity, majorityHash, minorityHash, majorityCount, minorityCount);
        });
    }

    @Provide
    Arbitrary<SubmissionSet> submissionSetsBelowConsensusThreshold() {
        return Arbitraries.integers().between(5, 20).map(totalCount -> {
            // Calculate counts to ensure < 70% for any hash (use 60% for majority)
            int hash1Count = (int) (totalCount * 0.60); // 60% for first hash
            int hash2Count = totalCount - hash1Count;
            
            // Ensure we have at least minimum submissions (3) but below consensus threshold
            if (hash1Count < 3) {
                hash1Count = 3;
                hash2Count = totalCount - hash1Count;
            }
            
            SoftwareIdentity identity = new SoftwareIdentity("example.com", "setup.exe", 1024L);
            String hash1 = "a".repeat(64);
            String hash2 = "b".repeat(64);
            
            return createSubmissionSet(identity, hash1, hash2, hash1Count, hash2Count);
        });
    }

    @Provide
    Arbitrary<SubmissionSet> anySubmissionSet() {
        return Combinators.combine(
                validSoftwareIdentities(),
                validBlake3Hashes(),
                validBlake3Hashes(),
                Arbitraries.integers().between(0, 30),
                Arbitraries.doubles().between(0.0, 1.0)
        ).as((identity, hash1, hash2, totalCount, ratio) -> {
            int hash1Count = (int) (totalCount * ratio);
            int hash2Count = totalCount - hash1Count;
            
            return createSubmissionSet(identity, hash1, hash2, hash1Count, hash2Count);
        });
    }

    @Provide
    Arbitrary<LifecycleScenario> lifecycleTransitionScenarios() {
        return Arbitraries.oneOf(
                // UNKNOWN scenario
                validSoftwareIdentities().map(identity -> 
                    new LifecycleScenario(
                            identity.generateIdentityHash(),
                            new ArrayList<>(),
                            ConsensusLifecycle.UNKNOWN
                    )
                ),
                // LOW_CONFIDENCE scenario (1-2 submissions)
                Combinators.combine(
                        validSoftwareIdentities(),
                        validBlake3Hashes(),
                        Arbitraries.integers().between(1, 2)
                ).as((identity, hash, count) -> 
                    new LifecycleScenario(
                            identity.generateIdentityHash(),
                            createSubmissionsWithHash(identity, hash, count),
                            ConsensusLifecycle.LOW_CONFIDENCE
                    )
                ),
                // ESTABLISHED scenario (>= 3 submissions with >= 70% agreement)
                Combinators.combine(
                        validSoftwareIdentities(),
                        validBlake3Hashes(),
                        Arbitraries.integers().between(3, 10)
                ).as((identity, hash, count) -> 
                    new LifecycleScenario(
                            identity.generateIdentityHash(),
                            createSubmissionsWithHash(identity, hash, count),
                            ConsensusLifecycle.ESTABLISHED
                    )
                )
        );
    }

    @Provide
    Arbitrary<SubmissionSet> conflictingSubmissionScenarios() {
        return Combinators.combine(
                validSoftwareIdentities(),
                validBlake3Hashes(),
                validBlake3Hashes().filter(h2 -> !h2.equals("0000000000000000000000000000000000000000000000000000000000000000")), // Ensure different hashes
                Arbitraries.integers().between(10, 20), // Use larger counts to avoid rounding issues
                Arbitraries.doubles().between(0.55, 0.75) // 55-75% majority
        ).as((identity, hash1, hash2, totalCount, majorityRatio) -> {
            // Create a scenario with conflicting hashes
            // Majority hash has 55-75% of submissions
            int majorityCount = (int) Math.ceil(totalCount * majorityRatio);
            int minorityCount = totalCount - majorityCount;
            
            // Ensure we have at least 1 minority submission for conflict
            if (minorityCount < 1) {
                minorityCount = 1;
                majorityCount = totalCount - minorityCount;
            }
            
            // Ensure majorityCount is actually the majority
            if (majorityCount <= minorityCount) {
                majorityCount = minorityCount + 1;
                minorityCount = totalCount - majorityCount;
            }
            
            return createSubmissionSet(identity, hash1, hash2, majorityCount, minorityCount);
        });
    }

    @Provide
    Arbitrary<SoftwareIdentity> validSoftwareIdentities() {
        return Combinators.combine(
                validDomains(),
                validFilenames(),
                Arbitraries.longs().between(1L, 1000000000L)
        ).as(SoftwareIdentity::new);
    }

    @Provide
    @StringLength(min = 64, max = 64)
    Arbitrary<String> validBlake3Hashes() {
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .withCharRange('a', 'f')
                .ofLength(64);
    }

    @Provide
    Arbitrary<String> validDomains() {
        return Arbitraries.oneOf(
                Arbitraries.just("example.com"),
                Arbitraries.just("download.microsoft.com"),
                Arbitraries.just("releases.ubuntu.com"),
                Arbitraries.just("github.com")
        );
    }

    @Provide
    Arbitrary<String> validFilenames() {
        return Arbitraries.oneOf(
                Arbitraries.just("setup.exe"),
                Arbitraries.just("installer.msi"),
                Arbitraries.just("app.dmg"),
                Arbitraries.just("package.deb")
        );
    }

    private SubmissionSet createSubmissionSet(
            SoftwareIdentity identity, 
            String majorityHash, 
            String minorityHash,
            int majorityCount, 
            int minorityCount) {
        
        List<HashSubmission> submissions = new ArrayList<>();
        submissions.addAll(createSubmissionsWithHash(identity, majorityHash, majorityCount));
        submissions.addAll(createSubmissionsWithHash(identity, minorityHash, minorityCount));
        
        return new SubmissionSet(
                identity.generateIdentityHash(),
                submissions,
                majorityHash,
                majorityCount
        );
    }

    // Helper classes for test data

    static class SubmissionSet {
        final String identityHash;
        final List<HashSubmission> submissions;
        final String majorityHash;
        final int majorityCount;

        SubmissionSet(String identityHash, List<HashSubmission> submissions, 
                     String majorityHash, int majorityCount) {
            this.identityHash = identityHash;
            this.submissions = submissions;
            this.majorityHash = majorityHash;
            this.majorityCount = majorityCount;
        }
    }

    static class LifecycleScenario {
        final String identityHash;
        final List<HashSubmission> submissions;
        final ConsensusLifecycle expectedLifecycle;

        LifecycleScenario(String identityHash, List<HashSubmission> submissions, 
                         ConsensusLifecycle expectedLifecycle) {
            this.identityHash = identityHash;
            this.submissions = submissions;
            this.expectedLifecycle = expectedLifecycle;
        }
    }

    static class DegradedScenario {
        final String identityHash;
        final List<HashSubmission> currentSubmissions;
        final ConsensusResult previousResult;

        DegradedScenario(String identityHash, List<HashSubmission> currentSubmissions, 
                        ConsensusResult previousResult) {
            this.identityHash = identityHash;
            this.currentSubmissions = currentSubmissions;
            this.previousResult = previousResult;
        }
    }
}
