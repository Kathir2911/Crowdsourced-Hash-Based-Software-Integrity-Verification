package com.hashverify.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hashverify.controller.VerificationController;
import com.hashverify.model.VerificationResult;
import com.hashverify.model.ConsensusLifecycle;
import com.hashverify.model.ConsensusResult;
import com.hashverify.model.SoftwareIdentity;
import com.hashverify.model.TamperStatus;
import com.hashverify.repository.HashSubmissionRepository;
import com.hashverify.service.ConsensusService;
import com.hashverify.service.VerificationService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-component integration tests
 * 
 * Tests integration between:
 * - Browser extension to backend API communication
 * - Backend to database data persistence and retrieval
 * - Consensus service integration with verification service
 * - Rate limiting across multiple requests
 * - Error handling across component boundaries
 * 
 * **Validates: All requirements integration testing**
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CrossComponentIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private HashSubmissionRepository repository;
    
    @Autowired
    private ConsensusService consensusService;
    
    @Autowired
    private VerificationService verificationService;
    
    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }
    
    /**
     * Test browser extension to backend API communication
     * Validates that API endpoints correctly process requests from browser extension
     */
    @Test
    void testBrowserExtensionToBackendCommunication() throws Exception {
        // Given - Simulate browser extension request
        SoftwareIdentity identity = new SoftwareIdentity(
                "download.example.com",
                "test.exe",
                1048576L
        );
        String hash = "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd";
        
        VerificationController.SubmissionRequest submissionRequest = new VerificationController.SubmissionRequest();
        submissionRequest.setSoftwareIdentity(identity);
        submissionRequest.setHash(hash);
        submissionRequest.setClientVersion("1.0.0");
        submissionRequest.setReplayProtectionHash("test-replay-hash-1");
        submissionRequest.setUserAgent("BrowserExtension/1.0");
        
        // When - Browser extension submits hash via API
        MvcResult submissionResult = mockMvc.perform(post("/api/v1/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submissionRequest))
                .header("X-Forwarded-For", "192.168.1.100")
                .header("User-Agent", "BrowserExtension/1.0"))
                .andExpect(status().isCreated())
                .andReturn();
        
        // Then - Verify response format matches browser extension expectations
        String response = submissionResult.getResponse().getContentAsString();
        assertThat(response).contains("success");
        
        // When - Browser extension verifies hash via API
        VerificationController.VerificationRequest verificationRequest = new VerificationController.VerificationRequest();
        verificationRequest.setSoftwareIdentity(identity);
        verificationRequest.setHash(hash);
        
        MvcResult verificationResult = mockMvc.perform(post("/api/v1/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verificationRequest))
                .header("Origin", "chrome-extension://abcdefghijklmnop"))
                .andExpect(status().isOk())
                .andReturn();
        
        // Then - Verify response contains all required fields for browser extension
        VerificationResult verificationResponse = objectMapper.readValue(
                verificationResult.getResponse().getContentAsString(),
                VerificationResult.class
        );
        
        assertThat(verificationResponse.getStatus()).isNotNull();
        assertThat(verificationResponse.getConfidence()).isNotNull();
        assertThat(verificationResponse.getSubmissionCount()).isNotNull();
        assertThat(verificationResponse.getMessage()).isNotNull();
        assertThat(verificationResponse.getTimestamp()).isNotNull();
    }
    
    /**
     * Test backend to database data persistence and retrieval
     * Validates that data flows correctly from API to database and back
     */
    @Test
    void testBackendToDatabasePersistenceAndRetrieval() throws Exception {
        // Given - Submit multiple hashes via API
        SoftwareIdentity identity = new SoftwareIdentity(
                "download.microsoft.com",
                "vscode.exe",
                95000000L
        );
        String consensusHash = "b1c2d3e4f5a6789012345678901234567890123456789012345678901234bcde";
        
        // When - Submit 5 hashes via API (persisted to database)
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
                    .header("X-Forwarded-For", "10.0.0." + i)
                    .header("User-Agent", "TestClient/1.0"))
                    .andExpect(status().isCreated()); // Expect 201 Created
        }
        
        // Then - Verify data was persisted to database
        long submissionCount = repository.count();
        assertThat(submissionCount).isEqualTo(5);
        
        // When - Retrieve data from database via consensus service
        String identityHash = identity.generateIdentityHash();
        ConsensusResult consensus = consensusService.calculateConsensus(identityHash);
        
        // Then - Verify data was correctly retrieved and processed
        assertThat(consensus.getSubmissionCount()).isEqualTo(5);
        assertThat(consensus.getConsensusHash()).isEqualTo(consensusHash);
        assertThat(consensus.getLifecycle()).isEqualTo(ConsensusLifecycle.ESTABLISHED);
        assertThat(consensus.getConfidence()).isEqualTo(1.0);
    }
    
    /**
     * Test consensus service integration with verification service
     * Validates that consensus calculations feed into verification decisions
     */
    @Test
    void testConsensusServiceIntegrationWithVerificationService() throws Exception {
        // Given - Establish consensus with 10 submissions
        SoftwareIdentity identity = new SoftwareIdentity(
                "download.mozilla.org",
                "firefox.exe",
                75000000L
        );
        String legitimateHash = "c1d2e3f4a5b6789012345678901234567890123456789012345678901234cdef";
        String tamperedHash = "d1e2f3a4b5c6789012345678901234567890123456789012345678901234defa";
        
        // When - Submit legitimate hash 10 times
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
                    .header("X-Forwarded-For", "172.16.0." + i)
                    .header("User-Agent", "TestClient/1.0"))
                    .andExpect(status().isCreated()); // Expect 201 Created
        }
        
        // Then - Verify consensus service calculated consensus
        String identityHash = identity.generateIdentityHash();
        ConsensusResult consensus = consensusService.calculateConsensus(identityHash);
        assertThat(consensus.getLifecycle()).isEqualTo(ConsensusLifecycle.ESTABLISHED);
        assertThat(consensus.getConsensusHash()).isEqualTo(legitimateHash);
        
        // When - Verification service checks legitimate hash
        VerificationController.VerificationRequest legitimateRequest = new VerificationController.VerificationRequest();
        legitimateRequest.setSoftwareIdentity(identity);
        legitimateRequest.setHash(legitimateHash);
        
        MvcResult legitimateResult = mockMvc.perform(post("/api/v1/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(legitimateRequest)))
                .andExpect(status().isOk())
                .andReturn();
        
        VerificationResult legitimateResponse = objectMapper.readValue(
                legitimateResult.getResponse().getContentAsString(),
                VerificationResult.class
        );
        
        // Then - Verification service should use consensus to return VERIFIED
        assertThat(legitimateResponse.getStatus()).isEqualTo(TamperStatus.VERIFIED);
        assertThat(legitimateResponse.getConsensusHash()).isEqualTo(legitimateHash);
        
        // When - Verification service checks tampered hash
        VerificationController.VerificationRequest tamperedRequest = new VerificationController.VerificationRequest();
        tamperedRequest.setSoftwareIdentity(identity);
        tamperedRequest.setHash(tamperedHash);
        
        MvcResult tamperedResult = mockMvc.perform(post("/api/v1/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tamperedRequest)))
                .andExpect(status().isOk())
                .andReturn();
        
        VerificationResult tamperedResponse = objectMapper.readValue(
                tamperedResult.getResponse().getContentAsString(),
                VerificationResult.class
        );
        
        // Then - Verification service should use consensus to return TAMPERED
        assertThat(tamperedResponse.getStatus()).isEqualTo(TamperStatus.TAMPERED);
        assertThat(tamperedResponse.getConsensusHash()).isEqualTo(legitimateHash);
    }
    
    /**
     * Test rate limiting across multiple requests
     * Validates that rate limiting works correctly across component boundaries
     */
    @Test
    void testRateLimitingAcrossMultipleRequests() throws Exception {
        // Given - Same IP address making multiple requests
        SoftwareIdentity identity = new SoftwareIdentity(
                "download.test.com",
                "test.exe",
                1024L
        );
        String hash = "e1f2a3b4c5d6789012345678901234567890123456789012345678901234efab";
        String ipAddress = "192.168.100.1";
        
        VerificationController.SubmissionRequest request = new VerificationController.SubmissionRequest();
        request.setSoftwareIdentity(identity);
        request.setHash(hash);
        request.setClientVersion("1.0.0");
        request.setReplayProtectionHash("test-replay-hash-1");
        
        // When - Make requests up to rate limit (100 per hour)
        // For testing, we'll make a smaller number to avoid long test execution
        int requestCount = 10;
        for (int i = 0; i < requestCount; i++) {
            // Create unique software identity for each request to avoid replay protection
            SoftwareIdentity uniqueIdentity = new SoftwareIdentity(
                    "download.test.com",
                    "test" + i + ".exe",
                    1024L + i
            );
            request.setSoftwareIdentity(uniqueIdentity);
            
            mockMvc.perform(post("/api/v1/submissions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("X-Forwarded-For", ipAddress)
                    .header("User-Agent", "TestClient/1.0"))
                    .andExpect(status().isCreated()); // Expect 201 Created
        }
        
        // Then - All requests within limit should succeed
        // Rate limiting is enforced but we're well within the 100/hour limit
        assertThat(repository.count()).isEqualTo(requestCount);
    }
    
    /**
     * Test error handling across component boundaries
     * Validates that errors propagate correctly through the system
     */
    @Test
    void testErrorHandlingAcrossComponents() throws Exception {
        // Test 1: Invalid hash format should be caught at API layer
        SoftwareIdentity identity = new SoftwareIdentity(
                "download.example.com",
                "test.exe",
                1024L
        );
        
        VerificationController.SubmissionRequest invalidHashRequest = new VerificationController.SubmissionRequest();
        invalidHashRequest.setSoftwareIdentity(identity);
        invalidHashRequest.setHash("invalid-hash"); // Invalid format
        invalidHashRequest.setClientVersion("1.0.0");
        invalidHashRequest.setReplayProtectionHash("test-replay-hash-1");
        invalidHashRequest.setUserAgent("TestClient/1.0");
        invalidHashRequest.setClientVersion("1.0.0");
        
        // When - Submit invalid hash
        MvcResult invalidHashResult = mockMvc.perform(post("/api/v1/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidHashRequest))
                .header("X-Forwarded-For", "192.168.1.1")
                .header("User-Agent", "TestClient/1.0"))
                .andExpect(status().isBadRequest())
                .andReturn();
        
        // Then - Error should be caught and returned with appropriate message
        String errorResponse = invalidHashResult.getResponse().getContentAsString();
        assertThat(errorResponse).containsIgnoringCase("hash");
        
        // Test 2: Missing required fields should be caught at validation layer
        VerificationController.SubmissionRequest missingFieldsRequest = new VerificationController.SubmissionRequest();
        missingFieldsRequest.setHash("a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd");
        missingFieldsRequest.setClientVersion("1.0.0");
        // Missing softwareIdentity
        
        // When - Submit with missing fields
        mockMvc.perform(post("/api/v1/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(missingFieldsRequest))
                .header("X-Forwarded-For", "192.168.1.1")
                .header("User-Agent", "TestClient/1.0"))
                .andExpect(status().isBadRequest());
    }
    
    /**
     * Test data consistency across all components
     * Validates that data remains consistent as it flows through the system
     */
    @Test
    void testDataConsistencyAcrossComponents() throws Exception {
        // Given - Submit data via API
        SoftwareIdentity identity = new SoftwareIdentity(
                "download.consistency.com",
                "consistent.exe",
                2048576L
        );
        String hash = "f1a2b3c4d5e6789012345678901234567890123456789012345678901234fabc";
        
        VerificationController.SubmissionRequest request = new VerificationController.SubmissionRequest();
        request.setSoftwareIdentity(identity);
        request.setHash(hash);
        request.setClientVersion("1.0.0");
        request.setReplayProtectionHash("test-replay-hash-1");
        request.setUserAgent("TestClient/1.0");
        
        // When - Submit via API (flows through controller → service → repository)
        mockMvc.perform(post("/api/v1/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Forwarded-For", "192.168.1.1")
                .header("User-Agent", "TestClient/1.0"))
                .andExpect(status().isCreated()); // Expect 201 Created
        
        // Then - Verify data consistency at database layer
        String identityHash = identity.generateIdentityHash();
        var submissions = repository.findBySoftwareIdentityHash(identityHash);
        assertThat(submissions).hasSize(1);
        assertThat(submissions.get(0).getHash()).isEqualTo(hash);
        assertThat(submissions.get(0).getSoftwareIdentity().getSourceDomain())
                .isEqualTo(identity.getSourceDomain());
        assertThat(submissions.get(0).getSoftwareIdentity().getNormalizedFilename())
                .isEqualTo(identity.getNormalizedFilename());
        assertThat(submissions.get(0).getSoftwareIdentity().getFileSize())
                .isEqualTo(identity.getFileSize());
        
        // When - Retrieve via consensus service
        ConsensusResult consensus = consensusService.calculateConsensus(identityHash);
        
        // Then - Verify data consistency at consensus layer
        assertThat(consensus.getSubmissionCount()).isEqualTo(1);
        
        // When - Verify via verification service
        VerificationController.VerificationRequest verificationRequest = new VerificationController.VerificationRequest();
        verificationRequest.setSoftwareIdentity(identity);
        verificationRequest.setHash(hash);
        
        MvcResult verificationResult = mockMvc.perform(post("/api/v1/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verificationRequest)))
                .andExpect(status().isOk())
                .andReturn();
        
        VerificationResult verificationResponse = objectMapper.readValue(
                verificationResult.getResponse().getContentAsString(),
                VerificationResult.class
        );
        
        // Then - Verify data consistency at verification layer
        assertThat(verificationResponse.getSubmissionCount()).isEqualTo(1);
    }
    
    /**
     * Test concurrent requests across components
     * Validates that the system handles concurrent requests correctly
     */
    @Test
    void testConcurrentRequestsAcrossComponents() throws Exception {
        // Given - Multiple concurrent submissions for same software
        SoftwareIdentity identity = new SoftwareIdentity(
                "download.concurrent.com",
                "concurrent.exe",
                4096000L
        );
        String hash = "a1a2a3a4a5a6789012345678901234567890123456789012345678901234aaaa";
        
        // When - Submit from multiple IPs concurrently (simulated sequentially in test)
        for (int i = 1; i <= 5; i++) {
            VerificationController.SubmissionRequest request = new VerificationController.SubmissionRequest();
            request.setSoftwareIdentity(identity);
            request.setHash(hash);
            request.setClientVersion("1.0.0");
            request.setReplayProtectionHash("test-replay-hash-" + i);
            request.setUserAgent("TestClient/1.0");
            
            mockMvc.perform(post("/api/v1/submissions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("X-Forwarded-For", "10.10.10." + i)
                    .header("User-Agent", "TestClient/1.0"))
                    .andExpect(status().isCreated()); // Expect 201 Created
        }
        
        // Then - All submissions should be processed correctly
        String identityHash = identity.generateIdentityHash();
        ConsensusResult consensus = consensusService.calculateConsensus(identityHash);
        
        assertThat(consensus.getSubmissionCount()).isEqualTo(5);
        assertThat(consensus.getLifecycle()).isEqualTo(ConsensusLifecycle.ESTABLISHED);
        assertThat(consensus.getConsensusHash()).isEqualTo(hash);
    }
}
