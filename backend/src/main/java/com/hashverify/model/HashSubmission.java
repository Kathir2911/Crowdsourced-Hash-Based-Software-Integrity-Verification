package com.hashverify.model;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing a hash submission from a client
 */
@Entity
@Table(name = "hash_submissions", indexes = {
    @Index(name = "idx_software_identity_hash", columnList = "softwareIdentityHash"),
    @Index(name = "idx_hash", columnList = "hash"),
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_replay_protection", columnList = "replayProtectionHash, softwareIdentityHash, timestamp")
})
public class HashSubmission {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "software_identity_hash", nullable = false, length = 64)
    @NotBlank(message = "Software identity hash cannot be blank")
    @Size(max = 64, message = "Software identity hash must not exceed 64 characters")
    private String softwareIdentityHash;
    
    @Embedded
    @Valid
    @NotNull(message = "Software identity cannot be null")
    @AttributeOverrides({
        @AttributeOverride(name = "sourceDomain", column = @Column(name = "source_domain", nullable = false)),
        @AttributeOverride(name = "normalizedFilename", column = @Column(name = "normalized_filename", nullable = false)),
        @AttributeOverride(name = "fileSize", column = @Column(name = "file_size", nullable = false))
    })
    private SoftwareIdentity softwareIdentity;
    
    @Column(name = "hash", nullable = false, length = 64)
    @NotBlank(message = "Hash cannot be blank")
    @Pattern(regexp = "^[a-f0-9]{64}$", message = "Hash must be a 64-character lowercase hexadecimal string")
    private String hash;
    
    @Column(name = "timestamp", nullable = false)
    @CreationTimestamp
    private LocalDateTime timestamp;
    
    @Column(name = "client_version", length = 32)
    @Size(max = 32, message = "Client version must not exceed 32 characters")
    private String clientVersion;
    
    @Column(name = "replay_protection_hash", nullable = false, length = 64)
    @NotBlank(message = "Replay protection hash cannot be blank")
    @Size(max = 64, message = "Replay protection hash must not exceed 64 characters")
    private String replayProtectionHash;
    
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
    
    // Default constructor for JPA
    public HashSubmission() {}
    
    public HashSubmission(SoftwareIdentity softwareIdentity, String hash, String clientVersion, 
                         String replayProtectionHash, String userAgent) {
        this.softwareIdentity = softwareIdentity;
        this.softwareIdentityHash = softwareIdentity.generateIdentityHash();
        this.hash = hash.toLowerCase(); // Normalize to lowercase
        this.clientVersion = clientVersion;
        this.replayProtectionHash = replayProtectionHash;
        this.userAgent = userAgent;
    }
    
    // Getters and setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
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
    
    public String getHash() {
        return hash;
    }
    
    public void setHash(String hash) {
        this.hash = hash != null ? hash.toLowerCase() : null;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
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
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HashSubmission that = (HashSubmission) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "HashSubmission{" +
               "id=" + id +
               ", softwareIdentityHash='" + softwareIdentityHash + '\'' +
               ", hash='" + hash + '\'' +
               ", timestamp=" + timestamp +
               ", clientVersion='" + clientVersion + '\'' +
               '}';
    }
}