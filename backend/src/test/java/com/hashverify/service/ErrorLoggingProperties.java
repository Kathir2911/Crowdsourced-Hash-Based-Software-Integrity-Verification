package com.hashverify.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hashverify.model.HashSubmission;
import com.hashverify.model.SoftwareIdentity;
import com.hashverify.repository.ConsensusCacheRepository;
import com.hashverify.repository.HashSubmissionRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.StringLength;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for error logging completeness
 * Feature: crowdsourced-hash-verification, Property 24: Error Logging Completeness
 * 
 * **Validates: Requirements 10.4**
 * 
 * Property: For any error condition, the Backend_Server should log the error 
 * with sufficient detail for debugging purposes.
 */
public class ErrorLoggingProperties {

    /**
     * Property: Database errors are logged with sufficient detail
     * Validates: Requirement 10.4
     */
    @Property(tries = 50)
    void databaseErrorsAreLoggedWithSufficientDetail(
            @ForAll @AlphaChars @StringLength(min = 10, max = 50) String filename,
            @ForAll @LongRange(min = 1, max = 1000000000) long fileSize,
            @ForAll @AlphaChars @StringLength(min = 64, max = 64) String hash) {
        
        // Set up log appender to capture log messages
        Logger logger = (Logger) LoggerFactory.getLogger(SubmissionStorageService.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        
        // Create mock repositories
        HashSubmissionRepository mockRepository = mock(HashSubmissionRepository.class);
        
        // Arrange: Create a submission that will trigger a database error
        SoftwareIdentity identity = new SoftwareIdentity("test.com", filename.toLowerCase(), fileSize);
        HashSubmission submission = new HashSubmission(identity, hash, "1.0.0", "replay123", "TestAgent");
        
        // Mock repository to throw DataAccessException
        when(mockRepository.save(any(HashSubmission.class)))
                .thenThrow(new DataAccessException("Database connection failed") {});
        
        // Create service with mocked dependencies
        SubmissionStorageService submissionStorageService = new SubmissionStorageService(mockRepository);
        
        // Act: Attempt to save submission (should fail and log error)
        try {
            submissionStorageService.saveSubmission(submission);
        } catch (Exception e) {
            // Expected to throw
        }
        
        // Assert: Verify error was logged with sufficient detail
        List<ILoggingEvent> logEvents = logAppender.list;
        
        // Property: At least one error log entry exists
        assertThat(logEvents).isNotEmpty();
        
        // Property: Error log contains error level
        boolean hasErrorLevel = logEvents.stream()
                .anyMatch(event -> event.getLevel().toString().equals("ERROR"));
        assertThat(hasErrorLevel).isTrue();
        
        // Property: Error log contains error message
        boolean hasErrorMessage = logEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("Failed to save hash submission"));
        assertThat(hasErrorMessage).isTrue();
        
        // Property: Error log contains exception details
        boolean hasExceptionDetails = logEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("Database connection failed"));
        assertThat(hasExceptionDetails).isTrue();
        
