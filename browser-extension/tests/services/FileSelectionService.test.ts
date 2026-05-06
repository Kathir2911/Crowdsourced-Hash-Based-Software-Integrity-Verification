/**
 * Unit tests for FileSelectionService
 */

import { FileSelectionService } from '../../src/services/FileSelectionService';

describe('FileSelectionService', () => {
  let service: FileSelectionService;

  beforeEach(() => {
    service = new FileSelectionService();
  });

  describe('validateFileType', () => {
    it('should identify executable files correctly', () => {
      const file = new File(['test'], 'setup.exe', { type: 'application/octet-stream' });
      const result = service.validateFileType(file);
      
      expect(result.isExecutable).toBe(true);
      expect(result.fileType).toBe('.exe');
      expect(result.warnings).toHaveLength(0);
    });

    it('should warn about non-executable files', () => {
      const file = new File(['test'], 'document.txt', { type: 'text/plain' });
      const result = service.validateFileType(file);
      
      expect(result.isExecutable).toBe(false);
      expect(result.fileType).toBe('.txt');
      expect(result.warnings.length).toBeGreaterThan(0);
    });

    it('should handle files without extensions', () => {
      const file = new File(['test'], 'README', { type: 'text/plain' });
      const result = service.validateFileType(file);
      
      expect(result.isExecutable).toBe(false);
      expect(result.fileType).toBe('');
    });
  });

  describe('extractMetadata', () => {
    it('should extract file metadata correctly', () => {
      const lastModified = Date.now();
      const file = new File(['test content'], 'test.exe', { 
        type: 'application/octet-stream',
        lastModified 
      });
      
      const metadata = service.extractMetadata(file);
      
      expect(metadata.filename).toBe('test.exe');
      expect(metadata.size).toBe(12); // 'test content' length
      expect(metadata.lastModified.getTime()).toBe(lastModified);
      expect(metadata.sourceDomain).toBe('manual-selection');
    });
  });

  describe('createSoftwareIdentity', () => {
    it('should create consistent software identity', () => {
      const metadata = {
        filename: 'Setup.EXE',
        size: 1024,
        lastModified: new Date(),
        sourceDomain: 'example.com'
      };
      
      const identity1 = service.createSoftwareIdentity(metadata);
      const identity2 = service.createSoftwareIdentity(metadata);
      
      expect(identity1.normalizedFilename).toBe('setup.exe');
      expect(identity1.sourceDomain).toBe('example.com');
      expect(identity1.size).toBe(1024);
      expect(identity1.identityHash).toBe(identity2.identityHash);
    });
  });
});