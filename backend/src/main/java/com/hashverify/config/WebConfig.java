package com.hashverify.config;

import com.hashverify.monitoring.PerformanceMonitoringInterceptor;
import com.hashverify.security.RateLimitingInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web configuration for CORS and interceptors
 * Implements requirements 3.7, 7.4, 8.2
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    private final RateLimitingInterceptor rateLimitingInterceptor;
    private final PerformanceMonitoringInterceptor performanceMonitoringInterceptor;
    
    public WebConfig(RateLimitingInterceptor rateLimitingInterceptor,
                    PerformanceMonitoringInterceptor performanceMonitoringInterceptor) {
        this.rateLimitingInterceptor = rateLimitingInterceptor;
        this.performanceMonitoringInterceptor = performanceMonitoringInterceptor;
    }
    
    /**
     * Configure CORS to allow browser extension requests
     * Implements requirement 7.4: HTTPS enforcement and CORS configuration
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                    "chrome-extension://*",
                    "moz-extension://*",
                    "http://localhost:*",
                    "https://localhost:*",
                    "https://*.example.com" // Replace with actual domain in production
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset")
                .allowCredentials(false)
                .maxAge(3600);
    }
    
    /**
     * Register interceptors for rate limiting and performance monitoring
     * Implements requirements 3.7, 8.2
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Performance monitoring runs first to track total response time
        registry.addInterceptor(performanceMonitoringInterceptor)
                .addPathPatterns("/api/**")
                .order(0);
        
        // Rate limiting runs second
        registry.addInterceptor(rateLimitingInterceptor)
                .addPathPatterns("/api/v1/submissions", "/api/v1/verify")
                .order(1);
    }
}
