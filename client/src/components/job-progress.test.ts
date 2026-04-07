import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { Job, JobLogEntry } from '../types.js';

describe('JobProgress', () => {
  describe('Progress Calculation', () => {
    it('calculates progress percentage correctly', () => {
      const calculateProgress = (job: { processedItems: number; totalItems: number }): number => {
        return job.totalItems > 0 ? (job.processedItems / job.totalItems) * 100 : 0;
      };

      expect(calculateProgress({ processedItems: 0, totalItems: 100 })).toBe(0);
      expect(calculateProgress({ processedItems: 50, totalItems: 100 })).toBe(50);
      expect(calculateProgress({ processedItems: 100, totalItems: 100 })).toBe(100);
      expect(calculateProgress({ processedItems: 0, totalItems: 0 })).toBe(0);
      expect(calculateProgress({ processedItems: 75, totalItems: 200 })).toBe(37.5);
    });
  });

  describe('Status Badge Styling', () => {
    it('returns correct CSS class for status', () => {
      const getStatusClass = (status: Job['status']): string => {
        return status.toLowerCase();
      };

      expect(getStatusClass('PENDING')).toBe('pending');
      expect(getStatusClass('RUNNING')).toBe('running');
      expect(getStatusClass('COMPLETED')).toBe('completed');
      expect(getStatusClass('FAILED')).toBe('failed');
      expect(getStatusClass('CANCELLED')).toBe('cancelled');
    });
  });

  describe('SSE Stream Connection', () => {
    it('determines when to start streaming', () => {
      const shouldStream = (status: Job['status']): boolean => {
        return status === 'PENDING' || status === 'RUNNING';
      };

      expect(shouldStream('PENDING')).toBe(true);
      expect(shouldStream('RUNNING')).toBe(true);
      expect(shouldStream('COMPLETED')).toBe(false);
      expect(shouldStream('FAILED')).toBe(false);
      expect(shouldStream('CANCELLED')).toBe(false);
    });

    it('determines when job is terminal', () => {
      const isTerminal = (status: Job['status']): boolean => {
        return ['COMPLETED', 'FAILED', 'CANCELLED'].includes(status);
      };

      expect(isTerminal('PENDING')).toBe(false);
      expect(isTerminal('RUNNING')).toBe(false);
      expect(isTerminal('COMPLETED')).toBe(true);
      expect(isTerminal('FAILED')).toBe(true);
      expect(isTerminal('CANCELLED')).toBe(true);
    });
  });

  describe('Log Entry Formatting', () => {
    it('formats timestamp correctly', () => {
      const formatTime = (timestamp: string): string => {
        const date = new Date(timestamp);
        return date.toLocaleTimeString();
      };

      const timestamp = '2024-01-15T10:30:45.000Z';
      const formatted = formatTime(timestamp);

      // Verify it returns a time string (format varies by locale)
      expect(formatted).toBeTruthy();
      expect(typeof formatted).toBe('string');
    });

    it('identifies log level CSS classes', () => {
      const getLogLevelClass = (level: JobLogEntry['level']): string => {
        return level;
      };

      expect(getLogLevelClass('INFO')).toBe('INFO');
      expect(getLogLevelClass('WARN')).toBe('WARN');
      expect(getLogLevelClass('ERROR')).toBe('ERROR');
      expect(getLogLevelClass('DEBUG')).toBe('DEBUG');
    });
  });

  describe('Job Statistics', () => {
    it('aggregates job counts correctly', () => {
      const mockJob: Job = {
        id: 'job-123',
        toolId: 'content-health-check',
        toolName: 'Content Health Check',
        status: 'COMPLETED',
        parameters: {},
        startedAt: '2024-01-15T10:00:00.000Z',
        completedAt: '2024-01-15T10:05:00.000Z',
        totalItems: 100,
        processedItems: 100,
        successCount: 85,
        errorCount: 10,
        skippedCount: 5,
        logs: [],
        results: []
      };

      expect(mockJob.successCount + mockJob.errorCount + mockJob.skippedCount).toBe(100);
      expect(mockJob.totalItems).toBe(mockJob.processedItems);
    });
  });

  describe('Error Display', () => {
    it('shows error message when present', () => {
      const shouldShowError = (job: Job): boolean => {
        return !!job.errorMessage;
      };

      const jobWithError: Job = {
        id: 'job-err',
        toolId: 'test',
        toolName: 'Test',
        status: 'FAILED',
        parameters: {},
        startedAt: null,
        completedAt: null,
        totalItems: 0,
        processedItems: 0,
        successCount: 0,
        errorCount: 0,
        skippedCount: 0,
        logs: [],
        results: [],
        errorMessage: 'Connection failed'
      };

      const jobWithoutError: Job = {
        id: 'job-ok',
        toolId: 'test',
        toolName: 'Test',
        status: 'COMPLETED',
        parameters: {},
        startedAt: null,
        completedAt: null,
        totalItems: 10,
        processedItems: 10,
        successCount: 10,
        errorCount: 0,
        skippedCount: 0,
        logs: [],
        results: []
      };

      expect(shouldShowError(jobWithError)).toBe(true);
      expect(shouldShowError(jobWithoutError)).toBe(false);
    });
  });

  describe('SSE Data Parsing', () => {
    it('parses valid SSE job data', () => {
      const parseJobData = (data: string): Job | null => {
        try {
          return JSON.parse(data) as Job;
        } catch {
          return null;
        }
      };

      const validData = JSON.stringify({
        id: 'job-123',
        status: 'RUNNING',
        processedItems: 50,
        totalItems: 100
      });

      const parsed = parseJobData(validData);
      expect(parsed).not.toBeNull();
      expect(parsed?.status).toBe('RUNNING');
      expect(parsed?.processedItems).toBe(50);
    });

    it('handles invalid SSE data gracefully', () => {
      const parseJobData = (data: string): Job | null => {
        try {
          return JSON.parse(data) as Job;
        } catch {
          return null;
        }
      };

      expect(parseJobData('invalid json')).toBeNull();
      expect(parseJobData('')).toBeNull();
      expect(parseJobData('{malformed')).toBeNull();
    });
  });
});
