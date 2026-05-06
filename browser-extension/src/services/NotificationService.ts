/**
 * Notification service for displaying verification results
 * Implements requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8, 10.1
 */

import { VerificationResult, TamperStatus } from '../types';

export interface NotificationConfig {
  filename: string;
  status: TamperStatus;
  confidence: number;
  submissionCount: number;
  message: string;
  recommendedAction?: string;
}

export interface StatusColorMapping {
  color: string;
  backgroundColor: string;
  borderColor: string;
  icon: string;
}

export class NotificationService {
  // Requirement 6.2, 6.3, 6.4, 6.5: Color mapping for each status
  private readonly STATUS_COLORS: Record<TamperStatus, StatusColorMapping> = {
    [TamperStatus.VERIFIED]: {
      color: '#155724',
      backgroundColor: '#d4edda',
      borderColor: '#c3e6cb',
      icon: '✅'
    },
    [TamperStatus.TAMPERED]: {
      color: '#721c24',
      backgroundColor: '#f8d7da',
      borderColor: '#f5c6cb',
      icon: '❌'
    },
    [TamperStatus.SUSPICIOUS_LOW_CONFIDENCE]: {
      color: '#856404',
      backgroundColor: '#fff3cd',
      borderColor: '#ffeaa7',
      icon: '⚠️'
    },
    [TamperStatus.UNKNOWN]: {
      color: '#856404',
      backgroundColor: '#fff3cd',
      borderColor: '#ffeaa7',
      icon: '❓'
    },
    [TamperStatus.SERVICE_UNAVAILABLE]: {
      color: '#383d41',
      backgroundColor: '#e2e3e5',
      borderColor: '#d6d8db',
      icon: '🔌'
    }
  };

  /**
   * Get color mapping for a given status
   * Requirement 6.2, 6.3, 6.4, 6.5: Return correct color for each status
   */
  public getStatusColor(status: TamperStatus): StatusColorMapping {
    return this.STATUS_COLORS[status];
  }

  /**
   * Display notification popup for verification result
   * Requirement 6.1: Display notification popup when verification completes
   * Requirement 6.6: Include file name, verification status, and confidence percentage
   */
  public displayNotification(config: NotificationConfig): void {
    const statusColor = this.getStatusColor(config.status);
    
    // Create notification content
    const notificationContent = this.createNotificationContent(config, statusColor);
    
    // Display in popup UI
    this.updatePopupUI(notificationContent, statusColor);
    
    // Show browser notification if supported
    this.showBrowserNotification(config, statusColor);
  }

  /**
   * Display verification result with all details
   * Requirement 6.6: Include file name, verification status, and confidence percentage
   */
  public displayVerificationResult(result: VerificationResult, filename: string): void {
    const config: NotificationConfig = {
      filename,
      status: result.status,
      confidence: result.confidence,
      submissionCount: result.submissionCount,
      message: result.message,
      recommendedAction: result.recommendedAction
    };

    this.displayNotification(config);

    // Show warning dialog for tampered files
    // Requirement 6.7: Display warning dialog for "TAMPERED" results
    if (result.status === TamperStatus.TAMPERED) {
      this.showTamperedWarningDialog(config);
    }

    // Show caution message for suspicious results
    // Requirement 6.8: Display caution message for "SUSPICIOUS_LOW_CONFIDENCE" results
    if (result.status === TamperStatus.SUSPICIOUS_LOW_CONFIDENCE) {
      this.showSuspiciousWarningDialog(config);
    }

    // Show service unavailable message
    // Requirement 10.1: Display "SERVICE_UNAVAILABLE" status when backend is unreachable
    if (result.status === TamperStatus.SERVICE_UNAVAILABLE) {
      this.showServiceUnavailableDialog(config);
    }
  }

  /**
   * Create notification content HTML
   */
  private createNotificationContent(
    config: NotificationConfig, 
    statusColor: StatusColorMapping
  ): string {
    const confidencePercent = Math.round(config.confidence * 100);
    
    return `
      <div class="notification-header">
        <span class="notification-icon">${statusColor.icon}</span>
        <span class="notification-status">${this.formatStatus(config.status)}</span>
      </div>
      <div class="notification-body">
        <div class="notification-field">
          <strong>File:</strong> ${this.escapeHtml(config.filename)}
        </div>
        <div class="notification-field">
          <strong>Status:</strong> ${this.formatStatus(config.status)}
        </div>
        <div class="notification-field">
          <strong>Confidence:</strong> ${confidencePercent}%
        </div>
        <div class="notification-field">
          <strong>Submissions:</strong> ${config.submissionCount}
        </div>
        ${config.message ? `
          <div class="notification-message">
            ${this.escapeHtml(config.message)}
          </div>
        ` : ''}
        ${config.recommendedAction ? `
          <div class="notification-action">
            <strong>Recommended Action:</strong> ${this.escapeHtml(config.recommendedAction)}
          </div>
        ` : ''}
      </div>
    `;
  }

