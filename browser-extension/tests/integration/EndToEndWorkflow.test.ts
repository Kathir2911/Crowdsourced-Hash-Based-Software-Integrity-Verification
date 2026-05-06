/**
 * End-to-end integration tests for browser extension complete workflows
 * 
 * Tests complete verification flow: file selection → hash → submission → verification
 * Tests API communication with backend
 * Tests error handling and resilience
 * 
 * **Validates: All requirements integration testing**
 */

import { BLAKE3Hasher } from '../../src/services/BLAKE3Hasher';
import { VerificationAPIClient } from '../../src/services/VerificationAPIClient';
import { NotificationService } from '../../src/services/NotificationService';
import { SoftwareIdentity } from '../../src/models/SoftwareIdentity';
import { HashSubmission } from '../../src/models/HashSubmission';
import { VerificationRequest } from '../../src/models/VerificationRequest';
import { TamperStatus } from '../../src/models/VerificationResult';

// Mock fetch for API testing
global.fetch = jest.fn();

describe('End-to-End Workflow Integration Tests', () => {
  let hasher: BLAKE3Hasher;
  let apiClient: VerificationAPIClient;
  let notificationService: NotificationService;

  beforeEach(() => {
    hasher = new BLAKE3Hasher();
    apiClient = new VerificationAPIClient('http://localhost:8080');
    notificationService = new NotificationService();
    
    // Reset fetch mock
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
   * Test complete verification flow: file selection → hash → submission → verification
   */
  test('complete verification flow with single submission', async () => {
    // Given - Simulate file selection and hash computation
    const fileContent = new Uint8Array([1, 2, 3, 4, 5]);
    const file = new File([fileContent], 'test.exe', { type: 'application/x-msdownload' });
    
    // When - Compute hash
    const hashResult = await hasher.computeHash(file);
    expect(hashResult.success).toBe(true);
    expect(hashResult.hash).toMatch(/^[a-f0-9]{64}$/);
    
    // Given - Create software identity
    const identity = new SoftwareIdentity('download.example.com', 'test.exe', file.size);
    
    // Mock API response for submission
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ success: true, message: 'Submission received' }),
    });
    
    // When - Submit hash
    const submission: HashSubmission = {
      softwareIdentity: identity,
      hash: hashResult.hash!,
      timestamp: new Date(),
      clientVersion: '1.0.0',
    };
    
    const submissionResult = await apiClient.submitHash(submission);
    expect(submissionResult.success).toBe(true);
    
    // Mock API response for verification (UNKNOWN - insufficient data)
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        status: 'UNKNOWN',
        confidence: 0.0,
        submissionCount: 1,
        message: 'Insufficient data for consensus',
        timestamp: new Date().toISOString(),
      }),
    });
    
    // When - Verify hash
    const verificationRequest: VerificationRequest = {
      softwareIdentity: identity,
      hash: hashResult.hash!,
    };
    
    const verificationResult = await apiClient.verifyHash(verificationRequest);
    
    // Then - Should return UNKNOWN status
    expect(verificationResult.status).toBe(TamperStatus.UNKNOWN);
    expect(verificationResult.submissionCount).toBe(1);
    expect(verificationResult.message).toContain('Insufficient data');
  });

  /**
   * Test consensus building scenario with multiple submissions
   */
  test('consensus building with verified status', async () => {
    // Given - File and hash
    const fileContent = new Uint8Array([10, 20, 30, 40, 50]);
    const file = new File([fileContent], 'vscode.exe', { type: 'application/x-msdownload' });
    const hashResult = await hasher.computeHash(file);
    
    const identity = new SoftwareIdentity('download.microsoft.com', 'vscode.exe', file.size);
    
    // Mock API response for verification (VERIFIED - consensus established)
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        status: 'VERIFIED',
        confidence: 0.95,
        submissionCount: 10,
        consensusHash: hashResult.hash,
        message: 'File verified against consensus',
        timestamp: new Date().toISOString(),
      }),
    });
    
    // When - Verify hash
    const verificationRequest: VerificationRequest = {
      softwareIdentity: identity,
      hash: hashResult.hash!,
    };
    
    const verificationResult = await apiClient.verifyHash(verificationRequest);
    
    // Then - Should return VERIFIED status with high confidence
    expect(verificationResult.status).toBe(TamperStatus.VERIFIED);
    expect(verificationResult.confidence).toBeGreaterThanOrEqual(0.70);
    expect(verificationResult.submissionCount).toBeGreaterThanOrEqual(3);
    expect(verificationResult.consensusHash).toBe(hashResult.hash);
    
    // When - Display notification
    notificationService.showVerificationResult(verificationResult, 'vscode.exe');
    
    // Then - Should create green notification
    expect(chrome.notifications.create).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        type: 'basic',
        iconUrl: expect.stringContaining('verified'),
        title: expect.stringContaining('Verified'),
      }),
      expect.any(Function)
    );
  });

  /**
   * Test tamper detection scenario
   */
  test('tamper detection with conflicting hash', async () => {
    // Given - File with different hash than consensus
    const fileContent = new Uint8Array([99, 88, 77, 66, 55]);
    const file = new File([fileContent], 'firefox.exe', { type: 'application/x-msdownload' });
    const tamperedHash = await hasher.computeHash(file);
    
    const identity = new SoftwareIdentity('download.mozilla.org', 'firefox.exe', file.size);
    const consensusHash = 'c1d2e3f4a5b6789012345678901234567890123456789012345678901234cdef';
    
    // Mock API response for verification (TAMPERED)
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        status: 'TAMPERED',
        confidence: 0.85,
        submissionCount: 12,
        consensusHash: consensusHash,
        message: 'WARNING: File hash does not match consensus - potential tampering detected',
        timestamp: new Date().toISOString(),
      }),
    });
    
    // When - Verify tampered hash
    const verificationRequest: VerificationRequest = {
      softwareIdentity: identity,
      hash: tamperedHash.hash!,
    };
    
    const verificationResult = await apiClient.verifyHash(verificationRequest);
    
    // Then - Should return TAMPERED status
    expect(verificationResult.status).toBe(TamperStatus.TAMPERED);
    expect(verificationResult.consensusHash).not.toBe(tamperedHash.hash);
    expect(verificationResult.message).toContain('tamper');
    
    // When - Display notification
    notificationService.showVerificationResult(verificationResult, 'firefox.exe');
    
    // Then - Should create red warning notification
    expect(chrome.notifications.create).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        type: 'basic',
        iconUrl: expect.stringContaining('tampered'),
        title: expect.stringContaining('Tampered'),
      }),
      expect.any(Function)
    );
  });

  /**
   * Test suspicious low confidence scenario
   */
  test('suspicious low confidence with weak consensus', async () => {
    // Given - File with weak consensus
    const fileContent = new Uint8Array([11, 22, 33, 44, 55]);
    const file = new File([fileContent], 'app.dmg', { type: 'application/x-apple-diskimage' });
    const hashResult = await hasher.computeHash(file);
    
    const identity = new SoftwareIdentity('download.example.org', 'app.dmg', file.size);
    
    // Mock API response for verification (SUSPICIOUS_LOW_CONFIDENCE)
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        status: 'SUSPICIOUS_LOW_CONFIDENCE',
        confidence: 0.72,
        submissionCount: 7,
        consensusHash: hashResult.hash,
        message: 'Weak consensus - proceed with caution',
        timestamp: new Date().toISOString(),
      }),
    });
    
    // When - Verify hash
    const verificationRequest: VerificationRequest = {
      softwareIdentity: identity,
      hash: hashResult.hash!,
    };
    
    const verificationResult = await apiClient.verifyHash(verificationRequest);
    
    // Then - Should return SUSPICIOUS_LOW_CONFIDENCE status
    expect(verificationResult.status).toBe(TamperStatus.SUSPICIOUS_LOW_CONFIDENCE);
    expect(verificationResult.confidence).toBeGreaterThanOrEqual(0.70);
    expect(verificationResult.submissionCount).toBeLessThan(10);
    
    // When - Display notification
    notificationService.showVerificationResult(verificationResult, 'app.dmg');
    
    // Then - Should create orange caution notification
    expect(chrome.notifications.create).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        type: 'basic',
        iconUrl: expect.stringContaining('suspicious'),
        title: expect.stringContaining('Suspicious'),
      }),
      expect.any(Function)
    );
  });

  /**
   * Test error handling when backend is unavailable
   */
  test('error handling with service unavailable', async () => {
    // Given - File and hash
    const fileContent = new Uint8Array([1, 2, 3]);
    const file = new File([fileContent], 'test.exe', { type: 'application/x-msdownload' });
    const hashResult = await hasher.computeHash(file);
    
    const identity = new SoftwareIdentity('download.test.com', 'test.exe', file.size);
    
    // Mock API failure
    (global.fetch as jest.Mock).mockRejectedValueOnce(new Error('Network error'));
    
    // When - Attempt to verify hash
    const verificationRequest: VerificationRequest = {
      softwareIdentity: identity,
      hash: hashResult.hash!,
    };
    
    const verificationResult = await apiClient.verifyHash(verificationRequest);
    
    // Then - Should return SERVICE_UNAVAILABLE status
    expect(verificationResult.status).toBe(TamperStatus.SERVICE_UNAVAILABLE);
    expect(verificationResult.message).toContain('unavailable');
  });

  /**
   * Test retry logic for network failures
   */
  test('retry logic for transient network failures', async () => {
    // Given - File and submission
    const fileContent = new Uint8Array([5, 10, 15]);
    const file = new File([fileContent], 'test.exe', { type: 'application/x-msdownload' });
    const hashResult = await hasher.computeHash(file);
    
    const identity = new SoftwareIdentity('download.test.com', 'test.exe', file.size);
    const submission: HashSubmission = {
      softwareIdentity: identity,
      hash: hashResult.hash!,
      timestamp: new Date(),
      clientVersion: '1.0.0',
    };
    
    // Mock API failure then success
    (global.fetch as jest.Mock)
      .mockRejectedValueOnce(new Error('Network timeout'))
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ success: true, message: 'Submission received' }),
      });
    
    // When - Submit hash (should retry and succeed)
    const submissionResult = await apiClient.submitHash(submission);
    
    // Then - Should eventually succeed after retry
    expect(submissionResult.success).toBe(true);
    expect(global.fetch).toHaveBeenCalledTimes(2); // Initial attempt + 1 retry
  });

  /**
   * Test privacy protection - no file paths transmitted
   */
  test('privacy protection in API communication', async () => {
    // Given - File with full path
    const fileContent = new Uint8Array([1, 2, 3]);
    const file = new File([fileContent], 'test.exe', { type: 'application/x-msdownload' });
    const hashResult = await hasher.computeHash(file);
    
    const identity = new SoftwareIdentity('download.example.com', 'test.exe', file.size);
    
    // Mock API response
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ success: true, message: 'Submission received' }),
    });
    
    // When - Submit hash
    const submission: HashSubmission = {
      softwareIdentity: identity,
      hash: hashResult.hash!,
      timestamp: new Date(),
      clientVersion: '1.0.0',
    };
    
    await apiClient.submitHash(submission);
    
    // Then - Verify no file paths in request
    const fetchCall = (global.fetch as jest.Mock).mock.calls[0];
    const requestBody = JSON.parse(fetchCall[1].body);
    
    expect(requestBody).not.toHaveProperty('filePath');
    expect(requestBody).not.toHaveProperty('fullPath');
    expect(JSON.stringify(requestBody)).not.toMatch(/[C-Z]:\\/); // No Windows paths
    expect(JSON.stringify(requestBody)).not.toMatch(/\/home\//); // No Unix paths
    expect(JSON.stringify(requestBody)).not.toMatch(/\/Users\//); // No Mac paths
  });

  /**
   * Test hash computation idempotence
   */
  test('hash computation idempotence', async () => {
    // Given - Same file content
    const fileContent = new Uint8Array([100, 200, 50, 75, 125]);
    const file1 = new File([fileContent], 'test.exe', { type: 'application/x-msdownload' });
    const file2 = new File([fileContent], 'test.exe', { type: 'application/x-msdownload' });
    
    // When - Compute hash twice
    const hash1 = await hasher.computeHash(file1);
    const hash2 = await hasher.computeHash(file2);
    
    // Then - Should produce identical hashes
    expect(hash1.success).toBe(true);
    expect(hash2.success).toBe(true);
    expect(hash1.hash).toBe(hash2.hash);
  });

  /**
   * Test memory management for large files
   */
  test('memory management with streaming for large files', async () => {
    // Given - Large file (simulated with smaller size for test)
    const largeContent = new Uint8Array(1024 * 1024); // 1MB
    for (let i = 0; i < largeContent.length; i++) {
      largeContent[i] = i % 256;
    }
    const largeFile = new File([largeContent], 'large.exe', { type: 'application/x-msdownload' });
    
    // When - Compute hash with progress callback
    let progressCalled = false;
    const hashResult = await hasher.computeHashStreaming(largeFile, (progress) => {
      progressCalled = true;
      expect(progress).toBeGreaterThanOrEqual(0);
      expect(progress).toBeLessThanOrEqual(100);
    });
    
    // Then - Should complete successfully with progress updates
    expect(hashResult.success).toBe(true);
    expect(hashResult.hash).toMatch(/^[a-f0-9]{64}$/);
    expect(progressCalled).toBe(true);
  });
});
