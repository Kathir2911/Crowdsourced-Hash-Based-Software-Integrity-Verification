package com.hashverify.repository;

import com.hashverify.model.HashSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for HashSubmission entities
 * Provides database access for hash submission storage and retrieval
 */
@Repository
public interface HashSubmissionRepository extends JpaRepository<HashSubmission, UUID> {
    
    /**
     * Find all submissions for a given software identity hash
     */
    List<HashSubmission> findBySoftwareIdentityHash(String softwareIdentityHash);
    
    /**
     * Find submissions by software identity hash within a time range
     */
    @Query("SELECT h FROM HashSubmission h WHERE h.softwareIdentityHash = :identityHash " +
           "AND h.timestamp >= :startDate")
    List<HashSubmission> findBySoftwareIdentityHashAndTimestampAfter(
            @Param("identityHash") String identityHash,
            @Param("startDate") LocalDateTime startDate);
    
    /**
     * Count submissions for a given software identity hash
     */
    long countBySoftwareIdentityHash(String softwareIdentityHash);
    
    /**
     * Find submissions by replay protection hash (for duplicate detection)
     */
    List<HashSubmission> findByReplayProtectionHash(String replayProtectionHash);
    
    /**
     * Delete submissions older than the specified date
     */
    @Modifying
    @Query("DELETE FROM HashSubmission h WHERE h.timestamp < :cutoffDate")
    int deleteByTimestampBefore(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Find all submissions older than the specified date
     */
    List<HashSubmission> findByTimestampBefore(LocalDateTime cutoffDate);
    
    /**
     * Check if a submission exists with the given replay protection hash and software identity
     */
    boolean existsByReplayProtectionHashAndSoftwareIdentityHash(
            String replayProtectionHash, 
            String softwareIdentityHash);


    /**
     * Count submissions after a specific timestamp
     */
    long countByTimestampAfter(LocalDateTime timestamp);
    
    /**
     * Count submissions before a specific timestamp (expired submissions)
     */
    long countByTimestampBefore(LocalDateTime timestamp);

    /**
     * Count distinct software identity hashes
     */
    @Query("SELECT COUNT(DISTINCT h.softwareIdentityHash) FROM HashSubmission h")
    long countDistinctSoftwareIdentityHash();

}
