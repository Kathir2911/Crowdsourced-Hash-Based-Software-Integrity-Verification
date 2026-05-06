/**
 * Unit tests and property-based tests for NotificationService
 * Tests verification notification display and status color mapping
 */

import { NotificationService, NotificationConfig } from '../../src/services/NotificationService';
import { TamperStatus, VerificationResult, SuspicionLevel } from '../../src/types';
import * as fc from 'fast-check';

describe('NotificationService', () => {
  let notificationService: NotificationService;

  beforeEach(() => {
    notificationService = new NotificationService();
    
    // Setup DOM elements for testing
    document.body.innerHTML = `
      <div id="statusDisplay"></div>
      <div id="warningDialog" style="display: none;">
        <div id="warningContent"></div>
      </div>
    `;
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  describe('getStatusColor', () => {
    it('should return green color for VERIFIED status', () => {
      const color = notificationService.getStatusColor(TamperStatus.VERIFIED);
      
      expect(color.color).toBe('#155724');
      expect(color.backgroundColor).toBe('#d4edda');
      expect(color.borderColor).toBe('#c3e6cb');
      expect(color.icon).toBe('✅');
    });

    it('should return red color for TAMPERED status', () => {
      const color = notificationService.getStatusColor(TamperStatus.TAMPERED);
      
      expect(color.color).toBe('#721c24');
      expect(color.backgroundColor).toBe('#f8d7da');
      expect(color.borderColor).toBe('#f5c6cb');
      expect(color.icon).toBe('❌');
    });

    it('should return orange color for SUSPICIOUS_LOW_CONFIDENCE status', () => {
      const color = notificationService.getStatusColor(TamperStatus.SUSPICIOUS_LOW_CONFIDENCE);
      
      expect(color.color).toBe('#856404');
      expect(color.backgroundColor).toBe('#fff3cd');
      expect(color.borderColor).toBe('#ffeaa7');
      expect(color.icon).toBe('⚠️');
    });

    it('should return yellow color for UNKNOWN status', () => {
      const color = notificationService.getStatusColor(TamperStatus.UNKNOWN);
      
      expect(color.color).toBe('#856404');
      expect(color.backgroundColor).toBe('#fff3cd');
      expect(color.borderColor).toBe('#ffeaa7');
      expect(color.icon).toBe('❓');
    });

    it('should return gray color for SERVICE_UNAVAILABLE status', () => {
      const color = notificationService.getStatusColor(TamperStatus.SERVICE_UNAVAILABLE);
      
      expect(color.color).toBe('#383d41');
      expect(color.backgroundColor).toBe('#e2e3e5');
      expect(color.borderColor).toBe('#d6d8db');
      expect(color.icon).toBe('🔌');
    });
  });

  describe('displayNotification', () => {
    it('should display notification with correct content', () => {
      const config: NotificationConfig = {
        filename: 'test.exe',
        status: TamperStatus.VERIFIED,
        confidence: 0.95,
        submissionCount: 50,
        message: 'File verified successfully',
        recommendedAction: 'Safe to install'
      };

      notificationService.displayNotification(config);

      const statusDisplay = document.getElementById('statusDisplay');
      expect(statusDisplay).not.toBeNull();
      expect(statusDisplay!.style.display).toBe('block');
      expect(statusDisplay!.innerHTML).toContain('test.exe');
      expect(statusDisplay!.innerHTML).toContain('95%');
      expect(statusDisplay!.innerHTML).toContain('50');
    });

    it('should apply correct colors to notification', () => {
      const config: NotificationConfig = {
        filename: 'test.exe',
        status: TamperStatus.TAMPERED,
        confidence: 0.85,
        submissionCount: 30,
        message: 'File tampered'
      };

      notificationService.displayNotification(config);

      const statusDisplay = document.getElementById('statusDisplay');
      expect(statusDisplay!.style.backgroundColor).toBe('rgb(248, 215, 218)'); // #f8d7da
      expect(statusDisplay!.style.color).toBe('rgb(114, 28, 36)'); // #721c24
    });
  });

  describe('displayVerificationResult', () => {
    it('should display verification result with all details', () => {
      const result: VerificationResult = {
        status: TamperStatus.VERIFIED,
        confidence: 0.92,
        submissionCount: 45,
        consensusHash: 'abc123',
        message: 'Verification successful',
        timestamp: new Date(),
        suspicionLevel: SuspicionLevel.NONE,
        recommendedAction: 'Safe to proceed'
      };

      notificationService.displayVerificationResult(result, 'myfile.exe');

      const statusDisplay = document.getElementById('statusDisplay');
      expect(statusDisplay!.innerHTML).toContain('myfile.exe');
      expect(statusDisplay!.innerHTML).toContain('92%');
      expect(statusDisplay!.innerHTML).toContain('45');
    });

    it('should show warning dialog for TAMPERED status', () => {
      const result: VerificationResult = {
        status: TamperStatus.TAMPERED,
        confidence: 0.88,
        submissionCount: 40,
        message: 'File tampered',
        timestamp: new Date(),
        suspicionLevel: SuspicionLevel.CRITICAL,
        recommendedAction: 'Do not install'
      };

      notificationService.displayVerificationResult(result, 'malware.exe');

      const warningDialog = document.getElementById('warningDialog');
      const warningContent = document.getElementById('warningContent');
      
      expect(warningDialog!.style.display).toBe('block');
      expect(warningContent!.innerHTML).toContain('SECURITY WARNING');
      expect(warningContent!.innerHTML).toContain('malware.exe');
      expect(warningContent!.innerHTML).toContain('Do NOT install');
    });

    it('should show caution dialog for SUSPICIOUS_LOW_CONFIDENCE status', () => {
      const result: VerificationResult = {
        status: TamperStatus.SUSPICIOUS_LOW_CONFIDENCE,
        confidence: 0.72,
        submissionCount: 5,
        message: 'Weak consensus',
        timestamp: new Date(),
        suspicionLevel: SuspicionLevel.MEDIUM,
        recommendedAction: 'Proceed with caution'
      };

      notificationService.displayVerificationResult(result, 'suspicious.exe');

      const warningDialog = document.getElementById('warningDialog');
      const warningContent = document.getElementById('warningContent');
      
      expect(warningDialog!.style.display).toBe('block');
      expect(warningContent!.innerHTML).toContain('CAUTION');
      expect(warningContent!.innerHTML).toContain('Weak Consensus');
      expect(warningContent!.innerHTML).toContain('suspicious.exe');
    });

    it('should show service unavailable dialog for SERVICE_UNAVAILABLE status', () => {
      const result: VerificationResult = {
        status: TamperStatus.SERVICE_UNAVAILABLE,
        confidence: 0,
        submissionCount: 0,
        message: 'Service unavailable',
        timestamp: new Date(),
        suspicionLevel: SuspicionLevel.NONE,
        recommendedAction: 'Try again later'
      };

      notificationService.displayVerificationResult(result, 'file.exe');

      const warningDialog = document.getElementById('warningDialog');
      const warningContent = document.getElementById('warningContent');
      
      expect(warningDialog!.style.display).toBe('block');
      expect(warningContent!.innerHTML).toContain('Service Unavailable');
      expect(warningContent!.innerHTML).toContain('file.exe');
    });
  });

  describe('closeWarningDialog', () => {
    it('should close warning dialog', () => {
      const warningDialog = document.getElementById('warningDialog');
      warningDialog!.style.display = 'block';

      notificationService.closeWarningDialog();

      expect(warningDialog!.style.display).toBe('none');
    });
  });

  // Property-Based Tests
  describe('Property Tests', () => {
    /**
     * Property 15: Verification Notification Display
     * **Validates: Requirements 6.1**
     * 
     * For any completed verification, the Browser_Extension should display a notification popup 
     * with the verification result.
     */
    describe('Property 15: Verification Notification Display', () => {
      it('should display notification for any verification result', () => {
        fc.assert(
          fc.property(
            // Generate arbitrary verification results
            fc.constantFrom(...Object.values(TamperStatus)),
            fc.double({ min: 0, max: 1 }),
            fc.integer({ min: 0, max: 1000 }),
            fc.string({ minLength: 1, maxLength: 100 }),
            fc.string({ minLength: 1, maxLength: 200 }),
            (status, confidence, submissionCount, filename, message) => {
              const result: VerificationResult = {
                status,
                confidence,
                submissionCount,
                message,
                timestamp: new Date(),
                suspicionLevel: SuspicionLevel.NONE,
                recommendedAction: 'Test action'
              };

              notificationService.displayVerificationResult(result, filename);

              // Verify notification is displayed
              const statusDisplay = document.getElementById('statusDisplay');
              expect(statusDisplay).not.toBeNull();
              expect(statusDisplay!.style.display).toBe('block');
              
              // Verify notification contains key information (HTML escaped by textContent)
              // textContent only escapes <, >, and & but not quotes
              const escapedFilename = filename
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;');
              expect(statusDisplay!.innerHTML).toContain(escapedFilename);
              
              // Verify confidence percentage is displayed (0-100%)
              const confidencePercent = Math.round(confidence * 100);
              expect(statusDisplay!.innerHTML).toContain(`${confidencePercent}%`);
              
              // Verify submission count is displayed
              expect(statusDisplay!.innerHTML).toContain(submissionCount.toString());
            }
          ),
          { numRuns: 50 }
        );
      });

      it('should display notification with correct structure for all statuses', () => {
        fc.assert(
          fc.property(
            fc.constantFrom(...Object.values(TamperStatus)),
            fc.double({ min: 0, max: 1 }),
            fc.integer({ min: 0, max: 1000 }),
            fc.string({ minLength: 1, maxLength: 100 }),
            (status, confidence, submissionCount, filename) => {
              const config: NotificationConfig = {
                filename,
                status,
                confidence,
                submissionCount,
                message: 'Test message'
              };

              notificationService.displayNotification(config);

              const statusDisplay = document.getElementById('statusDisplay');
              
              // Notification must be visible
              expect(statusDisplay!.style.display).toBe('block');
              
              // Notification must have background color set
              expect(statusDisplay!.style.backgroundColor).not.toBe('');
              
              // Notification must have text color set
              expect(statusDisplay!.style.color).not.toBe('');
              
              // Notification must contain filename (HTML escaped by textContent)
              const escapedFilename = filename
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;');
              expect(statusDisplay!.innerHTML).toContain(escapedFilename);
              
              // Notification must contain confidence percentage
              const confidencePercent = Math.round(confidence * 100);
              expect(statusDisplay!.innerHTML).toContain(`${confidencePercent}%`);
              
              // Notification must contain submission count
              expect(statusDisplay!.innerHTML).toContain(submissionCount.toString());
            }
          ),
          { numRuns: 50 }
        );
      });
    });

    /**
     * Property 16: Status Color Mapping
     * **Validates: Requirements 6.2, 6.3, 6.4, 6.5**
     * 
     * For any verification status (VERIFIED/TAMPERED/SUSPICIOUS_LOW_CONFIDENCE/UNKNOWN), 
     * the Browser_Extension should display the correct corresponding color 
     * (green/red/orange/yellow).
     */
    describe('Property 16: Status Color Mapping', () => {
      it('should return correct color mapping for all status values', () => {
        fc.assert(
          fc.property(
            fc.constantFrom(...Object.values(TamperStatus)),
            (status) => {
              const colorMapping = notificationService.getStatusColor(status);

              // All color mappings must have required fields
              expect(colorMapping).toHaveProperty('color');
              expect(colorMapping).toHaveProperty('backgroundColor');
              expect(colorMapping).toHaveProperty('borderColor');
              expect(colorMapping).toHaveProperty('icon');

              // Colors must be valid CSS color strings (hex format)
              expect(colorMapping.color).toMatch(/^#[0-9a-f]{6}$/i);
              expect(colorMapping.backgroundColor).toMatch(/^#[0-9a-f]{6}$/i);
              expect(colorMapping.borderColor).toMatch(/^#[0-9a-f]{6}$/i);

              // Icon must be non-empty
              expect(colorMapping.icon).not.toBe('');

              // Verify specific color requirements
              switch (status) {
                case TamperStatus.VERIFIED:
                  // Requirement 6.2: Green color for VERIFIED
                  expect(colorMapping.backgroundColor).toBe('#d4edda'); // Light green
                  expect(colorMapping.color).toBe('#155724'); // Dark green
                  break;
                
                case TamperStatus.TAMPERED:
                  // Requirement 6.3: Red color for TAMPERED
                  expect(colorMapping.backgroundColor).toBe('#f8d7da'); // Light red
                  expect(colorMapping.color).toBe('#721c24'); // Dark red
                  break;
                
                case TamperStatus.SUSPICIOUS_LOW_CONFIDENCE:
                  // Requirement 6.4: Orange color for SUSPICIOUS_LOW_CONFIDENCE
                  expect(colorMapping.backgroundColor).toBe('#fff3cd'); // Light orange/yellow
                  expect(colorMapping.color).toBe('#856404'); // Dark orange
                  break;
                
                case TamperStatus.UNKNOWN:
                  // Requirement 6.5: Yellow color for UNKNOWN
                  expect(colorMapping.backgroundColor).toBe('#fff3cd'); // Light yellow
                  expect(colorMapping.color).toBe('#856404'); // Dark yellow/orange
                  break;
                
                case TamperStatus.SERVICE_UNAVAILABLE:
                  // Gray color for SERVICE_UNAVAILABLE
                  expect(colorMapping.backgroundColor).toBe('#e2e3e5'); // Light gray
                  expect(colorMapping.color).toBe('#383d41'); // Dark gray
                  break;
              }
            }
          ),
          { numRuns: 50 }
        );
      });

      it('should apply correct colors to displayed notifications', () => {
        fc.assert(
          fc.property(
            fc.constantFrom(...Object.values(TamperStatus)),
            fc.double({ min: 0, max: 1 }),
            fc.integer({ min: 0, max: 1000 }),
            fc.string({ minLength: 1, maxLength: 100 }),
            (status, confidence, submissionCount, filename) => {
              const config: NotificationConfig = {
                filename,
                status,
                confidence,
                submissionCount,
                message: 'Test'
              };

              notificationService.displayNotification(config);

              const statusDisplay = document.getElementById('statusDisplay');
              const expectedColors = notificationService.getStatusColor(status);

              // Convert hex to RGB for comparison (browsers return RGB format)
              const hexToRgb = (hex: string) => {
                const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
                if (!result) return null;
                return {
                  r: parseInt(result[1]!, 16),
                  g: parseInt(result[2]!, 16),
                  b: parseInt(result[3]!, 16)
                };
              };

              const expectedBgRgb = hexToRgb(expectedColors.backgroundColor);
              const expectedColorRgb = hexToRgb(expectedColors.color);

              // Verify background color matches expected
              expect(statusDisplay!.style.backgroundColor).toBe(
                `rgb(${expectedBgRgb!.r}, ${expectedBgRgb!.g}, ${expectedBgRgb!.b})`
              );

              // Verify text color matches expected
              expect(statusDisplay!.style.color).toBe(
                `rgb(${expectedColorRgb!.r}, ${expectedColorRgb!.g}, ${expectedColorRgb!.b})`
              );
            }
          ),
          { numRuns: 50 }
        );
      });

      it('should maintain color consistency across multiple calls', () => {
        fc.assert(
          fc.property(
            fc.constantFrom(...Object.values(TamperStatus)),
            (status) => {
              // Get color mapping multiple times
              const color1 = notificationService.getStatusColor(status);
              const color2 = notificationService.getStatusColor(status);
              const color3 = notificationService.getStatusColor(status);

              // All calls should return identical color mappings
              expect(color1).toEqual(color2);
              expect(color2).toEqual(color3);
              expect(color1).toEqual(color3);
            }
          ),
          { numRuns: 50 }
        );
      });

      it('should have unique colors for different critical statuses', () => {
        // VERIFIED and TAMPERED should have distinctly different colors
        const verifiedColor = notificationService.getStatusColor(TamperStatus.VERIFIED);
        const tamperedColor = notificationService.getStatusColor(TamperStatus.TAMPERED);

        expect(verifiedColor.backgroundColor).not.toBe(tamperedColor.backgroundColor);
        expect(verifiedColor.color).not.toBe(tamperedColor.color);
        expect(verifiedColor.icon).not.toBe(tamperedColor.icon);
      });
    });

    /**
     * Additional property: Warning dialog display for critical statuses
     */
    describe('Property: Warning Dialog Display', () => {
      it('should show warning dialog for TAMPERED status', () => {
        fc.assert(
          fc.property(
            fc.double({ min: 0, max: 1 }),
            fc.integer({ min: 1, max: 1000 }),
            fc.string({ minLength: 1, maxLength: 100 }),
            (confidence, submissionCount, filename) => {
              const result: VerificationResult = {
                status: TamperStatus.TAMPERED,
                confidence,
                submissionCount,
                message: 'Tampered',
                timestamp: new Date(),
                suspicionLevel: SuspicionLevel.CRITICAL,
                recommendedAction: 'Do not install'
              };

              notificationService.displayVerificationResult(result, filename);

              const warningDialog = document.getElementById('warningDialog');
              expect(warningDialog!.style.display).toBe('block');
              
              const warningContent = document.getElementById('warningContent');
              const escapedFilename = filename
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;');
              expect(warningContent!.innerHTML).toContain(escapedFilename);
              expect(warningContent!.innerHTML).toContain('SECURITY WARNING');
            }
          ),
          { numRuns: 50 }
        );
      });

      it('should show warning dialog for SUSPICIOUS_LOW_CONFIDENCE status', () => {
        fc.assert(
          fc.property(
            fc.double({ min: 0, max: 1 }),
            fc.integer({ min: 0, max: 10 }), // Low submission count
            fc.string({ minLength: 1, maxLength: 100 }),
            (confidence, submissionCount, filename) => {
              const result: VerificationResult = {
                status: TamperStatus.SUSPICIOUS_LOW_CONFIDENCE,
                confidence,
                submissionCount,
                message: 'Suspicious',
                timestamp: new Date(),
                suspicionLevel: SuspicionLevel.MEDIUM,
                recommendedAction: 'Proceed with caution'
              };

              notificationService.displayVerificationResult(result, filename);

              const warningDialog = document.getElementById('warningDialog');
              expect(warningDialog!.style.display).toBe('block');
              
              const warningContent = document.getElementById('warningContent');
              const escapedFilename = filename
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;');
              expect(warningContent!.innerHTML).toContain(escapedFilename);
              expect(warningContent!.innerHTML).toContain('CAUTION');
            }
          ),
          { numRuns: 50 }
        );
      });

      it('should show warning dialog for SERVICE_UNAVAILABLE status', () => {
        fc.assert(
          fc.property(
            fc.string({ minLength: 1, maxLength: 100 }),
            (filename) => {
              const result: VerificationResult = {
                status: TamperStatus.SERVICE_UNAVAILABLE,
                confidence: 0,
                submissionCount: 0,
                message: 'Service unavailable',
                timestamp: new Date(),
                suspicionLevel: SuspicionLevel.NONE,
                recommendedAction: 'Try again later'
              };

              notificationService.displayVerificationResult(result, filename);

              const warningDialog = document.getElementById('warningDialog');
              expect(warningDialog!.style.display).toBe('block');
              
              const warningContent = document.getElementById('warningContent');
              const escapedFilename = filename
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;');
              expect(warningContent!.innerHTML).toContain(escapedFilename);
              expect(warningContent!.innerHTML).toContain('Service Unavailable');
            }
          ),
          { numRuns: 50 }
        );
      });
    });
  });
});
