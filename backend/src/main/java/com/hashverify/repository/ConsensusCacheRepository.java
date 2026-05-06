package com.hashverify.repository;

import com.hashverify.model.ConsensusResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for ConsensusResult caching
 * Provides database access for consensus cache storage and retrieval
 */
@Repository
public interface ConsensusCacheRepository extends JpaRepository<ConsensusResult, String> {
    
    /**
     * Find consensus result by software identity hash
     */
    Optional<ConsensusResult> findBySoftwareIdentityHash(String softwareIdentityHash);
    
    /**
     * Delete expired consensus cache entries
     */
    @Modifying
    @Query("DELETE FROM ConsensusResult c WHERE c.lastUpdated < :expirationDate")
    int deleteByLastUpdatedBefore(@Param("expirationDate") LocalDateTime expirationDate);
    
    /**
     * Find all consensus results that need refresh (older than specified date)
     */
    @Query("SELECT c FROM ConsensusResult c WHERE c.lastUpdated < :refreshDate")
    java.util.List<ConsensusResult> findStaleConsensusResults(@Param("refreshDate") LocalDateTime refreshDate);
    
    /**
     * Check if consensus exists for a software identity
     */
    boolean existsBySoftwareIdentityHash(String softwareIdentityHash);
}
