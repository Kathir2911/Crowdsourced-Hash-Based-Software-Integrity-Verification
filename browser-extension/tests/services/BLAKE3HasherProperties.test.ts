/**
 * Property-based tests for BLAKE3Hasher
 * **Validates: Requirements 2.2, 2.4, 2.6, 11.2, 11.3**
 */

import * as fc from 'fast-check';
import { BLAKE3Hasher } from '../../src/services/BLAKE3Hasher';

describe('BLAKE3Hasher Property Tests', () => {
  let hasher: BLAKE3Hasher;

  beforeEach(() => {
    hasher = new BLAKE3Hasher();
  });

  /**
   * Property 3: BLAKE3 Hash Format Consistency
   * 
   * For any valid software binary, the BLAKE3_Hasher should produce a 64-character 
   * lowercase hexadecimal string that represents a valid BLAKE3 hash with 256-bit output length.
   * 
   * **Validates: Requirements 2.2, 2.4, 11.2, 11.3**
   */
  test('Property 3: BLAKE3 hash format consistency', async () => {
    await fc.assert(fc.asyncProperty(
      validFileDataArbitrary(),
      async (fileData: Uint8Array) => {
        // Create a mock file from the generated data
        const mockFile = createMockFile(fileData, 'test.exe');
        
        // Compute hash using BLAKE3Hasher
        const result = await hasher.computeHash(mockFile);
        
        // Verify the operation succeeded
        expect(result.success).toBe(true);
        expect(result.error).toBeUndefined();
        
        // Verify hash format consistency
        expect(result.hash).toMatch(/^[a-f0-9]{64}$/); // 64-character lowercase hex
        expect(result.hash.length).toBe(64); // Exactly 64 characters
        expect(hasher.validateHashFormat(result.hash)).toBe(true); // Passes validation
        
        // Verify it's lowercase (no uppercase characters)
        expect(result.hash).toBe(result.hash.toLowerCase());
        
        // Verify processing time is recorded
        expect(result.processingTime).toBeGreaterThanOrEqual(0);
      }
    ), { numRuns: 50 });
  });

  /**
   * Property 4: Hash Computation Idempotence
   * 
   * For any valid software binary file, computing the BLAKE3 hash twice should produce 
   * identical results.
   * 
   * **Validates: Requirements 2.6**
   */
  test('Property 4: Hash computation idempotence', async () => {
    await fc.assert(fc.asyncProperty(
      validFileDataArbitrary(),
      async (fileData: Uint8Array) => {
        // Create a mock file from the generated data
        const mockFile = createMockFile(fileData, 'test.exe');
        
        // Compute hash twice
        const result1 = await hasher.computeHash(mockFile);
        const result2 = await hasher.computeHash(mockFile);
        
        // Verify both operations succeeded
        expect(result1.success).toBe(true);
        expect(result2.success).toBe(true);
        
        // Verify idempotence: identical inputs produce identical outputs
        expect(result1.hash).toBe(result2.hash);
        expect(result1.hash).toEqual(result2.hash);
        
        // Verify hash format is consistent across computations
        expect(hasher.validateHashFormat(result1.hash)).toBe(true);
        expect(hasher.validateHashFormat(result2.hash)).toBe(true);
      }
    ), { numRuns: 50 });
  });

  /**
   * Property 4b: Streaming Hash Computation Idempotence
   * 
   * For any valid software binary file, computing the BLAKE3 hash using streaming 
   * should produce identical results to standard computation.
   * 
   * **Validates: Requirements 2.6**
   */
  test('Property 4b: Streaming vs standard computation consistency', async () => {
    await fc.assert(fc.asyncProperty(
      validFileDataArbitrary(),
      async (fileData: Uint8Array) => {
        // Create a mock file from the generated data
        const mockFile = createMockFile(fileData, 'test.exe');
        
        // Compute hash using both methods
        const standardResult = await hasher.computeHash(mockFile);
        const streamingResult = await hasher.computeHashStreaming(mockFile);
        
        // Verify both operations succeeded
        expect(standardResult.success).toBe(true);
        expect(streamingResult.success).toBe(true);
        
        // Verify consistency: both methods produce identical hashes
        expect(standardResult.hash).toBe(streamingResult.hash);
        
        // Verify hash format is consistent across methods
        expect(hasher.validateHashFormat(standardResult.hash)).toBe(true);
        expect(hasher.validateHashFormat(streamingResult.hash)).toBe(true);
      }
    ), { numRuns: 50 }); // Fewer runs for streaming tests due to complexity
  });

  /**
   * Property 3b: Hash Format Validation Consistency
   * 
   * For any hash produced by BLAKE3Hasher, the validateHashFormat method should 
   * consistently return true.
   * 
   * **Validates: Requirements 11.2, 11.3**
   */
  test('Property 3b: Hash format validation consistency', async () => {
    await fc.assert(fc.asyncProperty(
      validFileDataArbitrary(),
      async (fileData: Uint8Array) => {
        // Create a mock file from the generated data
        const mockFile = createMockFile(fileData, 'test.exe');
        
        // Compute hash
        const result = await hasher.computeHash(mockFile);
        
        // Verify the operation succeeded
        expect(result.success).toBe(true);
        
        // Verify the hash passes validation
        expect(hasher.validateHashFormat(result.hash)).toBe(true);
        
        // Verify normalization is idempotent
        const normalized = hasher.normalizeHash(result.hash);
        expect(normalized).toBe(result.hash); // Already normalized
        expect(hasher.validateHashFormat(normalized)).toBe(true);
      }
    ), { numRuns: 50 });
  });

  /**
   * Property 3c: Hash Normalization Consistency
   * 
   * For any valid hash string (regardless of case), normalization should produce 
   * a valid lowercase hash that passes format validation.
   * 
   * **Validates: Requirements 11.3**
   */
  test('Property 3c: Hash normalization consistency', () => {
    fc.assert(fc.property(
      validHashStringArbitrary(),
      (hashString: string) => {
        // Normalize the hash
        const normalized = hasher.normalizeHash(hashString);
        
        // Verify normalization produces lowercase
        expect(normalized).toBe(hashString.toLowerCase());
        
        // If the original was a valid hash format, normalized should be too
        if (hasher.validateHashFormat(hashString.toLowerCase())) {
          expect(hasher.validateHashFormat(normalized)).toBe(true);
        }
      }
    ), { numRuns: 50 });
  });

  /**
   * Property 4c: Different Files Produce Different Hashes
   * 
   * For any two different file contents, BLAKE3 should produce different hashes
   * (cryptographic collision resistance property).
   * 
   * **Validates: Requirements 2.2**
   */
  test('Property 4c: Different files produce different hashes', async () => {
    await fc.assert(fc.asyncProperty(
      fc.tuple(validFileDataArbitrary(), validFileDataArbitrary())
        .filter(([data1, data2]) => !arraysEqual(data1, data2)), // Ensure different data
      async ([fileData1, fileData2]: [Uint8Array, Uint8Array]) => {
        // Create mock files from different data
        const mockFile1 = createMockFile(fileData1, 'test1.exe');
        const mockFile2 = createMockFile(fileData2, 'test2.exe');
        
        // Compute hashes
        const result1 = await hasher.computeHash(mockFile1);
        const result2 = await hasher.computeHash(mockFile2);
        
        // Verify both operations succeeded
        expect(result1.success).toBe(true);
        expect(result2.success).toBe(true);
        
        // Verify different inputs produce different hashes
        expect(result1.hash).not.toBe(result2.hash);
        
        // Verify both hashes are valid
        expect(hasher.validateHashFormat(result1.hash)).toBe(true);
        expect(hasher.validateHashFormat(result2.hash)).toBe(true);
      }
    ), { numRuns: 50 });
  });
});

