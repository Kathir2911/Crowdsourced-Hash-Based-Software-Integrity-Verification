package com.hashverify.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Class representing the result of hash verification
 */
public class VerificationResult {
    
    @NotNull(message = "Status cannot be null")
    private TamperStatus status;
    
    @DecimalMin(value = "0.0", message = "Confidence must be between 0.0 and 1.0")
    @DecimalMax(value = "1.0", message = "Confidence must be between 0.0 and 1.0")
    @NotNull(message = "Confidence cannot be null")
    private Double confidence;
    
    @PositiveOrZero(message = "Submission count must be zero or positive")
    @NotNull(message = "Submission count cannot be null")
    private Integer submissionCount;
    
    @Size(max = 64, message = "Consensus hash must not exceed 64 characters")
    private String consensusHash;
    
    @NotBlank(message = "Message cannot be blank")
    @Size(max = 500, message = "Message must not exceed 500 characters")
    private String message;
    
    @NotNull(message = "Timestamp cannot be null")
    private LocalDateTime timestamp;
    
    @NotNull(message = "Suspicion level cannot be null")
    private SuspicionLevel suspicionLevel;
    
    @NotBlank(message = "Recommended action cannot be blank")
    @Size(max = 500, message = "Recommended action must not exceed 500 characters")
    private String recommendedAction;
    
    // Default constructor
    public VerificationResult() {
        this.timestamp = LocalDateTime.now();
    }
    
    public VerificationResult(TamperStatus status, Double confidence, Integer submissionCount,
                             String consensusHash, String message, SuspicionLevel suspicionLevel,
                             String recommendedAction) {
        this.status = status;
        this.confidence = confidence;
        this.submissionCount = submissionCount;
        this.consensusHash = consensusHash;
        this.message = message;
        this.suspicionLevel = suspicionLevel;
        this.recommendedAction = recommendedAction;
        this.timestamp = LocalDateTime.now();
    }
    
    // Getters and setters
    public TamperStatus getStatus() {
        return status;
    }
    
    public void setStatus(TamperStatus status) {
        this.status = status;
    }
    
    public Double getConfidence() {
        return confidence;
    }
    
    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }
    
    public Integer getSubmissionCount() {
        return submissionCount;
    }
    
    public void setSubmissionCount(Integer submissionCount) {
        this.submissionCount = submissionCount;
    }
    
    public String getConsensusHash() {
        return consensusHash;
    }
    
    public void setConsensusHash(String consensusHash) {
        this.consensusHash = consensusHash;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public SuspicionLevel getSuspicionLevel() {
        return suspicionLevel;
    }
    
    public void setSuspicionLevel(SuspicionLevel suspicionLevel) {
        this.suspicionLevel = suspicionLevel;
    }
    
    public String getRecommendedAction() {
        return recommendedAction;
    }
    
    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VerificationResult that = (VerificationResult) o;
        return Objects.equals(status, that.status) &&
               Objects.equals(confidence, that.confidence) &&
               Objects.equals(submissionCount, that.submissionCount) &&
               Objects.equals(consensusHash, that.consensusHash) &&
               Objects.equals(message, that.message) &&
               Objects.equals(timestamp, that.timestamp) &&
               Objects.equals(suspicionLevel, that.suspicionLevel) &&
               Objects.equals(recommendedAction, that.recommendedAction);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(status, confidence, submissionCount, consensusHash, 
                           message, timestamp, suspicionLevel, recommendedAction);
    }
    
    @Override
    public String toString() {
        return "VerificationResult{" +
               "status=" + status +
               ", confidence=" + confidence +
               ", submissionCount=" + submissionCount +
               ", consensusHash='" + consensusHash + '\'' +
               ", message='" + message + '\'' +
               ", timestamp=" + timestamp +
               ", suspicionLevel=" + suspicionLevel +
               ", recommendedAction='" + recommendedAction + '\'' +
               '}';
    }
}