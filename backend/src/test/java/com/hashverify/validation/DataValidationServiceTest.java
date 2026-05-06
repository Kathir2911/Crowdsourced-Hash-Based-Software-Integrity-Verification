package com.hashverify.validation;

import com.hashverify.model.HashSubmission;
import com.hashverify.model.SoftwareIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DataValidationServiceTest {

    private DataValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new DataValidationService();
    }

    @Test
    void validateBlake3Hash_ValidHash_ReturnsValid() {
        String validHash = "a1b2c3d4e5f67890123456789012345678901234567890123456789012345678";
        ValidationResult result = validationService.validateBlake3Hash(validHash);
        assertTrue(result.isValid());
    }

    @Test
    void validateBlake3Hash_InvalidLength_ReturnsInvalid() {
        String invalidHash = "a1b2c3d4e5f6";
        ValidationResult result = validationService.validateBlake3Hash(invalidHash);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("64-character"));
    }

    @Test
    void validateBlake3Hash_NonHexadecimal_ReturnsInvalid() {
        String invalidHash = "g1b2c3d4e5f67890123456789012345678901234567890123456789012345678";
        ValidationResult result = validationService.validateBlake3Hash(invalidHash);
        assertFalse(result.isValid());
    }

    @Test
    void validateBlake3Hash_EmptyHash_ReturnsInvalid() {
        ValidationResult result = validationService.validateBlake3Hash("");
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("null or empty"));
    }

    @Test
    void validateSoftwareIdentity_ValidIdentity_ReturnsValid() {
        SoftwareIdentity identity = new SoftwareIdentity("example.com", "setup.exe", 1024L);
        ValidationResult result = validationService.validateSoftwareIdentity(identity);
        assertTrue(result.isValid());
    }

    @Test
    void validateSoftwareIdentity_InvalidDomain_ReturnsInvalid() {
        SoftwareIdentity identity = new SoftwareIdentity("invalid-domain", "setup.exe", 1024L);
        ValidationResult result = validationService.validateSoftwareIdentity(identity);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("domain format"));
    }

    @Test
    void validateSoftwareIdentity_ZeroFileSize_ReturnsInvalid() {
        SoftwareIdentity identity = new SoftwareIdentity("example.com", "setup.exe", 0L);
        ValidationResult result = validationService.validateSoftwareIdentity(identity);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("positive"));
    }

    @Test
    void validateHashSubmission_ValidSubmission_ReturnsValid() {
        SoftwareIdentity identity = new SoftwareIdentity("example.com", "setup.exe", 1024L);
        HashSubmission submission = new HashSubmission(
            identity,
            "a1b2c3d4e5f67890123456789012345678901234567890123456789012345678",
            "1.0.0",
            "abc123def456",
            "Mozilla/5.0"
        );
        submission.setTimestamp(LocalDateTime.now());
        
        ValidationResult result = validationService.validateHashSubmission(submission);
        assertTrue(result.isValid());
    }

    @Test
    void validateHashSubmission_InvalidHash_ReturnsInvalid() {
        SoftwareIdentity identity = new SoftwareIdentity("example.com", "setup.exe", 1024L);
        HashSubmission submission = new HashSubmission(
            identity,
            "invalid-hash",
            "1.0.0",
            "abc123def456",
            "Mozilla/5.0"
        );
        submission.setTimestamp(LocalDateTime.now());
        
        ValidationResult result = validationService.validateHashSubmission(submission);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Hash validation failed"));
    }

    @Test
    void normalizeFilename_UppercaseWithPath_ReturnsLowercase() {
        String result = validationService.normalizeFilename("/path/to/Setup.EXE");
        assertEquals("setup.exe", result);
    }

    @Test
    void normalizeFilename_WindowsPath_ReturnsFilenameOnly() {
        String result = validationService.normalizeFilename("C:\\Windows\\Setup.EXE");
        assertEquals("setup.exe", result);
    }

    @Test
    void normalizeDomain_HttpsWithWww_ReturnsCleanDomain() {
        String result = validationService.normalizeDomain("https://www.Example.COM/");
        assertEquals("example.com", result);
    }

    @Test
    void normalizeDomain_HttpWithoutWww_ReturnsCleanDomain() {
        String result = validationService.normalizeDomain("HTTP://Example.COM");
        assertEquals("example.com", result);
    }

    @Test
    void validateIdentityHashConsistency_ValidIdentity_ReturnsValid() {
        SoftwareIdentity identity = new SoftwareIdentity("example.com", "setup.exe", 1024L);
        ValidationResult result = validationService.validateIdentityHashConsistency(identity);
        assertTrue(result.isValid());
    }
}