        // Cleanup
        logger.detachAppender(logAppender);
        logAppender.stop();
    }

    /**
     * Property: Consensus calculation errors are logged with context
     * Validates: Requirement 10.4
     */
    @Property(tries = 50)
    void consensusCalculationErrorsAreLoggedWithContext(
            @ForAll @AlphaChars @StringLength(min = 8, max = 16) String identityHash) {
        
        // Set up logger for ConsensusService
        Logger consensusLogger = (Logger) LoggerFactory.getLogger(ConsensusService.class);
        ListAppender<ILoggingEvent> consensusLogAppender = new ListAppender<>();
        consensusLogAppender.start();
        consensusLogger.addAppender(consensusLogAppender);
        
        // Arrange: Mock repository to throw exception during consensus calculation
        HashSubmissionRepository mockRepository = mock(HashSubmissionRepository.class);
        ConsensusCacheRepository mockCacheRepository = mock(ConsensusCacheRepository.class);
        
        when(mockRepository.findBySoftwareIdentityHashAndTimestampAfter(anyString(), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Query timeout"));
        
        when(mockCacheRepository.findBySoftwareIdentityHash(anyString()))
                .thenReturn(Optional.empty());
        
        // Create services with mocked dependencies
        SubmissionStorageService submissionStorageService = new SubmissionStorageService(mockRepository);
        ConsensusService consensusService = new ConsensusService(submissionStorageService, mockCacheRepository);
        
        // Act: Attempt to calculate consensus (should fail and log error)
        try {
            consensusService.calculateConsensus(identityHash);
        } catch (Exception e) {
            // Expected to throw or return default result
        }
        
        // Assert: Verify error was logged with context
        List<ILoggingEvent> logEvents = consensusLogAppender.list;
        
        // Property: Error log exists
        assertThat(logEvents).isNotEmpty();
        
        // Property: Error log contains identity hash for context
        boolean hasIdentityHash = logEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains(identityHash));
        assertThat(hasIdentityHash).isTrue();
        
        // Property: Error log contains error type information
        boolean hasErrorType = logEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("Error") || 
                                   event.getFormattedMessage().contains("error"));
        assertThat(hasErrorType).isTrue();
        
        // Cleanup
        consensusLogger.detachAppender(consensusLogAppender);
        consensusLogAppender.stop();
    }

    /**
     * Property: Verification errors are logged with request details
     * Validates: Requirement 10.4
     */
    @Property(tries = 50)
    void verificationErrorsAreLoggedWithRequestDetails(
            @ForAll @AlphaChars @StringLength(min = 10, max = 50) String filename,
            @ForAll @LongRange(min = 1, max = 1000000000) long fileSize,
            @ForAll @AlphaChars @StringLength(min = 64, max = 64) String hash) {
        
        // Arrange: Create software identity and mock consensus service to throw exception
        SoftwareIdentity identity = new SoftwareIdentity("test.com", filename.toLowerCase(), fileSize);
        
        // Mock consensus service to throw exception
        ConsensusService mockConsensusService = mock(ConsensusService.class);
        when(mockConsensusService.calculateConsensus(anyString()))
                .thenThrow(new RuntimeException("Consensus service unavailable"));
        
        VerificationService testVerificationService = new VerificationService(mockConsensusService);
        
        // Set up logger for VerificationService
        Logger verificationLogger = (Logger) LoggerFactory.getLogger(VerificationService.class);
        ListAppender<ILoggingEvent> verificationLogAppender = new ListAppender<>();
        verificationLogAppender.start();
        verificationLogger.addAppender(verificationLogAppender);
        
        // Act: Attempt to verify hash (should handle error and log)
        testVerificationService.verifyHash(hash, identity);
        
        // Assert: Verify error was logged with request details
        List<ILoggingEvent> logEvents = verificationLogAppender.list;
        
        // Property: Error log exists
        assertThat(logEvents).isNotEmpty();
        
        // Property: Error log contains filename for context
        boolean hasFilename = logEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains(filename.toLowerCase()));
        assertThat(hasFilename).isTrue();
        
        // Property: Error log contains hash for debugging
        boolean hasHash = logEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains(hash));
        assertThat(hasHash).isTrue();
        
        // Property: Error log contains error message
        boolean hasErrorMessage = logEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("Error verifying hash"));
        assertThat(hasErrorMessage).isTrue();
        
        // Cleanup
        verificationLogger.detachAppender(verificationLogAppender);
        verificationLogAppender.stop();
    }

    /**
     * Property: All error logs include timestamp information
     * Validates: Requirement 10.4
     */
    @Property(tries = 50)
    void allErrorLogsIncludeTimestamp(
            @ForAll @AlphaChars @StringLength(min = 10, max = 50) String filename,
            @ForAll @LongRange(min = 1, max = 1000000000) long fileSize) {
        
        // Set up log appender
        Logger logger = (Logger) LoggerFactory.getLogger(SubmissionStorageService.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        
        // Create mock repository
        HashSubmissionRepository mockRepository = mock(HashSubmissionRepository.class);
        
        // Arrange: Create submission that will trigger error
        SoftwareIdentity identity = new SoftwareIdentity("test.com", filename.toLowerCase(), fileSize);
        HashSubmission submission = new HashSubmission(identity, "a".repeat(64), "1.0.0", "replay123", "TestAgent");
        
        when(mockRepository.save(any(HashSubmission.class)))
                .thenThrow(new RuntimeException("Test error"));
        
        // Create service
        SubmissionStorageService submissionStorageService = new SubmissionStorageService(mockRepository);
        
        // Act: Trigger error
        try {
            submissionStorageService.saveSubmission(submission);
        } catch (Exception e) {
            // Expected
        }
        
        // Assert: Verify all error logs have timestamps
        List<ILoggingEvent> logEvents = logAppender.list;
        
        // Property: All log events have timestamps
        boolean allHaveTimestamps = logEvents.stream()
                .allMatch(event -> event.getTimeStamp() > 0);
        assertThat(allHaveTimestamps).isTrue();
        
        // Property: Timestamps are recent (within last minute)
        long currentTime = System.currentTimeMillis();
        boolean timestampsAreRecent = logEvents.stream()
                .allMatch(event -> (currentTime - event.getTimeStamp()) < 60000);
        assertThat(timestampsAreRecent).isTrue();
        
        // Cleanup
        logger.detachAppender(logAppender);
        logAppender.stop();
    }

    /**
     * Property: Error logs include exception stack traces
     * Validates: Requirement 10.4
     */
    @Property(tries = 50)
    void errorLogsIncludeStackTraces(
            @ForAll @AlphaChars @StringLength(min = 8, max = 16) String identityHash) {
        
        // Set up log appender
        Logger logger = (Logger) LoggerFactory.getLogger(SubmissionStorageService.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        
        // Create mock repository
        HashSubmissionRepository mockRepository = mock(HashSubmissionRepository.class);
        
        // Arrange: Mock to throw exception with stack trace
        when(mockRepository.findBySoftwareIdentityHashAndTimestampAfter(anyString(), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Database error with stack trace"));
        
        // Create service
        SubmissionStorageService submissionStorageService = new SubmissionStorageService(mockRepository);
        
        // Act: Trigger error
        try {
            submissionStorageService.findRecentByIdentityHash(identityHash);
        } catch (Exception e) {
            // Expected
        }
        
        // Assert: Verify error logs include stack trace information
        List<ILoggingEvent> logEvents = logAppender.list;
        
        // Property: Error log exists
        assertThat(logEvents).isNotEmpty();
        
        // Property: At least one log entry has throwable information
        boolean hasThrowableInfo = logEvents.stream()
                .anyMatch(event -> event.getThrowableProxy() != null || 
                                   event.getFormattedMessage().contains("StackTrace"));
        assertThat(hasThrowableInfo).isTrue();
        
        // Cleanup
        logger.detachAppender(logAppender);
        logAppender.stop();
    }

    /**
     * Property: Multiple consecutive errors are all logged
     * Validates: Requirement 10.4
     */
    @Property(tries = 50)
    void multipleConsecutiveErrorsAreAllLogged(
            @ForAll @IntRange(min = 2, max = 10) int errorCount) {
        
        // Set up log appender
        Logger logger = (Logger) LoggerFactory.getLogger(SubmissionStorageService.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        
        // Create mock repository
        HashSubmissionRepository mockRepository = mock(HashSubmissionRepository.class);
        
        // Arrange: Mock to throw errors
        when(mockRepository.save(any(HashSubmission.class)))
                .thenThrow(new RuntimeException("Persistent error"));
        
        // Create service
        SubmissionStorageService submissionStorageService = new SubmissionStorageService(mockRepository);
        
        // Act: Trigger multiple errors
        for (int i = 0; i < errorCount; i++) {
            SoftwareIdentity identity = new SoftwareIdentity("test.com", "file" + i + ".exe", 1024L);
            HashSubmission submission = new HashSubmission(identity, "a".repeat(64), "1.0.0", "replay" + i, "TestAgent");
            
            try {
                submissionStorageService.saveSubmission(submission);
            } catch (Exception e) {
                // Expected
            }
        }
        
        // Assert: Verify all errors were logged
        List<ILoggingEvent> logEvents = logAppender.list;
        
        // Property: Number of error logs matches number of errors triggered
        long errorLogCount = logEvents.stream()
                .filter(event -> event.getLevel().toString().equals("ERROR"))
                .count();
        assertThat(errorLogCount).isGreaterThanOrEqualTo(errorCount);
        
        // Cleanup
        logger.detachAppender(logAppender);
        logAppender.stop();
    }
}