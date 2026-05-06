package com.hashverify.service;

import com.hashverify.model.SoftwareIdentity;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for ReplayProtectionService
 * Tests replay protection hash generation, duplicate detection, and time-based filtering
 * 
 * **Validates: Requirements 4.4, 4.7**
 */
class ReplayProtectionServiceProperties {

    /**
     * Property 11: Replay Protection
     * 
     * For any client submitting duplicate hash submissions for the same Software_Identity 
     * within the same 24-hour time window, the replay protection hash should be identical,
     * enabling duplicate detection.
     * 
     * **Validates: Requirements 4.4**
     */
    @Property(tries = 50)
    void replayProtectionPreventsDuplicates(
            @ForAll("validIpAddresses") String ipAddress,
            @ForAll("validUserAgents") String userAgent,
            @ForAll("timestampsWithinSameDay") LocalDateTime timestamp1,
            @ForAll("timestampsWithinSameDay") LocalDateTime timestamp2) {
        
        // Given: A replay protection service
        ReplayProtectionService service = new ReplayProtectionService(null);
        
        // When: Generate replay protection hashes for two submissions on the same day
        String replayHash1 = service.generateReplayProtectionHash(ipAddress, userAgent, timestamp1);
        String replayHash2 = service.generateReplayProtectionHash(ipAddress, userAgent, timestamp2);
        
        // Then: If timestamps are on the same day, replay hashes should be identical
        // This enables duplicate detection within the 24-hour window
        if (timestamp1.toLocalDate().equals(timestamp2.toLocalDate())) {
            assertThat(replayHash1).isEqualTo(replayHash2);
        }
    }

    /**
     * Property 11b: Replay Protection Hash Consistency
     * 
     * For any client with the same IP, UserAgent, and timestamp within the same 24-hour window,
     * the replay protection hash should be identical (idempotent).
     * 
     * **Validates: Requirements 4.4**
     */
    @Property(tries = 50)
    void replayProtectionHashIsIdempotent(
            @ForAll("validIpAddresses") String ipAddress,
            @ForAll("validUserAgents") String userAgent,
            @ForAll("timestampsWithinSameDay") LocalDateTime timestamp) {
        
        // Given: A replay protection service
        ReplayProtectionService service = new ReplayProtectionService(null);
        
        // When: Generate replay protection hash twice with same inputs
        String hash1 = service.generateReplayProtectionHash(ipAddress, userAgent, timestamp);
        String hash2 = service.generateReplayProtectionHash(ipAddress, userAgent, timestamp);
        
        // Then: Hashes should be identical
        assertThat(hash1).isEqualTo(hash2);
        
        // And: Hash should be valid SHA-256 format (64 hex characters)
        assertThat(hash1).hasSize(64);
        assertThat(hash1).matches("^[a-f0-9]{64}$");
    }

    /**
     * Property 11c: Replay Protection 24-Hour Window Grouping
     * 
     * For any two timestamps within the same calendar day, the replay protection hash 
     * should be identical (same 24-hour window).
     * 
     * **Validates: Requirements 4.4**
     */
    @Property(tries = 50)
    void replayProtectionGroupsSameDaySubmissions(
            @ForAll("validIpAddresses") String ipAddress,
            @ForAll("validUserAgents") String userAgent,
            @ForAll("timestampPairsWithinSameDay") TimestampPair timestamps) {
        
        // Given: A replay protection service
        ReplayProtectionService service = new ReplayProtectionService(null);
        
        // When: Generate replay protection hashes for two different times on the same day
        String hash1 = service.generateReplayProtectionHash(ipAddress, userAgent, timestamps.timestamp1);
        String hash2 = service.generateReplayProtectionHash(ipAddress, userAgent, timestamps.timestamp2);
        
        // Then: Hashes should be identical (same 24-hour window)
        assertThat(hash1).isEqualTo(hash2);
    }

    /**
     * Property 11d: Replay Protection Different Day Separation
     * 
     * For any two timestamps on different calendar days, the replay protection hash 
     * should be different (different 24-hour windows).
     * 
     * **Validates: Requirements 4.4**
     */
    @Property(tries = 50)
    void replayProtectionSeparatesDifferentDays(
            @ForAll("validIpAddresses") String ipAddress,
            @ForAll("validUserAgents") String userAgent,
            @ForAll("timestampPairsOnDifferentDays") TimestampPair timestamps) {
        
        // Given: A replay protection service
        ReplayProtectionService service = new ReplayProtectionService(null);
        
        // When: Generate replay protection hashes for different days
        String hash1 = service.generateReplayProtectionHash(ipAddress, userAgent, timestamps.timestamp1);
        String hash2 = service.generateReplayProtectionHash(ipAddress, userAgent, timestamps.timestamp2);
        
        // Then: Hashes should be different (different 24-hour windows)
        assertThat(hash1).isNotEqualTo(hash2);
    }