  /**
   * Update popup UI with notification
   */
  private updatePopupUI(content: string, statusColor: StatusColorMapping): void {
    const statusDisplay = document.getElementById('statusDisplay');
    
    if (statusDisplay) {
      statusDisplay.innerHTML = content;
      statusDisplay.style.display = 'block';
      statusDisplay.style.backgroundColor = statusColor.backgroundColor;
      statusDisplay.style.color = statusColor.color;
      statusDisplay.style.borderColor = statusColor.borderColor;
    }
  }

  /**
   * Show browser notification (if permission granted)
   */
  private showBrowserNotification(
    config: NotificationConfig, 
    statusColor: StatusColorMapping
  ): void {
    // Check if browser notifications are supported and permitted
    if ('Notification' in window && Notification.permission === 'granted') {
      const confidencePercent = Math.round(config.confidence * 100);
      
      const notification = new Notification('Hash Verification Complete', {
        body: `${statusColor.icon} ${config.filename}\nStatus: ${this.formatStatus(config.status)}\nConfidence: ${confidencePercent}%`,
        icon: this.getNotificationIcon(config.status),
        tag: 'hash-verification',
        requireInteraction: config.status === TamperStatus.TAMPERED
      });

      // Auto-close after 5 seconds for non-critical notifications
      if (config.status !== TamperStatus.TAMPERED) {
        setTimeout(() => notification.close(), 5000);
      }
    }
  }

  /**
   * Show warning dialog for tampered files
   * Requirement 6.7: Display warning dialog for "TAMPERED" results with recommended actions
   */
  private showTamperedWarningDialog(config: NotificationConfig): void {
    const warningDialog = document.getElementById('warningDialog');
    const warningContent = document.getElementById('warningContent');
    
    if (warningDialog && warningContent) {
      warningContent.innerHTML = `
        <div class="warning-header">
          <span class="warning-icon">⚠️</span>
          <h3>SECURITY WARNING: File Tampering Detected</h3>
        </div>
        <div class="warning-body">
          <p><strong>File:</strong> ${this.escapeHtml(config.filename)}</p>
          <p>This file's hash does not match the consensus hash from ${config.submissionCount} submissions.</p>
          <p><strong>This indicates the file may have been tampered with or modified.</strong></p>
          
          <div class="warning-recommendations">
            <h4>Recommended Actions:</h4>
            <ul>
              <li>❌ <strong>Do NOT install or execute this file</strong></li>
              <li>🗑️ Delete the file immediately</li>
              <li>🔄 Re-download from the official source</li>
              <li>🔍 Verify the download source is legitimate</li>
              <li>🛡️ Run a security scan on your system</li>
            </ul>
          </div>
          
          <p class="warning-note">
            <strong>Note:</strong> ${this.escapeHtml(config.recommendedAction || 'Exercise extreme caution')}
          </p>
        </div>
      `;
      
      warningDialog.style.display = 'block';
    }
  }

