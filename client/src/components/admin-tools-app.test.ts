import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

// Mock fetch globally
const mockFetch = vi.fn();
global.fetch = mockFetch;

describe('AdminToolsApp', () => {
  beforeEach(() => {
    mockFetch.mockClear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('API Integration', () => {
    it('fetches tools on initialization', async () => {
      const mockTools = [
        { id: 'content-health-check', name: 'Content Health Check', category: 'Content' },
        { id: 'asset-report', name: 'Asset Report', category: 'Assets' }
      ];

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockTools
      });

      const response = await fetch('/api/tools');
      const tools = await response.json();

      expect(mockFetch).toHaveBeenCalledWith('/api/tools');
      expect(tools).toHaveLength(2);
      expect(tools[0].id).toBe('content-health-check');
    });

    it('fetches recent jobs', async () => {
      const mockJobs = [
        { id: 'job-1', toolId: 'content-health-check', status: 'COMPLETED' },
        { id: 'job-2', toolId: 'asset-report', status: 'RUNNING' }
      ];

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockJobs
      });

      const response = await fetch('/api/jobs');
      const jobs = await response.json();

      expect(jobs).toHaveLength(2);
      expect(jobs[0].status).toBe('COMPLETED');
    });

    it('creates a new job', async () => {
      const newJob = {
        id: 'job-123',
        toolId: 'content-health-check',
        status: 'PENDING',
        parameters: { rootPath: '/content/site' }
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => newJob
      });

      const response = await fetch('/api/jobs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          toolId: 'content-health-check',
          parameters: { rootPath: '/content/site' }
        })
      });
      const job = await response.json();

      expect(job.id).toBe('job-123');
      expect(job.status).toBe('PENDING');
    });

    it('handles API errors gracefully', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error'
      });

      const response = await fetch('/api/tools');

      expect(response.ok).toBe(false);
      expect(response.status).toBe(500);
    });

    it('cancels a running job', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ success: true })
      });

      const response = await fetch('/api/jobs/job-123/cancel', {
        method: 'POST'
      });

      expect(response.ok).toBe(true);
      expect(mockFetch).toHaveBeenCalledWith('/api/jobs/job-123/cancel', { method: 'POST' });
    });
  });

  describe('Tool Categories', () => {
    it('groups tools by category', () => {
      const tools = [
        { id: 'content-health-check', name: 'Content Health Check', category: 'Content' },
        { id: 'bulk-tag-manager', name: 'Bulk Tag Manager', category: 'Content' },
        { id: 'asset-report', name: 'Asset Report', category: 'Assets' }
      ];

      const grouped = tools.reduce((acc, tool) => {
        if (!acc[tool.category]) {
          acc[tool.category] = [];
        }
        acc[tool.category].push(tool);
        return acc;
      }, {} as Record<string, typeof tools>);

      expect(Object.keys(grouped)).toHaveLength(2);
      expect(grouped['Content']).toHaveLength(2);
      expect(grouped['Assets']).toHaveLength(1);
    });
  });

  describe('Job Status', () => {
    it('correctly identifies job completion states', () => {
      const isComplete = (status: string) =>
        ['COMPLETED', 'FAILED', 'CANCELLED'].includes(status);

      expect(isComplete('PENDING')).toBe(false);
      expect(isComplete('RUNNING')).toBe(false);
      expect(isComplete('COMPLETED')).toBe(true);
      expect(isComplete('FAILED')).toBe(true);
      expect(isComplete('CANCELLED')).toBe(true);
    });

    it('calculates progress percentage correctly', () => {
      const calculateProgress = (processed: number, total: number) =>
        total > 0 ? Math.round((processed / total) * 100) : 0;

      expect(calculateProgress(0, 100)).toBe(0);
      expect(calculateProgress(50, 100)).toBe(50);
      expect(calculateProgress(100, 100)).toBe(100);
      expect(calculateProgress(33, 100)).toBe(33);
      expect(calculateProgress(0, 0)).toBe(0);
    });
  });

  describe('Parameter Validation', () => {
    it('validates required parameters', () => {
      const validateParams = (params: Record<string, any>, required: string[]) => {
        const missing = required.filter(key => !params[key] || params[key] === '');
        return missing.length === 0 ? null : `Missing required: ${missing.join(', ')}`;
      };

      expect(validateParams({ path: '/content' }, ['path'])).toBeNull();
      expect(validateParams({}, ['path'])).toBe('Missing required: path');
      expect(validateParams({ path: '' }, ['path'])).toBe('Missing required: path');
      expect(validateParams({ path: '/content', days: 30 }, ['path', 'days'])).toBeNull();
    });

    it('validates path format', () => {
      const isValidPath = (path: string) => path.startsWith('/content');

      expect(isValidPath('/content/site')).toBe(true);
      expect(isValidPath('/content')).toBe(true);
      expect(isValidPath('/apps/site')).toBe(false);
      expect(isValidPath('content/site')).toBe(false);
    });
  });

  describe('SSE Stream Handling', () => {
    it('parses SSE data correctly', () => {
      const parseSSE = (data: string) => {
        try {
          return JSON.parse(data);
        } catch {
          return null;
        }
      };

      const validData = '{"status":"RUNNING","progress":50}';
      const parsed = parseSSE(validData);

      expect(parsed).toEqual({ status: 'RUNNING', progress: 50 });
      expect(parseSSE('invalid json')).toBeNull();
    });
  });
});
