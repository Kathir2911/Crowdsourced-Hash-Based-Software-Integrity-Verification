package com.hashverify.model;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Objects;

/**
 * Embeddable entity representing software identity for hash grouping
 */
@Embeddable
public class SoftwareIdentity {
    
    @NotBlank(message = "Source domain cannot be blank")
    @Size(max = 255, message = "Source domain must not exceed 255 characters")
    private String sourceDomain;
    
    @NotBlank(message = "Normalized filename cannot be blank")
    @Size(max = 255, message = "Normalized filename must not exceed 255 characters")
    private String normalizedFilename;
    
    @NotNull(message = "File size cannot be null")
    @Positive(message = "File size must be positive")
    private Long fileSize;
    
    // Default constructor for JPA
    public SoftwareIdentity() {}
    
    public SoftwareIdentity(String sourceDomain, String normalizedFilename, Long fileSize) {
        this.sourceDomain = sourceDomain;
        this.normalizedFilename = normalizedFilename;
        this.fileSize = fileSize;
    }
    
    /**
     * Generate identity hash for grouping submissions
     */
    public String generateIdentityHash() {
        String identityString = sourceDomain + "|" + normalizedFilename + "|" + fileSize;
        return Integer.toHexString(identityString.hashCode());
    }
    
    // Getters and setters
    public String getSourceDomain() {
        return sourceDomain;
    }
    
    public void setSourceDomain(String sourceDomain) {
        this.sourceDomain = sourceDomain;
    }
    
    public String getNormalizedFilename() {
        return normalizedFilename;
    }
    
    public void setNormalizedFilename(String normalizedFilename) {
        this.normalizedFilename = normalizedFilename;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SoftwareIdentity that = (SoftwareIdentity) o;
        return Objects.equals(sourceDomain, that.sourceDomain) &&
               Objects.equals(normalizedFilename, that.normalizedFilename) &&
               Objects.equals(fileSize, that.fileSize);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(sourceDomain, normalizedFilename, fileSize);
    }
    
    @Override
    public String toString() {
        return "SoftwareIdentity{" +
               "sourceDomain='" + sourceDomain + '\'' +
               ", normalizedFilename='" + normalizedFilename + '\'' +
               ", fileSize=" + fileSize +
               '}';
    }
}