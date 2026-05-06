package com.hashverify.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor for rate limiting API requests
 * Implements requirement 3.7: Rate limiting enforcement
 */
@Component
public class RateLimitingInterceptor implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitingInterceptor.class);
    
    private final RateLimitingService rateLimitingService;
    
    public RateLimitingInterceptor(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Skip rate limiting for health check endpoint
        if (request.getRequestURI().endsWith("/status")) {
            return true;
        }
        
        // Get client IP address
        String ipAddress = getClientIpAddress(request);
        
        // Check rate limit
        if (!rateLimitingService.isAllowed(ipAddress)) {
            logger.warn("Rate limit exceeded for IP: {} on endpoint: {}", ipAddress, request.getRequestURI());
            
            // Set response headers
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            
            // Add rate limit headers
            int remaining = rateLimitingService.getRemainingRequests(ipAddress);
            response.setHeader("X-RateLimit-Limit", "100");
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            response.setHeader("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() + 3600000)); // 1 hour from now
            
            // Requirement 10.3: Return HTTP 429 with retry-after header when rate limits exceeded
            response.setHeader("Retry-After", "3600"); // Retry after 3600 seconds (1 hour)
            
            // Write error response
            String errorJson = String.format(
                "{\"status\":\"error\",\"message\":\"Rate limit exceeded. Maximum 100 requests per hour allowed. Retry after 1 hour.\",\"code\":429}");
            response.getWriter().write(errorJson);
            
            return false;
        }
        
        // Add rate limit headers to successful responses
        int remaining = rateLimitingService.getRemainingRequests(ipAddress);
        response.setHeader("X-RateLimit-Limit", "100");
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        
        return true;
    }
    
    /**
     * Extract client IP address from request, considering proxy headers
     * 
     * @param request The HTTP request
     * @return The client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        // Check for X-Forwarded-For header (proxy/load balancer)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }
        
        // Check for X-Real-IP header
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        
        // Fall back to remote address
        return request.getRemoteAddr();
    }
}
