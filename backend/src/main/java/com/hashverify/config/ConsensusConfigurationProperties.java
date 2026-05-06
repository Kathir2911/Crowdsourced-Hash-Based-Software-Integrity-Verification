package com.hashverify.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for consensus calculation and system parameters
 * Implements requirements 9.1, 9.2, 9.3
 */
@Configuration
@ConfigurationProperties(prefix = "app")
@Validated
public class ConsensusConfigurationProperties {
    
    private Consensus consensus = new Consensus();
    private Retention retention = new Retention();
    private Performance performance = new Performance();
    private RateLimiting rateLimiting = new RateLimiting();
    private Security security = new Security();
    private ReplayProtection replayProtection = new ReplayProtection();
    
    public Consensus getConsensus() {
        return consensus;
    }
    
    public void setConsensus(Consensus consensus) {
        this.consensus = consensus;
    }
    
    public Retention getRetention() {
        return retention;
    }
    
    public void setRetention(Retention retention) {
        this.retention = retention;
    }
    
    public Performance getPerformance() {
        return performance;
    }
    
    public void setPerformance(Performance performance) {
        this.performance = performance;
    }
    
    public RateLimiting getRateLimiting() {
        return rateLimiting;
    }
    
    public void setRateLimiting(RateLimiting rateLimiting) {
        this.rateLimiting = rateLimiting;
    }
    
    public Security getSecurity() {
        return security;
    }
    
    public void setSecurity(Security security) {
        this.security = security;
    }
    
    public ReplayProtection getReplayProtection() {
        return replayProtection;
    }
    
    public void setReplayProtection(ReplayProtection replayProtection) {
        this.replayProtection = replayProtection;
    }
    
    /**
     * Consensus calculation configuration
     * Requirement 9.1: Configurable consensus threshold percentage (default 70%)
     * Requirement 9.2: Configurable minimum submission count (default 3)
     */
    public static class Consensus {
        @NotNull
        @Min(0)
        @Max(100)
        private Double thresholdPercentage = 0.70;
        
        @NotNull
        @Min(1)
        private Integer minimumSubmissions = 3;
        
        @NotNull
        @Min(0)
        @Max(100)
        private Double confidenceHighThreshold = 0.80;
        
        @NotNull
        @Min(1)
        private Integer confidenceMinimumSubmissions = 10;
        
        @NotNull
        @Min(1)
        private Integer cacheTtlHours = 24;
        
        public Double getThresholdPercentage() {
            return thresholdPercentage;
        }
        
        public void setThresholdPercentage(Double thresholdPercentage) {
            this.thresholdPercentage = thresholdPercentage;
        }
        
        public Integer getMinimumSubmissions() {
            return minimumSubmissions;
        }
        
        public void setMinimumSubmissions(Integer minimumSubmissions) {
            this.minimumSubmissions = minimumSubmissions;
        }
        
        public Double getConfidenceHighThreshold() {
            return confidenceHighThreshold;
        }
        
        public void setConfidenceHighThreshold(Double confidenceHighThreshold) {
            this.confidenceHighThreshold = confidenceHighThreshold;
        }
        
        public Integer getConfidenceMinimumSubmissions() {
            return confidenceMinimumSubmissions;
        }
        
        public void setConfidenceMinimumSubmissions(Integer confidenceMinimumSubmissions) {
            this.confidenceMinimumSubmissions = confidenceMinimumSubmissions;
        }
        
        public Integer getCacheTtlHours() {
            return cacheTtlHours;
        }
        
        public void setCacheTtlHours(Integer cacheTtlHours) {
            this.cacheTtlHours = cacheTtlHours;
        }
    }
    
    /**
     * Data retention configuration
     * Requirement 9.3: Configurable hash retention period (default 90 days)
     */
    public static class Retention {
        @NotNull
        @Min(1)
        private Integer submissionTtlDays = 90;
        
        public Integer getSubmissionTtlDays() {
            return submissionTtlDays;
        }
        
        public void setSubmissionTtlDays(Integer submissionTtlDays) {
            this.submissionTtlDays = submissionTtlDays;
        }
    }
    
