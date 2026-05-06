/**
 * Service for queuing hash submissions when network is unavailable
 * Implements requirement 10.1: Queue submissions for retry when backend is unreachable
 */

import { HashSubmission } from '../types';

export interface QueuedSubmission {
  id: string;
  submission: HashSubmission;
  timestamp: Date;
  retryCount: number;
  lastAttempt?: Date;
}

export class SubmissionQueueService {
  private readonly STORAGE_KEY = 'submission_queue';
  private readonly MAX_QUEUE_SIZE = 100;
  private readonly MAX_RETRY_COUNT = 5;
  private readonly RETRY_DELAY_MS = 60000; // 1 minute between retries

  /**
   * Add a submission to the queue
   */
  public async enqueue(submission: HashSubmission): Promise<void> {
    const queue = await this.getQueue();
    
    // Check queue size limit
    if (queue.length >= this.MAX_QUEUE_SIZE) {
      // Remove oldest submission to make room
      queue.shift();
      console.warn('Submission queue full, removing oldest entry');
    }
    
    const queuedSubmission: QueuedSubmission = {
      id: this.generateId(),
      submission,
      timestamp: new Date(),
      retryCount: 0
    };
    
    queue.push(queuedSubmission);
    await this.saveQueue(queue);
    
    console.log(`Queued submission ${queuedSubmission.id} for later retry`);
  }

  /**
   * Get all pending submissions from the queue
   */
  public async getQueue(): Promise<QueuedSubmission[]> {
    try {
      const result = await chrome.storage.local.get(this.STORAGE_KEY);
      const queueData = result[this.STORAGE_KEY];
      
      if (!queueData) {
        return [];
      }
      
      // Parse and validate queue data
      const queue = JSON.parse(queueData) as QueuedSubmission[];
      
      // Convert date strings back to Date objects
      return queue.map(item => ({
        ...item,
        timestamp: new Date(item.timestamp),
        lastAttempt: item.lastAttempt ? new Date(item.lastAttempt) : undefined
      })) as QueuedSubmission[];
    } catch (error) {
      console.error('Failed to load submission queue:', error);
      return [];
    }
  }

  /**
   * Save the queue to storage
   */
  private async saveQueue(queue: QueuedSubmission[]): Promise<void> {
    try {
      const queueData = JSON.stringify(queue);
      await chrome.storage.local.set({ [this.STORAGE_KEY]: queueData });
    } catch (error) {
      console.error('Failed to save submission queue:', error);
      throw error;
    }
  }

  /**
   * Get submissions that are ready for retry
   */
  public async getRetryableSubmissions(): Promise<QueuedSubmission[]> {
    const queue = await this.getQueue();
    const now = new Date();
    
    return queue.filter(item => {
      // Skip if max retries exceeded
      if (item.retryCount >= this.MAX_RETRY_COUNT) {
        return false;
      }
      
      // Include if never attempted or retry delay has passed
      if (!item.lastAttempt) {
        return true;
      }
      
      const timeSinceLastAttempt = now.getTime() - item.lastAttempt.getTime();
      return timeSinceLastAttempt >= this.RETRY_DELAY_MS;
    });
  }

  /**
   * Mark a submission as attempted
   */
  public async markAttempted(submissionId: string): Promise<void> {
    const queue = await this.getQueue();
    const item = queue.find(q => q.id === submissionId);
    
    if (item) {
      item.retryCount++;
      item.lastAttempt = new Date();
      await this.saveQueue(queue);
    }
  }

  /**
   * Remove a submission from the queue (after successful submission)
   */
  public async remove(submissionId: string): Promise<void> {
    const queue = await this.getQueue();
    const filteredQueue = queue.filter(item => item.id !== submissionId);
    await this.saveQueue(filteredQueue);
    
    console.log(`Removed submission ${submissionId} from queue`);
  }

  /**
   * Remove submissions that have exceeded max retry count
   */
  public async cleanupFailedSubmissions(): Promise<number> {
    const queue = await this.getQueue();
    const failedSubmissions = queue.filter(item => item.retryCount >= this.MAX_RETRY_COUNT);
    
    if (failedSubmissions.length > 0) {
      const cleanedQueue = queue.filter(item => item.retryCount < this.MAX_RETRY_COUNT);
      await this.saveQueue(cleanedQueue);
      
      console.log(`Cleaned up ${failedSubmissions.length} failed submissions from queue`);
    }
    
    return failedSubmissions.length;
  }

  /**
   * Get queue statistics
   */
  public async getQueueStats(): Promise<{
    total: number;
    pending: number;
    failed: number;
  }> {
    const queue = await this.getQueue();
    
    return {
      total: queue.length,
      pending: queue.filter(item => item.retryCount < this.MAX_RETRY_COUNT).length,
      failed: queue.filter(item => item.retryCount >= this.MAX_RETRY_COUNT).length
    };
  }

  /**
   * Clear the entire queue (for testing or manual intervention)
   */
  public async clearQueue(): Promise<void> {
    await chrome.storage.local.remove(this.STORAGE_KEY);
    console.log('Submission queue cleared');
  }

  /**
   * Generate a unique ID for queued submissions
   */
  private generateId(): string {
    return `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
  }
}
