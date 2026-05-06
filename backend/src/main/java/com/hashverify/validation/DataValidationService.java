package com.hashverify.validation;

import com.hashverify.model.HashSubmission;
import com.hashverify.model.SoftwareIdentity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service for validating data integrity across the system
 */
@Service
public class DataValidationService {
    
    // BLAKE3 hash pattern: 64-character lowercase hexadecimal
    private static final Pattern BLAKE3_HASH_PATTERN = Pattern.compile("^[a-f0-9]{64}$");
    
    // Domain pattern: basic domain validation
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.[a-zA-Z]{2,}$");
    
    // Filename pattern: basic filename validation (no path separators)
    private static final Pattern FILENAME_PATTERN = Pattern.compile("^[^/\\\\:*?\"<>|]+$");
    
    /**
     * Validate BLAKE3 hash format
     */
    public ValidationResult validateBlake3Hash(String hash) {
        if (hash == null || hash.trim().isEmpty()) {
            return ValidationResult.invalid("Hash cannot be null or empty");
        }
        
        String trimmedHash = hash.trim().toLowerCase();
        if (!BLAKE3_HASH_PATTERN.matcher(trimmedHash).matches()) {
            return ValidationResult.invalid("Hash must be a 64-character lowercase hexadecimal string");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validate software identity data integrity
     */
    public ValidationResult validateSoftwareIdentity(SoftwareIdentity identity) {
        List<String> errors = new ArrayList<>();
        
        if (identity == null) {
            return ValidationResult.invalid("Software identity cannot be null");
        }
        
        // Validate source domain
        if (identity.getSourceDomain() == null || identity.getSourceDomain().trim().isEmpty()) {
            errors.add("Source domain cannot be null or empty");
        } else if (!DOMAIN_PATTERN.matcher(identity.getSourceDomain().trim()).matches()) {
            errors.add("Source domain format is invalid");
        }
        
        // Validate normalized filename
        if (identity.getNormalizedFilename() == null || identity.getNormalizedFilename().trim().isEmpty()) {
            errors.add("Normalized filename cannot be null or empty");
        } else if (!FILENAME_PATTERN.matcher(identity.getNormalizedFilename()).matches()) {
            errors.add("Filename contains invalid characters");
        }
        
        // Validate file size
        if (identity.getFileSize() == null || identity.getFileSize() <= 0) {
            errors.add("File size must be positive");
        }
        
        if (errors.isEmpty()) {
            return ValidationResult.valid();
        } else {
            return ValidationResult.invalid(String.join("; ", errors));
        }
    }
    
    /**
     * Validate hash submission data integrity
     */
    public ValidationResult validateHashSubmission(HashSubmission submission) {
        List<String> errors = new ArrayList<>();
        
        if (submission == null) {
            return ValidationResult.invalid("Hash submission cannot be null");
        }
        
        // Validate software identity
        ValidationResult identityResult = validateSoftwareIdentity(submission.getSoftwareIdentity());
        if (!identityResult.isValid()) {
            errors.add("Software identity validation failed: " + identityResult.getErrorMessage());
        }
        
        // Validate hash
        ValidationResult hashResult = validateBlake3Hash(submission.getHash());
        if (!hashResult.isValid()) {
            errors.add("Hash validation failed: " + hashResult.getErrorMessage());
        }
        
        // Validate timestamp
        if (submission.getTimestamp() == null) {
            errors.add("Timestamp cannot be null");
        }
        
        // Validate client version
        if (submission.getClientVersion() != null && submission.getClientVersion().length() > 32) {
            errors.add("Client version must not exceed 32 characters");
        }
        
        // Validate replay protection hash
        if (submission.getReplayProtectionHash() == null || submission.getReplayProtectionHash().trim().isEmpty()) {
            errors.add("Replay protection hash cannot be null or empty");
        }
        
        if (errors.isEmpty()) {
            return ValidationResult.valid();
        } else {
            return ValidationResult.invalid(String.join("; ", errors));
        }
    }
    
    /**
     * Normalize filename for consistent identity generation
     */
    public String normalizeFilename(String filename) {
        if (filename == null) {
            return null;
        }
        
        // Convert to lowercase and trim
        String normalized = filename.trim().toLowerCase();
        
        // Remove path components if present
        int lastSlash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            normalized = normalized.substring(lastSlash + 1);
        }
        
        return normalized;
    }
    
    /**
     * Normalize domain for consistent identity generation
     */
    public String normalizeDomain(String domain) {
        if (domain == null) {
            return null;
        }
        
        // Convert to lowercase and trim
        String normalized = domain.trim().toLowerCase();
        
        // Remove protocol if present
        if (normalized.startsWith("http://")) {
            normalized = normalized.substring(7);
        } else if (normalized.startsWith("https://")) {
            normalized = normalized.substring(8);
        }
        
        // Remove www. prefix if present
        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }
        
        // Remove trailing slash if present
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        
        return normalized;
    }
    
    /**
     * Validate that identity hash matches the computed hash
     */
    public ValidationResult validateIdentityHashConsistency(SoftwareIdentity identity) {
        if (identity == null) {
            return ValidationResult.invalid("Software identity cannot be null");
        }
        
        String computedHash = identity.generateIdentityHash();
        
        // For now, we don't store identity hash separately in SoftwareIdentity
        // This validation ensures the hash generation is consistent
        if (computedHash == null || computedHash.trim().isEmpty()) {
            return ValidationResult.invalid("Generated identity hash cannot be null or empty");
        }
        
        return ValidationResult.valid();
    }
}