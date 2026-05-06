package com.hashverify.controller;

import com.hashverify.model.*;
import com.hashverify.security.RateLimitingService;
import com.hashverify.service.SubmissionStorageService;
import com.hashverify.service.VerificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for VerificationController
 * Tests requirements 3.3, 5.1
 */
@WebMvcTest(controllers = VerificationController.class)
@AutoConfigureMockMvc(addFilters = false) // Disable security filters for testing
class VerificationControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private VerificationService verificationService;
    
    @MockBean
    private SubmissionStorageService submissionStorageService;
    
    @MockBean
    private RateLimitingService rateLimitingService;
    
    private SoftwareIdentity testIdentity;
    private String testHash;
    
    @BeforeEach
    void setUp() {
        testIdentity = new SoftwareIdentity("download.example.com", "setup.exe", 1048576L);
        
        // Mock rate limiting to always allow requests in tests
        when(rateLimitingService.isAllowed(anyString())).thenReturn(true);
        when(rateLimitingService.getRemainingRequests(anyString())).thenReturn(100);
        testHash = "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd";
    }
    
    @Test
    void testSubmitHash_Success() throws Exception {
        // Arrange
        VerificationController.SubmissionRequest request = new VerificationController.SubmissionRequest();
        request.setSoftwareIdentity(testIdentity);
        request.setHash(testHash);
        request.setClientVersion("1.0.0");
        request.setReplayProtectionHash("replay123");
        request.setUserAgent("Mozilla/5.0");
        
        HashSubmission savedSubmission = new HashSubmission(
            testIdentity, testHash, "1.0.0", "replay123", "Mozilla/5.0"
        );
        savedSubmission.setId(UUID.randomUUID());
        
        when(submissionStorageService.saveSubmission(any(HashSubmission.class)))
            .thenReturn(savedSubmission);
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.submissionId").exists())
                .andExpect(jsonPath("$.softwareIdentityHash").exists());
    }
    
    @Test
    void testVerifyHash_Success() throws Exception {
        // Arrange
        VerificationController.VerificationRequest request = new VerificationController.VerificationRequest();
        request.setSoftwareIdentity(testIdentity);
        request.setHash(testHash);
        
        VerificationResult mockResult = new VerificationResult(
            TamperStatus.VERIFIED,
            0.85,
            15,
            testHash,
            "File verified against consensus",
            SuspicionLevel.NONE,
            "File appears safe to use"
        );
        
        when(verificationService.verifyHash(any(String.class), any(SoftwareIdentity.class)))
            .thenReturn(mockResult);
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.confidence").value(0.85))
                .andExpect(jsonPath("$.submissionCount").value(15))
                .andExpect(jsonPath("$.suspicionLevel").value("NONE"));
    }
    
    @Test
    void testVerifyHash_InvalidHashFormat() throws Exception {
        // Arrange
        VerificationController.VerificationRequest request = new VerificationController.VerificationRequest();
        request.setSoftwareIdentity(testIdentity);
        request.setHash("invalid-hash"); // Invalid format
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void testGetStatus_Success() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.service").exists())
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }
    
    @Test
    void testSubmitHash_MissingHash() throws Exception {
        // Arrange
        VerificationController.SubmissionRequest request = new VerificationController.SubmissionRequest();
        request.setSoftwareIdentity(testIdentity);
        // Missing hash
        request.setClientVersion("1.0.0");
        request.setReplayProtectionHash("replay123");
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void testVerifyHash_MissingSoftwareIdentity() throws Exception {
        // Arrange
        VerificationController.VerificationRequest request = new VerificationController.VerificationRequest();
        // Missing software identity
        request.setHash(testHash);
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