    /**
     * Property 11e: Replay Protection Different IP Separation
     * 
     * For any two different IP addresses with the same UserAgent and timestamp,
     * the replay protection hash should be different.
     * 
     * **Validates: Requirements 4.4**
     */
    @Property(tries = 50)
    void replayProtectionSeparatesDifferentIPs(
            @ForAll("validIpAddresses") String ip1,
            @ForAll("validIpAddresses") String ip2,
            @ForAll("validUserAgents") String userAgent,
            @ForAll("timestampsWithinSameDay") LocalDateTime timestamp) {
        
        // Assume: IPs are different
        Assume.that(!ip1.equals(ip2));
        
        // Given: A replay protection service
        ReplayProtectionService service = new ReplayProtectionService(null);
        
        // When: Generate replay protection hashes for different IPs
        String hash1 = service.generateReplayProtectionHash(ip1, userAgent, timestamp);
        String hash2 = service.generateReplayProtectionHash(ip2, userAgent, timestamp);
        
        // Then: Hashes should be different
        assertThat(hash1).isNotEqualTo(hash2);
    }

    /**
     * Property 11f: Replay Protection Different UserAgent Separation
     * 
     * For any two different UserAgents with the same IP and timestamp,
     * the replay protection hash should be different.
     * 
     * **Validates: Requirements 4.4**
     */
    @Property(tries = 50)
    void replayProtectionSeparatesDifferentUserAgents(
            @ForAll("validIpAddresses") String ipAddress,
            @ForAll("validUserAgents") String userAgent1,
            @ForAll("validUserAgents") String userAgent2,
            @ForAll("timestampsWithinSameDay") LocalDateTime timestamp) {
        
        // Assume: UserAgents are different
        Assume.that(!userAgent1.equals(userAgent2));
        
        // Given: A replay protection service
        ReplayProtectionService service = new ReplayProtectionService(null);
        
        // When: Generate replay protection hashes for different UserAgents
        String hash1 = service.generateReplayProtectionHash(ipAddress, userAgent1, timestamp);
        String hash2 = service.generateReplayProtectionHash(ipAddress, userAgent2, timestamp);
        
        // Then: Hashes should be different
        assertThat(hash1).isNotEqualTo(hash2);
    }

    /**
     * Property 11g: Replay Protection Null Input Handling
     * 
     * For any timestamp, the replay protection service should handle null IP and UserAgent
     * gracefully without throwing exceptions.
     * 
     * **Validates: Requirements 4.4**
     */
    @Property(tries = 50)
    void replayProtectionHandlesNullInputs(
            @ForAll("timestampsWithinSameDay") LocalDateTime timestamp) {
        
        // Given: A replay protection service
        ReplayProtectionService service = new ReplayProtectionService(null);
        
        // When: Generate replay protection hash with null inputs
        String hash = service.generateReplayProtectionHash(null, null, timestamp);
        
        // Then: Should produce valid hash without exception
        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("^[a-f0-9]{64}$");
    }

    /**
     * Property 13: Time-Based Submission Filtering
     * 
     * For any consensus calculation, the Consensus_Service should exclude submissions 
     * older than 90 days and transition consensus to EXPIRED state when no recent 
     * submissions exist.
     * 
     * **Validates: Requirements 4.7**
     */
    @Property(tries = 50)
    void timeBasedFilteringExcludesExpiredSubmissions(
            @ForAll @IntRange(min = 1, max = 365) int retentionDays,
            @ForAll @IntRange(min = 0, max = 500) int daysOld) {
        
        // Given: A replay protection service
        ReplayProtectionService service = new ReplayProtectionService(null);
        
        // And: A submission timestamp
        LocalDateTime submissionTime = LocalDateTime.now().minusDays(daysOld);
        
        // When: Check if submission should be included in consensus
        boolean shouldInclude = service.shouldIncludeInConsensus(submissionTime, retentionDays);
        
        // Then: Submission should be included only if within retention period
        if (daysOld < retentionDays) {
            assertThat(shouldInclude).isTrue();
        } else {
            assertThat(shouldInclude).isFalse();
        }
    }

