/**
 * Cross-component integration tests for browser extension
 * 
 * Tests integration between:
 * - File selection → hash computation → API submission
 * - Hash computation → verification → notification display
 * - Error handling across all components
 * - Memory management across components
 * 
 * **Validates: All requirements integration testing**
 */

import { BLAKE3Hasher } from '../../src/services/BLAKE3Hasher';
import { VerificationAPIClient } from '../../src/services/VerificationAPIClient';
import { NotificationService } from '../../src/services/NotificationService';
import { SoftwareIdentity } from '../../src/models/SoftwareIdentity';
import { TamperStatus } from '../../src/models/VerificationResult';

// Mock fetch for API testing
global.fetch = jest.fn();

describe('Cross-Component Integration Tests', () => {
  let hasher: BLAKE3Hasher;
  let apiClient: VerificationAPIClient;
  let notificationService: NotificationService;

  beforeEach(() => {
    hasher = new BLAKE3Hasher();
    apiClient = new VerificationAPIClient('https://api.hashverify.com');
    notificationService = new NotificationService();
    
    // Reset mocks
    (global.fetch as jest.Mock).mockReset();
    
    // Mock chrome.notifications API
    global.chrome = {
      notifications: {
        create: jest.fn((id, options, callback) => {
          if (callback) callback(id);
        }),
        clear: jest.fn(),
      },
    } as any;
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  /**
   * Test file selection → hash computation → API submission
   * Validates complete data flow from file to backend
   */
  test('file selection to hash computation to API submission', async () => {
    // Given - Simulate file selection
    const fileContent = new Uint8Array([10, 20, 30, 40, 50, 60, 70, 80]);
    const file = new File([fileContent], 'vscode.exe', { 
      type: 'application/x-msdownload' 
    });
    
    // When - Compute hash
    const hashResult = await hasher.computeHash(file);
    expect(hashResult.success).toBe(true);
    expect(hashResult.hash).toBeDefined();
    
    // When - Create software identity from file metadata
    const identity = new SoftwareIdentity(
      'download.microsoft.com',
      file.name,
      file.size
    );
    
    // Mock API response
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ success: true, message: 'Submission received' }),
    });
    
    // When - Submit to API
    const submissionResult = await apiClient.submitHash({
      softwareIdentity: identity,
      hash: hashResult.hash!,
      timestamp: new Date(),
      clientVersion: '1.0.0',
    });
    
    // Then - Verify complete flow succeeded
    expect(submissionResult.success).toBe(true);
    expect(global.fetch).toHaveBeenCalledTimes(1);
    
    // Verify request payload contains correct data
    const fetchCall = (global.fetch as jest.Mock).mock.calls[0];
    const requestBody = JSON.parse(fetchCall[1].body);
    
    expect(requestBody.hash).toBe(hashResult.hash);
    expect(requestBody.softwareIdentity.normalizedFilename).toBe('vscode.exe');
    expect(requestBody.softwareIdentity.size).toBe(file.size);
  });

  /**
   * Test hash computation → verification → notification display
   * Validates complete verification workflow with user feedback
   */
  test('hash computation to verification to notification display', async () => {
    // Given - File and hash
    const fileContent = new Uint8Array([100, 200, 50, 75, 125, 150]);
    const file = new File([fileContent], 'firefox.exe', { 
      type: 'application/x-msdownload' 
    });
    
    // When - Compute hash
    const hashResult = await hasher.computeHash(file);
    expect(hashResult.success).toBe(true);
    
    // When - Create identity and verify
    const identity = new SoftwareIdentity(
      'download.mozilla.org',
      file.name,
      file.size
    );
    
    // Mock verification response (VERIFIED)
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        status: 'VERIFIED',
        confidence: 0.95,
        submissionCount: 15,
        consensusHash: hashResult.hash,
        message: 'File verified against consensus',
        timestamp: new Date().toISOString(),
      }),
    });
    
    const verificationResult = await apiClient.verifyHash({
      softwareIdentity: identity,
      hash: hashResult.hash!,
    });
    
    // Then - Verify result
    expect(verificationResult.status).toBe(TamperStatus.VERIFIED);
    
    // When - Display notification
    notificationService.showVerificationResult(verificationResult, file.name);
    
    // Then - Verify notification was created with correct data
    expect(chrome.notifications.create).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        type: 'basic',
        title: expect.stringContaining('Verified'),
        message: expect.stringContaining('firefox.exe'),
      }),
      expect.any(Function)
    );
  });

  /**
   * Test error handling across all components
   * Validates that errors propagate correctly through the system
   */
  test('error handling across all components', async () => {
    // Test 1: Hash computation error
    const invalidFile = new File([], 'empty.exe', { type: 'application/x-msdownload' });
    const hashResult = await hasher.computeHash(invalidFile);
    
    // Should handle empty file gracefully
    expect(hashResult.success).toBe(true); // BLAKE3 can hash empty files
    
    // Test 2: API communication error
    const fileContent = new Uint8Array([1, 2, 3]);
    const file = new File([fileContent], 'test.exe', { type: 'application/x-msdownload' });
    const validHashResult = await hasher.computeHash(file);
    
    const identity = new SoftwareIdentity('download.test.com', file.name, file.size);
    
    // Mock API failure
    (global.fetch as jest.Mock).mockRejectedValueOnce(new Error('Network error'));
    
    const verificationResult = await apiClient.verifyHash({
      softwareIdentity: identity,
      hash: validHashResult.hash!,
    });
    
    // Should return SERVICE_UNAVAILABLE status
    expect(verificationResult.status).toBe(TamperStatus.SERVICE_UNAVAILABLE);
    
    // Test 3: Notification display with error status
    notificationService.showVerificationResult(verificationResult, file.name);
    
    // Should create error notification
    expect(chrome.notifications.create).toHaveBeenCalled();
  });

  /**
   * Test memory management across components
   * Validates that large files are handled efficiently
   */
  test('memory management across components with large files', async () => {
    // Given - Large file (1MB)
    const largeContent = new Uint8Array(1024 * 1024);
    for (let i = 0; i < largeContent.length; i++) {
      largeContent[i] = i % 256;
    }
    const largeFile = new File([largeContent], 'large-installer.exe', { 
      type: 'application/x-msdownload' 
    });
    
    // When - Compute hash with streaming
    let progressUpdates = 0;
    const hashResult = await hasher.computeHashStreaming(largeFile, (progress) => {
      progressUpdates++;
      expect(progress).toBeGreaterThanOrEqual(0);
      expect(progress).toBeLessThanOrEqual(100);
    });
    
    // Then - Should complete successfully
    expect(hashResult.success).toBe(true);
    expect(hashResult.hash).toMatch(/^[a-f0-9]{64}$/);
    expect(progressUpdates).toBeGreaterThan(0);
    
    // When - Submit to API
    const identity = new SoftwareIdentity(
      'download.example.com',
      largeFile.name,
      largeFile.size
    );
    
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ success: true, message: 'Submission received' }),
    });
    
    const submissionResult = await apiClient.submitHash({
      softwareIdentity: identity,
      hash: hashResult.hash!,
      timestamp: new Date(),
      clientVersion: '1.0.0',
    });
    
    // Then - Should handle large file submission
    expect(submissionResult.success).toBe(true);
  });

  /**
   * Test data consistency across components
   * Validates that data remains consistent as it flows through components
   */
  test('data consistency across components', async () => {
    // Given - File with specific content
    const fileContent = new Uint8Array([42, 84, 126, 168, 210, 252]);
    const file = new File([fileContent], 'consistent.exe', { 
      type: 'application/x-msdownload' 
    });
    
    // When - Compute hash
    const hashResult1 = await hasher.computeHash(file);
    const hashResult2 = await hasher.computeHash(file);
    
    // Then - Hash should be consistent
    expect(hashResult1.hash).toBe(hashResult2.hash);
    
    // When - Create identity
    const identity1 = new SoftwareIdentity(
      'download.example.com',
      file.name,
      file.size
    );
    const identity2 = new SoftwareIdentity(
      'download.example.com',
      file.name,
      file.size
    );
    
    // Then - Identity hash should be consistent
    expect(identity1.generateIdentityHash()).toBe(identity2.generateIdentityHash());
    
    // When - Submit and verify
    (global.fetch as jest.Mock)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ success: true, message: 'Submission received' }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          status: 'UNKNOWN',
          confidence: 0.0,
          submissionCount: 1,
          message: 'Insufficient data',
          timestamp: new Date().toISOString(),
        }),
      });
    
    await apiClient.submitHash({
      softwareIdentity: identity1,
      hash: hashResult1.hash!,
      timestamp: new Date(),
      clientVersion: '1.0.0',
    });
    
    const verificationResult = await apiClient.verifyHash({
      softwareIdentity: identity2,
      hash: hashResult2.hash!,
    });
    
    // Then - Data should be consistent across API calls
    expect(verificationResult.submissionCount).toBeGreaterThanOrEqual(0);
  });

  /**
   * Test retry logic across components
   * Validates that transient failures are handled with retries
   */
  test('retry logic across components', async () => {
    // Given - File and hash
    const fileContent = new Uint8Array([5, 10, 15, 20, 25]);
    const file = new File([fileContent], 'retry-test.exe', { 
      type: 'application/x-msdownload' 
    });
    
    const hashResult = await hasher.computeHash(file);
    const identity = new SoftwareIdentity('download.test.com', file.name, file.size);
    
    // Mock API failure then success
    (global.fetch as jest.Mock)
      .mockRejectedValueOnce(new Error('Timeout'))
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ success: true, message: 'Submission received' }),
      });
    
    // When - Submit with retry
    const submissionResult = await apiClient.submitHash({
      softwareIdentity: identity,
      hash: hashResult.hash!,
      timestamp: new Date(),
      clientVersion: '1.0.0',
    });
    
    // Then - Should succeed after retry
    expect(submissionResult.success).toBe(true);
    expect(global.fetch).toHaveBeenCalledTimes(2);
  });

  /**
   * Test complete workflow with multiple files
   * Validates that the system handles multiple files correctly
   */
  test('complete workflow with multiple files', async () => {
    // Given - Multiple files
    const files = [
      new File([new Uint8Array([1, 2, 3])], 'file1.exe', { type: 'application/x-msdownload' }),
      new File([new Uint8Array([4, 5, 6])], 'file2.exe', { type: 'application/x-msdownload' }),
      new File([new Uint8Array([7, 8, 9])], 'file3.exe', { type: 'application/x-msdownload' }),
    ];
    
    // When - Process each file
    for (const file of files) {
      // Compute hash
      const hashResult = await hasher.computeHash(file);
      expect(hashResult.success).toBe(true);
      
      // Create identity
      const identity = new SoftwareIdentity(
        'download.example.com',
        file.name,
        file.size
      );
      
      // Mock API responses
      (global.fetch as jest.Mock)
        .mockResolvedValueOnce({
          ok: true,
          json: async () => ({ success: true, message: 'Submission received' }),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: async () => ({
            status: 'UNKNOWN',
            confidence: 0.0,
            submissionCount: 1,
            message: 'Insufficient data',
            timestamp: new Date().toISOString(),
          }),
        });
      
      // Submit and verify
      const submissionResult = await apiClient.submitHash({
        softwareIdentity: identity,
        hash: hashResult.hash!,
        timestamp: new Date(),
        clientVersion: '1.0.0',
      });
      
      const verificationResult = await apiClient.verifyHash({
        softwareIdentity: identity,
        hash: hashResult.hash!,
      });
      
      // Display notification
      notificationService.showVerificationResult(verificationResult, file.name);
      
      // Verify each file was processed
      expect(submissionResult.success).toBe(true);
      expect(verificationResult.status).toBeDefined();
    }
    
    // Then - All files should be processed
    expect(global.fetch).toHaveBeenCalledTimes(files.length * 2); // Submit + verify for each
    expect(chrome.notifications.create).toHaveBeenCalledTimes(files.length);
  });

  /**
   * Test privacy protection across components
   * Validates that no sensitive data leaks through the system
   */
  test('privacy protection across components', async () => {
    // Given - File with path information
    const fileContent = new Uint8Array([1, 2, 3, 4, 5]);
    const file = new File([fileContent], 'private.exe', { 
      type: 'application/x-msdownload' 
    });
    
    // When - Process file
    const hashResult = await hasher.computeHash(file);
    const identity = new SoftwareIdentity('download.example.com', file.name, file.size);
    
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ success: true, message: 'Submission received' }),
    });
    
    await apiClient.submitHash({
      softwareIdentity: identity,
      hash: hashResult.hash!,
      timestamp: new Date(),
      clientVersion: '1.0.0',
    });
    
    // Then - Verify no file paths in any API call
    const fetchCalls = (global.fetch as jest.Mock).mock.calls;
    fetchCalls.forEach((call) => {
      const requestBody = JSON.parse(call[1].body);
      const bodyString = JSON.stringify(requestBody);
      
      // No Windows paths
      expect(bodyString).not.toMatch(/[C-Z]:\\/);
      // No Unix paths
      expect(bodyString).not.toMatch(/\/home\//);
      expect(bodyString).not.toMatch(/\/Users\//);
      // No file:// URLs
      expect(bodyString).not.toMatch(/file:\/\//);
    });
  });
});
