package com.hashverify.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance monitoring service for tracking API response times and throughput
 * Implements requirement 8.2: Backend_Server shall respond to verification requests within 200 milliseconds
 */
@Component
public class PerformanceMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitor.class);
    
    // Response time threshold in milliseconds (requirement 8.2)
    private static final long RESPONSE_TIME_THRESHOLD_MS = 200;
    
    // Track response times by endpoint
    private final ConcurrentHashMap<String, ResponseTimeStats> endpointStats = new ConcurrentHashMap<>();
    
    // Track total requests
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong slowRequests = new AtomicLong(0);
    
    /**
     * Record response time for an endpoint
     * 
     * @param endpoint The endpoint path
     * @param responseTimeMs The response time in milliseconds
     */
    public void recordResponseTime(String endpoint, long responseTimeMs) {
        totalRequests.incrementAndGet();
        
        // Track slow requests (exceeding 200ms threshold)
        if (responseTimeMs > RESPONSE_TIME_THRESHOLD_MS) {
            slowRequests.incrementAndGet();
            logger.warn("Slow response detected: endpoint={}, responseTime={}ms, threshold={}ms",
                       endpoint, responseTimeMs, RESPONSE_TIME_THRESHOLD_MS);
        }
        
        // Update endpoint-specific stats
        endpointStats.computeIfAbsent(endpoint, k -> new ResponseTimeStats())
                    .recordResponseTime(responseTimeMs);
        
        // Log performance metrics periodically
        if (totalRequests.get() % 100 == 0) {
            logPerformanceMetrics();
        }
    }
    
    /**
     * Get response time statistics for an endpoint
     * 
     * @param endpoint The endpoint path
     * @return ResponseTimeStats or null if no data available
     */
    public ResponseTimeStats getEndpointStats(String endpoint) {
        return endpointStats.get(endpoint);
    }
    
    /**
     * Get total number of requests processed
     * 
     * @return Total request count
     */
    public long getTotalRequests() {
        return totalRequests.get();
    }
    
    /**
     * Get number of slow requests (exceeding threshold)
     * 
     * @return Slow request count
     */
    public long getSlowRequests() {
        return slowRequests.get();
    }
    
    /**
     * Get percentage of requests meeting performance target
     * 
     * @return Percentage of requests under 200ms threshold
     */
    public double getPerformanceTargetPercentage() {
        long total = totalRequests.get();
        if (total == 0) {
            return 100.0;
        }
        long fast = total - slowRequests.get();
        return (fast * 100.0) / total;
    }
    
    /**
     * Log performance metrics summary
     */
    private void logPerformanceMetrics() {
        long total = totalRequests.get();
        long slow = slowRequests.get();
        double targetPercentage = getPerformanceTargetPercentage();
        
        logger.info("Performance Metrics: totalRequests={}, slowRequests={}, targetMet={:.2f}%",
                   total, slow, targetPercentage);
        
        // Log per-endpoint statistics
        endpointStats.forEach((endpoint, stats) -> {
            logger.info("Endpoint Stats: endpoint={}, avgResponseTime={}ms, minResponseTime={}ms, maxResponseTime={}ms, requestCount={}",
                       endpoint, stats.getAverageResponseTime(), stats.getMinResponseTime(), 
                       stats.getMaxResponseTime(), stats.getRequestCount());
        });
    }
    
    /**
     * Reset all performance statistics
     */
    public void reset() {
        totalRequests.set(0);
        slowRequests.set(0);
        endpointStats.clear();
        logger.info("Performance statistics reset");
    }
    
    /**
     * Statistics for response times
     */
    public static class ResponseTimeStats {
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        private final AtomicLong requestCount = new AtomicLong(0);
        private volatile long minResponseTime = Long.MAX_VALUE;
        private volatile long maxResponseTime = 0;
        
        public void recordResponseTime(long responseTimeMs) {
            totalResponseTime.addAndGet(responseTimeMs);
            requestCount.incrementAndGet();
            
            // Update min/max
            synchronized (this) {
                if (responseTimeMs < minResponseTime) {
                    minResponseTime = responseTimeMs;
                }
                if (responseTimeMs > maxResponseTime) {
                    maxResponseTime = responseTimeMs;
                }
            }
        }
        
        public long getAverageResponseTime() {
            long count = requestCount.get();
            if (count == 0) {
                return 0;
            }
            return totalResponseTime.get() / count;
        }
        
        public long getMinResponseTime() {
            return minResponseTime == Long.MAX_VALUE ? 0 : minResponseTime;
        }
        
        public long getMaxResponseTime() {
            return maxResponseTime;
        }
        
        public long getRequestCount() {
            return requestCount.get();
        }
    }
}
