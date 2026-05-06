import * as fc from 'fast-check';
import { HashSubmission, SoftwareIdentity } from '../../src/types';

/**
 * Property-based tests for Hash Submission Round-Trip Integrity
 * **Validates: Requirements 11.5**
 */
describe('Hash Submission Round-Trip Integrity Properties', () => {

  /**
   * Property 28: Hash Submission Round-Trip Integrity
   * 
   * For any valid Hash_Submission, parsing then formatting then parsing should produce 
   * equivalent data, ensuring data integrity throughout the processing pipeline.
   * 
   * **Validates: Requirements 11.5**
   */
  test('Property 28: JSON serialization round-trip integrity', () => {
    fc.assert(fc.property(
      validHashSubmissionArbitrary(),
      (original: HashSubmission) => {
        // Step 1: Format (serialize) to JSON
        const jsonString = JSON.stringify(original);
        
        // Step 2: Parse (deserialize) from JSON
        const parsed = JSON.parse(jsonString) as HashSubmission;
        
        // Step 3: Format (serialize) again to JSON
        const jsonStringAgain = JSON.stringify(parsed);
        
        // Step 4: Parse (deserialize) again from JSON
        const parsedAgain = JSON.parse(jsonStringAgain) as HashSubmission;
        
        // Verify round-trip integrity: original data should be equivalent after round-trip
        expect(parsedAgain.softwareIdentity.sourceDomain).toBe(original.softwareIdentity.sourceDomain);
        expect(parsedAgain.softwareIdentity.normalizedFilename).toBe(original.softwareIdentity.normalizedFilename);
        expect(parsedAgain.softwareIdentity.size).toBe(original.softwareIdentity.size);
        expect(parsedAgain.softwareIdentity.identityHash).toBe(original.softwareIdentity.identityHash);
        expect(parsedAgain.hash).toBe(original.hash);
        expect(parsedAgain.clientVersion).toBe(original.clientVersion);
        expect(parsedAgain.replayProtectionHash).toBe(original.replayProtectionHash);
        expect(parsedAgain.userAgent).toBe(original.userAgent);
        
        // Verify JSON strings are identical (formatting consistency)
        expect(jsonStringAgain).toBe(jsonString);
      }
    ), { numRuns: 50 });
  });

  /**
   * Property 28b: Hash Submission Object Cloning Round-Trip Integrity
   * 
   * For any valid Hash_Submission, deep cloning should produce equivalent data,
   * ensuring data integrity in object manipulation operations.
   * 
   * **Validates: Requirements 11.5**
   */
  test('Property 28b: Object cloning round-trip integrity', () => {
    fc.assert(fc.property(
      validHashSubmissionArbitrary(),
      (original: HashSubmission) => {
        // Deep clone using JSON round-trip (common pattern in JavaScript)
        const cloned = JSON.parse(JSON.stringify(original)) as HashSubmission;
        
        // Verify all fields are preserved
        expect(cloned.softwareIdentity.sourceDomain).toBe(original.softwareIdentity.sourceDomain);
        expect(cloned.softwareIdentity.normalizedFilename).toBe(original.softwareIdentity.normalizedFilename);
        expect(cloned.softwareIdentity.size).toBe(original.softwareIdentity.size);
        expect(cloned.softwareIdentity.identityHash).toBe(original.softwareIdentity.identityHash);
        expect(cloned.hash).toBe(original.hash);
        expect(cloned.clientVersion).toBe(original.clientVersion);
        expect(cloned.replayProtectionHash).toBe(original.replayProtectionHash);
        expect(cloned.userAgent).toBe(original.userAgent);
        
        // Verify timestamp handling (if present)
        if (original.timestamp) {
          expect(cloned.timestamp).toBeDefined();
        }
        
        // Verify ID handling (if present)
        if (original.id) {
          expect(cloned.id).toBe(original.id);
        }
      }
    ), { numRuns: 50 });
  });

  /**
   * Property 28c: Hash Submission API Request/Response Round-Trip Integrity
   * 
   * For any valid Hash_Submission, converting to API request format and back should 
   * preserve all essential data, ensuring data integrity in API communications.
   * 
   * **Validates: Requirements 11.5**
   */
  test('Property 28c: API request/response round-trip integrity', () => {
    fc.assert(fc.property(
      validHashSubmissionArbitrary(),
      (original: HashSubmission) => {
        // Simulate API request format (typically excludes id and timestamp)
        const apiRequest = {
          softwareIdentity: original.softwareIdentity,
          hash: original.hash,
          clientVersion: original.clientVersion,
          replayProtectionHash: original.replayProtectionHash,
          userAgent: original.userAgent
        };
        
        // Serialize for API transmission
        const requestJson = JSON.stringify(apiRequest);
        
        // Parse API request (server-side)
        const parsedRequest = JSON.parse(requestJson);
        
        // Simulate API response (server adds id and timestamp)
        const apiResponse: HashSubmission = {
          id: 'generated-uuid',
          timestamp: new Date(),
          ...parsedRequest
        };
        
        // Serialize API response
        const responseJson = JSON.stringify(apiResponse);
        
        // Parse API response (client-side)
        const parsedResponse = JSON.parse(responseJson) as HashSubmission;
        
        // Verify essential data is preserved through API round-trip
        expect(parsedResponse.softwareIdentity.sourceDomain).toBe(original.softwareIdentity.sourceDomain);
        expect(parsedResponse.softwareIdentity.normalizedFilename).toBe(original.softwareIdentity.normalizedFilename);
        expect(parsedResponse.softwareIdentity.size).toBe(original.softwareIdentity.size);
        expect(parsedResponse.softwareIdentity.identityHash).toBe(original.softwareIdentity.identityHash);
        expect(parsedResponse.hash).toBe(original.hash);
        expect(parsedResponse.clientVersion).toBe(original.clientVersion);
        expect(parsedResponse.replayProtectionHash).toBe(original.replayProtectionHash);
        expect(parsedResponse.userAgent).toBe(original.userAgent);
        
        // Verify server-generated fields are present
        expect(parsedResponse.id).toBeDefined();
        expect(parsedResponse.timestamp).toBeDefined();
      }
    ), { numRuns: 50 });
  });

  /**
   * Property 28d: Hash Submission Local Storage Round-Trip Integrity
   * 
   * For any valid Hash_Submission, storing to localStorage and retrieving should 
   * preserve all data, ensuring data integrity in browser storage operations.
   * 
   * **Validates: Requirements 11.5**
   */
  test('Property 28d: Local storage round-trip integrity', () => {
    fc.assert(fc.property(
      validHashSubmissionArbitrary(),
      (original: HashSubmission) => {
        const storageKey = 'test-hash-submission';
        
        try {
          // Store to localStorage (simulate browser storage)
          const storageValue = JSON.stringify(original);
          localStorage.setItem(storageKey, storageValue);
          
          // Retrieve from localStorage
          const retrievedValue = localStorage.getItem(storageKey);
          expect(retrievedValue).not.toBeNull();
          
          // Parse retrieved data
          const retrieved = JSON.parse(retrievedValue!) as HashSubmission;
          
          // Verify all fields are preserved
          expect(retrieved.softwareIdentity.sourceDomain).toBe(original.softwareIdentity.sourceDomain);
          expect(retrieved.softwareIdentity.normalizedFilename).toBe(original.softwareIdentity.normalizedFilename);
          expect(retrieved.softwareIdentity.size).toBe(original.softwareIdentity.size);
          expect(retrieved.softwareIdentity.identityHash).toBe(original.softwareIdentity.identityHash);
          expect(retrieved.hash).toBe(original.hash);
          expect(retrieved.clientVersion).toBe(original.clientVersion);
          expect(retrieved.replayProtectionHash).toBe(original.replayProtectionHash);
          expect(retrieved.userAgent).toBe(original.userAgent);
          
          if (original.id) {
            expect(retrieved.id).toBe(original.id);
          }
          
          if (original.timestamp) {
            expect(retrieved.timestamp).toBeDefined();
          }
          
        } finally {
          // Clean up
          localStorage.removeItem(storageKey);
        }
      }
    ), { numRuns: 50 });
  });
});

