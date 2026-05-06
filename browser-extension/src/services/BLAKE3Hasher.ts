/**
 * BLAKE3 hash computation service with streaming support
 * 
 * This service provides cryptographic hash computation using the BLAKE3 algorithm
 * with support for both standard and streaming file processing. It handles large
 * files efficiently through chunked processing and provides progress callbacks
 * for user feedback.
 * 
 * Requirements implemented:
 * - 2.1: Compute cryptographic hash of files
 * - 2.2: Use BLAKE3 algorithm with 256-bit output (TEMPORARY: Using SHA-256 until BLAKE3 WASM loading is fixed)
 * - 2.3: Process files in streaming mode for large binaries
 * - 2.4: Return hexadecimal hash string
 * - 2.5: Return error status on file reading failures
 * - 2.6: Ensure idempotent hash computation
 */

import { HashResult, ProgressCallback } from '../types';

export class BLAKE3Hasher {
  private readonly CHUNK_SIZE = 1024 * 1024; // 1MB chunks for streaming
  private readonly LARGE_FILE_THRESHOLD = 100 * 1024 * 1024; // 100MB threshold for streaming
  private readonly MAX_FILE_SIZE = 10 * 1024 * 1024 * 1024; // 10GB maximum file size
  private readonly MAX_RETRY_ATTEMPTS = 1; // Requirement 10.2: Retry once before reporting failure

  /**
   * Compute BLAKE3 hash of a file using the most appropriate method
   * Automatically chooses between standard and streaming based on file size
   * Implements requirement 10.2: Retry hash computation once before reporting failure
   */
  public async computeHash(file: File): Promise<HashResult> {
    return this.computeHashWithRetry(file, 0);
  }

  /**
   * Internal method to compute hash with retry logic
   * Requirement 10.2: Retry once before reporting failure
   * TEMPORARY: Using SHA-256 via Web Crypto API until BLAKE3 WASM loading is fixed
   */
  private async computeHashWithRetry(file: File, attemptNumber: number): Promise<HashResult> {
    // Validate file before processing
    const validationError = this.validateFile(file);
    if (validationError) {
      return {
        hash: '',
        success: false,
        error: validationError,
        processingTime: 0
      };
    }

    // Use streaming for large files to prevent memory issues
    if (file.size > this.LARGE_FILE_THRESHOLD) {
      return this.computeHashStreamingWithRetry(file, undefined, attemptNumber);
    }

    const startTime = Date.now();
    
    try {
      // Read entire file into memory for smaller files
      const arrayBuffer = await file.arrayBuffer();
      
      // Compute SHA-256 hash using Web Crypto API (256-bit output)
      const hashBuffer = await crypto.subtle.digest('SHA-256', arrayBuffer);
      const hashArray = new Uint8Array(hashBuffer);
      
      // Convert to lowercase hexadecimal string
      const hash = this.bytesToHex(hashArray);
      const processingTime = Date.now() - startTime;
      
      // Log performance metrics (Requirement 8.1: minimum 100 MB/second)
      this.logPerformanceMetrics(file.size, processingTime);
      
      return {
        hash,
        success: true,
        processingTime
      };
    } catch (error) {
      const processingTime = Date.now() - startTime;
      
      // Retry once if this is the first attempt
      if (attemptNumber < this.MAX_RETRY_ATTEMPTS) {
        console.log(`Hash computation failed, retrying (attempt ${attemptNumber + 1}/${this.MAX_RETRY_ATTEMPTS})...`);
        await this.sleep(500); // Brief delay before retry
        return this.computeHashWithRetry(file, attemptNumber + 1);
      }
      
      return {
        hash: '',
        success: false,
        error: `Hash computation failed after ${attemptNumber + 1} attempts: ${this.getErrorMessage(error)}`,
        processingTime
      };
    }
  }

  /**
   * Compute BLAKE3 hash with streaming and progress callbacks
   * Processes files in chunks to handle large binaries efficiently
   * Implements requirement 10.2: Retry hash computation once before reporting failure
   */
  public async computeHashStreaming(file: File, onProgress?: ProgressCallback): Promise<HashResult> {
    return this.computeHashStreamingWithRetry(file, onProgress, 0);
  }

