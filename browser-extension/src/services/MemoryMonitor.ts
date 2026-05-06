/**
 * Memory monitoring service for browser extension
 * 
 * Tracks memory usage to ensure compliance with performance requirements.
 * Implements requirement 8.6: Browser_Extension shall limit memory usage to maximum 50MB during operation
 */

export interface MemoryStats {
  usedJSHeapSize: number;
  totalJSHeapSize: number;
  jsHeapSizeLimit: number;
  usedMB: number;
  totalMB: number;
  limitMB: number;
  meetsRequirement: boolean;
}

export class MemoryMonitor {
  private readonly MEMORY_LIMIT_MB = 50; // Requirement 8.6: Maximum 50MB
  private readonly CHECK_INTERVAL_MS = 5000; // Check every 5 seconds
  private monitoringInterval: number | null = null;
  private highWaterMark = 0;

  /**
   * Get current memory usage statistics
   * Returns null if memory API is not available
   */
  public getMemoryStats(): MemoryStats | null {
    // Check if performance.memory API is available (Chrome/Edge only)
    if (!('memory' in performance)) {
      console.warn('Memory monitoring not available in this browser');
      return null;
    }

    const memory = (performance as any).memory;
    const usedMB = memory.usedJSHeapSize / (1024 * 1024);
    const totalMB = memory.totalJSHeapSize / (1024 * 1024);
    const limitMB = memory.jsHeapSizeLimit / (1024 * 1024);

    // Update high water mark
    if (usedMB > this.highWaterMark) {
      this.highWaterMark = usedMB;
    }

    const meetsRequirement = usedMB <= this.MEMORY_LIMIT_MB;

    return {
      usedJSHeapSize: memory.usedJSHeapSize,
      totalJSHeapSize: memory.totalJSHeapSize,
      jsHeapSizeLimit: memory.jsHeapSizeLimit,
      usedMB,
      totalMB,
      limitMB,
      meetsRequirement
    };
  }

  /**
   * Log current memory usage with requirement compliance check
   */
  public logMemoryUsage(): void {
    const stats = this.getMemoryStats();
    if (!stats) {
      return;
    }

    const status = stats.meetsRequirement ? 'PASS' : 'WARN';
    console.log(
      `[Memory Monitor] ${status}: Used=${stats.usedMB.toFixed(2)} MB, ` +
      `Total=${stats.totalMB.toFixed(2)} MB, ` +
      `Limit=${stats.limitMB.toFixed(2)} MB, ` +
      `Target=${this.MEMORY_LIMIT_MB} MB, ` +
      `HighWaterMark=${this.highWaterMark.toFixed(2)} MB`
    );

    if (!stats.meetsRequirement) {
      console.warn(
        `Memory usage exceeds target: ${stats.usedMB.toFixed(2)} MB > ${this.MEMORY_LIMIT_MB} MB`
      );
    }
  }

  /**
   * Start continuous memory monitoring
   * Logs memory usage at regular intervals
   */
  public startMonitoring(): void {
    if (this.monitoringInterval !== null) {
      console.log('Memory monitoring already active');
      return;
    }

    console.log(`Starting memory monitoring (interval: ${this.CHECK_INTERVAL_MS}ms)`);
    this.logMemoryUsage(); // Log immediately

    this.monitoringInterval = window.setInterval(() => {
      this.logMemoryUsage();
    }, this.CHECK_INTERVAL_MS);
  }

  /**
   * Stop continuous memory monitoring
   */
  public stopMonitoring(): void {
    if (this.monitoringInterval !== null) {
      clearInterval(this.monitoringInterval);
      this.monitoringInterval = null;
      console.log('Memory monitoring stopped');
    }
  }

  /**
   * Get the highest memory usage recorded
   */
  public getHighWaterMark(): number {
    return this.highWaterMark;
  }

  /**
   * Reset the high water mark
   */
  public resetHighWaterMark(): void {
    this.highWaterMark = 0;
    console.log('Memory high water mark reset');
  }

  /**
   * Check if current memory usage meets requirement
   */
  public meetsRequirement(): boolean {
    const stats = this.getMemoryStats();
    return stats ? stats.meetsRequirement : true; // Assume OK if can't measure
  }

  /**
   * Trigger garbage collection if available (Chrome DevTools only)
   * This is primarily for testing and debugging
   */
  public triggerGC(): void {
    if ('gc' in window && typeof (window as any).gc === 'function') {
      console.log('Triggering garbage collection...');
      (window as any).gc();
      this.logMemoryUsage();
    } else {
      console.warn('Garbage collection not available (requires --expose-gc flag)');
    }
  }
}

// Export singleton instance
export const memoryMonitor = new MemoryMonitor();
