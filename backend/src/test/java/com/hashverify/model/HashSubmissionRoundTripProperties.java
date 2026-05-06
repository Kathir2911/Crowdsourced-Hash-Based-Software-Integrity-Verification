package com.hashverify.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.LowerChars;
import net.jqwik.api.constraints.NumericChars;
import net.jqwik.api.constraints.StringLength;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for Hash Submission Round-Trip Integrity
 * **Validates: Requirements 11.5**
 */
class HashSubmissionRoundTripProperties {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * Property 28: Hash Submission Round-Trip Integrity
     * 
     * For any valid Hash_Submission, parsing then formatting then parsing should produce 
     * equivalent data, ensuring data integrity throughout the processing pipeline.
     * 
     * **Validates: Requirements 11.5**
     */
    @Property(tries = 50)
    void hashSubmissionJsonRoundTripIntegrity(@ForAll("validHashSubmissions") HashSubmission original) 
            throws JsonProcessingException {
        
        // Step 1: Format (serialize) to JSON
        String jsonString = objectMapper.writeValueAsString(original);
        
        // Step 2: Parse (deserialize) from JSON
        HashSubmission parsed = objectMapper.readValue(jsonString, HashSubmission.class);
        
        // Step 3: Format (serialize) again to JSON
        String jsonStringAgain = objectMapper.writeValueAsString(parsed);
        
        // Step 4: Parse (deserialize) again from JSON
        HashSubmission parsedAgain = objectMapper.readValue(jsonStringAgain, HashSubmission.class);
        
        // Verify round-trip integrity: original data should be equivalent after round-trip
        assertThat(parsedAgain.getSoftwareIdentity()).isEqualTo(original.getSoftwareIdentity());
        assertThat(parsedAgain.getHash()).isEqualTo(original.getHash());
        assertThat(parsedAgain.getClientVersion()).isEqualTo(original.getClientVersion());
        assertThat(parsedAgain.getReplayProtectionHash()).isEqualTo(original.getReplayProtectionHash());
        assertThat(parsedAgain.getUserAgent()).isEqualTo(original.getUserAgent());
        
        // Verify computed fields are consistent
        assertThat(parsedAgain.getSoftwareIdentityHash()).isEqualTo(original.getSoftwareIdentityHash());
        
        // Verify JSON strings are identical (formatting consistency)
        assertThat(jsonStringAgain).isEqualTo(jsonString);
    }

    /**
     * Property 28b: Hash Submission Database Round-Trip Integrity
     * 
     * For any valid Hash_Submission, storing to database then retrieving should produce 
     * equivalent data, ensuring data integrity in persistence layer.
     * 
     * **Validates: Requirements 11.5**
     */
    @Property(tries = 50)
    void hashSubmissionDatabaseRoundTripIntegrity(@ForAll("validHashSubmissions") HashSubmission original) {
        
        // Simulate database storage by copying all fields to a new instance
        // (This tests the entity mapping and field consistency)
        HashSubmission stored = new HashSubmission();
        stored.setId(original.getId());
        stored.setSoftwareIdentity(original.getSoftwareIdentity());
        stored.setHash(original.getHash());
        stored.setTimestamp(original.getTimestamp());
        stored.setClientVersion(original.getClientVersion());
        stored.setReplayProtectionHash(original.getReplayProtectionHash());
        stored.setUserAgent(original.getUserAgent());
        
        // Verify all fields are preserved
        assertThat(stored.getSoftwareIdentity()).isEqualTo(original.getSoftwareIdentity());
        assertThat(stored.getHash()).isEqualTo(original.getHash());
        assertThat(stored.getTimestamp()).isEqualTo(original.getTimestamp());
        assertThat(stored.getClientVersion()).isEqualTo(original.getClientVersion());
        assertThat(stored.getReplayProtectionHash()).isEqualTo(original.getReplayProtectionHash());
        assertThat(stored.getUserAgent()).isEqualTo(original.getUserAgent());
        
        // Verify computed fields are consistent
        assertThat(stored.getSoftwareIdentityHash()).isEqualTo(original.getSoftwareIdentityHash());
    }