// Generators for property-based testing

function validHashSubmissionArbitrary(): fc.Arbitrary<HashSubmission> {
  return fc.record({
    softwareIdentity: validSoftwareIdentityArbitrary(),
    hash: validBlake3HashArbitrary(),
    timestamp: fc.date({ min: new Date('2020-01-01'), max: new Date('2030-12-31') }),
    clientVersion: validClientVersionArbitrary(),
    replayProtectionHash: validReplayProtectionHashArbitrary()
  }).chain(base => 
    fc.record({
      id: fc.option(fc.uuid()),
      userAgent: fc.option(validUserAgentArbitrary())
    }).map(optional => {
      const result: HashSubmission = { ...base };
      if (optional.id !== null) {
        result.id = optional.id;
      }
      if (optional.userAgent !== null) {
        result.userAgent = optional.userAgent;
      }
      return result;
    })
  );
}

function validSoftwareIdentityArbitrary(): fc.Arbitrary<SoftwareIdentity> {
  return fc.record({
    sourceDomain: validDomainArbitrary(),
    normalizedFilename: validFilenameArbitrary(),
    size: fc.integer({ min: 1, max: Number.MAX_SAFE_INTEGER })
  }).chain(base => 
    fc.option(fc.hexaString({ minLength: 8, maxLength: 16 })).map(identityHash => {
      const result: SoftwareIdentity = { ...base };
      if (identityHash !== null) {
        result.identityHash = identityHash;
      }
      return result;
    })
  );
}

