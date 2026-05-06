package com.hashverify.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * API Key authentication filter for administrative endpoints
 * Implements requirement 9.6: Authenticate administrators using API keys
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";
    
    @Value("${app.security.admin-api-key:}")
    private String adminApiKey;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        String requestPath = request.getRequestURI();
        
        // Only apply to admin endpoints
        if (!requestPath.startsWith("/api/v1/admin")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String apiKey = request.getHeader(API_KEY_HEADER);
        
        // Check if API key is provided and valid
        if (apiKey != null && !apiKey.isEmpty() && isValidApiKey(apiKey)) {
            // Create authentication token with ADMIN role
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(
                    "admin",
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
                );
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            logger.debug("API key authentication successful for admin endpoint: {}", requestPath);
            
            filterChain.doFilter(request, response);
        } else {
            // Unauthorized - missing or invalid API key
            logger.warn("Unauthorized admin access attempt to: {} from IP: {}", 
                       requestPath, request.getRemoteAddr());
            
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Valid API key required for admin endpoints\"}");
        }
    }
    
    /**
     * Validate the provided API key
     * 
     * @param apiKey The API key to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidApiKey(String apiKey) {
        // If no admin API key is configured, deny all access
        if (adminApiKey == null || adminApiKey.isEmpty()) {
            logger.warn("Admin API key not configured - denying access");
            return false;
        }
        
        // Constant-time comparison to prevent timing attacks
        return constantTimeEquals(apiKey, adminApiKey);
    }
    
    /**
     * Constant-time string comparison to prevent timing attacks
     * 
     * @param a First string
     * @param b Second string
     * @return true if strings are equal, false otherwise
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        
        if (a.length() != b.length()) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        
        return result == 0;
    }
}
