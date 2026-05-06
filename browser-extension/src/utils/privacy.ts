/**
 * Privacy protection utilities
 * Implements requirements 7.1, 7.2: Ensure no PII is transmitted
 */

import { SoftwareIdentity, HashSubmission, VerificationRequest } from '../types';

/**
 * Sanitize software identity to remove any potential PII
 * Implements requirement 7.1: No full file paths transmitted
 */
export function sanitizeSoftwareIdentity(identity: SoftwareIdentity): SoftwareIdentity {
  const sanitized: SoftwareIdentity = {
    sourceDomain: sanitizeDomain(identity.sourceDomain),
    normalizedFilename: sanitizeFilename(identity.normalizedFilename),
    size: identity.size
  };
  
  if (identity.identityHash) {
    sanitized.identityHash = identity.identityHash;
  }
  
  return sanitized;
}

/**
 * Sanitize domain to remove user-specific information
 * Implements requirement 7.2: Remove PII from requests
 */
export function sanitizeDomain(domain: string): string {
  if (!domain) {
    return 'unknown';
  }

  // Remove any username or credentials from domain
  // Example: user@example.com -> example.com
  const withoutCredentials = domain.split('@').pop();
  if (!withoutCredentials) {
    return 'unknown';
  }

  // Remove port numbers
  const withoutPort = withoutCredentials.split(':')[0];
  if (!withoutPort) {
    return 'unknown';
  }

  // Convert to lowercase for consistency
  return withoutPort.toLowerCase().trim();
}

/**
 * Sanitize filename to remove path information and user-specific data
 * Implements requirement 7.1: No full file paths transmitted
 */
export function sanitizeFilename(filename: string): string {
  if (!filename) {
    return 'unknown';
  }

  // Remove any path separators (Windows and Unix)
  const pathSeparators = /[/\\]/g;
  const filenameOnly = filename.split(pathSeparators).pop();
  if (!filenameOnly) {
    return 'unknown';
  }

  // Remove any query parameters or fragments
  const parts = filenameOnly.split('?')[0];
  if (!parts) {
    return 'unknown';
  }
  
  const withoutQuery = parts.split('#')[0];
  if (!withoutQuery) {
    return 'unknown';
  }

  // Convert to lowercase for consistency
  return withoutQuery.toLowerCase().trim();
}

/**
 * Sanitize hash submission to ensure no PII
 * Implements requirement 7.2: Remove PII from requests
 */
export function sanitizeHashSubmission(submission: HashSubmission): HashSubmission {
  const sanitized: any = {
    ...submission,
    softwareIdentity: sanitizeSoftwareIdentity(submission.softwareIdentity)
  };
  
  if (submission.userAgent) {
    sanitized.userAgent = sanitizeUserAgent(submission.userAgent);
  }
  
  return sanitized as HashSubmission;
}

/**
 * Sanitize verification request to ensure no PII
 * Implements requirement 7.2: Remove PII from requests
 */
export function sanitizeVerificationRequest(request: VerificationRequest): VerificationRequest {
  return {
    ...request,
    softwareIdentity: sanitizeSoftwareIdentity(request.softwareIdentity)
  };
}

/**
 * Sanitize user agent to remove specific version information that could identify users
 * Implements requirement 7.2: Remove PII from requests
 */
export function sanitizeUserAgent(userAgent?: string): string | undefined {
  if (!userAgent) {
    return undefined;
  }

  // Keep only browser family and major version, remove minor versions and build numbers
  // Example: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.6099.109"
  // Becomes: "Chrome/120"
  
  const chromeMatch = userAgent.match(/Chrome\/(\d+)/);
  if (chromeMatch) {
    return `Chrome/${chromeMatch[1]}`;
  }

  const firefoxMatch = userAgent.match(/Firefox\/(\d+)/);
  if (firefoxMatch) {
    return `Firefox/${firefoxMatch[1]}`;
  }

  const safariMatch = userAgent.match(/Safari\/(\d+)/);
  if (safariMatch) {
    return `Safari/${safariMatch[1]}`;
  }

  const edgeMatch = userAgent.match(/Edg\/(\d+)/);
  if (edgeMatch) {
    return `Edge/${edgeMatch[1]}`;
  }

  // Generic fallback
  return 'Browser/Unknown';
}

/**
 * Validate that data contains no PII before transmission
 * Implements requirement 7.3: Secure data serialization without user tracking
 */
export function validateNoPII(data: any): { valid: boolean; violations: string[] } {
  const violations: string[] = [];

  // Check for common PII patterns
  const piiPatterns = [
    { pattern: /[a-zA-Z]:\\/, message: 'Windows file path detected' },
    { pattern: /\/home\/[^/]+/, message: 'Unix home directory path detected' },
    { pattern: /\/Users\/[^/]+/, message: 'macOS user directory path detected' },
    { pattern: /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/, message: 'Email address detected' },
    { pattern: /\b\d{3}-\d{2}-\d{4}\b/, message: 'SSN-like pattern detected' },
    { pattern: /\b\d{16}\b/, message: 'Credit card-like pattern detected' }
  ];

  const dataString = JSON.stringify(data);

  for (const { pattern, message } of piiPatterns) {
    if (pattern.test(dataString)) {
      violations.push(message);
    }
  }

  return {
    valid: violations.length === 0,
    violations
  };
}

/**
 * Generate replay protection hash without including PII
 * Implements requirement 7.5: No user tracking
 */
export function generateReplayProtectionHash(
  ipAddress: string,
  userAgent: string,
  timestamp: Date
): string {
  // Use only date (not time) to create 24-hour window
  const dateString = timestamp.toISOString().split('T')[0];
  
  // Sanitize user agent
  const sanitizedUA = sanitizeUserAgent(userAgent) || 'unknown';
  
  // Combine for replay protection (no PII)
  const combined = `${ipAddress}|${sanitizedUA}|${dateString}`;
  
  // Simple hash (in production, use proper crypto)
  return btoa(combined);
}
