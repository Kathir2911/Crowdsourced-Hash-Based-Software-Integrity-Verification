package com.hashverify.config;

import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for configuration management
 * **Validates: Requirements 9.1, 9.2, 9.3**
 */
@SpringBootTest
@TestPropertySource(properties = {
    "app.consensus.threshold-percentage=0.70",
    "app.consensus.minimum-submissions=3",
    "app.retention.submission-ttl-days=90",
    "spring.flyway.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ConsensusConfigurationPropertiesTest {
    
    @Autowired
    private ConsensusConfigurationProperties configProperties;
    
    /**
     * Unit test: Verify default configuration values are loaded correctly
     */
    @Test
    void shouldLoadDefaultConfiguration() {
        assertThat(configProperties).isNotNull();
        assertThat(configProperties.getConsensus()).isNotNull();
        assertThat(configProperties.getRetention()).isNotNull();
        
        // Verify default values (Requirement 9.1, 9.2, 9.3)
        assertThat(configProperties.getConsensus().getThresholdPercentage()).isEqualTo(0.70);
        assertThat(configProperties.getConsensus().getMinimumSubmissions()).isEqualTo(3);
        assertThat(configProperties.getRetention().getSubmissionTtlDays()).isEqualTo(90);
    }
    
    /**
     * Property 20: Configuration Management
     * **Validates: Requirements 9.1, 9.2, 9.3**
     * 
     * For any configurable system parameter (consensus threshold, minimum submissions, 
     * retention period), the Backend_Server should accept and apply configuration changes correctly.
     */
    @Property(tries = 50)
    void property20_configurationManagement_acceptsValidParameters(
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double thresholdPercentage,
            @ForAll @IntRange(min = 1, max = 100) int minimumSubmissions,
            @ForAll @IntRange(min = 1, max = 365) int retentionDays) {
        
        // Create configuration with provided parameters
        ConsensusConfigurationProperties.Consensus consensus = new ConsensusConfigurationProperties.Consensus();
        consensus.setThresholdPercentage(thresholdPercentage);
        consensus.setMinimumSubmissions(minimumSubmissions);
        
        ConsensusConfigurationProperties.Retention retention = new ConsensusConfigurationProperties.Retention();
        retention.setSubmissionTtlDays(retentionDays);
        
        // Verify configuration accepts and stores values correctly
        assertThat(consensus.getThresholdPercentage())
            .isEqualTo(thresholdPercentage)
            .isBetween(0.0, 1.0);
        
        assertThat(consensus.getMinimumSubmissions())
            .isEqualTo(minimumSubmissions)
            .isGreaterThanOrEqualTo(1);
        
        assertThat(retention.getSubmissionTtlDays())
            .isEqualTo(retentionDays)
            .isGreaterThanOrEqualTo(1);
    }
    
    /**
     * Property 20.1: Consensus threshold percentage validation
     * **Validates: Requirement 9.1**
     * 
     * For any consensus threshold percentage between 0 and 1, the configuration 
     * should accept and store the value correctly.
     */
    @Property(tries = 50)
    void property20_1_consensusThresholdValidation(
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double threshold) {
        
        ConsensusConfigurationProperties.Consensus consensus = new ConsensusConfigurationProperties.Consensus();
        consensus.setThresholdPercentage(threshold);
        
        assertThat(consensus.getThresholdPercentage())
            .isEqualTo(threshold)
            .isBetween(0.0, 1.0);
    }
    
    /**
     * Property 20.2: Minimum submission count validation
     * **Validates: Requirement 9.2**
     * 
     * For any minimum submission count >= 1, the configuration should accept 
     * and store the value correctly.
     */
    @Property(tries = 50)
    void property20_2_minimumSubmissionCountValidation(
            @ForAll @IntRange(min = 1, max = 100) int minSubmissions) {
        
        ConsensusConfigurationProperties.Consensus consensus = new ConsensusConfigurationProperties.Consensus();
        consensus.setMinimumSubmissions(minSubmissions);
        
        assertThat(consensus.getMinimumSubmissions())
            .isEqualTo(minSubmissions)
            .isGreaterThanOrEqualTo(1);
    }
    
    /**
     * Property 20.3: Retention period validation
     * **Validates: Requirement 9.3**
     * 
     * For any retention period >= 1 day, the configuration should accept 
     * and store the value correctly.
     */
    @Property(tries = 50)
    void property20_3_retentionPeriodValidation(
            @ForAll @IntRange(min = 1, max = 365) int retentionDays) {
        
        ConsensusConfigurationProperties.Retention retention = new ConsensusConfigurationProperties.Retention();
        retention.setSubmissionTtlDays(retentionDays);
        
        assertThat(retention.getSubmissionTtlDays())
            .isEqualTo(retentionDays)
            .isGreaterThanOrEqualTo(1);
    }
    
    /**
     * Property 20.4: Configuration immutability after retrieval
     * 
     * For any configuration parameter, retrieving the value multiple times 
     * should return the same value (configuration is stable).
     */
    @Property(tries = 50)
    void property20_4_configurationStability(
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double threshold) {
        
        ConsensusConfigurationProperties.Consensus consensus = new ConsensusConfigurationProperties.Consensus();
        consensus.setThresholdPercentage(threshold);
        
        // Retrieve value multiple times
        double firstRetrieval = consensus.getThresholdPercentage();
        double secondRetrieval = consensus.getThresholdPercentage();
        double thirdRetrieval = consensus.getThresholdPercentage();
        
        // All retrievals should return the same value
        assertThat(firstRetrieval).isEqualTo(threshold);
        assertThat(secondRetrieval).isEqualTo(threshold);
        assertThat(thirdRetrieval).isEqualTo(threshold);
        assertThat(firstRetrieval).isEqualTo(secondRetrieval).isEqualTo(thirdRetrieval);
    }
    
    /**
     * Property 20.5: Performance configuration validation
     * 
     * For any performance parameter, the configuration should accept valid values.
     */
    @Property(tries = 50)
    void property20_5_performanceConfigurationValidation(
            @ForAll @IntRange(min = 1, max = 10000) int maxRequests,
            @ForAll @IntRange(min = 1, max = 300) int timeoutSeconds) {
        
        ConsensusConfigurationProperties.Performance performance = new ConsensusConfigurationProperties.Performance();
        performance.setMaxConcurrentRequests(maxRequests);
        performance.setApiTimeoutSeconds(timeoutSeconds);
        
        assertThat(performance.getMaxConcurrentRequests())
            .isEqualTo(maxRequests)
            .isGreaterThanOrEqualTo(1);
        
        assertThat(performance.getApiTimeoutSeconds())
            .isEqualTo(timeoutSeconds)
            .isGreaterThanOrEqualTo(1);
    }
    
    /**
     * Property 20.6: Rate limiting configuration validation
     * 
     * For any rate limiting parameter, the configuration should accept valid values.
     */
    @Property(tries = 50)
    void property20_6_rateLimitingConfigurationValidation(
            @ForAll @IntRange(min = 1, max = 1000) int submissionsPerHour,
            @ForAll @IntRange(min = 1, max = 1000) int verificationsPerMinute) {
        
        ConsensusConfigurationProperties.RateLimiting rateLimiting = new ConsensusConfigurationProperties.RateLimiting();
        rateLimiting.setSubmissionsPerIpPerHour(submissionsPerHour);
        rateLimiting.setVerificationsPerIpPerMinute(verificationsPerMinute);
        
        assertThat(rateLimiting.getSubmissionsPerIpPerHour())
            .isEqualTo(submissionsPerHour)
            .isGreaterThanOrEqualTo(1);
        
        assertThat(rateLimiting.getVerificationsPerIpPerMinute())
            .isEqualTo(verificationsPerMinute)
            .isGreaterThanOrEqualTo(1);
    }
}