    /**
     * Performance configuration
     */
    public static class Performance {
        @NotNull
        @Min(1)
        private Integer maxConcurrentRequests = 1000;
        
        @NotNull
        @Min(1)
        private Integer apiTimeoutSeconds = 30;
        
        @NotNull
        @Min(1)
        private Integer hashComputationTimeoutSeconds = 60;
        
        public Integer getMaxConcurrentRequests() {
            return maxConcurrentRequests;
        }
        
        public void setMaxConcurrentRequests(Integer maxConcurrentRequests) {
            this.maxConcurrentRequests = maxConcurrentRequests;
        }
        
        public Integer getApiTimeoutSeconds() {
            return apiTimeoutSeconds;
        }
        
        public void setApiTimeoutSeconds(Integer apiTimeoutSeconds) {
            this.apiTimeoutSeconds = apiTimeoutSeconds;
        }
        
        public Integer getHashComputationTimeoutSeconds() {
            return hashComputationTimeoutSeconds;
        }
        
        public void setHashComputationTimeoutSeconds(Integer hashComputationTimeoutSeconds) {
            this.hashComputationTimeoutSeconds = hashComputationTimeoutSeconds;
        }
    }
    
    /**
     * Rate limiting configuration
     */
    public static class RateLimiting {
        @NotNull
        @Min(1)
        private Integer submissionsPerIpPerHour = 100;
        
        @NotNull
        @Min(1)
        private Integer verificationsPerIpPerMinute = 60;
        
        @NotNull
        @Min(1)
        private Integer adminApiRequestsPerMinute = 1000;
        
        public Integer getSubmissionsPerIpPerHour() {
            return submissionsPerIpPerHour;
        }
        
        public void setSubmissionsPerIpPerHour(Integer submissionsPerIpPerHour) {
            this.submissionsPerIpPerHour = submissionsPerIpPerHour;
        }
        
        public Integer getVerificationsPerIpPerMinute() {
            return verificationsPerIpPerMinute;
        }
        
        public void setVerificationsPerIpPerMinute(Integer verificationsPerIpPerMinute) {
            this.verificationsPerIpPerMinute = verificationsPerIpPerMinute;
        }
        
        public Integer getAdminApiRequestsPerMinute() {
            return adminApiRequestsPerMinute;
        }
        
        public void setAdminApiRequestsPerMinute(Integer adminApiRequestsPerMinute) {
            this.adminApiRequestsPerMinute = adminApiRequestsPerMinute;
        }
    }
    
    /**
     * Security configuration
     */
    public static class Security {
        @NotNull
        private Boolean requireHttps = true;
        
        @NotNull
        private String[] corsAllowedOrigins = {"chrome-extension://*", "moz-extension://*"};
        
        @NotNull
        @Min(16)
        private Integer adminApiKeyLength = 32;
        
        public Boolean getRequireHttps() {
            return requireHttps;
        }
        
        public void setRequireHttps(Boolean requireHttps) {
            this.requireHttps = requireHttps;
        }
        
        public String[] getCorsAllowedOrigins() {
            return corsAllowedOrigins;
        }
        
        public void setCorsAllowedOrigins(String[] corsAllowedOrigins) {
            this.corsAllowedOrigins = corsAllowedOrigins;
        }
        
        public Integer getAdminApiKeyLength() {
            return adminApiKeyLength;
        }
        
        public void setAdminApiKeyLength(Integer adminApiKeyLength) {
            this.adminApiKeyLength = adminApiKeyLength;
        }
    }
    
    /**
     * Replay protection configuration
     */
    public static class ReplayProtection {
        @NotNull
        @Min(1)
        private Integer timeWindowHours = 24;
        
        @NotNull
        private String hashAlgorithm = "SHA-256";
        
        public Integer getTimeWindowHours() {
            return timeWindowHours;
        }
        
        public void setTimeWindowHours(Integer timeWindowHours) {
            this.timeWindowHours = timeWindowHours;
        }
        
        public String getHashAlgorithm() {
            return hashAlgorithm;
        }
        
        public void setHashAlgorithm(String hashAlgorithm) {
            this.hashAlgorithm = hashAlgorithm;
        }
    }
}