  /**
   * Show caution message for suspicious results
   * Requirement 6.8: Display caution message for "SUSPICIOUS_LOW_CONFIDENCE" results explaining weak consensus
   */
  private showSuspiciousWarningDialog(config: NotificationConfig): void {
    const warningDialog = document.getElementById('warningDialog');
    const warningContent = document.getElementById('warningContent');
    
    if (warningDialog && warningContent) {
      const confidencePercent = Math.round(config.confidence * 100);
      
      warningContent.innerHTML = `
        <div class="warning-header caution">
          <span class="warning-icon">⚠️</span>
          <h3>CAUTION: Weak Consensus Detected</h3>
        </div>
        <div class="warning-body">
          <p><strong>File:</strong> ${this.escapeHtml(config.filename)}</p>
          <p>This file has weak consensus with only ${config.submissionCount} submissions and ${confidencePercent}% confidence.</p>
          
          <div class="warning-explanation">
            <h4>What does this mean?</h4>
            <p>The system has insufficient data to confidently verify this file's integrity. This could mean:</p>
            <ul>
              <li>📊 Not enough users have submitted hashes for this file</li>
              <li>🔄 The file is newly released with limited distribution</li>
              <li>⚠️ There are conflicting hash submissions from different sources</li>
              <li>🕐 The consensus is still being established</li>
            </ul>
          </div>
          
          <div class="warning-recommendations">
            <h4>Recommended Actions:</h4>
            <ul>
              <li>✅ Verify the download source is official and legitimate</li>
              <li>🔍 Check the publisher's website for hash verification</li>
              <li>⏳ Wait for more submissions to establish stronger consensus</li>
              <li>🛡️ Proceed with caution if you must install</li>
              <li>📝 Consider submitting your hash to help build consensus</li>
            </ul>
          </div>
          
          <p class="warning-note">
            <strong>Note:</strong> ${this.escapeHtml(config.recommendedAction || 'Exercise caution and verify through alternative means')}
          </p>
        </div>
      `;
      
      warningDialog.style.display = 'block';
    }
  }

  /**
   * Show service unavailable dialog
   * Requirement 10.1: Display "SERVICE_UNAVAILABLE" status when backend is unreachable
   */
  private showServiceUnavailableDialog(config: NotificationConfig): void {
    const warningDialog = document.getElementById('warningDialog');
    const warningContent = document.getElementById('warningContent');
    
    if (warningDialog && warningContent) {
      warningContent.innerHTML = `
        <div class="warning-header service-unavailable">
          <span class="warning-icon">🔌</span>
          <h3>Verification Service Unavailable</h3>
        </div>
        <div class="warning-body">
          <p><strong>File:</strong> ${this.escapeHtml(config.filename)}</p>
          <p>The verification service is currently unavailable. Unable to verify file integrity.</p>
          
          <div class="warning-explanation">
            <h4>Possible Causes:</h4>
            <ul>
              <li>🌐 No internet connection</li>
              <li>🔧 Backend service is down for maintenance</li>
              <li>⏱️ Request timeout due to network issues</li>
              <li>🚫 Service is temporarily overloaded</li>
            </ul>
          </div>
          
          <div class="warning-recommendations">
            <h4>Recommended Actions:</h4>
            <ul>
              <li>🔄 Check your internet connection</li>
              <li>⏳ Try again in a few moments</li>
              <li>🔍 Verify the file through alternative means</li>
              <li>📝 Check the service status page</li>
            </ul>
          </div>
          
          <p class="warning-note">
            <strong>Note:</strong> ${this.escapeHtml(config.recommendedAction || 'Try again later or check network connection')}
          </p>
        </div>
      `;
      
      warningDialog.style.display = 'block';
    }
  }

  /**
   * Close warning dialog
   */
  public closeWarningDialog(): void {
    const warningDialog = document.getElementById('warningDialog');
    if (warningDialog) {
      warningDialog.style.display = 'none';
    }
  }

  /**
   * Format status enum to human-readable string
   */
  private formatStatus(status: TamperStatus): string {
    switch (status) {
      case TamperStatus.VERIFIED:
        return 'Verified';
      case TamperStatus.TAMPERED:
        return 'Tampered';
      case TamperStatus.SUSPICIOUS_LOW_CONFIDENCE:
        return 'Suspicious (Low Confidence)';
      case TamperStatus.UNKNOWN:
        return 'Unknown';
      case TamperStatus.SERVICE_UNAVAILABLE:
        return 'Service Unavailable';
      default:
        return 'Unknown';
    }
  }

  /**
   * Get notification icon based on status
   */
  private getNotificationIcon(_status: TamperStatus): string {
    // Return a data URL or path to icon based on status
    // For now, return empty string as icons are handled via emoji
    return '';
  }

  /**
   * Escape HTML to prevent XSS
   */
  private escapeHtml(text: string): string {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  /**
   * Request notification permission
   */
  public async requestNotificationPermission(): Promise<boolean> {
    if ('Notification' in window) {
      if (Notification.permission === 'granted') {
        return true;
      } else if (Notification.permission !== 'denied') {
        const permission = await Notification.requestPermission();
        return permission === 'granted';
      }
    }
    return false;
  }
}