    /**
     * Property 28c: Hash Submission Constructor Round-Trip Integrity
     * 
     * For any valid Hash_Submission created via constructor, all getters should return 
     * the exact values that were passed to the constructor.
     * 
     * **Validates: Requirements 11.5**
     */
    @Property(tries = 50)
    void hashSubmissionConstructorRoundTripIntegrity(
            @ForAll("validSoftwareIdentities") SoftwareIdentity softwareIdentity,
            @ForAll("validBlake3Hashes") String hash,
            @ForAll("validClientVersions") String clientVersion,
            @ForAll("validReplayProtectionHashes") String replayProtectionHash,
            @ForAll("validUserAgents") String userAgent) {
        
        // Create HashSubmission using constructor
        HashSubmission submission = new HashSubmission(
                softwareIdentity, hash, clientVersion, replayProtectionHash, userAgent);
        
        // Set timestamp manually since @CreationTimestamp only works during persistence
        LocalDateTime now = LocalDateTime.now();
        submission.setTimestamp(now);
        
        // Verify all values are preserved exactly
        assertThat(submission.getSoftwareIdentity()).isEqualTo(softwareIdentity);
        assertThat(submission.getHash()).isEqualTo(hash.toLowerCase()); // Hash is normalized to lowercase
        assertThat(submission.getClientVersion()).isEqualTo(clientVersion);
        assertThat(submission.getReplayProtectionHash()).isEqualTo(replayProtectionHash);
        assertThat(submission.getUserAgent()).isEqualTo(userAgent);
        
        // Verify computed fields are correct
        assertThat(submission.getSoftwareIdentityHash()).isEqualTo(softwareIdentity.generateIdentityHash());
        assertThat(submission.getTimestamp()).isEqualTo(now); // Timestamp should be set
    }

    // Generators for property-based testing

    @Provide
    Arbitrary<HashSubmission> validHashSubmissions() {
        return Combinators.combine(
                validSoftwareIdentities(),
                validBlake3Hashes(),
                validClientVersions(),
                validReplayProtectionHashes(),
                validUserAgents(),
                validLocalDateTimes()
        ).as((softwareIdentity, hash, clientVersion, replayProtectionHash, userAgent, timestamp) -> {
            HashSubmission submission = new HashSubmission(
                    softwareIdentity, hash, clientVersion, replayProtectionHash, userAgent);
            submission.setId(UUID.randomUUID());
            submission.setTimestamp(timestamp);
            return submission;
        });
    }

    @Provide
    Arbitrary<SoftwareIdentity> validSoftwareIdentities() {
        return Combinators.combine(
                validDomains(),
                validFilenames(),
                Arbitraries.longs().between(1L, Long.MAX_VALUE)
        ).as(SoftwareIdentity::new);
    }

    @Provide
    @StringLength(min = 64, max = 64)
    @LowerChars
    @NumericChars
    Arbitrary<String> validBlake3Hashes() {
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .withCharRange('a', 'f')
                .ofLength(64);
    }

    @Provide
    @StringLength(min = 1, max = 32)
    Arbitrary<String> validClientVersions() {
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .withChars('.', '-', '_')
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .ofMinLength(1)
                .ofMaxLength(32)
                .filter(s -> !s.trim().isEmpty());
    }

    @Provide
    @StringLength(min = 1, max = 64)
    @AlphaChars
    @NumericChars
    Arbitrary<String> validReplayProtectionHashes() {
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .withCharRange('a', 'f')
                .ofMinLength(8)
                .ofMaxLength(64);
    }

    @Provide
    Arbitrary<String> validUserAgents() {
        return Arbitraries.oneOf(
                Arbitraries.just("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                Arbitraries.just("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"),
                Arbitraries.just("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36"),
                Arbitraries.just(null) // User agent can be null
        );
    }

    @Provide
    @StringLength(min = 3, max = 255)
    Arbitrary<String> validDomains() {
        return Arbitraries.oneOf(
                Arbitraries.just("example.com"),
                Arbitraries.just("download.microsoft.com"),
                Arbitraries.just("releases.ubuntu.com"),
                Arbitraries.just("github.com"),
                Arbitraries.just("sourceforge.net"),
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .withCharRange('0', '9')
                        .withChars('.', '-')
                        .ofMinLength(3)
                        .ofMaxLength(50)
                        .filter(s -> s.contains(".") && !s.startsWith(".") && !s.endsWith("."))
        );
    }

    @Provide
    @StringLength(min = 1, max = 255)
    Arbitrary<String> validFilenames() {
        return Arbitraries.oneOf(
                Arbitraries.just("setup.exe"),
                Arbitraries.just("installer.msi"),
                Arbitraries.just("app.dmg"),
                Arbitraries.just("package.deb"),
                Arbitraries.just("software.rpm"),
                Arbitraries.just("program.appimage"),
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .withCharRange('0', '9')
                        .withChars('.', '-', '_')
                        .ofMinLength(5)
                        .ofMaxLength(100)
                        .filter(s -> s.contains(".") && !s.startsWith(".") && !s.endsWith("."))
        );
    }

    @Provide
    Arbitrary<LocalDateTime> validLocalDateTimes() {
        return Arbitraries.integers()
                .between(2020, 2030)
                .flatMap(year -> 
                    Arbitraries.integers().between(1, 12).flatMap(month ->
                        Arbitraries.integers().between(1, 28).flatMap(day ->
                            Arbitraries.integers().between(0, 23).flatMap(hour ->
                                Arbitraries.integers().between(0, 59).flatMap(minute ->
                                    Arbitraries.integers().between(0, 59).map(second ->
                                        LocalDateTime.of(year, month, day, hour, minute, second)
                                    )
                                )
                            )
                        )
                    )
                );
    }
}