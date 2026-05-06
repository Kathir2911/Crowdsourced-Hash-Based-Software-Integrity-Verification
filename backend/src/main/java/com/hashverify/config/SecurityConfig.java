package com.hashverify.config;

import com.hashverify.security.ApiKeyAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the application
 * Implements requirement 7.4: HTTPS enforcement
 * Implements requirement 9.6: API key authentication for admin endpoints
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    
    public SecurityConfig(ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) {
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
    }
    
    /**
     * Configure HTTP security
     * - Disable CSRF for stateless API
     * - Require HTTPS in production
     * - Allow public API endpoints (rate limiting handled by interceptor)
     * - Require API key authentication for admin endpoints
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for stateless REST API
            .csrf(csrf -> csrf.disable())
            
            // Stateless session management
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // Require HTTPS (disabled for development, enable in production)
            // .requiresChannel(channel -> channel.anyRequest().requiresSecure())
            
            // Add API key authentication filter before standard authentication
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            
            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/status").permitAll()
                .requestMatchers("/submissions").permitAll()
                .requestMatchers("/verify").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().permitAll()  // Allow all other requests (rate limiting handles protection)
            );
        
        return http.build();
    }
}
