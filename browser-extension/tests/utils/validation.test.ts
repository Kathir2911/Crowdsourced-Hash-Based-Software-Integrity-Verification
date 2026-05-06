import {
  validateBlake3Hash,
  validateSoftwareIdentity,
  validateHashSubmission,
  isExecutableFile,
  normalizeFilename,
  normalizeDomain,
  generateIdentityHash,
  validateIdentityHashConsistency
} from '../../src/utils/validation';
import { SoftwareIdentity, HashSubmission } from '../../src/types';

describe('Validation Utils', () => {
  describe('validateBlake3Hash', () => {
    it('should validate correct BLAKE3 hash', () => {
      const validHash = 'a1b2c3d4e5f67890123456789012345678901234567890123456789012345678';
      const result = validateBlake3Hash(validHash);
      expect(result.valid).toBe(true);
    });

    it('should reject invalid hash length', () => {
      const invalidHash = 'a1b2c3d4e5f6';
      const result = validateBlake3Hash(invalidHash);
      expect(result.valid).toBe(false);
      expect(result.errorMessage).toContain('64-character');
    });

    it('should reject non-hexadecimal characters', () => {
      const invalidHash = 'g1b2c3d4e5f67890123456789012345678901234567890123456789012345678';
      const result = validateBlake3Hash(invalidHash);
      expect(result.valid).toBe(false);
    });

    it('should reject empty hash', () => {
      const result = validateBlake3Hash('');
      expect(result.valid).toBe(false);
      expect(result.errorMessage).toContain('null or empty');
    });
  });

  describe('validateSoftwareIdentity', () => {
    it('should validate correct software identity', () => {
      const identity: SoftwareIdentity = {
        sourceDomain: 'example.com',
        normalizedFilename: 'setup.exe',
        size: 1024
      };
      const result = validateSoftwareIdentity(identity);
      expect(result.valid).toBe(true);
    });

    it('should reject invalid domain', () => {
      const identity: SoftwareIdentity = {
        sourceDomain: 'invalid-domain',
        normalizedFilename: 'setup.exe',
        size: 1024
      };
      const result = validateSoftwareIdentity(identity);
      expect(result.valid).toBe(false);
      expect(result.errorMessage).toContain('domain format');
    });

    it('should reject zero file size', () => {
      const identity: SoftwareIdentity = {
        sourceDomain: 'example.com',
        normalizedFilename: 'setup.exe',
        size: 0
      };
      const result = validateSoftwareIdentity(identity);
      expect(result.valid).toBe(false);
      expect(result.errorMessage).toContain('positive');
    });
  });

  describe('validateHashSubmission', () => {
    it('should validate correct hash submission', () => {
      const submission: HashSubmission = {
        softwareIdentity: {
          sourceDomain: 'example.com',
          normalizedFilename: 'setup.exe',
          size: 1024
        },
        hash: 'a1b2c3d4e5f67890123456789012345678901234567890123456789012345678',
        timestamp: new Date(),
        clientVersion: '1.0.0',
        replayProtectionHash: 'abc123def456'
      };
      const result = validateHashSubmission(submission);
      expect(result.valid).toBe(true);
    });

    it('should reject submission with invalid hash', () => {
      const submission: HashSubmission = {
        softwareIdentity: {
          sourceDomain: 'example.com',
          normalizedFilename: 'setup.exe',
          size: 1024
        },
        hash: 'invalid-hash',
        timestamp: new Date(),
        clientVersion: '1.0.0',
        replayProtectionHash: 'abc123def456'
      };
      const result = validateHashSubmission(submission);
      expect(result.valid).toBe(false);
      expect(result.errorMessage).toContain('Hash validation failed');
    });
  });

  describe('isExecutableFile', () => {
    it('should identify executable files', () => {
      expect(isExecutableFile('setup.exe')).toBe(true);
      expect(isExecutableFile('installer.msi')).toBe(true);
      expect(isExecutableFile('app.dmg')).toBe(true);
      expect(isExecutableFile('package.deb')).toBe(true);
      expect(isExecutableFile('software.rpm')).toBe(true);
      expect(isExecutableFile('program.appimage')).toBe(true);
    });

    it('should reject non-executable files', () => {
      expect(isExecutableFile('document.txt')).toBe(false);
      expect(isExecutableFile('image.jpg')).toBe(false);
      expect(isExecutableFile('data.json')).toBe(false);
    });
  });

  describe('normalizeFilename', () => {
    it('should normalize filename correctly', () => {
      expect(normalizeFilename('Setup.EXE')).toBe('setup.exe');
      expect(normalizeFilename('/path/to/Setup.EXE')).toBe('setup.exe');
      expect(normalizeFilename('C:\\Windows\\Setup.EXE')).toBe('setup.exe');
    });
  });

  describe('normalizeDomain', () => {
    it('should normalize domain correctly', () => {
      expect(normalizeDomain('https://www.Example.COM/')).toBe('example.com');
      expect(normalizeDomain('HTTP://Example.COM')).toBe('example.com');
      expect(normalizeDomain('www.Example.COM')).toBe('example.com');
    });
  });

  describe('generateIdentityHash', () => {
    it('should generate consistent hash for same identity', () => {
      const identity: SoftwareIdentity = {
        sourceDomain: 'example.com',
        normalizedFilename: 'setup.exe',
        size: 1024
      };
      
      const hash1 = generateIdentityHash(identity);
      const hash2 = generateIdentityHash(identity);
      
      expect(hash1).toBe(hash2);
      expect(hash1).toBeTruthy();
    });

    it('should generate different hashes for different identities', () => {
      const identity1: SoftwareIdentity = {
        sourceDomain: 'example.com',
        normalizedFilename: 'setup.exe',
        size: 1024
      };
      
      const identity2: SoftwareIdentity = {
        sourceDomain: 'example.com',
        normalizedFilename: 'setup.exe',
        size: 2048
      };
      
      const hash1 = generateIdentityHash(identity1);
      const hash2 = generateIdentityHash(identity2);
      
      expect(hash1).not.toBe(hash2);
    });
  });

  describe('validateIdentityHashConsistency', () => {
    it('should validate consistent identity hash', () => {
      const identity: SoftwareIdentity = {
        sourceDomain: 'example.com',
        normalizedFilename: 'setup.exe',
        size: 1024,
        identityHash: generateIdentityHash({
          sourceDomain: 'example.com',
          normalizedFilename: 'setup.exe',
          size: 1024
        })
      };
      
      const result = validateIdentityHashConsistency(identity);
      expect(result.valid).toBe(true);
    });

    it('should reject inconsistent identity hash', () => {
      const identity: SoftwareIdentity = {
        sourceDomain: 'example.com',
        normalizedFilename: 'setup.exe',
        size: 1024,
        identityHash: 'wrong-hash'
      };
      
      const result = validateIdentityHashConsistency(identity);
      expect(result.valid).toBe(false);
      expect(result.errorMessage).toContain('does not match');
    });
  });
});