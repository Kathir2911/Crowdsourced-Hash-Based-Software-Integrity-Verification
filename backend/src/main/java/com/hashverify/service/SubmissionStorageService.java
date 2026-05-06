package com.hashverify.service;

import com.hashverify.model.HashSubmission;
import com.hashverify.repository.HashSubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for hash submission storage and retrieval operations
 * Implements requirements 3.4, 4.7
 */
@Service
public class SubmissionStorageService {
    
    private static final Logger logger = LoggerFactory.getLogger(SubmissionStorageService.class);
    
    private final HashSubmissionRepository repository;
    
    @Value("${app.retention.submission-ttl-days:90}")
    private int submissionTtlDays;
    
    public SubmissionStorageService(HashSubmissionRepository repository) {
        this.repository = repository;
    }
    
    /**
     * Save a hash submission to the database
     * 
     * @param submission The hash submission to save
     * @return The saved submission with generated ID
     */
    @Transactional
    public HashSubmission saveSubmission(HashSubmission submission) {
        try {
            logger.debug("Saving hash submission for software identity: {}", 
                        submission.getSoftwareIdentityHash());
            
            HashSubmission saved = repository.save(submission);
            
            logger.info("Successfully saved hash submission with ID: {}", saved.getId());
            return saved;
            
        } catch (Exception e) {
            logger.error("Failed to save hash submission: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save hash submission", e);
        }
    }
    
    /**
     * Find all submissions for a given software identity hash
     * 
     * @param identityHash The software identity hash
     * @return List of submissions for the identity
     */
    @Transactional(readOnly = true)
    public List<HashSubmission> findByIdentityHash(String identityHash) {
        try {
            logger.debug("Finding submissions for identity hash: {}", identityHash);
            return repository.findBySoftwareIdentityHash(identityHash);
        } catch (Exception e) {
            logger.error("Failed to find submissions for identity hash {}: {}", 
                        identityHash, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve submissions", e);
        }
    }
    
    /**
     * Find recent submissions for a given software identity hash
     * Only returns submissions within the retention period
     * 
     * @param identityHash The software identity hash
     * @return List of recent submissions
     */
    @Transactional(readOnly = true)
    public List<HashSubmission> findRecentByIdentityHash(String identityHash) {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(submissionTtlDays);
            logger.debug("Finding recent submissions for identity hash: {} since {}", 
                        identityHash, cutoffDate);
            
            return repository.findBySoftwareIdentityHashAndTimestampAfter(identityHash, cutoffDate);
        } catch (Exception e) {
            logger.error("Failed to find recent submissions for identity hash {}: {}", 
                        identityHash, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve recent submissions", e);
        }
    }
    
    /**
     * Count submissions for a given software identity hash
     * 
     * @param identityHash The software identity hash
     * @return Number of submissions
     */
    @Transactional(readOnly = true)
    public long countByIdentityHash(String identityHash) {
        try {
            return repository.countBySoftwareIdentityHash(identityHash);
        } catch (Exception e) {
            logger.error("Failed to count submissions for identity hash {}: {}", 
                        identityHash, e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * Check if a submission already exists with the given replay protection hash
     * Used for duplicate detection
     * 
     * @param replayProtectionHash The replay protection hash
     * @param identityHash The software identity hash
     * @return true if duplicate exists, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isDuplicateSubmission(String replayProtectionHash, String identityHash) {
        try {
            return repository.existsByReplayProtectionHashAndSoftwareIdentityHash(
                    replayProtectionHash, identityHash);
        } catch (Exception e) {
            logger.error("Failed to check for duplicate submission: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Delete expired submissions older than the retention period
     * 
     * @return Number of submissions deleted
     */
    @Transactional
    public int deleteExpiredSubmissions() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(submissionTtlDays);
            logger.info("Deleting submissions older than {}", cutoffDate);
            
            int deletedCount = repository.deleteByTimestampBefore(cutoffDate);
            
            logger.info("Successfully deleted {} expired submissions", deletedCount);
            return deletedCount;
            
        } catch (Exception e) {
            logger.error("Failed to delete expired submissions: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete expired submissions", e);
        }
    }
    
    /**
     * Find a submission by ID
     * 
     * @param id The submission ID
     * @return Optional containing the submission if found
     */
    @Transactional(readOnly = true)
    public Optional<HashSubmission> findById(UUID id) {
        try {
            return repository.findById(id);
        } catch (Exception e) {
            logger.error("Failed to find submission by ID {}: {}", id, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Get all submissions (use with caution - for admin/testing only)
     * 
     * @return List of all submissions
     */
    @Transactional(readOnly = true)
    public List<HashSubmission> findAll() {
        try {
            return repository.findAll();
        } catch (Exception e) {
            logger.error("Failed to retrieve all submissions: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve submissions", e);
        }
    }


    /**
     * Get total submission count across all software identities
     *
     * @return Total number of submissions
     */
    @Transactional(readOnly = true)
    public long getTotalSubmissionCount() {
        try {
            return repository.count();
        } catch (Exception e) {
            logger.error("Failed to get total submission count: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Get submission count since a specific timestamp
     *
     * @param since The timestamp to count from
     * @return Number of submissions since the timestamp
     */
    @Transactional(readOnly = true)
    public long getSubmissionCountSince(LocalDateTime since) {
        try {
            return repository.countByTimestampAfter(since);
        } catch (Exception e) {
            logger.error("Failed to get submission count since {}: {}", since, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Get count of unique software identities
     *
     * @return Number of unique software identities
     */
    @Transactional(readOnly = true)
    public long getUniqueSoftwareIdentityCount() {
        try {
            return repository.countDistinctSoftwareIdentityHash();
        } catch (Exception e) {
            logger.error("Failed to get unique software identity count: {}", e.getMessage(), e);
            return 0;
        }
    }

}
