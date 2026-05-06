/**
 * API client for communicating with the backend verification service
 * Implements requirements 10.1, 10.2
 */

import { 
  HashSubmission, 
  VerificationRequest, 
  VerificationResult, 
  SubmissionResult, 
  ServiceStatus,
  ExtensionConfig,
  ApiResponse,
  TamperStatus
} from '../types';
import { SubmissionQueueService } from './SubmissionQueueService';

export class VerificationAPIClient {
  private config: ExtensionConfig | null = null;
  private readonly CLIENT_VERSION = '1.0.0';
  private readonly MAX_RETRIES = 3;
  private readonly INITIAL_RETRY_DELAY_MS = 1000;
  private submissionQueue: SubmissionQueueService;

  constructor() {
    this.submissionQueue = new SubmissionQueueService();
  }

  /**
   * Set configuration for API client
   */
  public setConfig(config: ExtensionConfig): void {
    this.config = config;
  }

  /**
   * Submit hash to backend for storage
   * Implements requirement 10.1: Retry logic with exponential backoff
   * Implements requirement 10.1: Queue submissions when network fails
   */
  public async submitHash(submission: HashSubmission): Promise<SubmissionResult> {
    if (!this.config) {
      throw new Error('API client not configured');
    }

    try {
      const response = await this.makeRequestWithRetry<SubmissionResult>('POST', '/submissions', {
        ...submission,
        clientVersion: this.CLIENT_VERSION
      });

      return response.data || { success: false, message: 'No response data' };
    } catch (error) {
      // Queue submission for later retry if network failure
      console.log('Submission failed, queuing for later retry:', error);
      await this.submissionQueue.enqueue(submission);
      
      return {
        success: false,
        message: `Submission queued for retry: ${error}`,
        error: String(error)
      };
    }
  }

  /**
   * Process queued submissions
   * Attempts to submit any pending submissions from the queue
   */
  public async processQueuedSubmissions(): Promise<{
    processed: number;
    succeeded: number;
    failed: number;
  }> {
    const retryableSubmissions = await this.submissionQueue.getRetryableSubmissions();
    
    let succeeded = 0;
    let failed = 0;
    
    for (const queuedItem of retryableSubmissions) {
      try {
        await this.submissionQueue.markAttempted(queuedItem.id);
        
        const response = await this.makeRequest<SubmissionResult>('POST', '/submissions', {
          ...queuedItem.submission,
          clientVersion: this.CLIENT_VERSION
        });
        
        if (response.data?.success) {
          // Remove from queue on success
          await this.submissionQueue.remove(queuedItem.id);
          succeeded++;
          console.log(`Successfully submitted queued submission ${queuedItem.id}`);
        } else {
          failed++;
          console.log(`Queued submission ${queuedItem.id} failed:`, response.data?.message);
        }
      } catch (error) {
        failed++;
        console.log(`Failed to process queued submission ${queuedItem.id}:`, error);
      }
    }
    
    // Clean up submissions that have exceeded max retries
    await this.submissionQueue.cleanupFailedSubmissions();
    
    return {
      processed: retryableSubmissions.length,
      succeeded,
      failed
    };
  }

  /**
   * Get submission queue statistics
   */
  public async getQueueStats(): Promise<{
    total: number;
    pending: number;
    failed: number;
  }> {
    return this.submissionQueue.getQueueStats();
  }

  /**
   * Verify hash against consensus
   * Implements requirement 10.1: Retry logic with exponential backoff
   */
  public async verifyHash(request: VerificationRequest): Promise<VerificationResult> {
    if (!this.config) {
      throw new Error('API client not configured');
    }

    try {
      // Backend returns VerificationResult directly, not wrapped in ApiResponse
      const result = await this.makeRequestWithRetry<VerificationResult>('POST', '/verify', request);
      
      // The result IS the VerificationResult (not wrapped in .data)
      return result as VerificationResult;
    } catch (error) {
      // Return service unavailable result
      return {
        status: TamperStatus.SERVICE_UNAVAILABLE,
        confidence: 0,
        submissionCount: 0,
        message: `Verification service unavailable: ${error}`,
        timestamp: new Date(),
        suspicionLevel: 0,
        recommendedAction: 'Try again later or check network connection'
      };
    }
  }

  /**
   * Get service status
   */
  public async getStatus(): Promise<ServiceStatus> {
    if (!this.config) {
      throw new Error('API client not configured');
    }

    try {
      const response = await this.makeRequest<ServiceStatus>('GET', '/status');
      
      return response.data || { 
        available: false, 
        version: 'unknown',
        message: 'No status data received'
      };
    } catch (error) {
      return {
        available: false,
        version: 'unknown',
        message: `Service check failed: ${error}`
      };
    }
  }

  /**
   * Make HTTP request with retry logic and exponential backoff
   * Implements requirement 10.1: Retry logic with exponential backoff for network failures
   */
  private async makeRequestWithRetry<T>(
    method: string,
    endpoint: string,
    data?: any,
    retryCount: number = 0
  ): Promise<ApiResponse<T>> {
    try {
      return await this.makeRequest<T>(method, endpoint, data);
    } catch (error) {
      // Check if we should retry
      if (retryCount < this.MAX_RETRIES && this.isRetryableError(error)) {
        // Calculate exponential backoff delay
        const delay = this.INITIAL_RETRY_DELAY_MS * Math.pow(2, retryCount);
        
        console.log(`Request failed, retrying in ${delay}ms (attempt ${retryCount + 1}/${this.MAX_RETRIES})`);
        
        // Wait before retrying
        await this.sleep(delay);
        
        // Retry the request
        return this.makeRequestWithRetry<T>(method, endpoint, data, retryCount + 1);
      }
      
      // Max retries exceeded or non-retryable error
      throw error;
    }
  }

  /**
   * Determine if an error is retryable
   */
  private isRetryableError(error: any): boolean {
    // Retry on network errors, timeouts, and 5xx server errors
    if (error instanceof Error) {
      const errorMessage = error.message.toLowerCase();
      
      // Network errors
      if (errorMessage.includes('network') || 
          errorMessage.includes('timeout') ||
          errorMessage.includes('abort')) {
        return true;
      }
      
      // 5xx server errors (but not 4xx client errors)
      if (errorMessage.includes('http 5')) {
        return true;
      }
      
      // 429 Too Many Requests - don't retry immediately
      if (errorMessage.includes('http 429')) {
        return false;
      }
    }
    
    return false;
  }

  /**
   * Sleep for specified milliseconds
   */
  private sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * Make HTTP request to backend API
   */
  private async makeRequest<T>(
    method: string, 
    endpoint: string, 
    data?: any
  ): Promise<ApiResponse<T>> {
    if (!this.config) {
      throw new Error('API client not configured');
    }

    const url = `${this.config.apiBaseUrl}${endpoint}`;
    const options: RequestInit = {
      method,
      headers: {
        'Content-Type': 'application/json',
        'User-Agent': `HashVerificationExtension/${this.CLIENT_VERSION}`
      }
    };

    if (data && (method === 'POST' || method === 'PUT')) {
      options.body = JSON.stringify(data);
    }

    // Add timeout
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.config.timeoutMs);
    options.signal = controller.signal;

    try {
      const response = await fetch(url, options);
      clearTimeout(timeoutId);

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const responseData = await response.json() as ApiResponse<T>;
      return responseData;
    } catch (error) {
      clearTimeout(timeoutId);
      
      if (error instanceof Error && error.name === 'AbortError') {
        throw new Error('Request timeout');
      }
      
      throw error;
    }
  }
}
