/**
 * Popup UI controller for file selection and verification display
 */

import { FileSelectionService } from './services/FileSelectionService';
import { BLAKE3Hasher } from './services/BLAKE3Hasher';
import { VerificationAPIClient } from './services/VerificationAPIClient';
import { NotificationService } from './services/NotificationService';
import { memoryMonitor } from './services/MemoryMonitor';
import { TamperStatus, VerificationResult, ExtensionConfig } from './types';

class PopupController {
  private fileSelectionService: FileSelectionService;
  private blake3Hasher: BLAKE3Hasher;
  private apiClient: VerificationAPIClient;
  private notificationService: NotificationService;
  private config: ExtensionConfig | null = null;
  private currentFilename: string = '';

  constructor() {
    this.fileSelectionService = new FileSelectionService();
    this.blake3Hasher = new BLAKE3Hasher();
    this.apiClient = new VerificationAPIClient();
    this.notificationService = new NotificationService();
    
    // Start memory monitoring (Requirement 8.6: limit memory usage to 50MB)
    memoryMonitor.startMonitoring();
    
    this.initializePopup();
  }

  private async initializePopup(): Promise<void> {
    try {
      // Load configuration
      await this.loadConfig();
      
      // Setup event listeners
      this.setupEventListeners();
      
      // Check service status
      if (this.config) {
        await this.checkServiceStatus();
      }
      
      // Process any queued submissions from previous failures
      // Requirement 10.1: Retry queued submissions when extension starts
      this.processQueuedSubmissionsInBackground();
      
      // Set up periodic queue processing (every 5 minutes)
      setInterval(() => {
        this.processQueuedSubmissionsInBackground();
      }, 5 * 60 * 1000);
      
    } catch (error) {
      console.error('Failed to initialize popup:', error);
      this.showError('Failed to initialize extension');
    }
  }

  private async loadConfig(): Promise<void> {
    try {
      const response = await chrome.runtime.sendMessage({ type: 'GET_CONFIG' });
      if (response.success) {
        this.config = response.data;
        if (this.config) {
          this.apiClient.setConfig(this.config);
        }
      } else {
        throw new Error(response.error || 'Failed to load config');
      }
    } catch (error) {
      console.error('Config loading error:', error);
      throw error;
    }
  }

  private setupEventListeners(): void {
    const fileInput = document.getElementById('fileInput') as HTMLInputElement;
    const fileButton = document.getElementById('fileButton') as HTMLElement;
    const settingsLink = document.getElementById('settingsLink') as HTMLElement;
    const closeWarningBtn = document.getElementById('closeWarningBtn') as HTMLElement;

    if (fileInput) {
      fileInput.addEventListener('change', (event) => {
        this.handleFileSelection(event);
      });
    }

    if (fileButton) {
      fileButton.addEventListener('click', () => {
        fileInput?.click();
      });
    }

    if (settingsLink) {
      settingsLink.addEventListener('click', (event) => {
        event.preventDefault();
        this.openSettings();
      });
    }

    if (closeWarningBtn) {
      closeWarningBtn.addEventListener('click', () => {
        this.notificationService.closeWarningDialog();
      });
    }

    // Request notification permission
    this.notificationService.requestNotificationPermission();
  }

  private async handleFileSelection(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    
    if (!file) {
      return;
    }

    try {
      this.showStatus('loading', 'Validating file...');
      
      // Validate file
      const validation = this.fileSelectionService.validateFileType(file);
      if (!validation.isExecutable && validation.warnings.length > 0) {
        this.showWarning(validation.warnings.join(', '));
      }

      // Extract metadata
      const metadata = this.fileSelectionService.extractMetadata(file);
      this.currentFilename = metadata.filename;
      this.showFileInfo(metadata.filename, metadata.size);

      // Compute hash
      this.showStatus('loading', 'Computing BLAKE3 hash...');
      this.showProgress(0);
      
      const hashResult = await this.blake3Hasher.computeHashStreaming(file, (progress) => {
        this.showProgress(progress);
      });

      if (!hashResult.success) {
        // Requirement 10.6: Continue monitoring even when hash computation fails
        this.showError(hashResult.error || 'Hash computation failed');
        console.error('Hash computation failed, but extension continues to monitor:', hashResult.error);
        return; // Allow user to select another file
      }

      // Verify with backend
      this.showStatus('loading', 'Verifying with consensus...');
      this.hideProgress();
      
      const verificationResult = await this.apiClient.verifyHash({
        softwareIdentity: this.fileSelectionService.createSoftwareIdentity(metadata),
        hash: hashResult.hash
      });

      // Display result using NotificationService
      // Requirement 10.6: Display results even if verification partially failed
      this.notificationService.displayVerificationResult(verificationResult, this.currentFilename);
      
      // Process any queued submissions in the background
      // This ensures submissions are retried when service becomes available
      this.processQueuedSubmissionsInBackground();

    } catch (error) {
      // Requirement 10.6: Continue monitoring downloads even when verification fails
      console.error('File verification error:', error);
      this.showError(`Verification failed: ${error}`);
      
      // Extension continues to function - user can select another file
      console.log('Extension continues to monitor despite error');
    }
  }

