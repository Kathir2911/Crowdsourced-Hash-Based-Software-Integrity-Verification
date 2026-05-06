package com.hashverify.controller;

import com.hashverify.model.HashSubmission;
import com.hashverify.model.SoftwareIdentity;
import com.hashverify.model.VerificationResult;
import com.hashverify.service.SubmissionStorageService;
import com.hashverify.service.VerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for hash verification and submission endpoints
 * Implements requirements 3.3, 5.1
 */
@RestController
@RequestMapping("")
public class VerificationController {
    
    private static final Logger logger = LoggerFactory.getLogger(VerificationController.class);
    
    private final VerificationService verificationService;
    private final SubmissionStorageService submissionStorageService;
    
    public VerificationController(VerificationService verificationService,
                                 SubmissionStorageService submissionStorageService) {
        this.verificationService = verificationService;
        this.submissionStorageService = submissionStorageService;
    }
    
    /**
     * Submit a hash for a software binary
     * POST /api/v1/submissions
     * 
     * Implements requirement 3.3: Hash submission with software identity
     * 
     * @param request The submission request containing hash and software identity
     * @return ResponseEntity with submission confirmation
     */
    @PostMapping("/submissions")
    public ResponseEntity<Map<String, Object>> submitHash(@Valid @RequestBody SubmissionRequest request) {
        logger.info("Received hash submission for software: {}", request.getSoftwareIdentity().getNormalizedFilename());
        
        try {
            // Create HashSubmission from request
            HashSubmission submission = new HashSubmission(
                request.getSoftwareIdentity(),
                request.getHash(),
                request.getClientVersion(),
                request.getReplayProtectionHash(),
                request.getUserAgent()
            );
            
            // Store the submission
            HashSubmission savedSubmission = submissionStorageService.saveSubmission(submission);
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Hash submission received successfully");
            response.put("submissionId", savedSubmission.getId().toString());
            response.put("softwareIdentityHash", savedSubmission.getSoftwareIdentityHash());
            
            logger.info("Hash submission saved successfully: {}", savedSubmission.getId());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            logger.error("Error processing hash submission", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Failed to process hash submission: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Verify a hash against consensus
     * POST /api/v1/verify
     * 
     * Implements requirement 5.1: Hash verification against consensus
     * 
     * @param request The verification request containing hash and software identity
     * @return ResponseEntity with verification result
     */
    @PostMapping("/verify")
    public ResponseEntity<VerificationResult> verifyHash(@Valid @RequestBody VerificationRequest request) {
        logger.info("Received verification request for software: {}", request.getSoftwareIdentity().getNormalizedFilename());
        
        try {
            // Perform verification
            VerificationResult result = verificationService.verifyHash(
                request.getHash(),
                request.getSoftwareIdentity()
            );
            
            logger.info("Verification complete: status={}, confidence={}", 
                       result.getStatus(), result.getConfidence());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Error processing verification request", e);
            
            // Return SERVICE_UNAVAILABLE status on error
            VerificationResult errorResult = new VerificationResult();
            errorResult.setStatus(com.hashverify.model.TamperStatus.SERVICE_UNAVAILABLE);
            errorResult.setConfidence(0.0);
            errorResult.setSubmissionCount(0);
            errorResult.setMessage("Verification service encountered an error: " + e.getMessage());
            errorResult.setSuspicionLevel(com.hashverify.model.SuspicionLevel.LOW);
            errorResult.setRecommendedAction("Unable to verify at this time. Try again later or verify through alternative means");
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResult);
        }
    }
    
    /**
     * Health check endpoint
     * GET /api/v1/status
     * 
     * @return ResponseEntity with service status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "healthy");
        status.put("service", "Crowdsourced Hash Verification");
        status.put("version", "1.0.0");
        status.put("timestamp", java.time.LocalDateTime.now().toString());
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Request DTO for hash submission
     */
    public static class SubmissionRequest {
        
        @Valid
        @jakarta.validation.constraints.NotNull(message = "Software identity cannot be null")
        private SoftwareIdentity softwareIdentity;
        
        @NotBlank(message = "Hash cannot be blank")
        @Pattern(regexp = "^[a-f0-9]{64}$", message = "Hash must be a 64-character lowercase hexadecimal string")
        private String hash;
        
        private String clientVersion;
        
        @NotBlank(message = "Replay protection hash cannot be blank")
        private String replayProtectionHash;
        
        private String userAgent;
        
        // Getters and setters
        public SoftwareIdentity getSoftwareIdentity() {
            return softwareIdentity;
        }
        
        public void setSoftwareIdentity(SoftwareIdentity softwareIdentity) {
            this.softwareIdentity = softwareIdentity;
        }
        
        public String getHash() {
            return hash;
        }
        
        public void setHash(String hash) {
            this.hash = hash;
        }
        
        public String getClientVersion() {
            return clientVersion;
        }
        
        public void setClientVersion(String clientVersion) {
            this.clientVersion = clientVersion;
        }
        
        public String getReplayProtectionHash() {
            return replayProtectionHash;
        }
        
        public void setReplayProtectionHash(String replayProtectionHash) {
            this.replayProtectionHash = replayProtectionHash;
        }
        
        public String getUserAgent() {
            return userAgent;
        }
        
        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }
    }
    
    /**
     * Request DTO for hash verification
     */
    public static class VerificationRequest {
        
        @Valid
        @jakarta.validation.constraints.NotNull(message = "Software identity cannot be null")
        private SoftwareIdentity softwareIdentity;
        
        @NotBlank(message = "Hash cannot be blank")
        @Pattern(regexp = "^[a-f0-9]{64}$", message = "Hash must be a 64-character lowercase hexadecimal string")
        private String hash;
        
        // Getters and setters
        public SoftwareIdentity getSoftwareIdentity() {
            return softwareIdentity;
        }
        
        public void setSoftwareIdentity(SoftwareIdentity softwareIdentity) {
            this.softwareIdentity = softwareIdentity;
        }
        
        public String getHash() {
            return hash;
        }
        
        public void setHash(String hash) {
            this.hash = hash;
        }
    }
}
