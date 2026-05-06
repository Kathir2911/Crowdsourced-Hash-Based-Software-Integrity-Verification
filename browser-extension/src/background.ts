/**
 * Background service worker for the Crowdsourced Hash Verification extension
 */

import { ExtensionConfig } from './types';

// Default configuration
const DEFAULT_CONFIG: ExtensionConfig = {
  apiBaseUrl: 'http://localhost:8080/api/v1',
  enabledFileTypes: ['.exe', '.msi', '.dmg', '.deb', '.rpm', '.appimage'],
  maxFileSize: 5 * 1024 * 1024 * 1024, // 5GB
  timeoutMs: 60000, // 60 seconds
  retryAttempts: 3
};

class BackgroundService {
  private config: ExtensionConfig = DEFAULT_CONFIG;

  constructor() {
    this.initializeExtension();
    this.setupMessageHandlers();
  }

  private async initializeExtension(): Promise<void> {
    try {
      // Load configuration from storage
      const stored = await chrome.storage.sync.get('config');
      if (stored['config']) {
        this.config = { ...DEFAULT_CONFIG, ...stored['config'] };
      } else {
        // Save default config
        await chrome.storage.sync.set({ config: this.config });
      }

      console.log('Hash Verification extension initialized');
    } catch (error) {
      console.error('Failed to initialize extension:', error);
    }
  }

  private setupMessageHandlers(): void {
    chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
      this.handleMessage(message, sender)
        .then(sendResponse)
        .catch(error => {
          console.error('Message handler error:', error);
          sendResponse({ success: false, error: error.message });
        });
      
      // Return true to indicate async response
      return true;
    });
  }

  private async handleMessage(message: any, _sender: chrome.runtime.MessageSender): Promise<any> {
    switch (message.type) {
      case 'GET_CONFIG':
        return { success: true, data: this.config };
      
      case 'UPDATE_CONFIG':
        return this.updateConfig(message.config);
      
      case 'GET_STATUS':
        return this.getServiceStatus();
      
      default:
        throw new Error(`Unknown message type: ${message.type}`);
    }
  }

  private async updateConfig(newConfig: Partial<ExtensionConfig>): Promise<any> {
    try {
      this.config = { ...this.config, ...newConfig };
      await chrome.storage.sync.set({ config: this.config });
      return { success: true, data: this.config };
    } catch (error) {
      throw new Error(`Failed to update config: ${error}`);
    }
  }

  private async getServiceStatus(): Promise<any> {
    try {
      const response = await fetch(`${this.config.apiBaseUrl}/status`, {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json'
        }
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const status = await response.json();
      return { success: true, data: status };
    } catch (error) {
      return { 
        success: false, 
        error: `Service unavailable: ${error}`,
        data: { available: false, message: 'Backend service unreachable' }
      };
    }
  }
}

// Initialize the background service
new BackgroundService();