    /**
     * Property 13b: Time-Based Filtering Boundary Test
     * 
     * For any submission exactly at the retention boundary (90 days old),
     * the filtering should correctly determine inclusion.
     * 
     * **Validates: Requirements 4.7**
     */
    @Property(tries = 50)
    void timeBasedFilteringBoundaryTest(
            @ForAll @IntRange(min = 1, max = 365) int retentionDays) {
        
        // Given: A replay protection service
        ReplayProtectionService service = new ReplayProtectionService(null);
        
        // And: Submissions at various boundaries
        LocalDateTime justWithin = LocalDateTime.now().minusDays(retentionDays).plusMinutes(1);
        LocalDateTime justOutside = LocalDateTime.now().minusDays(retentionDays).minusMinutes(1);
        
        // When: Check if submissions should be included
        boolean shouldIncludeWithin = service.shouldIncludeInConsensus(justWithin, retentionDays);
        boolean shouldIncludeOutside = service.shouldIncludeInConsensus(justOutside, retentionDays);
        
        // Then: Just within should be included, just outside should not
        assertThat(shouldIncludeWithin).isTrue();
        assertThat(shouldIncludeOutside).isFalse();
    }

    /**
     * Property 13c: Time-Based Filtering Consistency
     * 
     * For any submission, isWithinRetentionPeriod and shouldIncludeInConsensus
     * should return the same result (they are equivalent operations).
     * 
     * **Validates: Requirements 4.7**
     */
    @Property(tries = 50)
    void timeBasedFilteringConsistency(
            @ForAll @IntRange(min = 1, max = 365) int retentionDays,
            @ForAll @IntRange(min = 0, max = 500) int daysOld) {
        
        // Given: A replay protection service
        ReplayProtectionService service = new ReplayProtectionService(null);
        
        // And: A submission timestamp
        LocalDateTime submissionTime = LocalDateTime.now().minusDays(daysOld);
        
        // When: Check both methods
        boolean isWithinRetention = service.isWithinRetentionPeriod(submissionTime, retentionDays);
        boolean shouldInclude = service.shouldIncludeInConsensus(submissionTime, retentionDays);
        
        // Then: Both methods should return the same result
        assertThat(isWithinRetention).isEqualTo(shouldInclude);
    }

    /**
     * Property 13d: Time-Based Filtering Default Retention Period
     * 
     * For any submission with the default 90-day retention period,
     * submissions older than 90 days should be excluded.
     * 
     * **Validates: Requirements 4.7**
     */
    @Property(tries = 50)
    void timeBasedFilteringDefaultRetentionPeriod(
            @ForAll @IntRange(min = 0, max = 200) int daysOld) {
        
        // Given: A replay protection service with default 90-day retention
        ReplayProtectionService service = new ReplayProtectionService(null);
        int defaultRetentionDays = 90;
        
        // And: A submission timestamp
        LocalDateTime submissionTime = LocalDateTime.now().minusDays(daysOld);
        
        // When: Check if submission should be included
        boolean shouldInclude = service.shouldIncludeInConsensus(submissionTime, defaultRetentionDays);
        
        // Then: Submission should be included only if less than 90 days old
        if (daysOld < 90) {
            assertThat(shouldInclude).isTrue();
        } else {
            assertThat(shouldInclude).isFalse();
        }
    }

    /**
     * Property 13e: Time-Based Filtering Recent Submissions Always Included
     * 
     * For any submission less than 1 day old, it should always be included
     * regardless of retention period (as long as retention period >= 1 day).
     * 
     * **Validates: Requirements 4.7**
     */
    @Property(tries = 50)
    void timeBasedFilteringRecentSubmissionsAlwaysIncluded(
            @ForAll @IntRange(min = 1, max = 365) int retentionDays,
            @ForAll @IntRange(min = 0, max = 23) int hoursOld) {
        
        // Given: A replay protection service
        ReplayProtectionService service = new ReplayProtectionService(null);
        
        // And: A very recent submission (less than 1 day old)
        LocalDateTime recentSubmission = LocalDateTime.now().minusHours(hoursOld);
        
        // When: Check if submission should be included
        boolean shouldInclude = service.shouldIncludeInConsensus(recentSubmission, retentionDays);
        
        // Then: Recent submission should always be included
        assertThat(shouldInclude).isTrue();
    }

    // Generators for property-based testing

