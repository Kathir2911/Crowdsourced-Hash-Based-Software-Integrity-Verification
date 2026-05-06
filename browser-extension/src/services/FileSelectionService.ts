/**
 * Service for file selection, validation, and metadata extraction
 */

import { FileValidationResult, FileMetadata, SoftwareIdentity } from '../types';

export class FileSelectionService {
  private readonly EXECUTABLE_EXTENSIONS = ['.exe', '.msi', '.dmg', '.deb', '.rpm', '.appimage'];

  /**
   * Validate file type and return validation result
   */
  public validateFileType(file: File): FileValidationResult {
    const extension = this.getFileExtension(file.name);
    const isExecutable = this.EXECUTABLE_EXTENSIONS.includes(extension.toLowerCase());
    
    const warnings: string[] = [];
    
    if (!isExecutable) {
      warnings.push(`File type '${extension}' is not a recognized executable format`);
    }
    
    // Check file size (warn for very large files)
    const maxSize = 5 * 1024 * 1024 * 1024; // 5GB
    if (file.size > maxSize) {
      warnings.push(`File size (${this.formatFileSize(file.size)}) exceeds recommended maximum (5GB)`);
    }
    
    return {
      isExecutable,
      fileType: extension,
      warnings
    };
  }

  /**
   * Extract metadata from selected file
   */
  public extractMetadata(file: File): FileMetadata {
    const sourceDomain = this.extractSourceDomain();
    return {
      filename: file.name,
      size: file.size,
      lastModified: new Date(file.lastModified),
      sourceDomain: sourceDomain
    };
  }

  /**
   * Create software identity from file metadata
   */
  public createSoftwareIdentity(metadata: FileMetadata): SoftwareIdentity {
    const normalizedFilename = this.normalizeFilename(metadata.filename);
    const sourceDomain = metadata.sourceDomain || 'unknown';
    
    const identity: SoftwareIdentity = {
      sourceDomain,
      normalizedFilename,
      fileSize: metadata.size  // Changed from 'size' to 'fileSize' to match backend
    };

    // Generate identity hash
    identity.identityHash = this.generateIdentityHash(identity);
    
    return identity;
  }

  /**
   * Normalize filename for consistent identity generation
   */
  private normalizeFilename(filename: string): string {
    // Convert to lowercase and preserve extension
    return filename.toLowerCase();
  }

  /**
   * Extract source domain from current tab or download context
   */
  private extractSourceDomain(): string {
    // For MVP, we'll use a placeholder
    // In a full implementation, this would extract from download context
    return 'manual-selection';
  }

  /**
   * Generate identity hash from software identity components
   */
  private generateIdentityHash(identity: SoftwareIdentity): string {
    const identityString = `${identity.sourceDomain}|${identity.normalizedFilename}|${identity.fileSize}`;
    
    // Use a simple hash for now (in production, use crypto.subtle.digest)
    return this.simpleHash(identityString);
  }

  /**
   * Simple hash function for identity generation
   */
  private simpleHash(str: string): string {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      const char = str.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash; // Convert to 32-bit integer
    }
    return Math.abs(hash).toString(16).padStart(8, '0');
  }

  /**
   * Get file extension from filename
   */
  private getFileExtension(filename: string): string {
    const lastDot = filename.lastIndexOf('.');
    return lastDot === -1 ? '' : filename.substring(lastDot);
  }

  /**
   * Format file size for display
   */
  private formatFileSize(bytes: number): string {
    const units = ['B', 'KB', 'MB', 'GB'];
    let size = bytes;
    let unitIndex = 0;
    
    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }
    
    return `${size.toFixed(1)} ${units[unitIndex]}`;
  }
}