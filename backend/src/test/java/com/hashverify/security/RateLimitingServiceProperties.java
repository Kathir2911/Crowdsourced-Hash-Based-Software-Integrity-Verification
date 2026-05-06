package com.hashverify.security;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for RateLimitingService
 * Tests requirement 3.7: Rate limiting enforcement
 */
class RateLimitingServiceProperties {
    
    /**
     * **Property 8: Rate Limiting Enforcement**
     * **Validates: Requirement 3.7**
     * 
     * Property: For any IP address, after 100 requests within an hour,
     * subsequent requests should be denied until the time window resets
     */
    @Property(tries = 50)
    void rateLimitEnforcement_BlocksAfter100Requests(
        @ForAll @IntRange(min = 1, max = 255) int ip1,
        @ForAll @IntRange(min = 1, max = 255) int ip2,
        @ForAll @IntRange(min = 1, max = 255) int ip3,
        @ForAll @IntRange(min = 1, max = 255) int ip4
    ) {
        // Arrange
        String ipAddress = String.format("%d.%d.%d.%d", ip1, ip2, ip3, ip4);
        RateLimitingService service = new RateLimitingService();
        
        // Act - make exactly 100 requests
        for (int i = 0; i < 100; i++) {
            boolean allowed = service.isAllowed(ipAddress);
            assertThat(allowed).as("Request %d should be allowed", i + 1).isTrue();
        }
        
        // Assert - 101st request should be denied
        boolean blocked = service.isAllowed(ipAddress);
        assertThat(blocked).as("Request 101 should be blocked").isFalse();
        
        // Assert - subsequent requests should also be denied
        for (int i = 0; i < 10; i++) {
            boolean stillBlocked = service.isAllowed(ipAddress);
            assertThat(stillBlocked).as("Request %d after limit should be blocked", 102 + i).isFalse();
        }
    }
    
    /**
     * Property: Different IP addresses should have independent rate limits
     */
    @Property(tries = 50)
    void rateLimitIndependence_DifferentIPsHaveIndependentLimits(
        @ForAll @IntRange(min = 1, max = 255) int ip1,
        @ForAll @IntRange(min = 1, max = 255) int ip2
    ) {
        Assume.that(ip1 != ip2);
        
        // Arrange
        String ipAddress1 = String.format("192.168.1.%d", ip1);
        String ipAddress2 = String.format("192.168.1.%d", ip2);
        RateLimitingService service = new RateLimitingService();
        
        // Act - exhaust limit for IP1
        for (int i = 0; i < 100; i++) {
            service.isAllowed(ipAddress1);
        }
        
        // Assert - IP1 should be blocked
        assertThat(service.isAllowed(ipAddress1)).isFalse();
        
        // Assert - IP2 should still be allowed
        assertThat(service.isAllowed(ipAddress2)).isTrue();
    }
    
    /**
     * Property: Request count should accurately track the number of requests
     */
    @Property(tries = 50)
    void requestCount_AccuratelyTracksRequests(
        @ForAll @IntRange(min = 1, max = 100) int requestCount
    ) {
        // Arrange
        String ipAddress = "192.168.1.100";
        RateLimitingService service = new RateLimitingService();
        
        // Act - make N requests
        for (int i = 0; i < requestCount; i++) {
            boolean allowed = service.isAllowed(ipAddress);
            assertThat(allowed).isTrue(); // Should all be allowed since < 100
        }
        
        // Assert - count should match
        int actualCount = service.getRequestCount(ipAddress);
        assertThat(actualCount).isEqualTo(requestCount);
    }
    
    /**
     * Property: Remaining requests should decrease with each request
     */
    @Property(tries = 50)
    void remainingRequests_DecreasesWithEachRequest(
        @ForAll @IntRange(min = 1, max = 50) int requestCount
    ) {
        // Arrange
        String ipAddress = "192.168.1.101";
        RateLimitingService service = new RateLimitingService();
        
        // Act & Assert
        for (int i = 0; i < requestCount; i++) {
            int remainingBefore = service.getRemainingRequests(ipAddress);
            boolean allowed = service.isAllowed(ipAddress);
            assertThat(allowed).isTrue();
            int remainingAfter = service.getRemainingRequests(ipAddress);
            
            assertThat(remainingAfter).isEqualTo(remainingBefore - 1);
        }
    }
    
    /**
     * Property: Reset should clear all request history for an IP
     */
    @Property(tries = 50)
    void reset_ClearsRequestHistory(
        @ForAll @IntRange(min = 1, max = 100) int requestCount
    ) {
        // Arrange
        String ipAddress = "192.168.1.102";
        RateLimitingService service = new RateLimitingService();
        
        // Act - make some requests
        for (int i = 0; i < requestCount; i++) {
            service.isAllowed(ipAddress);
        }
        
        // Reset
        service.reset(ipAddress);
        
        // Assert - count should be zero
        assertThat(service.getRequestCount(ipAddress)).isZero();
        assertThat(service.getRemainingRequests(ipAddress)).isEqualTo(100);
    }
    
    /**
     * Property: Null or blank IP addresses should be allowed (fail open)
     */
    @Property(tries = 20)
    void nullOrBlankIP_AllowedForSafety() {
        // Arrange
        RateLimitingService service = new RateLimitingService();
        
        // Act & Assert - null IP should be allowed
        assertThat(service.isAllowed(null)).isTrue();
        
        // Act & Assert - blank IP should be allowed
        assertThat(service.isAllowed("")).isTrue();
        assertThat(service.isAllowed("   ")).isTrue();
    }
    
    /**
     * Property: Rate limit should be consistent across multiple checks
     */
    @Property(tries = 50)
    void rateLimitConsistency_SameResultForSameState() {
        // Arrange
        String ipAddress = "192.168.1.103";
        RateLimitingService service = new RateLimitingService();
        
        // Act - exhaust the limit
        for (int i = 0; i < 100; i++) {
            service.isAllowed(ipAddress);
        }
        
        // Assert - multiple checks should all return false
        for (int i = 0; i < 10; i++) {
            assertThat(service.isAllowed(ipAddress)).isFalse();
        }
    }
    
    /**
     * Property: Remaining requests should never be negative
     */
    @Property(tries = 50)
    void remainingRequests_NeverNegative(
        @ForAll @IntRange(min = 1, max = 150) int requestCount
    ) {
        // Arrange
        String ipAddress = "192.168.1.104";
        RateLimitingService service = new RateLimitingService();
        
        // Act - make requests (possibly exceeding limit)
        for (int i = 0; i < requestCount; i++) {
            service.isAllowed(ipAddress);
        }
        
        // Assert - remaining should never be negative
        int remaining = service.getRemainingRequests(ipAddress);
        assertThat(remaining).isGreaterThanOrEqualTo(0);
    }
}
