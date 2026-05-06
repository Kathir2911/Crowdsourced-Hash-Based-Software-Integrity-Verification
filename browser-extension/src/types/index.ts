/**
 * Core data types for the Crowdsourced Hash Verification System
 */

export interface SoftwareIdentity {
  sourceDomain: string;
  normalizedFilename: string;
  fileSize: number;  // Changed from 'size' to match backend
  identityHash?: string; // Computed hash of sourceDomain+normalizedFilename+fileSize
}

export interface FileMetadata {
  filename: string;
  size: number;
  lastModified: Date;
  sourceDomain?: string;
}

export interface FileValidationResult {
  isExecutable: boolean;
  fileType: string;
  warnings: string[];
}

export interface HashResult {
  hash: string;
  success: boolean;
  error?: string;
  processingTime: number;
}

export interface HashSubmission {
  id?: string;
  softwareIdentity: SoftwareIdentity;
  hash: string;
  timestamp: Date;
  clientVersion: string;
  replayProtectionHash: string;
  userAgent?: string;
}

export interface VerificationRequest {
  softwareIdentity: SoftwareIdentity;
  hash: string;
}

export interface VerificationResult {
  status: TamperStatus;
  confidence: number;
  submissionCount: number;
  consensusHash?: string;
  message: string;
  timestamp: Date;
  suspicionLevel: SuspicionLevel;
  recommendedAction: string;
}

export interface SubmissionResult {
  success: boolean;
  message: string;
  submissionId?: string;
  error?: string;
}

export interface ServiceStatus {
  available: boolean;
  version: string;
  message?: string;
}

export enum TamperStatus {
  VERIFIED = 'VERIFIED',
  TAMPERED = 'TAMPERED',
  SUSPICIOUS_LOW_CONFIDENCE = 'SUSPICIOUS_LOW_CONFIDENCE',
  UNKNOWN = 'UNKNOWN',
  SERVICE_UNAVAILABLE = 'SERVICE_UNAVAILABLE'
}

export enum SuspicionLevel {
  NONE = 0,
  LOW = 1,
  MEDIUM = 2,
  HIGH = 3,
  CRITICAL = 4
}

export interface ConsensusResult {
  softwareIdentity: SoftwareIdentity;
  consensusHash?: string;
  confidence: number;
  submissionCount: number;
  status: ConsensusStatus;
  lifecycle: ConsensusLifecycle;
  lastUpdated: Date;
  hashDistribution: Map<string, number>;
  oldestSubmission: Date;
  newestSubmission: Date;
}

export enum ConsensusStatus {
  ESTABLISHED = 'ESTABLISHED',
  INSUFFICIENT_DATA = 'INSUFFICIENT_DATA',
  NO_CONSENSUS = 'NO_CONSENSUS',
  EXPIRED = 'EXPIRED'
}

export enum ConsensusLifecycle {
  UNKNOWN = 'UNKNOWN',
  LOW_CONFIDENCE = 'LOW_CONFIDENCE',
  ESTABLISHED = 'ESTABLISHED',
  DEGRADED = 'DEGRADED',
  EXPIRED = 'EXPIRED'
}

// Configuration types
export interface ExtensionConfig {
  apiBaseUrl: string;
  enabledFileTypes: string[];
  maxFileSize: number;
  timeoutMs: number;
  retryAttempts: number;
}

// API response types
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
  timestamp: string;
}

// Progress callback type
export type ProgressCallback = (progress: number) => void;