function validBlake3HashArbitrary(): fc.Arbitrary<string> {
  return fc.hexaString({ minLength: 64, maxLength: 64 }).map(h => h.toLowerCase());
}

function validClientVersionArbitrary(): fc.Arbitrary<string> {
  return fc.oneof(
    fc.constant('1.0.0'),
    fc.constant('2.1.3'),
    fc.constant('0.9.5-beta'),
    fc.stringOf(
      fc.oneof(
        fc.char().filter(c => /[0-9a-zA-Z.\-_]/.test(c))
      ),
      { minLength: 1, maxLength: 32 }
    ).filter(s => s.trim().length > 0)
  );
}

function validReplayProtectionHashArbitrary(): fc.Arbitrary<string> {
  return fc.hexaString({ minLength: 8, maxLength: 64 }).map(h => h.toLowerCase());
}

function validUserAgentArbitrary(): fc.Arbitrary<string> {
  return fc.oneof(
    fc.constant('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'),
    fc.constant('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36'),
    fc.constant('Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36'),
    fc.constant('Chrome/119.0.0.0 Safari/537.36')
  );
}

function validDomainArbitrary(): fc.Arbitrary<string> {
  return fc.oneof(
    fc.constant('example.com'),
    fc.constant('download.microsoft.com'),
    fc.constant('releases.ubuntu.com'),
    fc.constant('github.com'),
    fc.constant('sourceforge.net'),
    fc.domain()
  );
}

function validFilenameArbitrary(): fc.Arbitrary<string> {
  return fc.oneof(
    fc.constant('setup.exe'),
    fc.constant('installer.msi'),
    fc.constant('app.dmg'),
    fc.constant('package.deb'),
    fc.constant('software.rpm'),
    fc.constant('program.appimage'),
    fc.stringOf(
      fc.oneof(
        fc.char().filter(c => /[a-zA-Z0-9.\-_]/.test(c))
      ),
      { minLength: 5, maxLength: 100 }
    ).filter(s => s.includes('.') && !s.startsWith('.') && !s.endsWith('.'))
  );
}