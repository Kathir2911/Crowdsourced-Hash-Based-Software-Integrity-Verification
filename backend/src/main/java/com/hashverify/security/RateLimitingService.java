package com.hashverify.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for rate limiting API requests
 * Implements requirement 3.7: Rate limiting for submissions (100 per IP per hour)
 */
@Service
public class RateLimitingService {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitingService.class);
    
    private static final int MAX_REQUESTS_PER_HOUR = 100;
    private static final int WINDOW_SIZE_MINUTES = 60;
    
    // Map of IP address to request tracking
    private final Map<String, RequestTracker> requestTrackers = new ConcurrentHashMap<>();
    
    /**
     * Check if a request from the given IP address should be allowed
     * 
     * @param ipAddress The client IP address
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean isAllowed(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            logger.warn("Rate limit check called with null or blank IP address");
            return true; // Allow if IP cannot be determined
        }
        
        RequestTracker tracker = requestTrackers.computeIfAbsent(ipAddress, k -> new RequestTracker());
        
        synchronized (tracker) {
            // Clean up old requests outside the time window
            tracker.cleanupOldRequests();
            
            // Check if limit exceeded
            if (tracker.getRequestCount() >= MAX_REQUESTS_PER_HOUR) {
                logger.warn("Rate limit exceeded for IP: {}", ipAddress);
                return false;
            }
            
            // Record this request
            tracker.addRequest();
            logger.debug("Request allowed for IP: {} (count: {})", ipAddress, tracker.getRequestCount());
            return true;
        }
    }
    
    /**
     * Get the current request count for an IP address
     * 
     * @param ipAddress The client IP address
     * @return Number of requests in the current window
     */
    public int getRequestCount(String ipAddress) {
        RequestTracker tracker = requestTrackers.get(ipAddress);
        if (tracker == null) {
            return 0;
        }
        
        synchronized (tracker) {
            tracker.cleanupOldRequests();
            return tracker.getRequestCount();
        }
    }
    
    /**
     * Get remaining requests allowed for an IP address
     * 
     * @param ipAddress The client IP address
     * @return Number of requests remaining in the current window
     */
    public int getRemainingRequests(String ipAddress) {
        int currentCount = getRequestCount(ipAddress);
        return Math.max(0, MAX_REQUESTS_PER_HOUR - currentCount);
    }
    
    /**
     * Reset rate limit for an IP address (for testing or administrative purposes)
     * 
     * @param ipAddress The client IP address
     */
    public void reset(String ipAddress) {
        requestTrackers.remove(ipAddress);
        logger.info("Rate limit reset for IP: {}", ipAddress);
    }
    
    /**
     * Clean up expired trackers to prevent memory leaks
     */
    public void cleanupExpiredTrackers() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(WINDOW_SIZE_MINUTES * 2);
        
        requestTrackers.entrySet().removeIf(entry -> {
            RequestTracker tracker = entry.getValue();
            synchronized (tracker) {
                return tracker.isExpired(cutoff);
            }
        });
        
        logger.debug("Cleaned up expired rate limit trackers. Active trackers: {}", requestTrackers.size());
    }
    
    /**
     * Inner class to track requests for a single IP address
     */
    private static class RequestTracker {
        private final Map<LocalDateTime, Integer> requestsByMinute = new ConcurrentHashMap<>();
        
        public void addRequest() {
            LocalDateTime currentMinute = LocalDateTime.now().withSecond(0).withNano(0);
            requestsByMinute.merge(currentMinute, 1, Integer::sum);
        }
        
        public int getRequestCount() {
            return requestsByMinute.values().stream().mapToInt(Integer::intValue).sum();
        }
        
        public void cleanupOldRequests() {
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(WINDOW_SIZE_MINUTES);
            requestsByMinute.entrySet().removeIf(entry -> entry.getKey().isBefore(cutoff));
        }
        
        public boolean isExpired(LocalDateTime cutoff) {
            if (requestsByMinute.isEmpty()) {
                return true;
            }
            
            LocalDateTime latestRequest = requestsByMinute.keySet().stream()
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.MIN);
            
            return latestRequest.isBefore(cutoff);
        }
    }
}