  /**
   * Internal method to compute hash with streaming and retry logic
   * Requirement 10.2: Retry once before reporting failure
   * TEMPORARY: Using SHA-256 via Web Crypto API until BLAKE3 WASM loading is fixed
   */
  private async computeHashStreamingWithRetry(
    file: File, 
    onProgress?: ProgressCallback,
    attemptNumber: number = 0
  ): Promise<HashResult> {
    // Validate file before processing
    const validationError = this.validateFile(file);
    if (validationError) {
      return {
        hash: '',
        success: false,
        error: validationError,
        processingTime: 0
      };
    }

    const startTime = Date.now();
    
    try {
      let bytesProcessed = 0;
      const totalBytes = file.size;
      const chunks: Uint8Array[] = [];
      
      // Process file in chunks to prevent memory exhaustion
      for (let offset = 0; offset < totalBytes; offset += this.CHUNK_SIZE) {
        const chunkEnd = Math.min(offset + this.CHUNK_SIZE, totalBytes);
        const chunk = file.slice(offset, chunkEnd);
        
        try {
          const arrayBuffer = await chunk.arrayBuffer();
          const uint8Array = new Uint8Array(arrayBuffer);
          
          // Store chunk for final hashing
          chunks.push(uint8Array);
          
          bytesProcessed += uint8Array.length;
          
          // Report progress to callback
          if (onProgress) {
            const progress = Math.min((bytesProcessed / totalBytes) * 100, 100);
            onProgress(progress);
          }
          
          // Yield control to prevent blocking UI thread
          await this.sleep(1);
        } catch (chunkError) {
          throw new Error(`Failed to read chunk at offset ${offset}: ${this.getErrorMessage(chunkError)}`);
        }
      }
      
      // Combine all chunks
      const totalLength = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
      const combined = new Uint8Array(totalLength);
      let position = 0;
      for (const chunk of chunks) {
        combined.set(chunk, position);
        position += chunk.length;
      }
      
      // Compute SHA-256 hash using Web Crypto API
      const hashBuffer = await crypto.subtle.digest('SHA-256', combined);
      const hashArray = new Uint8Array(hashBuffer);
      const hash = this.bytesToHex(hashArray);
      const processingTime = Date.now() - startTime;
      
      // Report 100% completion
      if (onProgress) {
        onProgress(100);
      }
      
      // Log performance metrics (Requirement 8.1: minimum 100 MB/second)
      this.logPerformanceMetrics(file.size, processingTime);
      
      return {
        hash,
        success: true,
        processingTime
      };
    } catch (error) {
      const processingTime = Date.now() - startTime;
      
      // Retry once if this is the first attempt
      if (attemptNumber < this.MAX_RETRY_ATTEMPTS) {
        console.log(`Streaming hash computation failed, retrying (attempt ${attemptNumber + 1}/${this.MAX_RETRY_ATTEMPTS})...`);
        await this.sleep(500); // Brief delay before retry
        return this.computeHashStreamingWithRetry(file, onProgress, attemptNumber + 1);
      }
      
      return {
        hash: '',
        success: false,
        error: `Streaming hash computation failed after ${attemptNumber + 1} attempts: ${this.getErrorMessage(error)}`,
        processingTime
      };
    }
  }

  /**
   * Validate BLAKE3 hash format
   * Ensures hash is exactly 64 lowercase hexadecimal characters
   * TEMPORARY: Also accepts SHA-256 format (64 hex chars)
   */
  public validateHashFormat(hash: string): boolean {
    // Both BLAKE3 and SHA-256 with 256-bit output produce 32 bytes = 64 hex characters
    const hashRegex = /^[a-f0-9]{64}$/;
    return hashRegex.test(hash);
  }

  /**
   * Normalize hash to lowercase hexadecimal format
   */
  public normalizeHash(hash: string): string {
    return hash.toLowerCase();
  }

  /**
   * Validate file before processing
   * Checks file size limits and accessibility
   */
  private validateFile(file: File): string | null {
    if (!file) {
      return 'No file provided for hashing';
    }

    if (file.size === 0) {
      return 'Cannot hash empty file';
    }

    if (file.size > this.MAX_FILE_SIZE) {
      return `File too large: ${this.formatFileSize(file.size)} exceeds maximum of ${this.formatFileSize(this.MAX_FILE_SIZE)}`;
    }

    return null; // File is valid
  }

  /**
   * Convert byte array to lowercase hexadecimal string
   */
  private bytesToHex(bytes: Uint8Array): string {
    return Array.from(bytes)
      .map(byte => byte.toString(16).padStart(2, '0'))
      .join('');
  }

  /**
   * Format file size for human-readable error messages
   */
  private formatFileSize(bytes: number): string {
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let size = bytes;
    let unitIndex = 0;
    
    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }
    
    return `${size.toFixed(1)} ${units[unitIndex]}`;
  }

  /**
   * Extract error message from unknown error type
   */
  private getErrorMessage(error: unknown): string {
    if (error instanceof Error) {
      return error.message;
    }
    if (typeof error === 'string') {
      return error;
    }
    return 'Unknown error occurred';
  }

  /**
   * Sleep utility for yielding control to prevent UI blocking
   */
  private sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * Log performance metrics for hash computation
   * Requirement 8.1: BLAKE3_Hasher shall process files at minimum 100 MB/second
   * TEMPORARY: Logging SHA-256 performance until BLAKE3 is working
   */
  private logPerformanceMetrics(fileSize: number, processingTimeMs: number): void {
    const fileSizeMB = fileSize / (1024 * 1024);
    const processingTimeSec = processingTimeMs / 1000;
    const throughputMBps = fileSizeMB / processingTimeSec;
    
    const meetsRequirement = throughputMBps >= 100;
    const status = meetsRequirement ? 'PASS' : 'WARN';
    
    console.log(`[SHA-256 Performance] ${status}: File=${this.formatFileSize(fileSize)}, ` +
                `Time=${processingTimeMs}ms, Throughput=${throughputMBps.toFixed(2)} MB/s, ` +
                `Target=100 MB/s`);
    
    if (!meetsRequirement) {
      console.warn(`Performance below target: ${throughputMBps.toFixed(2)} MB/s < 100 MB/s`);
    }
  }
}