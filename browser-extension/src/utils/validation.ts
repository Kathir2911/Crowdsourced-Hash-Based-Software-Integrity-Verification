/**
 * Validation utilities for data integrity in the browser extension
 */

import { SoftwareIdentity, HashSubmission, FileMetadata } from '../types';

export interface ValidationResult {
  valid: boolean;
  errorMessage?: string;
}

/**
 * BLAKE3 hash pattern: 64-character lowercase hexadecimal
 */
const BLAKE3_HASH_PATTERN = /^[a-f0-9]{64}$/;

/**
 * Domain pattern: basic domain validation
 */
const DOMAIN_PATTERN = /^[a-zA-Z0-9]([a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?\.[a-zA-Z]{2,}$/;

/**
 * Filename pattern: basic filename validation (no path separators)
 */
const FILENAME_PATTERN = /^[^/\\:*?"<>|]+$/;

/**
 * Executable file extensions supported by the system
 */
const EXECUTABLE_EXTENSIONS = ['.exe', '.msi', '.dmg', '.deb', '.rpm', '.appimage'];

/**
 * Validate BLAKE3 hash format
 */
export function validateBlake3Hash(hash: string): ValidationResult {
  if (!hash || hash.trim().length === 0) {
    return { valid: false, errorMessage: 'Hash cannot be null or empty' };
  }
  
  const trimmedHash = hash.trim().toLowerCase();
  if (!BLAKE3_HASH_PATTERN.test(trimmedHash)) {
    return { valid: false, errorMessage: 'Hash must be a 64-character lowercase hexadecimal string' };
  }
  
  return { valid: true };
}

/**
 * Validate software identity data integrity
 */
export function validateSoftwareIdentity(identity: SoftwareIdentity): ValidationResult {
  const errors: string[] = [];
  
  if (!identity) {
    return { valid: false, errorMessage: 'Software identity cannot be null' };
  }
  
  // Validate source domain
  if (!identity.sourceDomain || identity.sourceDomain.trim().length === 0) {
    errors.push('Source domain cannot be null or empty');
  } else if (!DOMAIN_PATTERN.test(identity.sourceDomain.trim())) {
    errors.push('Source domain format is invalid');
  }
  
  // Validate normalized filename
  if (!identity.normalizedFilename || identity.normalizedFilename.trim().length === 0) {
    errors.push('Normalized filename cannot be null or empty');
  } else if (!FILENAME_PATTERN.test(identity.normalizedFilename)) {
    errors.push('Filename contains invalid characters');
  }
  
  // Validate file size
  if (!identity.size || identity.size <= 0) {
    errors.push('File size must be positive');
  }
  
  if (errors.length === 0) {
    return { valid: true };
  } else {
    return { valid: false, errorMessage: errors.join('; ') };
  }
}

/**
 * Validate hash submission data integrity
 */
export function validateHashSubmission(submission: HashSubmission): ValidationResult {
  const errors: string[] = [];
  
  if (!submission) {
    return { valid: false, errorMessage: 'Hash submission cannot be null' };
  }
  
  // Validate software identity
  const identityResult = validateSoftwareIdentity(submission.softwareIdentity);
  if (!identityResult.valid) {
    errors.push(`Software identity validation failed: ${identityResult.errorMessage}`);
  }
  
  // Validate hash
  const hashResult = validateBlake3Hash(submission.hash);
  if (!hashResult.valid) {
    errors.push(`Hash validation failed: ${hashResult.errorMessage}`);
  }
  
  // Validate timestamp
  if (!submission.timestamp) {
    errors.push('Timestamp cannot be null');
  }
  
  // Validate client version
  if (submission.clientVersion && submission.clientVersion.length > 32) {
    errors.push('Client version must not exceed 32 characters');
  }
  
  // Validate replay protection hash
  if (!submission.replayProtectionHash || submission.replayProtectionHash.trim().length === 0) {
    errors.push('Replay protection hash cannot be null or empty');
  }
  
  if (errors.length === 0) {
    return { valid: true };
  } else {
    return { valid: false, errorMessage: errors.join('; ') };
  }
}

/**
 * Validate file metadata
 */
export function validateFileMetadata(metadata: FileMetadata): ValidationResult {
  const errors: string[] = [];
  
  if (!metadata) {
    return { valid: false, errorMessage: 'File metadata cannot be null' };
  }
  
  // Validate filename
  if (!metadata.filename || metadata.filename.trim().length === 0) {
    errors.push('Filename cannot be null or empty');
  }
  
  // Validate file size
  if (!metadata.size || metadata.size <= 0) {
    errors.push('File size must be positive');
  }
  
  // Validate last modified date
  if (!metadata.lastModified) {
    errors.push('Last modified date cannot be null');
  }
  
  if (errors.length === 0) {
    return { valid: true };
  } else {
    return { valid: false, errorMessage: errors.join('; ') };
  }
}

/**
 * Check if file is an executable type
 */
export function isExecutableFile(filename: string): boolean {
  if (!filename) {
    return false;
  }
  
  const lowerFilename = filename.toLowerCase();
  return EXECUTABLE_EXTENSIONS.some(ext => lowerFilename.endsWith(ext));
}

/**
 * Get file extension from filename
 */
export function getFileExtension(filename: string): string {
  if (!filename) {
    return '';
  }
  
  const lastDot = filename.lastIndexOf('.');
  if (lastDot === -1) {
    return '';
  }
  
  return filename.substring(lastDot).toLowerCase();
}

/**
 * Normalize filename for consistent identity generation
 */
export function normalizeFilename(filename: string): string {
  if (!filename) {
    return '';
  }
  
  // Convert to lowercase and trim
  let normalized = filename.trim().toLowerCase();
  
  // Remove path components if present
  const lastSlash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
  if (lastSlash >= 0) {
    normalized = normalized.substring(lastSlash + 1);
  }
  
  return normalized;
}

/**
 * Normalize domain for consistent identity generation
 */
export function normalizeDomain(domain: string): string {
  if (!domain) {
    return '';
  }
  
  // Convert to lowercase and trim
  let normalized = domain.trim().toLowerCase();
  
  // Remove protocol if present
  if (normalized.startsWith('http://')) {
    normalized = normalized.substring(7);
  } else if (normalized.startsWith('https://')) {
    normalized = normalized.substring(8);
  }
  
  // Remove www. prefix if present
  if (normalized.startsWith('www.')) {
    normalized = normalized.substring(4);
  }
  
  // Remove trailing slash if present
  if (normalized.endsWith('/')) {
    normalized = normalized.substring(0, normalized.length - 1);
  }
  
  return normalized;
}

/**
 * Generate identity hash for software identity
 */
export function generateIdentityHash(identity: SoftwareIdentity): string {
  const identityString = `${identity.sourceDomain}|${identity.normalizedFilename}|${identity.size}`;
  
  // Simple hash function for browser compatibility (not cryptographic)
  let hash = 0;
  for (let i = 0; i < identityString.length; i++) {
    const char = identityString.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash = hash & hash; // Convert to 32-bit integer
  }
  
  return Math.abs(hash).toString(16);
}

/**
 * Validate that identity hash matches the computed hash
 */
export function validateIdentityHashConsistency(identity: SoftwareIdentity): ValidationResult {
  if (!identity) {
    return { valid: false, errorMessage: 'Software identity cannot be null' };
  }
  
  const computedHash = generateIdentityHash(identity);
  
  if (!computedHash || computedHash.trim().length === 0) {
    return { valid: false, errorMessage: 'Generated identity hash cannot be null or empty' };
  }
  
  // If identity has an identityHash field, validate it matches
  if (identity.identityHash && identity.identityHash !== computedHash) {
    return { valid: false, errorMessage: 'Identity hash does not match computed hash' };
  }
  
  return { valid: true };
}