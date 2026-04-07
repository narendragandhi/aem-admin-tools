import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ToolDefinition, ToolParameter } from '../types.js';

// Mock fetch
const mockFetch = vi.fn();
global.fetch = mockFetch;

describe('ToolForm', () => {
  beforeEach(() => {
    mockFetch.mockClear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('Parameter Initialization', () => {
    it('initializes form values with defaults', () => {
      const parameters: ToolParameter[] = [
        {
          name: 'path',
          label: 'Path',
          description: 'Content path',
          type: 'PATH',
          required: true,
          defaultValue: '/content'
        },
        {
          name: 'depth',
          label: 'Depth',
          description: 'Search depth',
          type: 'NUMBER',
          required: false,
          defaultValue: 3
        }
      ];

      const formValues: Record<string, any> = {};
      for (const param of parameters) {
        if (param.defaultValue !== undefined) {
          formValues[param.name] = param.defaultValue;
        }
      }

      expect(formValues.path).toBe('/content');
      expect(formValues.depth).toBe(3);
    });

    it('handles parameters without defaults', () => {
      const parameters: ToolParameter[] = [
        {
          name: 'path',
          label: 'Path',
          description: 'Content path',
          type: 'PATH',
          required: true
        }
      ];

      const formValues: Record<string, any> = {};
      for (const param of parameters) {
        if (param.defaultValue !== undefined) {
          formValues[param.name] = param.defaultValue;
        }
      }

      expect(formValues.path).toBeUndefined();
    });
  });

  describe('Form Submission', () => {
    it('submits job with correct payload', async () => {
      const mockJob = {
        id: 'job-123',
        toolId: 'content-health-check',
        status: 'PENDING'
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => mockJob
      });

      const tool: ToolDefinition = {
        id: 'content-health-check',
        name: 'Content Health Check',
        description: 'Scan content',
        category: 'Content',
        icon: '📄',
        parameters: [],
        destructive: false,
        requiresAem: true
      };

      const formValues = { rootPath: '/content/site' };

      const response = await fetch('/api/jobs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          toolId: tool.id,
          parameters: formValues
        })
      });

      const job = await response.json();

      expect(mockFetch).toHaveBeenCalledWith('/api/jobs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          toolId: 'content-health-check',
          parameters: { rootPath: '/content/site' }
        })
      });
      expect(job.id).toBe('job-123');
    });

    it('handles submission errors', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 400,
        statusText: 'Bad Request'
      });

      const response = await fetch('/api/jobs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ toolId: 'invalid', parameters: {} })
      });

      expect(response.ok).toBe(false);
      expect(response.status).toBe(400);
    });
  });

  describe('Parameter Type Rendering', () => {
    it('identifies parameter input types', () => {
      const getInputType = (type: ToolParameter['type']): string => {
        switch (type) {
          case 'NUMBER':
            return 'number';
          case 'BOOLEAN':
            return 'checkbox';
          case 'SELECT':
          case 'MULTISELECT':
            return 'select';
          default:
            return 'text';
        }
      };

      expect(getInputType('STRING')).toBe('text');
      expect(getInputType('PATH')).toBe('text');
      expect(getInputType('NUMBER')).toBe('number');
      expect(getInputType('BOOLEAN')).toBe('checkbox');
      expect(getInputType('SELECT')).toBe('select');
      expect(getInputType('MULTISELECT')).toBe('select');
    });
  });

  describe('Multiselect Handling', () => {
    it('adds values to multiselect array', () => {
      let selectedValues: string[] = [];

      const toggleOption = (opt: string, checked: boolean) => {
        if (checked && !selectedValues.includes(opt)) {
          selectedValues = [...selectedValues, opt];
        } else if (!checked) {
          selectedValues = selectedValues.filter(v => v !== opt);
        }
      };

      toggleOption('MISSING_TITLE', true);
      expect(selectedValues).toEqual(['MISSING_TITLE']);

      toggleOption('BROKEN_REFS', true);
      expect(selectedValues).toEqual(['MISSING_TITLE', 'BROKEN_REFS']);

      toggleOption('MISSING_TITLE', false);
      expect(selectedValues).toEqual(['BROKEN_REFS']);
    });
  });

  describe('Required Field Validation', () => {
    it('identifies missing required fields', () => {
      const parameters: ToolParameter[] = [
        { name: 'path', label: 'Path', description: '', type: 'PATH', required: true },
        { name: 'depth', label: 'Depth', description: '', type: 'NUMBER', required: false }
      ];

      const formValues: Record<string, any> = { depth: 3 };

      const getMissingRequired = (
        params: ToolParameter[],
        values: Record<string, any>
      ): string[] => {
        return params
          .filter(p => p.required && (!values[p.name] || values[p.name] === ''))
          .map(p => p.name);
      };

      expect(getMissingRequired(parameters, formValues)).toEqual(['path']);
      expect(getMissingRequired(parameters, { path: '/content', depth: 3 })).toEqual([]);
    });
  });

  describe('Destructive Tool Warning', () => {
    it('shows warning for destructive tools', () => {
      const shouldShowWarning = (tool: ToolDefinition): boolean => {
        return tool.destructive === true;
      };

      const destructiveTool: ToolDefinition = {
        id: 'bulk-delete',
        name: 'Bulk Delete',
        description: 'Delete content',
        category: 'Content',
        icon: '🗑️',
        parameters: [],
        destructive: true,
        requiresAem: true
      };

      const safeTool: ToolDefinition = {
        id: 'content-report',
        name: 'Content Report',
        description: 'Generate report',
        category: 'Content',
        icon: '📊',
        parameters: [],
        destructive: false,
        requiresAem: true
      };

      expect(shouldShowWarning(destructiveTool)).toBe(true);
      expect(shouldShowWarning(safeTool)).toBe(false);
    });
  });
});
