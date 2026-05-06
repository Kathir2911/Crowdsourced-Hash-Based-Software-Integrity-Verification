package com.hashverify.validation;

import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.LowerChars;
import net.jqwik.api.constraints.NumericChars;
import net.jqwik.api.constraints.StringLength;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for HashValidator
 * **Property 26: Hash Parsing and Validation**
 * **Validates: Requirements 11.1**
 */
class HashValidatorProperties {
    
    private final HashValidator hashValidator = new HashValidator();
    
    /**
     * Property 26a: Valid Hash Format
     * 
     * For any valid 64-character lowercase hexadecimal string,
     * the hash validator should accept it as valid.
     * 
     * **Validates: Requirement 11.1**
     */
    @Property(tries = 50)
    void validHashFormat_ShouldBeAccepted(@ForAll("validBlake3Hashes") String hash) {
        // Act
        ValidationResult result = hashValidator.validateHash(hash);
        
        // Assert
        assertThat(result.isValid()).isTrue();
        assertThat(hashValidator.isValidHash(hash)).isTrue();
    }
    
    /**
     * Property 26b: Hash Parsing Idempotence
     * 
     * For any valid hash, parsing it multiple times should produce the same result.
     * 
     * **Validates: Requirement 11.1**
     */
    @Property(tries = 50)
    void hashParsing_ShouldBeIdempotent(@ForAll("validBlake3Hashes") String hash) {
        // Act
        String parsed1 = hashValidator.parseHash(hash);
        String parsed2 = hashValidator.parseHash(parsed1);
        String parsed3 = hashValidator.parseHash(parsed2);
        
        // Assert
        assertThat(parsed1).isEqualTo(parsed2);
        assertThat(parsed2).isEqualTo(parsed3);
        assertThat(parsed1).isEqualTo(hash.toLowerCase());
    }
    
    /**
     * Property 26c: Invalid Length Rejection
     * 
     * For any string that is not exactly 64 characters long,
     * the hash validator should reject it.
     * 
     * **Validates: Requirement 11.1**
     */
    @Property(tries = 50)
    void invalidLength_ShouldBeRejected(
            @ForAll @StringLength(min = 1, max = 200) String hash) {
        Assume.that(hash.length() != 64);
        Assume.that(!hash.isEmpty()); // Skip empty strings as they have their own error message
        
        // Act
        ValidationResult result = hashValidator.validateHash(hash);
        
        // Assert
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("64 characters");
    }
    
    /**
     * Property 26d: Non-Hexadecimal Character Rejection
     * 
     * For any 64-character string containing non-hexadecimal characters,
     * the hash validator should reject it.
     * 
     * **Validates: Requirement 11.1**
     */
    @Property(tries = 50)
    void nonHexadecimalCharacters_ShouldBeRejected(
            @ForAll @StringLength(64) @AlphaChars String hash) {
        // Filter to only test strings with non-hex characters
        Assume.that(hash.matches(".*[g-zG-Z].*"));
        
        // Act
        ValidationResult result = hashValidator.validateHash(hash);
        
        // Assert
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("hexadecimal");
    }
    
    /**
     * Property 26e: Uppercase Hash Rejection
     * 
     * For any hash containing uppercase characters,
     * the hash validator should reject it (only lowercase allowed).
     * 
     * **Validates: Requirement 11.1, 11.3**
     */
    @Property(tries = 50)
    void uppercaseHash_ShouldBeRejected(@ForAll("validBlake3Hashes") String hash) {
        String uppercaseHash = hash.toUpperCase();
        Assume.that(!hash.equals(uppercaseHash)); // Only test if there are letters
        
        // Act
        ValidationResult result = hashValidator.validateHash(uppercaseHash);
        
        // Assert
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("lowercase");
    }
    
    /**
     * Property 26f: Null and Empty Hash Rejection
     * 
     * Null and empty hashes should always be rejected.
     * 
     * **Validates: Requirement 11.1**
     */
    @Property(tries = 10)
    void nullAndEmptyHash_ShouldBeRejected() {
        // Test null
        ValidationResult nullResult = hashValidator.validateHash(null);
        assertThat(nullResult.isValid()).isFalse();
        assertThat(nullResult.getErrorMessage()).contains("null");
        
        // Test empty
        ValidationResult emptyResult = hashValidator.validateHash("");
        assertThat(emptyResult.isValid()).isFalse();
        assertThat(emptyResult.getErrorMessage()).contains("empty");
    }
    
    /**
     * Property 26g: Parse Invalid Hash Throws Exception
     * 
     * Attempting to parse an invalid hash should throw IllegalArgumentException.
     * 
     * **Validates: Requirement 11.1**
     */
    @Property(tries = 50)
    void parseInvalidHash_ShouldThrowException(
            @ForAll @StringLength(min = 0, max = 63) String invalidHash) {
        
        // Act & Assert
        assertThatThrownBy(() -> hashValidator.parseHash(invalidHash))
                .isInstanceOf(IllegalArgumentException.class);
    }
    
    /**
     * Property 26h: Hash Normalization to Lowercase
     * 
     * For any valid hash (even with mixed case during generation),
     * parsing should normalize it to lowercase.
     * 
     * **Validates: Requirement 11.3**
     */
    @Property(tries = 50)
    void hashParsing_ShouldNormalizeToLowercase(@ForAll("validBlake3Hashes") String hash) {
        // Act
        String parsed = hashValidator.parseHash(hash);
        
        // Assert
        assertThat(parsed).isEqualTo(hash.toLowerCase());
        assertThat(parsed).isLowerCase();
    }
    
    // Generators
    
    @Provide
    @StringLength(64)
    @LowerChars
    @NumericChars
    Arbitrary<String> validBlake3Hashes() {
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .withCharRange('a', 'f')
                .ofLength(64);
    }
}
