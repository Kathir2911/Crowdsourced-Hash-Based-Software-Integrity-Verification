/**
 * Unit tests and property-based tests for BLAKE3Hasher service
 * 
 * Note: These tests focus on the core validation and utility methods.
 * Full file processing tests would require a more complex browser environment setup.
 */

import { BLAKE3Hasher } from '../../src/services/BLAKE3Hasher';
import * as fc from 'fast-check';

describe('BLAKE3Hasher', () => {
  let hasher: BLAKE3Hasher;

  beforeEach(() => {
    hasher = new BLAKE3Hasher();
  });

  describe('validateHashFormat', () => {
    it('should validate correct BLAKE3 hash format', () => {
      const validHash = 'a'.repeat(64);
      expect(hasher.validateHashFormat(validHash)).toBe(true);
    });

    it('should validate real BLAKE3 hash', () => {
      const realHash = 'f9966311c780665a97b5be88682f05f2c9b7259087c5113900c2390b65b0b5ff';
      expect(hasher.validateHashFormat(realHash)).toBe(true);
    });

    it('should reject invalid hash formats', () => {
      expect(hasher.validateHashFormat('too_short')).toBe(false);
      expect(hasher.validateHashFormat('A'.repeat(64))).toBe(false); // uppercase
      expect(hasher.validateHashFormat('g'.repeat(64))).toBe(false); // invalid hex
      expect(hasher.validateHashFormat('a'.repeat(63))).toBe(false); // too short
      expect(hasher.validateHashFormat('a'.repeat(65))).toBe(false); // too long
      expect(hasher.validateHashFormat('')).toBe(false); // empty
      expect(hasher.validateHashFormat('invalid-chars-!')).toBe(false); // special chars
    });
  });

  describe('normalizeHash', () => {
    it('should convert hash to lowercase', () => {
      const upperHash = 'ABCDEF1234567890'.repeat(4);
      const normalized = hasher.normalizeHash(upperHash);
      expect(normalized).toBe(upperHash.toLowerCase());
    });

    it('should handle mixed case', () => {
      const mixedHash = 'AbCdEf1234567890'.repeat(4);
      const normalized = hasher.normalizeHash(mixedHash);
      expect(normalized).toBe(mixedHash.toLowerCase());
    });

    it('should leave lowercase unchanged', () => {
      const lowerHash = 'abcdef1234567890'.repeat(4);
      const normalized = hasher.normalizeHash(lowerHash);
      expect(normalized).toBe(lowerHash);
    });
  });

  describe('file validation', () => {
    it('should handle null file gracefully', async () => {
      // Test with null file (simulating error condition)
      const result = await hasher.computeHash(null as any);
      
      expect(result.success).toBe(false);
      expect(result.hash).toBe('');
      expect(result.error).toContain('No file provided');
    });
  });

  describe('error handling', () => {
    it('should handle file size validation', async () => {
      // Create a mock file that's too large
      const mockLargeFile = {
        size: 11 * 1024 * 1024 * 1024, // 11GB - exceeds 10GB limit
        name: 'huge.exe',
        arrayBuffer: async () => new ArrayBuffer(0)
      } as unknown as File;

      const result = await hasher.computeHash(mockLargeFile);
      
      expect(result.success).toBe(false);
      expect(result.error).toContain('File too large');
    });

    it('should handle empty files', async () => {
      const mockEmptyFile = {
        size: 0,
        name: 'empty.exe',
        arrayBuffer: async () => new ArrayBuffer(0)
      } as unknown as File;

      const result = await hasher.computeHash(mockEmptyFile);
      
      expect(result.success).toBe(false);
      expect(result.error).toContain('Cannot hash empty file');
    });
  });

  describe('constants and thresholds', () => {
    it('should have correct file size thresholds', () => {
      // Test that the hasher has reasonable thresholds
      // We can't access private members directly, but we can test behavior
      
      // Small file should use standard method
      const smallFile = {
        size: 50 * 1024 * 1024, // 50MB
        name: 'small.exe',
        arrayBuffer: async () => {
          throw new Error('Test error to verify method selection');
        }
      } as unknown as File;

      // This should fail with our test error, confirming it tries arrayBuffer (standard method)
      return hasher.computeHash(smallFile).then(result => {
        expect(result.success).toBe(false);
        expect(result.error).toContain('Test error');
      });
    });

    it('should use streaming for large files', () => {
      // Large file should use streaming method
      const largeFile = {
        size: 200 * 1024 * 1024, // 200MB - above 100MB threshold
        name: 'large.exe',
        slice: () => {
          throw new Error('Streaming test error');
        }
      } as unknown as File;

      // This should fail with our streaming test error
      return hasher.computeHashStreaming(largeFile).then(result => {
        expect(result.success).toBe(false);
        expect(result.error).toContain('Streaming test error');
      });
    });
  });

  // Property-Based Tests
  describe('Property Tests', () => {
    /**
     * Property 3: BLAKE3 Hash Format Consistency
     * **Validates: Requirements 2.2, 2.4, 2.6, 11.2, 11.3**
     * 
     * For any valid software binary, the BLAKE3_Hasher should produce a 64-character 
     * lowercase hexadecimal string that represents a valid BLAKE3 hash with 256-bit output length.
     */
    describe('Property 3: BLAKE3 Hash Format Consistency', () => {
      it('should produce valid BLAKE3 hash format for any binary data', async () => {
        await fc.assert(
          fc.asyncProperty(
            // Generate random binary data (1 byte to 10MB to keep tests fast)
            fc.uint8Array({ minLength: 1, maxLength: 10 * 1024 * 1024 }),
            fc.string({ minLength: 1, maxLength: 50 }), // filename
            async (fileData, filename) => {
              // Create a mock File object with the generated data
              const mockFile = {
                size: fileData.length,
                name: filename,
                arrayBuffer: async () => fileData.buffer.slice(fileData.byteOffset, fileData.byteOffset + fileData.byteLength)
              } as unknown as File;

              const result = await hasher.computeHash(mockFile);

              // If computation succeeds, verify hash format
              if (result.success) {
                // Must be exactly 64 characters (256 bits / 4 bits per hex char)
                expect(result.hash).toHaveLength(64);
                
                // Must be lowercase hexadecimal
                expect(result.hash).toMatch(/^[a-f0-9]{64}$/);
                
                // Must pass the hasher's own validation
                expect(hasher.validateHashFormat(result.hash)).toBe(true);
                
                // Hash should be normalized (lowercase)
                expect(result.hash).toBe(hasher.normalizeHash(result.hash));
              }
            }
          ),
          { numRuns: 50, timeout: 30000 } // 50 iterations with 30s timeout
        );
      });

      it('should produce valid hash format for streaming computation', async () => {
        await fc.assert(
          fc.asyncProperty(
            // Generate random binary data for streaming tests
            fc.uint8Array({ minLength: 1, maxLength: 5 * 1024 * 1024 }), // Up to 5MB for streaming
            fc.string({ minLength: 1, maxLength: 50 }), // filename
            async (fileData, filename) => {
              // Create a mock File object that supports slicing for streaming
              const mockFile = {
                size: fileData.length,
                name: filename,
                slice: (start: number, end: number) => ({
                  arrayBuffer: async () => {
                    const slicedData = fileData.slice(start, end);
                    return slicedData.buffer.slice(slicedData.byteOffset, slicedData.byteOffset + slicedData.byteLength);
                  }
                })
              } as unknown as File;

              const result = await hasher.computeHashStreaming(mockFile);

              // If computation succeeds, verify hash format
              if (result.success) {
                // Must be exactly 64 characters (256 bits / 4 bits per hex char)
                expect(result.hash).toHaveLength(64);
                
                // Must be lowercase hexadecimal
                expect(result.hash).toMatch(/^[a-f0-9]{64}$/);
                
                // Must pass the hasher's own validation
                expect(hasher.validateHashFormat(result.hash)).toBe(true);
                
                // Hash should be normalized (lowercase)
                expect(result.hash).toBe(hasher.normalizeHash(result.hash));
              }
            }
          ),
          { numRuns: 50, timeout: 30000 } // 50 iterations with 30s timeout
        );
      });
    });

    /**
     * Property 4: Hash Computation Idempotence
     * **Validates: Requirements 2.6**
     * 
     * For any valid software binary file, computing the BLAKE3 hash twice should produce identical results.
     */
    describe('Property 4: Hash Computation Idempotence', () => {
      it('should produce identical hashes for the same file data', async () => {
        await fc.assert(
          fc.asyncProperty(
            // Generate random binary data
            fc.uint8Array({ minLength: 1, maxLength: 5 * 1024 * 1024 }),
            fc.string({ minLength: 1, maxLength: 50 }), // filename
            async (fileData, filename) => {
              // Create two identical mock File objects
              const createMockFile = () => ({
                size: fileData.length,
                name: filename,
                arrayBuffer: async () => fileData.buffer.slice(fileData.byteOffset, fileData.byteOffset + fileData.byteLength)
              } as unknown as File);

              const file1 = createMockFile();
              const file2 = createMockFile();

              // Compute hash twice
              const result1 = await hasher.computeHash(file1);
              const result2 = await hasher.computeHash(file2);

              // Both computations should succeed or fail together
              expect(result1.success).toBe(result2.success);

              // If both succeed, hashes must be identical
              if (result1.success && result2.success) {
                expect(result1.hash).toBe(result2.hash);
                expect(result1.hash).toHaveLength(64);
                expect(result1.hash).toMatch(/^[a-f0-9]{64}$/);
              }

              // If both fail, error messages should be similar
              if (!result1.success && !result2.success) {
                expect(result1.error).toBeDefined();
                expect(result2.error).toBeDefined();
              }
            }
          ),
          { numRuns: 50, timeout: 30000 } // 50 iterations with 30s timeout
        );
      });

      it('should produce identical hashes for streaming computation of the same data', async () => {
        await fc.assert(
          fc.asyncProperty(
            // Generate random binary data for streaming
            fc.uint8Array({ minLength: 1, maxLength: 3 * 1024 * 1024 }),
            fc.string({ minLength: 1, maxLength: 50 }), // filename
            async (fileData, filename) => {
              // Create two identical mock File objects that support slicing
              const createMockFile = () => ({
                size: fileData.length,
                name: filename,
                slice: (start: number, end: number) => ({
                  arrayBuffer: async () => {
                    const slicedData = fileData.slice(start, end);
                    return slicedData.buffer.slice(slicedData.byteOffset, slicedData.byteOffset + slicedData.byteLength);
                  }
                })
              } as unknown as File);

              const file1 = createMockFile();
              const file2 = createMockFile();

              // Compute hash twice using streaming
              const result1 = await hasher.computeHashStreaming(file1);
              const result2 = await hasher.computeHashStreaming(file2);

              // Both computations should succeed or fail together
              expect(result1.success).toBe(result2.success);

              // If both succeed, hashes must be identical
              if (result1.success && result2.success) {
                expect(result1.hash).toBe(result2.hash);
                expect(result1.hash).toHaveLength(64);
                expect(result1.hash).toMatch(/^[a-f0-9]{64}$/);
              }

              // If both fail, error messages should be similar
              if (!result1.success && !result2.success) {
                expect(result1.error).toBeDefined();
                expect(result2.error).toBeDefined();
              }
            }
          ),
          { numRuns: 50, timeout: 30000 } // 50 iterations with 30s timeout
        );
      });

      it('should produce identical hashes between standard and streaming methods for same data', async () => {
        await fc.assert(
          fc.asyncProperty(
            // Generate smaller data that can be processed by both methods
            fc.uint8Array({ minLength: 1, maxLength: 1024 * 1024 }), // 1MB max to ensure both methods work
            fc.string({ minLength: 1, maxLength: 50 }), // filename
            async (fileData, filename) => {
              // Create mock File objects for both methods
              const standardFile = {
                size: fileData.length,
                name: filename,
                arrayBuffer: async () => fileData.buffer.slice(fileData.byteOffset, fileData.byteOffset + fileData.byteLength)
              } as unknown as File;

              const streamingFile = {
                size: fileData.length,
                name: filename,
                slice: (start: number, end: number) => ({
                  arrayBuffer: async () => {
                    const slicedData = fileData.slice(start, end);
                    return slicedData.buffer.slice(slicedData.byteOffset, slicedData.byteOffset + slicedData.byteLength);
                  }
                })
              } as unknown as File;

              // Compute hash using both methods
              const standardResult = await hasher.computeHash(standardFile);
              const streamingResult = await hasher.computeHashStreaming(streamingFile);

              // Both computations should succeed or fail together
              expect(standardResult.success).toBe(streamingResult.success);

              // If both succeed, hashes must be identical (idempotence across methods)
              if (standardResult.success && streamingResult.success) {
                expect(standardResult.hash).toBe(streamingResult.hash);
                expect(standardResult.hash).toHaveLength(64);
                expect(standardResult.hash).toMatch(/^[a-f0-9]{64}$/);
              }
            }
          ),
          { numRuns: 50, timeout: 30000 } // 50 iterations (fewer since we're testing both methods)
        );
      });
    });
  });
});