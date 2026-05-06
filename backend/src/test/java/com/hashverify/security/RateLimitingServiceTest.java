package com.hashverify.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RateLimitingService
 */
class RateLimitingServiceTest {
    
    private RateLimitingService service;
    
    @BeforeEach
    void setUp() {
        service = new RateLimitingService();
    }
    
    @Test
    void testRateLimitEnforcementBlocksAfter100Requests() {
        // Arrange
        String ipAddress = "192.168.1.1";
        
        // Act - make exactly 100 requests
        for (int i = 0; i < 100; i++) {
            boolean allowed = service.isAllowed(ipAddress);
            assertThat(allowed).as("Request %d should be allowed", i + 1).isTrue();
        }
        
        // Assert - 101st request should be denied
        boolean blocked = service.isAllowed(ipAddress);
        assertThat(blocked).as("Request 101 should be blocked").isFalse();
    }
    
    @Test
    void testRequestCountAccuratelyTracks() {
        // Arrange
        String ipAddress = "192.168.1.1";
        
        // Act
        service.isAllowed(ipAddress);
        service.isAllowed(ipAddress);
        service.isAllowed(ipAddress);
        
        // Assert
        assertThat(service.getRequestCount(ipAddress)).isEqualTo(3);
    }
    
    @Test
    void testRemainingRequestsDecreases() {
        // Arrange
        String ipAddress = "192.168.1.1";
        
        // Act & Assert
        assertThat(service.getRemainingRequests(ipAddress)).isEqualTo(100);
        
        service.isAllowed(ipAddress);
        assertThat(service.getRemainingRequests(ipAddress)).isEqualTo(99);
        
        service.isAllowed(ipAddress);
        assertThat(service.getRemainingRequests(ipAddress)).isEqualTo(98);
    }
    
    @Test
    void testResetClearsHistory() {
        // Arrange
        String ipAddress = "192.168.1.1";
        service.isAllowed(ipAddress);
        service.isAllowed(ipAddress);
        
        // Act
        service.reset(ipAddress);
        
        // Assert
        assertThat(service.getRequestCount(ipAddress)).isZero();
        assertThat(service.getRemainingRequests(ipAddress)).isEqualTo(100);
    }
    
    @Test
    void testDifferentIPsIndependentLimits() {
        // Arrange
        String ip1 = "192.168.1.1";
        String ip2 = "192.168.1.2";
        
        // Act - exhaust limit for IP1
        for (int i = 0; i < 100; i++) {
            service.isAllowed(ip1);
        }
        
        // Assert
        assertThat(service.isAllowed(ip1)).isFalse();
        assertThat(service.isAllowed(ip2)).isTrue();
    }
}
