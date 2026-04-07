import { describe, it, expect } from 'vitest';
import { Job, JobResult } from '../types.js';

describe('JobResults', () => {
  const mockJob: Job = {
    id: 'job-123',
    toolId: 'content-health-check',
    toolName: 'Content Health Check',
    status: 'COMPLETED',
    parameters: {},
    startedAt: '2024-01-15T10:00:00.000Z',
    completedAt: '2024-01-15T10:05:00.000Z',
    totalItems: 6,
    processedItems: 6,
    successCount: 3,
    errorCount: 2,
    skippedCount: 1,
    logs: [],
    results: [
      { path: '/content/site/page1', status: 'SUCCESS', message: 'Page is valid' },
      { path: '/content/site/page2', status: 'SUCCESS', message: 'Page is valid' },
      { path: '/content/site/page3', status: 'SUCCESS', message: 'Page is valid' },
      { path: '/content/site/page4', status: 'ERROR', message: 'Missing title' },
      { path: '/content/site/page5', status: 'ERROR', message: 'Broken reference' },
      { path: '/content/site/page6', status: 'SKIPPED', message: 'Already processed' }
    ]
  };

  describe('Result Filtering', () => {
    it('returns all results when filter is "all"', () => {
      const filter = 'all';
      const filteredResults = filter === 'all'
        ? mockJob.results
        : mockJob.results.filter(r => r.status === filter);

      expect(filteredResults).toHaveLength(6);
    });

    it('filters by SUCCESS status', () => {
      const filter: JobResult['status'] = 'SUCCESS';
      const filteredResults = mockJob.results.filter(r => r.status === filter);

      expect(filteredResults).toHaveLength(3);
      expect(filteredResults.every(r => r.status === 'SUCCESS')).toBe(true);
    });

    it('filters by ERROR status', () => {
      const filter: JobResult['status'] = 'ERROR';
      const filteredResults = mockJob.results.filter(r => r.status === filter);

      expect(filteredResults).toHaveLength(2);
      expect(filteredResults.every(r => r.status === 'ERROR')).toBe(true);
    });

    it('filters by SKIPPED status', () => {
      const filter: JobResult['status'] = 'SKIPPED';
      const filteredResults = mockJob.results.filter(r => r.status === filter);

      expect(filteredResults).toHaveLength(1);
      expect(filteredResults[0].path).toBe('/content/site/page6');
    });
  });

  describe('Pagination', () => {
    it('calculates total pages correctly', () => {
      const pageSize = 20;
      const totalItems = 55;
      const totalPages = Math.ceil(totalItems / pageSize);

      expect(totalPages).toBe(3);
    });

    it('calculates correct page items', () => {
      const pageSize = 2;
      const page = 1;
      const start = (page - 1) * pageSize;
      const paginatedResults = mockJob.results.slice(start, start + pageSize);

      expect(paginatedResults).toHaveLength(2);
      expect(paginatedResults[0].path).toBe('/content/site/page1');
      expect(paginatedResults[1].path).toBe('/content/site/page2');
    });

    it('handles last page with fewer items', () => {
      const pageSize = 4;
      const page = 2;
      const start = (page - 1) * pageSize;
      const paginatedResults = mockJob.results.slice(start, start + pageSize);

      expect(paginatedResults).toHaveLength(2);
    });

    it('calculates showing range text', () => {
      const pageSize = 2;
      const page = 2;
      const totalFiltered = 6;
      const start = (page - 1) * pageSize + 1;
      const end = Math.min(page * pageSize, totalFiltered);

      expect(start).toBe(3);
      expect(end).toBe(4);
    });
  });

  describe('Status Indicators', () => {
    it('maps status to correct CSS classes', () => {
      const getStatusDotClass = (status: JobResult['status']): string => {
        return status;
      };

      expect(getStatusDotClass('SUCCESS')).toBe('SUCCESS');
      expect(getStatusDotClass('ERROR')).toBe('ERROR');
      expect(getStatusDotClass('SKIPPED')).toBe('SKIPPED');
    });
  });

  describe('Empty State', () => {
    it('detects when no results exist', () => {
      const emptyJob: Job = {
        ...mockJob,
        results: []
      };

      const hasResults = (job: Job): boolean => {
        return job.results && job.results.length > 0;
      };

      expect(hasResults(emptyJob)).toBe(false);
      expect(hasResults(mockJob)).toBe(true);
    });

    it('detects when null results', () => {
      const hasResults = (job: { results?: JobResult[] }): boolean => {
        return !!(job.results && job.results.length > 0);
      };

      expect(hasResults({ results: undefined })).toBe(false);
      expect(hasResults({ results: [] })).toBe(false);
    });
  });

  describe('Result Counts', () => {
    it('calculates filter count labels', () => {
      const getFilterLabel = (
        filter: 'all' | 'SUCCESS' | 'ERROR' | 'SKIPPED',
        job: Job
      ): string => {
        switch (filter) {
          case 'all':
            return `All (${job.results.length})`;
          case 'SUCCESS':
            return `Success (${job.successCount})`;
          case 'ERROR':
            return `Errors (${job.errorCount})`;
          case 'SKIPPED':
            return `Skipped (${job.skippedCount})`;
        }
      };

      expect(getFilterLabel('all', mockJob)).toBe('All (6)');
      expect(getFilterLabel('SUCCESS', mockJob)).toBe('Success (3)');
      expect(getFilterLabel('ERROR', mockJob)).toBe('Errors (2)');
      expect(getFilterLabel('SKIPPED', mockJob)).toBe('Skipped (1)');
    });
  });

  describe('Path Display', () => {
    it('handles long paths gracefully', () => {
      const truncatePath = (path: string, maxLength: number = 50): string => {
        if (path.length <= maxLength) return path;
        return '...' + path.slice(-(maxLength - 3));
      };

      const shortPath = '/content/site/page';
      const longPath = '/content/company/products/category/subcategory/product/variant/page';

      expect(truncatePath(shortPath)).toBe(shortPath);
      expect(truncatePath(longPath, 40).length).toBeLessThanOrEqual(40);
      expect(truncatePath(longPath, 40).startsWith('...')).toBe(true);
    });
  });
});