// Generators for property-based testing

function validFileDataArbitrary(): fc.Arbitrary<Uint8Array> {
  return fc.uint8Array({ 
    minLength: 1, 
    maxLength: 10 * 1024 * 1024 // 10MB max for testing
  });
}

function validHashStringArbitrary(): fc.Arbitrary<string> {
  return fc.oneof(
    // Valid 64-character hex strings (mixed case)
    fc.hexaString({ minLength: 64, maxLength: 64 }),
    // Known valid BLAKE3 hashes
    fc.constantFrom(
      'af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9adc112b7cc9a93cae41f3262',
      'ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890',
      '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
    ),
    // Invalid formats for edge case testing
    fc.string({ minLength: 1, maxLength: 100 })
  );
}

function createMockFile(data: Uint8Array, filename: string): File {
  // Create a proper File object for testing
  // Use a simpler approach that works in Jest environment
  const buffer = Buffer.from(data);
  
  // Create a mock File object with the necessary methods
  const file = {
    name: filename,
    size: buffer.length,
    type: 'application/octet-stream',
    lastModified: Date.now(),
    arrayBuffer: async () => buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength),
    slice: (start?: number, end?: number) => {
      const slicedBuffer = buffer.slice(start, end);
      return {
        arrayBuffer: async () => slicedBuffer.buffer.slice(slicedBuffer.byteOffset, slicedBuffer.byteOffset + slicedBuffer.byteLength)
      };
    }
  } as File;
  
  return file;
}

function arraysEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) return false;
  }
  return true;
}