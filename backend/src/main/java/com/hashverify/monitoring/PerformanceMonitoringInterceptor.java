package com.hashverify.monitoring;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor for monitoring API endpoint performance
 * Automatically tracks response times for all requests
 * Implements requirement 8.2: Backend_Server shall respond to verification requests within 200 milliseconds
 */
@Component
public class PerformanceMonitoringInterceptor implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitoringInterceptor.class);
    private static final String START_TIME_ATTRIBUTE = "startTime";
    
    private final PerformanceMonitor performanceMonitor;
    
    public PerformanceMonitoringInterceptor(PerformanceMonitor performanceMonitor) {
        this.performanceMonitor = performanceMonitor;
    }
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Record start time
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                               Object handler, Exception ex) {
        // Calculate response time
        Long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);
        if (startTime != null) {
            long responseTime = System.currentTimeMillis() - startTime;
            String endpoint = request.getMethod() + " " + request.getRequestURI();
            
            // Record response time in performance monitor
            performanceMonitor.recordResponseTime(endpoint, responseTime);
            
            // Log individual request performance
            logger.debug("Request completed: endpoint={}, responseTime={}ms, status={}", 
                        endpoint, responseTime, response.getStatus());
        }
    }
}
