package com.hashverify.service;

import com.hashverify.model.ConsensusLifecycle;
import com.hashverify.model.ConsensusResult;
import com.hashverify.model.ConsensusStatus;
import com.hashverify.model.HashSubmission;
import com.hashverify.model.SoftwareIdentity;
import com.hashverify.repository.ConsensusCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for consensus calculation and lifecycle management
 * Implements requirements 4.1, 4.2, 4.3, 4.5
 */
@Service
public class ConsensusService {
    
    private static final Logger logger = LoggerFactory.getLogger(ConsensusService.class);
    
    private final SubmissionStorageService submissionStorageService;
    private final ConsensusCacheRepository consensusCacheRepository;
    
    @Value("${app.consensus.threshold-percentage:0.70}")
    private double consensusThreshold;
    
    @Value("${app.consensus.minimum-submissions:3}")
    private int minimumSubmissions;
    
    @Value("${app.retention.submission-ttl-days:90}")
    private int submissionTtlDays;
    
    @Value("${app.consensus.cache-ttl-hours:24}")
    private int cacheTtlHours;
    
    public ConsensusService(SubmissionStorageService submissionStorageService,
                           ConsensusCacheRepository consensusCacheRepository) {
        this.submissionStorageService = submissionStorageService;
        this.consensusCacheRepository = consensusCacheRepository;
    }
    
    /**
     * Calculate consensus for a given software identity hash
     * Implements requirements 4.1, 4.2, 4.3
     * Implements requirement 10.4: Log errors with sufficient detail for debugging
     * 
     * @param identityHash The software identity hash
     * @return ConsensusResult containing consensus hash, confidence, and lifecycle state
     */
    @Transactional
    public ConsensusResult calculateConsensus(String identityHash) {
        logger.debug("Calculating consensus for identity hash: {}", identityHash);
        
        try {
            // Check cache first
            Optional<ConsensusResult> cachedResult = consensusCacheRepository.findBySoftwareIdentityHash(identityHash);
            if (cachedResult.isPresent() && !isCacheStale(cachedResult.get())) {
                logger.debug("Returning cached consensus result for identity hash: {}", identityHash);
                return cachedResult.get();
            }
            
            // Get recent submissions (within retention period)
            List<HashSubmission> submissions = submissionStorageService.findRecentByIdentityHash(identityHash);
            
            logger.debug("Found {} recent submissions for identity hash: {}", submissions.size(), identityHash);
            
            // Calculate consensus from submissions
            ConsensusResult result = computeConsensusFromSubmissions(submissions, identityHash);
            
            // TEMPORARY: Skip cache saving due to JPA mapping issue
            // TODO: Fix @AttributeOverrides mapping and re-enable caching
            // consensusCacheRepository.save(result);
            
            logger.info("Calculated consensus for identity hash: {} - Status: {}, Lifecycle: {}, Confidence: {} (cache disabled)", 
                       identityHash, result.getStatus(), result.getLifecycle(), result.getConfidence());
            
            return result;
            
        } catch (Exception e) {
            // Requirement 10.4: Log errors with sufficient detail for debugging
            logger.error("Error calculating consensus for identity hash: {} - Error: {}, Type: {}, StackTrace: {}", 
                        identityHash, 
                        e.getMessage(), 
                        e.getClass().getName(),
                        getStackTraceString(e));
            
            // Return a safe default result on error
            return createUnknownConsensus(identityHash);
        }
    }
    
    /**
     * Get stack trace as string for logging
     * Requirement 10.4: Include stack trace for debugging
     */
    private String getStackTraceString(Exception ex) {
        StringBuilder stackTrace = new StringBuilder();
        StackTraceElement[] elements = ex.getStackTrace();
        int limit = Math.min(3, elements.length);
        
        for (int i = 0; i < limit; i++) {
            stackTrace.append(elements[i].toString()).append("; ");
        }
        
        return stackTrace.toString();
    }
    
