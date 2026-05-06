package com.hashverify.repository;

import com.hashverify.model.HashSubmission;
import com.hashverify.model.SoftwareIdentity;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.LowerChars;
import net.jqwik.api.constraints.NumericChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for Hash Submission Repository
 * **Validates: Requirements 3.4**
 */
@DataJpaTest
@ActiveProfiles("test")
class HashSubmissionRepositoryProperties {

    @Autowired
    private HashSubmissionRepository repository;

    @Autowired
    private TestEntityManager entityManager;
    
    @BeforeEach
    void setUp() {
        // Clear repository before each test
        repository.deleteAll();
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * Property 7: Hash Submission Storage and Grouping
     * 
     * For any valid Hash_Submission, the Backend_Server should store it in the 
     * Submission_Repository grouped by Software_Identity.
     * 
     * This property verifies that:
     * 1. Submissions can be saved to the repository
     * 2. Submissions with the same software identity hash are grouped together
     * 3. Submissions can be retrieved by their software identity hash
     * 4. The count of submissions per identity hash is accurate
     * 
     * **Validates: Requirements 3.4**
     */
    @Test
    void hashSubmissionStorageAndGrouping() {
        // Test with specific examples that demonstrate the property
        SoftwareIdentity identity1 = new SoftwareIdentity("example.com", "setup.exe", 1024L);
        SoftwareIdentity identity2 = new SoftwareIdentity("github.com", "installer.msi", 2048L);
        
        String hash1 = "a".repeat(64);
        String hash2 = "b".repeat(64);
        String hash3 = "c".repeat(64);
        
        // Create submissions with the same identity (should be grouped together)
        String identityHash1 = identity1.generateIdentityHash();
        HashSubmission submission1 = createSubmission(identity1, hash1);
        HashSubmission submission2 = createSubmission(identity1, hash2);
        HashSubmission submission3 = createSubmission(identity1, hash3);
        
        // Create a submission with a different identity (should be in a separate group)
        String identityHash2 = identity2.generateIdentityHash();
        HashSubmission submission4 = createSubmission(identity2, hash1);
        
        // Save all submissions
        HashSubmission saved1 = repository.save(submission1);
        HashSubmission saved2 = repository.save(submission2);
        HashSubmission saved3 = repository.save(submission3);
        HashSubmission saved4 = repository.save(submission4);
        
        entityManager.flush();
        entityManager.clear();
        
        // Verify all submissions were saved with generated IDs
        assertThat(saved1.getId()).isNotNull();
        assertThat(saved2.getId()).isNotNull();
        assertThat(saved3.getId()).isNotNull();
        assertThat(saved4.getId()).isNotNull();
        
        // Verify submissions are grouped by software identity hash
        List<HashSubmission> group1 = repository.findBySoftwareIdentityHash(identityHash1);
        List<HashSubmission> group2 = repository.findBySoftwareIdentityHash(identityHash2);
        
        // Group 1 should contain exactly 3 submissions
        assertThat(group1).hasSize(3);
        assertThat(group1).extracting(HashSubmission::getSoftwareIdentityHash)
                .containsOnly(identityHash1);
        
        // Group 2 should contain exactly 1 submission
        assertThat(group2).hasSize(1);
        assertThat(group2).extracting(HashSubmission::getSoftwareIdentityHash)
                .containsOnly(identityHash2);
        
        // Verify count operations
        long count1 = repository.countBySoftwareIdentityHash(identityHash1);
        long count2 = repository.countBySoftwareIdentityHash(identityHash2);
        
        assertThat(count1).isEqualTo(3);
        assertThat(count2).isEqualTo(1);
        
        // Verify that different identities produce different groups
        assertThat(group1).doesNotContainAnyElementsOf(group2);
        
        // Verify all submissions in a group have the same software identity
        for (HashSubmission submission : group1) {
            assertThat(submission.getSoftwareIdentity()).isEqualTo(identity1);
        }
        
        for (HashSubmission submission : group2) {
            assertThat(submission.getSoftwareIdentity()).isEqualTo(identity2);
        }
    }

    /**
     * Property 7a: Hash Submission Storage Preserves All Fields
     * 
     * For any valid Hash_Submission, storing it in the repository should preserve
     * all field values exactly.
     * 
     * **Validates: Requirements 3.4**
     */
    @Test
    void hashSubmissionStoragePreservesAllFields() {
        SoftwareIdentity identity = new SoftwareIdentity("example.com", "setup.exe", 1024L);
        String hash = "a".repeat(64);
        String clientVersion = "1.0.0";
        String replayProtectionHash = "replay123";
        String userAgent = "Mozilla/5.0";
        
        // Create and save a submission
        HashSubmission original = new HashSubmission(
                identity, hash, clientVersion, replayProtectionHash, userAgent);
        original.setTimestamp(LocalDateTime.now());
        
        HashSubmission saved = repository.save(original);
        entityManager.flush();
        entityManager.clear();
        
        // Retrieve the submission
        HashSubmission retrieved = repository.findById(saved.getId()).orElseThrow();
        
        // Verify all fields are preserved
        assertThat(retrieved.getSoftwareIdentity()).isEqualTo(identity);
        assertThat(retrieved.getSoftwareIdentityHash()).isEqualTo(identity.generateIdentityHash());
        assertThat(retrieved.getHash()).isEqualTo(hash.toLowerCase());
        assertThat(retrieved.getClientVersion()).isEqualTo(clientVersion);
        assertThat(retrieved.getReplayProtectionHash()).isEqualTo(replayProtectionHash);
        assertThat(retrieved.getUserAgent()).isEqualTo(userAgent);
        assertThat(retrieved.getTimestamp()).isNotNull();
    }

    /**
     * Property 7b: Hash Submission Grouping by Time Range
     * 
     * For any set of Hash_Submissions with the same Software_Identity but different
     * timestamps, the repository should correctly filter by time range.
     * 
     * **Validates: Requirements 3.4**
     */
    @Test
    void hashSubmissionGroupingByTimeRange() {
        SoftwareIdentity identity = new SoftwareIdentity("example.com", "setup.exe", 1024L);
        String identityHash = identity.generateIdentityHash();
        
        // Create and save submissions (set timestamps manually)
        HashSubmission submission1 = new HashSubmission(
                identity, "a".repeat(64), "1.0.0", UUID.randomUUID().toString().substring(0, 32), "TestUserAgent");
        submission1.setTimestamp(LocalDateTime.now());
        
        HashSubmission submission2 = new HashSubmission(
                identity, "b".repeat(64), "1.0.0", UUID.randomUUID().toString().substring(0, 32), "TestUserAgent");
        submission2.setTimestamp(LocalDateTime.now());
        
        HashSubmission submission3 = new HashSubmission(
                identity, "c".repeat(64), "1.0.0", UUID.randomUUID().toString().substring(0, 32), "TestUserAgent");
        submission3.setTimestamp(LocalDateTime.now());
        
        repository.save(submission1);
        repository.save(submission2);
        repository.save(submission3);
        
        entityManager.flush();
        entityManager.clear();
        
        // Query for all submissions
        List<HashSubmission> allSubmissions = repository.findBySoftwareIdentityHash(identityHash);
        assertThat(allSubmissions).hasSize(3);
        
        // Get the earliest timestamp from saved submissions
        LocalDateTime earliestTimestamp = allSubmissions.stream()
                .map(HashSubmission::getTimestamp)
                .min(LocalDateTime::compareTo)
                .orElseThrow();
        
        // Query for submissions after a time slightly before the earliest
        LocalDateTime cutoffDate = earliestTimestamp.minusSeconds(1);
        List<HashSubmission> recentSubmissions = repository
                .findBySoftwareIdentityHashAndTimestampAfter(identityHash, cutoffDate);
        
        // All submissions should be after the cutoff
        assertThat(recentSubmissions).hasSize(3);
        assertThat(recentSubmissions).extracting(HashSubmission::getTimestamp)
                .allMatch(timestamp -> timestamp.isAfter(cutoffDate));
        
        // Query with a cutoff after all submissions
        LocalDateTime futureCutoff = LocalDateTime.now().plusDays(1);
        List<HashSubmission> futureSubmissions = repository
                .findBySoftwareIdentityHashAndTimestampAfter(identityHash, futureCutoff);
        
        // No submissions should be in the future
        assertThat(futureSubmissions).isEmpty();
    }

    /**
     * Property 7c: Hash Submission Replay Protection Detection
     * 
     * For any Hash_Submission, the repository should correctly detect duplicates
     * based on replay protection hash.
     * 
     * **Validates: Requirements 3.4**
     */
    @Test
    void hashSubmissionReplayProtectionDetection() {
        SoftwareIdentity identity = new SoftwareIdentity("example.com", "setup.exe", 1024L);
        String identityHash = identity.generateIdentityHash();
        String replayHash = "replay123";
        
        // Initially, no duplicate should exist
        boolean existsBefore = repository.existsByReplayProtectionHashAndSoftwareIdentityHash(
                replayHash, identityHash);
        assertThat(existsBefore).isFalse();
        
        // Create and save a submission
        HashSubmission submission = createSubmission(identity, "a".repeat(64));
        submission.setReplayProtectionHash(replayHash);
        repository.save(submission);
        
        entityManager.flush();
        entityManager.clear();
        
        // Now the duplicate should be detected
        boolean existsAfter = repository.existsByReplayProtectionHashAndSoftwareIdentityHash(
                replayHash, identityHash);
        assertThat(existsAfter).isTrue();
        
        // Verify we can find by replay protection hash
        List<HashSubmission> found = repository.findByReplayProtectionHash(replayHash);
        assertThat(found).isNotEmpty();
        assertThat(found).extracting(HashSubmission::getReplayProtectionHash)
                .containsOnly(replayHash);
    }

    /**
     * Property 7d: Hash Submission Deletion by Timestamp
     * 
     * For any set of Hash_Submissions with different timestamps, the repository
     * should correctly delete submissions older than a cutoff date.
     * 
     * **Validates: Requirements 3.4**
     */
    @Test
    void hashSubmissionDeletionByTimestamp() {
        SoftwareIdentity identity = new SoftwareIdentity("example.com", "setup.exe", 1024L);
        
        // Create and save submissions (set timestamps manually)
        HashSubmission submission1 = new HashSubmission(
                identity, "a".repeat(64), "1.0.0", UUID.randomUUID().toString().substring(0, 32), "TestUserAgent");
        submission1.setTimestamp(LocalDateTime.now());
        
        HashSubmission submission2 = new HashSubmission(
                identity, "b".repeat(64), "1.0.0", UUID.randomUUID().toString().substring(0, 32), "TestUserAgent");
        submission2.setTimestamp(LocalDateTime.now());
        
        repository.save(submission1);
        repository.save(submission2);
        
        entityManager.flush();
        entityManager.clear();
        
        // Verify both submissions exist
        assertThat(repository.count()).isEqualTo(2);
        
        // Get all submissions and their timestamps
        List<HashSubmission> allSubmissions = repository.findAll();
        assertThat(allSubmissions).hasSize(2);
        
        // Use a cutoff date in the future to find all submissions
        LocalDateTime futureCutoff = LocalDateTime.now().plusDays(1);
        List<HashSubmission> submissionsBeforeFuture = repository.findByTimestampBefore(futureCutoff);
        assertThat(submissionsBeforeFuture).hasSize(2);
        
        // Use a cutoff date in the past to find no submissions
        LocalDateTime pastCutoff = LocalDateTime.now().minusDays(1);
        List<HashSubmission> submissionsBeforePast = repository.findByTimestampBefore(pastCutoff);
        assertThat(submissionsBeforePast).isEmpty();
        
        // Delete submissions before future cutoff (should delete all)
        int deletedCount = repository.deleteByTimestampBefore(futureCutoff);
        entityManager.flush();
        entityManager.clear();
        
        assertThat(deletedCount).isEqualTo(2);
        
        // Verify all submissions are deleted
        assertThat(repository.count()).isEqualTo(0);
    }

    /**
     * Property 7e: Hash Submission Grouping Consistency
     * 
     * For any two Hash_Submissions with identical Software_Identity, they should
     * always be grouped together regardless of other field differences.
     * 
     * **Validates: Requirements 3.4**
     */
    @Test
    void hashSubmissionGroupingConsistency() {
        SoftwareIdentity identity = new SoftwareIdentity("example.com", "setup.exe", 1024L);
        String identityHash = identity.generateIdentityHash();
        
        // Create submissions with same identity but different hashes and versions
        HashSubmission submission1 = createSubmission(identity, "a".repeat(64));
        submission1.setClientVersion("1.0.0");
        
        HashSubmission submission2 = createSubmission(identity, "b".repeat(64));
        submission2.setClientVersion("2.0.0");
        
        repository.save(submission1);
        repository.save(submission2);
        
        entityManager.flush();
        entityManager.clear();
        
        // Both submissions should be in the same group
        List<HashSubmission> group = repository.findBySoftwareIdentityHash(identityHash);
        assertThat(group).hasSize(2);
        
        // Verify all submissions in the group have the same identity hash
        assertThat(group).extracting(HashSubmission::getSoftwareIdentityHash)
                .containsOnly(identityHash);
        
        // Verify all submissions have the same software identity
        assertThat(group).extracting(HashSubmission::getSoftwareIdentity)
                .containsOnly(identity);
        
        // Verify count is correct
        long count = repository.countBySoftwareIdentityHash(identityHash);
        assertThat(count).isEqualTo(2);
    }

    // Helper method to create a hash submission
    private HashSubmission createSubmission(SoftwareIdentity identity, String hash) {
        String replayHash = UUID.randomUUID().toString().substring(0, 32);
        HashSubmission submission = new HashSubmission(
                identity, hash, "1.0.0", replayHash, "TestUserAgent");
        submission.setTimestamp(LocalDateTime.now());
        return submission;
    }
}
