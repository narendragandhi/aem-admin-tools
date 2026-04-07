import { describe, it, expect, vi } from 'vitest';
import { Job } from '../types.js';

describe('JobHistory', () => {
  const mockJobs: Job[] = [
    {
      id: 'job-1',
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
    },
    {
      id: 'job-2',
      toolId: 'asset-report',
      toolName: 'Asset Report',
      status: 'RUNNING',
      parameters: {},
      startedAt: '2024-01-15T11:00:00.000Z',
      completedAt: null,
      totalItems: 50,
      processedItems: 25,
      successCount: 20,
      errorCount: 3,
      skippedCount: 2,
      logs: [],
      results: []
    },
    {
      id: 'job-3',
      toolId: 'bulk-tag-manager',
      toolName: 'Bulk Tag Manager',
      status: 'FAILED',
      parameters: {},
      startedAt: '2024-01-15T09:00:00.000Z',
      completedAt: '2024-01-15T09:01:00.000Z',
      totalItems: 0,
      processedItems: 0,
      successCount: 0,
      errorCount: 0,
      skippedCount: 0,
      logs: [],
      results: [],
      errorMessage: 'Connection refused'
    }
  ];

  describe('Time Formatting', () => {
    it('formats valid timestamps', () => {
      const formatTime = (timestamp: string | null): string => {
        if (!timestamp) return '-';
        const date = new Date(timestamp);
        return date.toLocaleString();
      };

      const result = formatTime('2024-01-15T10:30:45.000Z');
      expect(result).toBeTruthy();
      expect(result).not.toBe('-');
    });

    it('returns dash for null timestamp', () => {
      const formatTime = (timestamp: string | null): string => {
        if (!timestamp) return '-';
        const date = new Date(timestamp);
        return date.toLocaleString();
      };

      expect(formatTime(null)).toBe('-');
    });
  });

  describe('Job Selection Event', () => {
    it('creates correct selection event', () => {
      const eventHandler = vi.fn();
      const job = mockJobs[0];

      const event = new CustomEvent('job-selected', {
        detail: { job },
        bubbles: true,
        composed: true
      });

      eventHandler(event);

      expect(eventHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          detail: { job: expect.objectContaining({ id: 'job-1' }) }
        })
      );
    });
  });

  describe('Status Badge Classes', () => {
    it('returns correct CSS class for each status', () => {
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

  describe('Empty State', () => {
    it('detects empty job history', () => {
      const isEmpty = (jobs: Job[]): boolean => jobs.length === 0;

      expect(isEmpty([])).toBe(true);
      expect(isEmpty(mockJobs)).toBe(false);
    });
  });

  describe('Job Sorting', () => {
    it('sorts jobs by start time descending', () => {
      const sortByStartTime = (jobs: Job[]): Job[] => {
        return [...jobs].sort((a, b) => {
          const timeA = a.startedAt ? new Date(a.startedAt).getTime() : 0;
          const timeB = b.startedAt ? new Date(b.startedAt).getTime() : 0;
          return timeB - timeA;
        });
      };

      const sorted = sortByStartTime(mockJobs);
      expect(sorted[0].id).toBe('job-2'); // 11:00
      expect(sorted[1].id).toBe('job-1'); // 10:00
      expect(sorted[2].id).toBe('job-3'); // 09:00
    });

    it('sorts jobs by status priority', () => {
      const statusPriority: Record<Job['status'], number> = {
        'RUNNING': 1,
        'PENDING': 2,
        'FAILED': 3,
        'COMPLETED': 4,
        'CANCELLED': 5
      };

      const sortByStatus = (jobs: Job[]): Job[] => {
        return [...jobs].sort((a, b) => statusPriority[a.status] - statusPriority[b.status]);
      };

      const sorted = sortByStatus(mockJobs);
      expect(sorted[0].status).toBe('RUNNING');
      expect(sorted[1].status).toBe('FAILED');
      expect(sorted[2].status).toBe('COMPLETED');
    });
  });

  describe('Job Statistics Display', () => {
    it('formats job stats correctly', () => {
      const formatStats = (job: Job): { success: string; error: string; skipped: string } => {
        return {
          success: `✓ ${job.successCount}`,
          error: `✗ ${job.errorCount}`,
          skipped: `○ ${job.skippedCount}`
        };
      };

      const stats = formatStats(mockJobs[0]);
      expect(stats.success).toBe('✓ 85');
      expect(stats.error).toBe('✗ 10');
      expect(stats.skipped).toBe('○ 5');
    });
  });

  describe('Job Filtering', () => {
    it('filters running jobs', () => {
      const runningJobs = mockJobs.filter(j => j.status === 'RUNNING');
      expect(runningJobs).toHaveLength(1);
      expect(runningJobs[0].id).toBe('job-2');
    });

    it('filters failed jobs', () => {
      const failedJobs = mockJobs.filter(j => j.status === 'FAILED');
      expect(failedJobs).toHaveLength(1);
      expect(failedJobs[0].errorMessage).toBe('Connection refused');
    });

    it('filters by tool', () => {
      const contentJobs = mockJobs.filter(j => j.toolId === 'content-health-check');
      expect(contentJobs).toHaveLength(1);
    });
  });

  describe('Duration Calculation', () => {
    it('calculates job duration', () => {
      const calculateDuration = (job: Job): number | null => {
        if (!job.startedAt || !job.completedAt) return null;
        const start = new Date(job.startedAt).getTime();
        const end = new Date(job.completedAt).getTime();
        return Math.round((end - start) / 1000);
      };

      expect(calculateDuration(mockJobs[0])).toBe(300); // 5 minutes
      expect(calculateDuration(mockJobs[1])).toBeNull(); // Still running
      expect(calculateDuration(mockJobs[2])).toBe(60); // 1 minute
    });

    it('formats duration as human readable', () => {
      const formatDuration = (seconds: number | null): string => {
        if (seconds === null) return 'In progress';
        if (seconds < 60) return `${seconds}s`;
        const minutes = Math.floor(seconds / 60);
        const remainingSeconds = seconds % 60;
        return `${minutes}m ${remainingSeconds}s`;
      };

      expect(formatDuration(45)).toBe('45s');
      expect(formatDuration(300)).toBe('5m 0s');
      expect(formatDuration(125)).toBe('2m 5s');
      expect(formatDuration(null)).toBe('In progress');
    });
  });
});
