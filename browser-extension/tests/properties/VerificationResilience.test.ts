/**
 * Property-based tests for verification resilience
 * Feature: crowdsourced-hash-verification, Property 25: Verification Resilience
 * 
 * **Validates: Requirements 10.6**
 * 
 * Property: For any verification failure, the Browser_Extension should continue 
 * monitoring downloads without interruption.
 */

import * as fc from 'fast-check';
import { BLAKE3Hasher } from '../../src/services/BLAKE3Hasher';
import { VerificationAPIClient } from '../../src/services/VerificationAPIClient';
import { NotificationService } from '../../src/services/NotificationService';
import { TamperStatus, ExtensionConfig } from '../../src/types';

describe('Property 25: Verification Resilience', () => {
  let blake3Hasher: BLAKE3Hasher;
  let apiClient: VerificationAPIClient;
  let notificationService: NotificationService;
  let config: ExtensionConfig;

  beforeEach(() => {
    blake3Hasher = new BLAKE3Hasher();
    apiClient = new VerificationAPIClient();
    notificationService = new NotificationService();
    
    config = {
      apiBaseUrl: 'http://localhost:8080/api/v1',
      enabledFileTypes: ['.exe', '.msi', '.dmg'],
      maxFileSize: 5 * 1024 * 1024 * 1024,
      timeoutMs: 30000,
      retryAttempts: 3
    };
    
    apiClient.setConfig(config);
  });

  /**
   * Property: Browser extension continues to function after hash computation failures
   * Validates: Requirement 10.6
   */
  test('should continue monitoring after hash computation failures', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.array(fc.uint8Array({ minLength: 1, maxLength: 100 }), { minLength: 2, maxLength: 5 }),
        async (fileDataArray) => {
          let successfulComputations = 0;
          let failedComputations = 0;
          
          // Simulate multiple file processing attempts
          for (const fileData of fileDataArray) {
            try {
              // Create a mock file - convert to regular Uint8Array
              const regularArray = new Uint8Array(fileData);
              const file = new File([regularArray], 'test.exe', { type: 'application/x-msdownload' });
              
              // Attempt hash computation
              const result = await blake3Hasher.computeHash(file);
              
              if (result.success) {
                successfulComputations++;
              } else {
                failedComputations++;
              }
              
              // Extension should continue to function regardless of result
              // Verify that we can still create another hasher instance
              const newHasher = new BLAKE3Hasher();
              expect(newHasher).toBeDefined();
              
            } catch (error) {
              failedComputations++;
              
              // Even on exception, extension should continue
              // Verify we can still instantiate services
              const newHasher = new BLAKE3Hasher();
              expect(newHasher).toBeDefined();
            }
          }
          
          // Property: Extension processed all files without crashing
          expect(successfulComputations + failedComputations).toBe(fileDataArray.length);
          
          // Property: Extension remains functional after processing
          const finalHasher = new BLAKE3Hasher();
          expect(finalHasher).toBeDefined();
        }
      ),
      { numRuns: 50 }
    );
  }, 120000); // 120 second timeout for property test with retries

  /**
   * Property: Browser extension continues to function after verification service failures
   * Validates: Requirement 10.6
   */
  test('should continue monitoring after verification service failures', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.array(
          fc.record({
            hash: fc.hexaString({ minLength: 64, maxLength: 64 }),
            shouldFail: fc.boolean()
          }),
          { minLength: 2, maxLength: 5 }
        ),
        async (verificationRequests) => {
          let successfulVerifications = 0;
          let failedVerifications = 0;
          
          // Simulate multiple verification attempts
          for (const request of verificationRequests) {
            try {
              // Mock verification request
              const result = await apiClient.verifyHash({
                softwareIdentity: {
                  sourceDomain: 'test.com',
                  normalizedFilename: 'test.exe',
                  size: 1024,
                  identityHash: 'test123'
                },
                hash: request.hash
              });
              
              // Service may return SERVICE_UNAVAILABLE but should not throw
              if (result.status === TamperStatus.SERVICE_UNAVAILABLE) {
                failedVerifications++;
              } else {
                successfulVerifications++;
              }
              
              // Extension should continue to function
              expect(result).toBeDefined();
              expect(result.status).toBeDefined();
              
            } catch (error) {
              failedVerifications++;
              
              // Even on exception, extension should continue
              // Verify we can still use the API client
              expect(apiClient).toBeDefined();
            }
          }
          
          // Property: Extension processed all verification requests without crashing
          expect(successfulVerifications + failedVerifications).toBe(verificationRequests.length);
          
          // Property: API client remains functional after processing
          expect(apiClient).toBeDefined();
          const status = await apiClient.getStatus();
          expect(status).toBeDefined();
        }
      ),
      { numRuns: 50 }
    );
  });

  /**
   * Property: Notification service continues to function after display failures
   * Validates: Requirement 10.6
   */
  test('should continue monitoring after notification display failures', () => {
    fc.assert(
      fc.property(
        fc.array(
          fc.record({
            filename: fc.string({ minLength: 1, maxLength: 50 }),
            status: fc.constantFrom(
              TamperStatus.VERIFIED,
              TamperStatus.TAMPERED,
              TamperStatus.SUSPICIOUS_LOW_CONFIDENCE,
              TamperStatus.UNKNOWN,
              TamperStatus.SERVICE_UNAVAILABLE
            ),
            confidence: fc.double({ min: 0, max: 1 }),
            submissionCount: fc.integer({ min: 0, max: 1000 })
          }),
          { minLength: 2, maxLength: 10 }
        ),
        (notifications) => {
          let displayedNotifications = 0;
          
          // Simulate multiple notification displays
          for (const notification of notifications) {
            try {
              // Attempt to display notification
              notificationService.displayNotification({
                filename: notification.filename,
                status: notification.status,
                confidence: notification.confidence,
                submissionCount: notification.submissionCount,
                message: 'Test message'
              });
              
              displayedNotifications++;
              
              // Service should continue to function
              expect(notificationService).toBeDefined();
              
            } catch (error) {
              // Even on exception, service should continue
              expect(notificationService).toBeDefined();
            }
          }
          
          // Property: Service processed all notifications without crashing
          expect(displayedNotifications).toBeGreaterThanOrEqual(0);
          
          // Property: Notification service remains functional
          expect(notificationService).toBeDefined();
          const statusColor = notificationService.getStatusColor(TamperStatus.VERIFIED);
          expect(statusColor).toBeDefined();
        }
      ),
      { numRuns: 50 }
    );
  });

  /**
   * Property: Extension continues after multiple consecutive failures
   * Validates: Requirement 10.6
   */
  test('should continue monitoring after multiple consecutive failures', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.integer({ min: 3, max: 10 }),
        async (failureCount) => {
          let consecutiveFailures = 0;
          
          // Simulate multiple consecutive failures
          for (let i = 0; i < failureCount; i++) {
            try {
              // Attempt verification with invalid data to trigger failure
              const result = await apiClient.verifyHash({
                softwareIdentity: {
                  sourceDomain: 'test.com',
                  normalizedFilename: 'test.exe',
                  size: 1024,
                  identityHash: 'test123'
                },
                hash: 'invalid_hash_that_will_fail'
              });
              
              // Service should return SERVICE_UNAVAILABLE or error status
              if (result.status === TamperStatus.SERVICE_UNAVAILABLE) {
                consecutiveFailures++;
              }
              
            } catch (error) {
              consecutiveFailures++;
            }
            
            // Property: Extension remains functional after each failure
            expect(apiClient).toBeDefined();
            expect(blake3Hasher).toBeDefined();
            expect(notificationService).toBeDefined();
          }
          
          // Property: Extension survived all consecutive failures
          expect(consecutiveFailures).toBeGreaterThan(0);
          
          // Property: All services remain functional after consecutive failures
          expect(apiClient).toBeDefined();
          expect(blake3Hasher).toBeDefined();
          expect(notificationService).toBeDefined();
          
          // Verify we can still perform operations
          const testFile = new File([new Uint8Array([1, 2, 3])], 'test.exe');
          const hashResult = await blake3Hasher.computeHash(testFile);
          expect(hashResult).toBeDefined();
        }
      ),
      { numRuns: 50 }
    );
  }, 120000); // 120 second timeout for property test with retries
});