  /**
   * Process queued submissions in the background
   * Requirement 10.1: Retry queued submissions when service becomes available
   */
  private async processQueuedSubmissionsInBackground(): Promise<void> {
    try {
      const result = await this.apiClient.processQueuedSubmissions();
      
      if (result.processed > 0) {
        console.log(`Processed ${result.processed} queued submissions: ${result.succeeded} succeeded, ${result.failed} failed`);
      }
    } catch (error) {
      console.error('Failed to process queued submissions:', error);
      // Don't show error to user - this is a background operation
    }
  }

  private async checkServiceStatus(): Promise<void> {
    try {
      const status = await this.apiClient.getStatus();
      if (!status.available) {
        this.showWarning('Backend service is currently unavailable');
      }
    } catch (error) {
      console.error('Service status check failed:', error);
      this.showWarning('Unable to connect to verification service');
    }
  }

  private showStatus(type: string, message: string): void {
    const statusDisplay = document.getElementById('statusDisplay');
    const statusText = document.getElementById('statusText');
    
    if (statusDisplay && statusText) {
      statusDisplay.className = `status-display status-${type}`;
      statusDisplay.style.display = 'block';
      statusText.textContent = message;
    }
  }

  private showVerificationResult(result: VerificationResult): void {
    const statusMap = {
      [TamperStatus.VERIFIED]: { type: 'verified', icon: '✅' },
      [TamperStatus.TAMPERED]: { type: 'tampered', icon: '❌' },
      [TamperStatus.SUSPICIOUS_LOW_CONFIDENCE]: { type: 'suspicious', icon: '⚠️' },
      [TamperStatus.UNKNOWN]: { type: 'unknown', icon: '❓' },
      [TamperStatus.SERVICE_UNAVAILABLE]: { type: 'unknown', icon: '🔌' }
    };

    const statusInfo = statusMap[result.status];
    const message = `${statusInfo.icon} ${result.message}`;
    
    this.showStatus(statusInfo.type, message);
    
    // Show additional details
    const fileInfo = document.getElementById('fileInfo');
    if (fileInfo) {
      fileInfo.innerHTML = `
        <div>Confidence: ${Math.round(result.confidence * 100)}%</div>
        <div>Submissions: ${result.submissionCount}</div>
        <div>Recommendation: ${result.recommendedAction}</div>
      `;
    }
  }

  private showProgress(progress: number): void {
    const progressBar = document.getElementById('progressBar');
    const progressFill = document.getElementById('progressFill');
    
    if (progressBar && progressFill) {
      progressBar.style.display = 'block';
      progressFill.style.width = `${progress}%`;
    }
  }

  private hideProgress(): void {
    const progressBar = document.getElementById('progressBar');
    if (progressBar) {
      progressBar.style.display = 'none';
    }
  }

  private showFileInfo(filename: string, size: number): void {
    const fileInfo = document.getElementById('fileInfo');
    if (fileInfo) {
      const sizeStr = this.formatFileSize(size);
      fileInfo.innerHTML = `
        <div><strong>File:</strong> ${filename}</div>
        <div><strong>Size:</strong> ${sizeStr}</div>
      `;
    }
  }

  private showError(message: string): void {
    this.showStatus('tampered', 'Error');
    const errorMessage = document.getElementById('errorMessage');
    if (errorMessage) {
      errorMessage.textContent = message;
    }
  }

  private showWarning(message: string): void {
    const errorMessage = document.getElementById('errorMessage');
    if (errorMessage) {
      errorMessage.textContent = `⚠️ ${message}`;
      errorMessage.style.color = '#856404';
    }
  }

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

  private openSettings(): void {
    // TODO: Implement settings page
    alert('Settings page coming soon!');
  }
}

// Initialize popup when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
  new PopupController();
});