package com.hashverify.model;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Entity representing consensus calculation results for a software identity
 */
@Entity
@Table(name = "consensus_cache", indexes = {
    @Index(name = "idx_expires", columnList = "expiresAt"),
    @Index(name = "idx_lifecycle", columnList = "lifecycle"),
    @Index(name = "idx_last_updated", columnList = "lastUpdated")
})
public class ConsensusResult {
    
    @Id
    @Column(name = "software_identity_hash", length = 64)
    @NotBlank(message = "Software identity hash cannot be blank")
    @Size(max = 64, message = "Software identity hash must not exceed 64 characters")
    private String softwareIdentityHash;
    
    @Embedded
    @Valid
    @NotNull(message = "Software identity cannot be null")
    @AttributeOverrides({
        @AttributeOverride(name = "sourceDomain", column = @Column(name = "source_domain")),
        @AttributeOverride(name = "normalizedFilename", column = @Column(name = "normalized_filename")),
        @AttributeOverride(name = "fileSize", column = @Column(name = "file_size"))
    })
    private SoftwareIdentity softwareIdentity;
    
    @Column(name = "consensus_hash", length = 64)
    @Size(max = 64, message = "Consensus hash must not exceed 64 characters")
    private String consensusHash;
    
    @Column(name = "confidence")
    @DecimalMin(value = "0.0", message = "Confidence must be between 0.0 and 1.0")
    @DecimalMax(value = "1.0", message = "Confidence must be between 0.0 and 1.0")
    @NotNull(message = "Confidence cannot be null")
    private Double confidence;
    
    @Column(name = "submission_count")
    @PositiveOrZero(message = "Submission count must be zero or positive")
    @NotNull(message = "Submission count cannot be null")
    private Integer submissionCount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32)
    @NotNull(message = "Status cannot be null")
    private ConsensusStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle", length = 32)
    @NotNull(message = "Lifecycle cannot be null")
    private ConsensusLifecycle lifecycle;
    
    @Column(name = "last_updated")
    @UpdateTimestamp
    private LocalDateTime lastUpdated;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "oldest_submission")
    private LocalDateTime oldestSubmission;
    
    @Column(name = "newest_submission")
    private LocalDateTime newestSubmission;
    
    // Hash distribution stored as JSON string for simplicity in MVP
    @Column(name = "hash_distribution", columnDefinition = "TEXT")
    private String hashDistributionJson;
    
    // Default constructor for JPA
    public ConsensusResult() {}
    
    public ConsensusResult(SoftwareIdentity softwareIdentity, String consensusHash, 
                          Double confidence, Integer submissionCount, ConsensusStatus status, 
                          ConsensusLifecycle lifecycle) {
        this.softwareIdentity = softwareIdentity;
        this.softwareIdentityHash = softwareIdentity.generateIdentityHash();
        this.consensusHash = consensusHash;
        this.confidence = confidence;
        this.submissionCount = submissionCount;
        this.status = status;
        this.lifecycle = lifecycle;
    }
    
    /**
     * Get hash distribution as a map
     */
    public Map<String, Integer> getHashDistribution() {
        Map<String, Integer> distribution = new HashMap<>();
        if (hashDistributionJson != null && !hashDistributionJson.trim().isEmpty()) {
            // Simple parsing for MVP - in production would use Jackson
            String[] pairs = hashDistributionJson.replace("{", "").replace("}", "").split(",");
            for (String pair : pairs) {
                if (pair.trim().isEmpty()) continue;
                String[] keyValue = pair.split(":");
                if (keyValue.length == 2) {
                    String hash = keyValue[0].trim().replace("\"", "");
                    Integer count = Integer.parseInt(keyValue[1].trim());
                    distribution.put(hash, count);
                }
            }
        }
        return distribution;
    }
    
    /**
     * Set hash distribution from a map
     */
    public void setHashDistribution(Map<String, Integer> distribution) {
        if (distribution == null || distribution.isEmpty()) {
            this.hashDistributionJson = "{}";
            return;
        }
        
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        json.append("}");
        this.hashDistributionJson = json.toString();
    }
    
    // Getters and setters
    public String getSoftwareIdentityHash() {
        return softwareIdentityHash;
    }
    
    public void setSoftwareIdentityHash(String softwareIdentityHash) {
        this.softwareIdentityHash = softwareIdentityHash;
    }
    
    public SoftwareIdentity getSoftwareIdentity() {
        return softwareIdentity;
    }
    
    public void setSoftwareIdentity(SoftwareIdentity softwareIdentity) {
        this.softwareIdentity = softwareIdentity;
        if (softwareIdentity != null) {
            this.softwareIdentityHash = softwareIdentity.generateIdentityHash();
        }
    }
    
    public String getConsensusHash() {
        return consensusHash;
    }
    
    public void setConsensusHash(String consensusHash) {
        this.consensusHash = consensusHash;
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
    
    public ConsensusStatus getStatus() {
        return status;
    }
    
    public void setStatus(ConsensusStatus status) {
        this.status = status;
    }
    
    public ConsensusLifecycle getLifecycle() {
        return lifecycle;
    }
    
    public void setLifecycle(ConsensusLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }
    
    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public LocalDateTime getOldestSubmission() {
        return oldestSubmission;
    }
    
    public void setOldestSubmission(LocalDateTime oldestSubmission) {
        this.oldestSubmission = oldestSubmission;
    }
    
    public LocalDateTime getNewestSubmission() {
        return newestSubmission;
    }
    
    public void setNewestSubmission(LocalDateTime newestSubmission) {
        this.newestSubmission = newestSubmission;
    }
    
    public String getHashDistributionJson() {
        return hashDistributionJson;
    }
    
    public void setHashDistributionJson(String hashDistributionJson) {
        this.hashDistributionJson = hashDistributionJson;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConsensusResult that = (ConsensusResult) o;
        return Objects.equals(softwareIdentityHash, that.softwareIdentityHash);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(softwareIdentityHash);
    }
    
    @Override
    public String toString() {
        return "ConsensusResult{" +
               "softwareIdentityHash='" + softwareIdentityHash + '\'' +
               ", consensusHash='" + consensusHash + '\'' +
               ", confidence=" + confidence +
               ", submissionCount=" + submissionCount +
               ", status=" + status +
               ", lifecycle=" + lifecycle +
               ", lastUpdated=" + lastUpdated +
               '}';
    }
}