/**
 * Property-based tests for privacy protection utilities
 * Tests requirements 7.1, 7.2, 7.3, 7.5, 7.6
 */

import fc from 'fast-check';
import {
  sanitizeSoftwareIdentity,
  sanitizeDomain,
  sanitizeFilename,
  sanitizeUserAgent,
  validateNoPII,
  sanitizeHashSubmission,
  sanitizeVerificationRequest
} from '../../src/utils/privacy';
import { SoftwareIdentity, HashSubmission, VerificationRequest } from '../../src/types';

describe('Privacy Protection Properties', () => {
  /**
   * Property 17: Privacy Protection in Transmissions
   * Validates: Requirements 7.1, 7.2
   * 
   * Property: For any file path, the sanitized filename should never contain path separators
   */
  test('Property 17: Sanitized filenames never contain path separators', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 100 }),
        (filepath) => {
          const sanitized = sanitizeFilename(filepath);
          
          // Should not contain path separators
          expect(sanitized).not.toMatch(/[/\\]/);
          
          // Should not be empty
          expect(sanitized.length).toBeGreaterThan(0);
        }
      ),
      { numRuns: 50 }
    );
  });

  /**
   * Property: Sanitized domains never contain credentials
   */
  test('Property: Sanitized domains never contain @ symbol (credentials)', () => {
    fc.assert(
      fc.property(
        fc.webUrl(),
        (url) => {
          // Extract domain from URL
          const domain = new URL(url).hostname;
          const withCredentials = `user:pass@${domain}`;
          
          const sanitized = sanitizeDomain(withCredentials);
          
          // Should not contain @ symbol
          expect(sanitized).not.toContain('@');
          
          // Should not contain colon (port or credentials)
          expect(sanitized).not.toContain(':');
        }
      ),
      { numRuns: 50 }
    );
  });

  /**
   * Property: Sanitized user agents are generic (no specific versions)
   */
  test('Property: Sanitized user agents contain only major version', () => {
    const userAgents = [
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.6099.109',
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Firefox/121.0.1',
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Safari/537.36',
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Edg/120.0.2210.91'
    ];

    userAgents.forEach(ua => {
      const sanitized = sanitizeUserAgent(ua);
      
      // Should be defined
      expect(sanitized).toBeDefined();
      
      // Should not contain minor version numbers (dots followed by numbers)
      expect(sanitized).not.toMatch(/\.\d+/);
      
      // Should contain only browser name and major version
      expect(sanitized).toMatch(/^(Chrome|Firefox|Safari|Edge)\/\d+$/);
    });
  });

  /**
   * Property 18: Privacy Protection in Storage
   * Validates: Requirements 7.3, 7.5, 7.6
   * 
   * Property: Sanitized data should never contain common PII patterns
   */
  test('Property 18: Sanitized data contains no PII patterns', () => {
    fc.assert(
      fc.property(
        fc.record({
          sourceDomain: fc.webUrl().map(url => new URL(url).hostname),
          normalizedFilename: fc.string({ minLength: 1, maxLength: 50 }),
          size: fc.integer({ min: 1, max: 1000000 })
        }),
        (identity) => {
          const sanitized = sanitizeSoftwareIdentity(identity as SoftwareIdentity);
          
          const validation = validateNoPII(sanitized);
          
          // Should not contain PII
          expect(validation.valid).toBe(true);
          expect(validation.violations).toHaveLength(0);
        }
      ),
      { numRuns: 50 }
    );
  });

  /**
   * Property: Windows file paths are sanitized
   */
  test('Property: Windows file paths are removed', () => {
    const windowsPaths = [
      'C:\\Users\\John\\Downloads\\setup.exe',
      'D:\\Program Files\\app\\installer.msi',
      'E:\\Documents\\file.exe'
    ];

    windowsPaths.forEach(path => {
      const sanitized = sanitizeFilename(path);
      
      // Should only contain filename
      expect(sanitized).not.toContain('\\');
      expect(sanitized).not.toContain('C:');
      expect(sanitized).not.toContain('Users');
      expect(sanitized).not.toContain('John');
      
      // Should contain only the filename
      expect(sanitized).toMatch(/^[a-z0-9._-]+$/);
    });
  });

  /**
   * Property: Unix file paths are sanitized
   */
  test('Property: Unix file paths are removed', () => {
    const unixPaths = [
      '/home/john/downloads/setup.exe',
      '/Users/jane/Documents/installer.dmg',
      '/tmp/file.bin'
    ];

    unixPaths.forEach(path => {
      const sanitized = sanitizeFilename(path);
      
      // Should only contain filename
      expect(sanitized).not.toContain('/');
      expect(sanitized).not.toContain('home');
      expect(sanitized).not.toContain('john');
      expect(sanitized).not.toContain('jane');
      
      // Should contain only the filename
      expect(sanitized).toMatch(/^[a-z0-9._-]+$/);
    });
  });

  /**
   * Property: Hash submissions are sanitized
   */
  test('Property: Hash submissions contain no PII', () => {
    fc.assert(
      fc.property(
        fc.record({
          softwareIdentity: fc.record({
            sourceDomain: fc.webUrl().map(url => new URL(url).hostname),
            normalizedFilename: fc.string({ minLength: 1, maxLength: 50 }),
            size: fc.integer({ min: 1, max: 1000000 })
          }),
          hash: fc.hexaString({ minLength: 64, maxLength: 64 }),
          timestamp: fc.date(),
          clientVersion: fc.constantFrom('1.0.0', '1.1.0', '2.0.0'),
          replayProtectionHash: fc.hexaString({ minLength: 32, maxLength: 32 }),
          userAgent: fc.constantFrom(
            'Mozilla/5.0 Chrome/120.0.6099.109',
            'Mozilla/5.0 Firefox/121.0.1'
          )
        }),
        (submission) => {
          const sanitized = sanitizeHashSubmission(submission as HashSubmission);
          
          const validation = validateNoPII(sanitized);
          
          // Should not contain PII
          expect(validation.valid).toBe(true);
          expect(validation.violations).toHaveLength(0);
        }
      ),
      { numRuns: 50 }
    );
  });

  /**
   * Property: Verification requests are sanitized
   */
  test('Property: Verification requests contain no PII', () => {
    fc.assert(
      fc.property(
        fc.record({
          softwareIdentity: fc.record({
            sourceDomain: fc.webUrl().map(url => new URL(url).hostname),
            normalizedFilename: fc.string({ minLength: 1, maxLength: 50 }),
            size: fc.integer({ min: 1, max: 1000000 })
          }),
          hash: fc.hexaString({ minLength: 64, maxLength: 64 })
        }),
        (request) => {
          const sanitized = sanitizeVerificationRequest(request as VerificationRequest);
          
          const validation = validateNoPII(sanitized);
          
          // Should not contain PII
          expect(validation.valid).toBe(true);
          expect(validation.violations).toHaveLength(0);
        }
      ),
      { numRuns: 50 }
    );
  });

  /**
   * Property: Email addresses are detected in PII validation
   */
  test('Property: PII validation detects email addresses', () => {
    const dataWithEmail = {
      filename: 'setup.exe',
      email: 'user@example.com'
    };

    const validation = validateNoPII(dataWithEmail);
    
    expect(validation.valid).toBe(false);
    expect(validation.violations.length).toBeGreaterThan(0);
    expect(validation.violations.some(v => v.includes('Email'))).toBe(true);
  });

  /**
   * Property: File paths are detected in PII validation
   */
  test('Property: PII validation detects file paths', () => {
    const dataWithPath = {
      filename: 'C:\\Users\\John\\setup.exe'
    };

    const validation = validateNoPII(dataWithPath);
    
    expect(validation.valid).toBe(false);
    expect(validation.violations.length).toBeGreaterThan(0);
  });

  /**
   * Property: Sanitization is idempotent
   */
  test('Property: Sanitization is idempotent (applying twice gives same result)', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 100 }),
        (filename) => {
          const sanitized1 = sanitizeFilename(filename);
          const sanitized2 = sanitizeFilename(sanitized1);
          
          // Applying sanitization twice should give same result
          expect(sanitized1).toBe(sanitized2);
        }
      ),
      { numRuns: 50 }
    );
  });

  /**
   * Property: Sanitized filenames are always lowercase
   */
  test('Property: Sanitized filenames are lowercase', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 100 }),
        (filename) => {
          const sanitized = sanitizeFilename(filename);
          
          // Should be lowercase
          expect(sanitized).toBe(sanitized.toLowerCase());
        }
      ),
      { numRuns: 50 }
    );
  });

  /**
   * Property: Sanitized domains are always lowercase
   */
  test('Property: Sanitized domains are lowercase', () => {
    fc.assert(
      fc.property(
        fc.webUrl(),
        (url) => {
          const domain = new URL(url).hostname;
          const sanitized = sanitizeDomain(domain);
          
          // Should be lowercase
          expect(sanitized).toBe(sanitized.toLowerCase());
        }
      ),
      { numRuns: 50 }
    );
  });
});
