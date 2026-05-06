package com.hashverify.validation;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Validator for BLAKE3 hash strings
 * Implements requirement 11.1: Hash parsing and validation
 */
@Component
public class HashValidator {
    
    private static final Pattern HASH_PATTERN = Pattern.compile("^[a-f0-9]{64}$");
    private static final int HASH_LENGTH = 64;
    
    /**
     * Validate hash format
     * 
     * @param hash The hash string to validate
     * @return ValidationResult with validation status
     */
    public ValidationResult validateHash(String hash) {
        if (hash == null) {
            return ValidationResult.invalid("Hash cannot be null");
        }
        
        if (hash.isEmpty()) {
            return ValidationResult.invalid("Hash cannot be empty");
        }
        
        if (hash.length() != HASH_LENGTH) {
            return ValidationResult.invalid(
                String.format("Hash must be exactly %d characters long, got %d", HASH_LENGTH, hash.length())
            );
        }
        
        if (!HASH_PATTERN.matcher(hash).matches()) {
            return ValidationResult.invalid(
                "Hash must be a lowercase hexadecimal string (only characters 0-9 and a-f allowed)"
            );
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Parse and normalize hash string
     * 
     * @param hash The hash string to parse
     * @return Normalized hash string (lowercase)
     * @throws IllegalArgumentException if hash is invalid
     */
    public String parseHash(String hash) {
        ValidationResult result = validateHash(hash);
        
        if (!result.isValid()) {
            throw new IllegalArgumentException(result.getErrorMessage());
        }
        
        return hash.toLowerCase();
    }
    
    /**
     * Check if a string is a valid hash format
     * 
     * @param hash The hash string to check
     * @return true if valid, false otherwise
     */
    public boolean isValidHash(String hash) {
        return validateHash(hash).isValid();
    }
}
