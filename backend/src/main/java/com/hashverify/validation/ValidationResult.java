package com.hashverify.validation;

import java.util.Objects;

/**
 * Result of a validation operation
 */
public class ValidationResult {
    
    private final boolean valid;
    private final String errorMessage;
    
    private ValidationResult(boolean valid, String errorMessage) {
        this.valid = valid;
        this.errorMessage = errorMessage;
    }
    
    /**
     * Create a valid result
     */
    public static ValidationResult valid() {
        return new ValidationResult(true, null);
    }
    
    /**
     * Create an invalid result with error message
     */
    public static ValidationResult invalid(String errorMessage) {
        return new ValidationResult(false, errorMessage);
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationResult that = (ValidationResult) o;
        return valid == that.valid && Objects.equals(errorMessage, that.errorMessage);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(valid, errorMessage);
    }
    
    @Override
    public String toString() {
        return "ValidationResult{" +
               "valid=" + valid +
               ", errorMessage='" + errorMessage + '\'' +
               '}';
    }
}