    /**
     * Compute consensus from a list of submissions
     * 
     * @param submissions List of hash submissions
     * @param identityHash The software identity hash
     * @return ConsensusResult with calculated values
     */
    private ConsensusResult computeConsensusFromSubmissions(List<HashSubmission> submissions, String identityHash) {
        // Handle no submissions case
        if (submissions.isEmpty()) {
            return createUnknownConsensus(identityHash);
        }
        
        // Extract software identity from first submission
        SoftwareIdentity softwareIdentity = submissions.get(0).getSoftwareIdentity();
        
        // Count hash occurrences
        Map<String, Integer> hashCounts = new HashMap<>();
        LocalDateTime oldestTimestamp = null;
        LocalDateTime newestTimestamp = null;
        
        for (HashSubmission submission : submissions) {
            String hash = submission.getHash();
            hashCounts.put(hash, hashCounts.getOrDefault(hash, 0) + 1);
            
            LocalDateTime timestamp = submission.getTimestamp();
            if (oldestTimestamp == null || timestamp.isBefore(oldestTimestamp)) {
                oldestTimestamp = timestamp;
            }
            if (newestTimestamp == null || timestamp.isAfter(newestTimestamp)) {
                newestTimestamp = timestamp;
            }
        }
        
        // Find the hash with the most submissions (consensus hash)
        String consensusHash = null;
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : hashCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                consensusHash = entry.getKey();
            }
        }
        
        // Calculate confidence
        int totalSubmissions = submissions.size();
        double confidence = (double) maxCount / totalSubmissions;
        
        // Determine status and lifecycle
        ConsensusStatus status = determineConsensusStatus(totalSubmissions, confidence, oldestTimestamp);
        ConsensusLifecycle lifecycle = determineLifecycleState(totalSubmissions, confidence, status, 
                                                               cachedResult(identityHash));
        
        // Create result
        ConsensusResult result = new ConsensusResult(
            softwareIdentity,
            consensusHash,
            confidence,
            totalSubmissions,
            status,
            lifecycle
        );
        
        result.setHashDistribution(hashCounts);
        result.setOldestSubmission(oldestTimestamp);
        result.setNewestSubmission(newestTimestamp);
        result.setExpiresAt(LocalDateTime.now().plusHours(cacheTtlHours));
        
        return result;
    }
    
    /**
     * Create a consensus result for unknown software identity (no submissions)
     */
    private ConsensusResult createUnknownConsensus(String identityHash) {
        ConsensusResult result = new ConsensusResult();
        result.setSoftwareIdentityHash(identityHash);
        result.setConsensusHash(null);
        result.setConfidence(0.0);
        result.setSubmissionCount(0);
        result.setStatus(ConsensusStatus.INSUFFICIENT_DATA);
        result.setLifecycle(ConsensusLifecycle.UNKNOWN);
        result.setHashDistribution(new HashMap<>());
        result.setExpiresAt(LocalDateTime.now().plusHours(cacheTtlHours));
        
        return result;
    }
    
    /**
     * Determine consensus status based on submission count and confidence
     * Implements requirement 4.2 (minimum threshold) and 4.3 (confidence calculation)
     */
    private ConsensusStatus determineConsensusStatus(int submissionCount, double confidence, 
                                                     LocalDateTime oldestSubmission) {
        // Check if submissions are expired (requirement 4.7)
        if (oldestSubmission != null) {
            LocalDateTime expirationCutoff = LocalDateTime.now().minusDays(submissionTtlDays);
            if (oldestSubmission.isBefore(expirationCutoff)) {
                return ConsensusStatus.EXPIRED;
            }
        }
        
        // Check minimum submission threshold (requirement 4.2)
        if (submissionCount < minimumSubmissions) {
            return ConsensusStatus.INSUFFICIENT_DATA;
        }
        
        // Check consensus threshold (requirement 4.3)
        if (confidence >= consensusThreshold) {
            return ConsensusStatus.ESTABLISHED;
        }
        
        return ConsensusStatus.NO_CONSENSUS;
    }
    
    /**
     * Determine lifecycle state based on current and previous consensus
     * Implements requirement 4.5 (lifecycle state transitions)
     */
    private ConsensusLifecycle determineLifecycleState(int submissionCount, double confidence,
                                                       ConsensusStatus status,
                                                       Optional<ConsensusResult> previousResult) {
        // UNKNOWN: No submissions yet
        if (submissionCount == 0) {
            return ConsensusLifecycle.UNKNOWN;
        }
        
        // EXPIRED: No recent submissions
        if (status == ConsensusStatus.EXPIRED) {
            return ConsensusLifecycle.EXPIRED;
        }
        
        // LOW_CONFIDENCE: Below minimum threshold or below consensus percentage
        if (submissionCount < minimumSubmissions || confidence < consensusThreshold) {
            return ConsensusLifecycle.LOW_CONFIDENCE;
        }
        
        // ESTABLISHED: Meets threshold and confidence requirements
        if (status == ConsensusStatus.ESTABLISHED) {
            // Check if this was previously ESTABLISHED and confidence dropped (DEGRADED)
            if (previousResult.isPresent()) {
                ConsensusLifecycle previousLifecycle = previousResult.get().getLifecycle();
                double previousConfidence = previousResult.get().getConfidence();
                
                // If was ESTABLISHED and confidence dropped significantly, mark as DEGRADED
                if (previousLifecycle == ConsensusLifecycle.ESTABLISHED && 
                    confidence < previousConfidence - 0.1) {
                    return ConsensusLifecycle.DEGRADED;
                }
                
                // If was DEGRADED and confidence recovered, return to ESTABLISHED
                if (previousLifecycle == ConsensusLifecycle.DEGRADED && 
                    confidence >= consensusThreshold) {
                    return ConsensusLifecycle.ESTABLISHED;
                }
            }
            
            return ConsensusLifecycle.ESTABLISHED;
        }
        
        // Default to LOW_CONFIDENCE for other cases
        return ConsensusLifecycle.LOW_CONFIDENCE;
    }
    
    /**
     * Get cached consensus result if available
     */
    private Optional<ConsensusResult> cachedResult(String identityHash) {
        return consensusCacheRepository.findBySoftwareIdentityHash(identityHash);
    }
    
    /**
     * Check if cached result is stale and needs refresh
     */
    private boolean isCacheStale(ConsensusResult cachedResult) {
        if (cachedResult.getExpiresAt() == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(cachedResult.getExpiresAt());
    }
    
    /**
     * Calculate confidence percentage for a given consensus hash
     * Implements requirement 4.3
     * 
     * @param submissions List of submissions
     * @param consensusHash The consensus hash to calculate confidence for
     * @return Confidence value between 0.0 and 1.0
     */
    public double calculateConfidence(List<HashSubmission> submissions, String consensusHash) {
        if (submissions.isEmpty() || consensusHash == null) {
            return 0.0;
        }
        
        long matchingCount = submissions.stream()
            .filter(s -> consensusHash.equals(s.getHash()))
            .count();
        
        return (double) matchingCount / submissions.size();
    }
    
    /**
     * Invalidate cached consensus for a software identity
     * Used when new submissions arrive to trigger recalculation
     * Implements requirement 4.5
     * 
     * @param identityHash The software identity hash
     */
    @Transactional
    public void invalidateCache(String identityHash) {
        logger.debug("Invalidating consensus cache for identity hash: {}", identityHash);
        consensusCacheRepository.findBySoftwareIdentityHash(identityHash)
            .ifPresent(result -> {
                result.setExpiresAt(LocalDateTime.now().minusHours(1));
                consensusCacheRepository.save(result);
            });
    }
    
    /**
     * Get consensus result from cache if available, otherwise calculate
     * 
     * @param identityHash The software identity hash
     * @return Optional containing consensus result if available
     */
    @Transactional(readOnly = true)
    public Optional<ConsensusResult> getConsensus(String identityHash) {
        Optional<ConsensusResult> cachedResult = consensusCacheRepository.findBySoftwareIdentityHash(identityHash);
        
        if (cachedResult.isPresent() && !isCacheStale(cachedResult.get())) {
            return cachedResult;
        }
        
        // Cache is stale or doesn't exist, calculate new consensus
        return Optional.of(calculateConsensus(identityHash));
    }
    
    /**
     * Clean up expired consensus cache entries
     * 
     * @return Number of entries deleted
     */
    @Transactional
    public int cleanupExpiredCache() {
        LocalDateTime expirationDate = LocalDateTime.now().minusHours(cacheTtlHours * 2);
        logger.info("Cleaning up consensus cache entries older than {}", expirationDate);
        
        int deletedCount = consensusCacheRepository.deleteByLastUpdatedBefore(expirationDate);
        
        logger.info("Deleted {} expired consensus cache entries", deletedCount);
        return deletedCount;
    }
}