    @Provide
    Arbitrary<String> validIpAddresses() {
        return Arbitraries.oneOf(
                // IPv4 addresses
                Combinators.combine(
                        Arbitraries.integers().between(1, 255),
                        Arbitraries.integers().between(0, 255),
                        Arbitraries.integers().between(0, 255),
                        Arbitraries.integers().between(1, 255)
                ).as((a, b, c, d) -> String.format("%d.%d.%d.%d", a, b, c, d)),
                // Common test IPs
                Arbitraries.just("192.168.1.1"),
                Arbitraries.just("10.0.0.1"),
                Arbitraries.just("172.16.0.1"),
                Arbitraries.just("127.0.0.1")
        );
    }

    @Provide
    Arbitrary<String> validUserAgents() {
        return Arbitraries.oneOf(
                Arbitraries.just("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                Arbitraries.just("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"),
                Arbitraries.just("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36"),
                Arbitraries.just("Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X)"),
                Arbitraries.just("Chrome/91.0.4472.124"),
                Arbitraries.just("Firefox/89.0"),
                Arbitraries.just("Safari/14.1.1")
        );
    }

    @Provide
    Arbitrary<SoftwareIdentity> validSoftwareIdentities() {
        return Combinators.combine(
                validDomains(),
                validFilenames(),
                Arbitraries.longs().between(1L, 1000000000L)
        ).as(SoftwareIdentity::new);
    }

    @Provide
    @StringLength(min = 64, max = 64)
    Arbitrary<String> validBlake3Hashes() {
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .withCharRange('a', 'f')
                .ofLength(64);
    }

    @Provide
    Arbitrary<String> validDomains() {
        return Arbitraries.oneOf(
                Arbitraries.just("example.com"),
                Arbitraries.just("download.microsoft.com"),
                Arbitraries.just("releases.ubuntu.com"),
                Arbitraries.just("github.com"),
                Arbitraries.just("npmjs.org")
        );
    }

    @Provide
    Arbitrary<String> validFilenames() {
        return Arbitraries.oneOf(
                Arbitraries.just("setup.exe"),
                Arbitraries.just("installer.msi"),
                Arbitraries.just("app.dmg"),
                Arbitraries.just("package.deb"),
                Arbitraries.just("software.rpm")
        );
    }

    @Provide
    Arbitrary<LocalDateTime> timestampsWithinSameDay() {
        return Combinators.combine(
                Arbitraries.integers().between(2020, 2025),
                Arbitraries.integers().between(1, 12),
                Arbitraries.integers().between(1, 28), // Use 28 to avoid month boundary issues
                Arbitraries.integers().between(0, 23),
                Arbitraries.integers().between(0, 59)
        ).as(LocalDateTime::of);
    }

    @Provide
    Arbitrary<TimestampPair> timestampPairsWithinSameDay() {
        return Combinators.combine(
                Arbitraries.integers().between(2020, 2025),
                Arbitraries.integers().between(1, 12),
                Arbitraries.integers().between(1, 28),
                Arbitraries.integers().between(0, 23),
                Arbitraries.integers().between(0, 59),
                Arbitraries.integers().between(0, 23),
                Arbitraries.integers().between(0, 59)
        ).as((year, month, day, hour1, minute1, hour2, minute2) -> {
            LocalDateTime timestamp1 = LocalDateTime.of(year, month, day, hour1, minute1);
            LocalDateTime timestamp2 = LocalDateTime.of(year, month, day, hour2, minute2);
            return new TimestampPair(timestamp1, timestamp2);
        });
    }

    @Provide
    Arbitrary<TimestampPair> timestampPairsOnDifferentDays() {
        return Combinators.combine(
                Arbitraries.integers().between(2020, 2025),
                Arbitraries.integers().between(1, 12),
                Arbitraries.integers().between(1, 27), // Use 27 to ensure day+1 is valid
                Arbitraries.integers().between(0, 23),
                Arbitraries.integers().between(0, 59)
        ).as((year, month, day, hour, minute) -> {
            LocalDateTime timestamp1 = LocalDateTime.of(year, month, day, hour, minute);
            LocalDateTime timestamp2 = LocalDateTime.of(year, month, day + 1, hour, minute);
            return new TimestampPair(timestamp1, timestamp2);
        });
    }

    // Helper classes for test data

    static class TimestampPair {
        final LocalDateTime timestamp1;
        final LocalDateTime timestamp2;

        TimestampPair(LocalDateTime timestamp1, LocalDateTime timestamp2) {
            this.timestamp1 = timestamp1;
            this.timestamp2 = timestamp2;
        }
    }
}
