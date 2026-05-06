package com.hashverify.service;

import com.hashverify.model.ConsensusLifecycle;
import com.hashverify.model.ConsensusResult;
import com.hashverify.model.ConsensusStatus;
import com.hashverify.model.SoftwareIdentity;
import com.hashverify.model.SuspicionLevel;
import com.hashverify.model.TamperStatus;
import com.hashverify.model.VerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for hash verification and tamper detection
 * Implements requirements 5.1, 5.2, 5.3, 5.4, 5.5
 */
@Service
public class VerificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(VerificationService.class);
    
    private final ConsensusService consensusService;
    
    @Value("${app.consensus.confidence-high-threshold:0.70}")
    private double confidenceHighThreshold;
    
    @Value("${app.consensus.confidence-minimum-submissions:10}")
    private int confidenceMinimumSubmissions;
    
    public VerificationService(ConsensusService consensusService) {
        this.consensusService = consensusService;
    }
    
    /**
     * Verify a hash against consensus
     * Implements requirements 5.1, 5.2, 5.3, 5.4, 5.5
     * Implements requirement 10.4: Log errors with sufficient detail for debugging
     * 
     * @param hash The hash to verify
     * @param softwareIdentity The software identity
     * @return VerificationResult with status, confidence, and suspicion level
     */
    public VerificationResult verifyHash(String hash, SoftwareIdentity softwareIdentity) {
        logger.debug("Verifying hash for software identity: {}", softwareIdentity);
        
        try {
            // Get consensus for this software identity
            String identityHash = softwareIdentity.generateIdentityHash();
            ConsensusResult consensus = consensusService.calculateConsensus(identityHash);
            
            // Determine tamper status based on hash comparison and consensus
            TamperStatus tamperStatus = determineTamperStatus(hash, consensus);
            
            // Calculate suspicion level
            SuspicionLevel suspicionLevel = calculateSuspicionLevel(tamperStatus, consensus);
            
            // Generate user-friendly message
            String message = generateMessage(tamperStatus, consensus);
            
            // Generate recommended action
            String recommendedAction = generateRecommendedAction(tamperStatus, suspicionLevel);
            
            // Create and return verification result
            VerificationResult result = new VerificationResult(
                tamperStatus,
                consensus.getConfidence(),
                consensus.getSubmissionCount(),
                consensus.getConsensusHash(),
                message,
                suspicionLevel,
                recommendedAction
            );
            
            logger.info("Verification complete for identity hash: {} - Status: {}, Suspicion: {}, Confidence: {}", 
                       identityHash, tamperStatus, suspicionLevel, consensus.getConfidence());
            
            return result;
            
        } catch (Exception e) {
            // Requirement 10.4: Log errors with sufficient detail for debugging
            logger.error("Error verifying hash for software identity: {} - Hash: {}, Error: {}, Type: {}, StackTrace: {}", 
                        softwareIdentity.getNormalizedFilename(),
                        hash,
                        e.getMessage(), 
                        e.getClass().getName(),
                        getStackTraceString(e));
            
            // Return SERVICE_UNAVAILABLE result on error
            return new VerificationResult(
                TamperStatus.SERVICE_UNAVAILABLE,
                0.0,
                0,
                null,
                "Verification service encountered an error: " + e.getMessage(),
                SuspicionLevel.LOW,
                "Unable to verify at this time. Try again later or verify through alternative means"
            );
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
     * Determine tamper status based on hash comparison and consensus
     * Implements requirements 5.1, 5.2, 5.3, 5.4, 5.5
     * 
     * @param hash The hash to verify
     * @param consensus The consensus result
     * @return TamperStatus indicating verification result
     */
    public TamperStatus determineTamperStatus(String hash, ConsensusResult consensus) {
        // Requirement 5.5: No consensus available (UNKNOWN status)
        if (consensus.getStatus() == ConsensusStatus.INSUFFICIENT_DATA || 
            consensus.getStatus() == ConsensusStatus.EXPIRED ||
            consensus.getConsensusHash() == null) {
            logger.debug("No consensus available - returning UNKNOWN status");
            return TamperStatus.UNKNOWN;
        }
        
        // Requirement 5.1: Compare submitted hash against consensus hash
        boolean hashMatches = hash.equals(consensus.getConsensusHash());
        
        // Check if we have high confidence (>= 70% agreement AND >= 10 submissions)
        boolean hasHighConfidence = consensus.getConfidence() >= confidenceHighThreshold && 
                                   consensus.getSubmissionCount() >= confidenceMinimumSubmissions;
        
        // Requirement 5.4: VERIFIED status when hash matches consensus with high confidence
        if (hashMatches && hasHighConfidence) {
            logger.debug("Hash matches consensus with high confidence - VERIFIED");
            return TamperStatus.VERIFIED;
        }
        
        // Requirement 5.3: TAMPERED status when hash doesn't match consensus with high confidence
        if (!hashMatches && hasHighConfidence) {
            logger.debug("Hash does not match consensus with high confidence - TAMPERED");
            return TamperStatus.TAMPERED;
        }
        
        // Requirement 5.2: SUSPICIOUS_LOW_CONFIDENCE when consensus exists but confidence is weak
        // This covers cases where:
        // - Confidence >= 70% but < 10 submissions
        // - Consensus exists but confidence < 70%
        if (consensus.getStatus() == ConsensusStatus.ESTABLISHED || 
            consensus.getStatus() == ConsensusStatus.NO_CONSENSUS ||
            consensus.getLifecycle() == ConsensusLifecycle.DEGRADED) {
            logger.debug("Consensus exists but confidence is weak - SUSPICIOUS_LOW_CONFIDENCE");
            return TamperStatus.SUSPICIOUS_LOW_CONFIDENCE;
        }
        
        // Default to UNKNOWN for any other cases
        logger.debug("Defaulting to UNKNOWN status");
        return TamperStatus.UNKNOWN;
    }
    
    /**
     * Calculate suspicion level based on tamper status and consensus
     * Implements requirement 5.3
     * 
     * @param tamperStatus The determined tamper status
     * @param consensus The consensus result
     * @return SuspicionLevel indicating risk level
     */
    public SuspicionLevel calculateSuspicionLevel(TamperStatus tamperStatus, ConsensusResult consensus) {
        switch (tamperStatus) {
            case VERIFIED:
                // NONE: File is verified with high confidence
                return SuspicionLevel.NONE;
                
            case TAMPERED:
                // Check if confidence is very high (>90%) for CRITICAL
                if (consensus.getConfidence() >= 0.90) {
                    return SuspicionLevel.CRITICAL;
                }
                // HIGH: File doesn't match consensus with high confidence
                return SuspicionLevel.HIGH;
                
            case SUSPICIOUS_LOW_CONFIDENCE:
                // Determine suspicion level based on confidence percentage
                double confidence = consensus.getConfidence();
                
                if (confidence >= 0.50 && confidence < 0.70) {
                    // LOW: Confidence between 50-70%
                    return SuspicionLevel.LOW;
                } else if (confidence >= 0.30 && confidence < 0.50) {
                    // MEDIUM: Confidence between 30-50%
                    return SuspicionLevel.MEDIUM;
                } else {
                    // MEDIUM: Default for other low confidence cases
                    return SuspicionLevel.MEDIUM;
                }
                
            case UNKNOWN:
            case SERVICE_UNAVAILABLE:
            default:
                // LOW: Unknown status has minimal suspicion
                return SuspicionLevel.LOW;
        }
    }
    
    /**
     * Generate user-friendly message based on verification result
     * 
     * @param tamperStatus The tamper status
     * @param consensus The consensus result
     * @return User-friendly message
     */
    private String generateMessage(TamperStatus tamperStatus, ConsensusResult consensus) {
        switch (tamperStatus) {
            case VERIFIED:
                return String.format("File verified against consensus with %.1f%% confidence (%d submissions)",
                                   consensus.getConfidence() * 100, consensus.getSubmissionCount());
                
            case TAMPERED:
                return String.format("WARNING: File hash does not match consensus (%.1f%% confidence, %d submissions)",
                                   consensus.getConfidence() * 100, consensus.getSubmissionCount());
                
            case SUSPICIOUS_LOW_CONFIDENCE:
                return String.format("Consensus exists but confidence is weak (%.1f%% confidence, %d submissions)",
                                   consensus.getConfidence() * 100, consensus.getSubmissionCount());
                
            case UNKNOWN:
                if (consensus.getSubmissionCount() == 0) {
                    return "No consensus data available - this is the first submission for this software";
                } else {
                    return String.format("Insufficient data to establish consensus (%d submissions)",
                                       consensus.getSubmissionCount());
                }
                
            case SERVICE_UNAVAILABLE:
                return "Verification service is temporarily unavailable";
                
            default:
                return "Unable to determine verification status";
        }
    }
    
    /**
     * Generate recommended action based on tamper status and suspicion level
     * 
     * @param tamperStatus The tamper status
     * @param suspicionLevel The suspicion level
     * @return Recommended action for the user
     */
    private String generateRecommendedAction(TamperStatus tamperStatus, SuspicionLevel suspicionLevel) {
        switch (tamperStatus) {
            case VERIFIED:
                return "File appears safe to use based on crowdsourced consensus";
                
            case TAMPERED:
                if (suspicionLevel == SuspicionLevel.CRITICAL) {
                    return "DO NOT USE THIS FILE - High probability of tampering detected. Delete immediately and download from official source";
                } else {
                    return "CAUTION: File may be tampered. Verify download source and consider re-downloading from official source";
                }
                
            case SUSPICIOUS_LOW_CONFIDENCE:
                if (suspicionLevel == SuspicionLevel.MEDIUM) {
                    return "Exercise caution - consensus is weak. Verify file source and consider waiting for more submissions";
                } else {
                    return "Limited consensus data available. Verify download source before use";
                }
                
            case UNKNOWN:
                return "No consensus data available. Verify download source and proceed with normal security precautions";
                
            case SERVICE_UNAVAILABLE:
                return "Unable to verify at this time. Try again later or verify through alternative means";
                
            default:
                return "Unable to provide recommendation - verify download source manually";
        }
    }
}
