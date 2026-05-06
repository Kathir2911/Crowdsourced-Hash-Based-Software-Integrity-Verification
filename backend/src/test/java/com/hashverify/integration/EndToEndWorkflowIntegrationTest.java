package com.hashverify.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hashverify.controller.VerificationController;
import com.hashverify.model.SoftwareIdentity;
import com.hashverify.model.VerificationResult;
import com.hashverify.model.TamperStatus;
import com.hashverify.repository.HashSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for complete verification workflows
 * Uses TestContainers with real PostgreSQL database
 * 
 * Tests complete verification flow: file selection → hash → submission → verification
 * Tests consensus building with multiple client submissions
 * Tests tamper detection with conflicting hash submissions
 * 
 * **Validates: All requirements integration testing**
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration-test")
@Transactional
class EndToEndWorkflowIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private HashSubmissionRepository repository;
    
    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }
    
    /**
     * Test complete verification flow: file selection → hash → submission → verification
     * Simulates a user selecting a file, computing hash, submitting, and verifying
     */
    @Test
    void testCompleteVerificationFlow_SingleSubmission() throws Exception {
        // Given - Simulate file selection and hash computation
        SoftwareIdentity identity = new SoftwareIdentity("download.example.com", "setup.exe", 1048576L);
        String computedHash = "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd";
        
        VerificationController.SubmissionRequest submissionRequest = new VerificationController.SubmissionRequest();
        submissionRequest.setSoftwareIdentity(identity);
        submissionRequest.setHash(computedHash);
        submissionRequest.setClientVersion("1.0.0");
        submissionRequest.setReplayProtectionHash("test-replay-hash-1");
        submissionRequest.setUserAgent("TestClient/1.0");
        
        // When - Submit hash to backend
        MvcResult submissionResult = mockMvc.perform(post("/api/v1/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submissionRequest))
                .header("X-Forwarded-For", "192.168.1.1")
                .header("User-Agent", "TestClient/1.0"))
                .andExpect(status().isOk())
                .andReturn();
        
        String submissionResponse = submissionResult.getResponse().getContentAsString();
        assertThat(submissionResponse).contains("success");
        
        // When - Verify the same hash
        VerificationController.VerificationRequest verificationRequest = new VerificationController.VerificationRequest();
        verificationRequest.setSoftwareIdentity(identity);
        verificationRequest.setHash(computedHash);
        
        MvcResult verificationResult = mockMvc.perform(post("/api/v1/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verificationRequest)))
                .andExpect(status().isOk())
                .andReturn();
        
        // Then - Should return UNKNOWN status (insufficient data - only 1 submission)
        VerificationResult response = objectMapper.readValue(
                verificationResult.getResponse().getContentAsString(),
                VerificationResult.class
        );
        
        assertThat(response.getStatus()).isEqualTo(TamperStatus.UNKNOWN);
        assertThat(response.getSubmissionCount()).isEqualTo(1);
        assertThat(response.getMessage()).contains("Insufficient data");
    }
    
    /**
     * Test consensus building with multiple client submissions
     * Simulates multiple users submitting the same hash to establish consensus
     */
    @Test
    void testConsensusBuilding_MultipleSubmissions() throws Exception {
        // Given - Same software identity and hash from multiple clients
        SoftwareIdentity identity = new SoftwareIdentity("download.microsoft.com", "vscode.exe", 95000000L);
        String consensusHash = "b1c2d3e4f5a6789012345678901234567890123456789012345678901234bcde";
        
        // When - Submit from 5 different clients (different IPs)
        for (int i = 1; i <= 5; i++) {
            VerificationController.SubmissionRequest request = new VerificationController.SubmissionRequest();
            request.setSoftwareIdentity(identity);
            request.setHash(consensusHash);
            request.setClientVersion("1.0.0");
            request.setReplayProtectionHash("test-replay-hash-" + i);
            request.setUserAgent("TestClient/1.0");
            
            mockMvc.perform(post("/api/v1/submissions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("X-Forwarded-For", "192.168.1." + i)
                    .header("User-Agent", "TestClient/1.0"))
                    .andExpect(status().isOk());
        }
        
        // When - Verify the consensus hash
        VerificationController.VerificationRequest verificationRequest = new VerificationController.VerificationRequest();
        verificationRequest.setSoftwareIdentity(identity);
        verificationRequest.setHash(consensusHash);
        
        MvcResult result = mockMvc.perform(post("/api/v1/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verificationRequest)))
                .andExpect(status().isOk())
                .andReturn();
        
        // Then - Should return VERIFIED status with high confidence
        VerificationResult response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                VerificationResult.class
        );
        
        assertThat(response.getStatus()).isEqualTo(TamperStatus.VERIFIED);
        assertThat(response.getSubmissionCount()).isEqualTo(5);
        assertThat(response.getConfidence()).isGreaterThanOrEqualTo(0.70);
        assertThat(response.getConsensusHash()).isEqualTo(consensusHash);
        assertThat(response.getMessage()).contains("verified");
    }
    
    /**
     * Test tamper detection with conflicting hash submissions
     * Simulates a scenario where most users have one hash, but one user has a different hash
     */
    @Test
    void testTamperDetection_ConflictingSubmissions() throws Exception {
        // Given - Establish consensus with 10 submissions
        SoftwareIdentity identity = new SoftwareIdentity("download.mozilla.org", "firefox.exe", 75000000L);
        String legitimateHash = "c1d2e3f4a5b6789012345678901234567890123456789012345678901234cdef";
        String tamperedHash = "d1e2f3a4b5c6789012345678901234567890123456789012345678901234defa";
        
        // When - Submit legitimate hash from 10 clients
        for (int i = 1; i <= 10; i++) {
            VerificationController.SubmissionRequest request = new VerificationController.SubmissionRequest();
            request.setSoftwareIdentity(identity);
            request.setHash(legitimateHash);
            request.setClientVersion("1.0.0");
            request.setReplayProtectionHash("test-replay-hash-" + i);
            request.setUserAgent("TestClient/1.0");
            
            mockMvc.perform(post("/api/v1/submissions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("X-Forwarded-For", "10.0.0." + i)
                    .header("User-Agent", "TestClient/1.0"))
                    .andExpect(status().isOk());
        }
        
        // When - Submit tampered hash from 2 clients
        for (int i = 1; i <= 2; i++) {
            VerificationController.SubmissionRequest request = new VerificationController.SubmissionRequest();
            request.setSoftwareIdentity(identity);
            request.setHash(tamperedHash);
            request.setClientVersion("1.0.0");
            request.setReplayProtectionHash("test-replay-hash-" + i);
            request.setUserAgent("TestClient/1.0");
            
            mockMvc.perform(post("/api/v1/submissions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("X-Forwarded-For", "172.16.0." + i)
                    .header("User-Agent", "TestClient/1.0"))
                    .andExpect(status().isOk());
        }
        
        // When - Verify the legitimate hash
        VerificationController.VerificationRequest legitimateRequest = new VerificationController.VerificationRequest();
        legitimateRequest.setSoftwareIdentity(identity);
        legitimateRequest.setHash(legitimateHash);
        
        MvcResult legitimateResult = mockMvc.perform(post("/api/v1/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(legitimateRequest)))
                .andExpect(status().isOk())
                .andReturn();
        
        // Then - Legitimate hash should be VERIFIED
        VerificationResult legitimateResponse = objectMapper.readValue(
                legitimateResult.getResponse().getContentAsString(),
                VerificationResult.class
        );
        
        assertThat(legitimateResponse.getStatus()).isEqualTo(TamperStatus.VERIFIED);
        assertThat(legitimateResponse.getSubmissionCount()).isEqualTo(12);
        assertThat(legitimateResponse.getConfidence()).isGreaterThanOrEqualTo(0.70);
        
        // When - Verify the tampered hash
        VerificationController.VerificationRequest tamperedRequest = new VerificationController.VerificationRequest();
        tamperedRequest.setSoftwareIdentity(identity);
        tamperedRequest.setHash(tamperedHash);
        
        MvcResult tamperedResult = mockMvc.perform(post("/api/v1/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tamperedRequest)))
                .andExpect(status().isOk())
                .andReturn();
        
        // Then - Tampered hash should be TAMPERED
        VerificationResult tamperedResponse = objectMapper.readValue(
                tamperedResult.getResponse().getContentAsString(),
                VerificationResult.class
        );
        
        assertThat(tamperedResponse.getStatus()).isEqualTo(TamperStatus.TAMPERED);
        assertThat(tamperedResponse.getMessage()).containsIgnoringCase("tampered");
    }
    
    /**
     * Test consensus with weak confidence (70-80% agreement, <10 submissions)
     * Should return SUSPICIOUS_LOW_CONFIDENCE status
     */
    @Test
    void testConsensusBuilding_WeakConfidence() throws Exception {
        // Given - Software identity with mixed submissions
        SoftwareIdentity identity = new SoftwareIdentity("download.example.org", "app.dmg", 50000000L);
        String majorityHash = "e1f2a3b4c5d6789012345678901234567890123456789012345678901234efab";
        String minorityHash = "f1a2b3c4d5e6789012345678901234567890123456789012345678901234fabc";
        
        // When - Submit majority hash from 5 clients (71% of 7 total)
        for (int i = 1; i <= 5; i++) {
            VerificationController.SubmissionRequest request = new VerificationController.SubmissionRequest();
            request.setSoftwareIdentity(identity);
            request.setHash(majorityHash);
            request.setClientVersion("1.0.0");
            request.setReplayProtectionHash("test-replay-hash-" + i);
            request.setUserAgent("TestClient/1.0");
            
            mockMvc.perform(post("/api/v1/submissions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("X-Forwarded-For", "192.168.2." + i)
                    .header("User-Agent", "TestClient/1.0"))
                    .andExpect(status().isOk());
        }
        
        // When - Submit minority hash from 2 clients
        for (int i = 1; i <= 2; i++) {
            VerificationController.SubmissionRequest request = new VerificationController.SubmissionRequest();
            request.setSoftwareIdentity(identity);
            request.setHash(minorityHash);
            request.setClientVersion("1.0.0");
            request.setReplayProtectionHash("test-replay-hash-" + i);
            request.setUserAgent("TestClient/1.0");
            
            mockMvc.perform(post("/api/v1/submissions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("X-Forwarded-For", "192.168.3." + i)
                    .header("User-Agent", "TestClient/1.0"))
                    .andExpect(status().isOk());
        }
        
        // When - Verify the majority hash
        VerificationController.VerificationRequest verificationRequest = new VerificationController.VerificationRequest();
        verificationRequest.setSoftwareIdentity(identity);
        verificationRequest.setHash(majorityHash);
        
        MvcResult result = mockMvc.perform(post("/api/v1/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verificationRequest)))
                .andExpect(status().isOk())
                .andReturn();
        
        // Then - Should return SUSPICIOUS_LOW_CONFIDENCE (consensus exists but <10 submissions)
        VerificationResult response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                VerificationResult.class
        );
        
        assertThat(response.getStatus()).isEqualTo(TamperStatus.SUSPICIOUS_LOW_CONFIDENCE);
        assertThat(response.getSubmissionCount()).isEqualTo(7);
        assertThat(response.getConfidence()).isGreaterThanOrEqualTo(0.70).isLessThan(0.80);
    }
    
    /**
     * Test replay protection - duplicate submissions from same client should be ignored
     */
    @Test
    void testReplayProtection_DuplicateSubmissionsIgnored() throws Exception {
        // Given - Same client trying to submit multiple times
        SoftwareIdentity identity = new SoftwareIdentity("download.test.com", "test.exe", 1024L);
        String hash = "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd";
        
        String ipAddress = "192.168.100.1";
        String userAgent = "TestClient/1.0";
        
        VerificationController.SubmissionRequest request = new VerificationController.SubmissionRequest();
        request.setSoftwareIdentity(identity);
        request.setHash(hash);
        request.setClientVersion("1.0.0");
        request.setReplayProtectionHash("test-replay-hash-1");
        request.setUserAgent(userAgent);
        
        // When - Submit first time (should succeed)
        mockMvc.perform(post("/api/v1/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Forwarded-For", ipAddress)
                .header("User-Agent", userAgent))
                .andExpect(status().isOk());
        
        // When - Submit second time with same IP and User-Agent (should be rejected as duplicate)
        MvcResult duplicateResult = mockMvc.perform(post("/api/v1/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Forwarded-For", ipAddress)
                .header("User-Agent", userAgent))
                .andExpect(status().isConflict())
                .andReturn();
        
        // Then - Should reject duplicate submission
        String response = duplicateResult.getResponse().getContentAsString();
        assertThat(response).containsIgnoringCase("duplicate");
        
        // Verify only one submission was stored
        long count = repository.count();
        assertThat(count).isEqualTo(1);
    }
}
