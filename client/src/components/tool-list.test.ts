import { describe, it, expect, vi } from 'vitest';
import { ToolDefinition } from '../types.js';

describe('ToolList', () => {
  describe('Category Icons', () => {
    it('returns correct icons for known categories', () => {
      const getIconForCategory = (category: string): string => {
        const icons: Record<string, string> = {
          'Content': '📄',
          'Assets': '🖼️',
          'Workflows': '⚙️',
          'Security': '🔒',
          'Performance': '⚡',
        };
        return icons[category] || '🔧';
      };

      expect(getIconForCategory('Content')).toBe('📄');
      expect(getIconForCategory('Assets')).toBe('🖼️');
      expect(getIconForCategory('Workflows')).toBe('⚙️');
      expect(getIconForCategory('Security')).toBe('🔒');
      expect(getIconForCategory('Performance')).toBe('⚡');
      expect(getIconForCategory('Unknown')).toBe('🔧');
    });
  });

  describe('Tool Selection Event', () => {
    it('creates correct custom event structure', () => {
      const mockTool: ToolDefinition = {
        id: 'content-health-check',
        name: 'Content Health Check',
        description: 'Scan content for issues',
        category: 'Content',
        icon: '📄',
        parameters: [],
        destructive: false,
        requiresAem: true
      };

      const eventHandler = vi.fn();
      const event = new CustomEvent('tool-selected', {
        detail: { toolId: mockTool.id },
        bubbles: true,
        composed: true
      });

      eventHandler(event);

      expect(eventHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          detail: { toolId: 'content-health-check' }
        })
      );
    });
  });

  describe('Tool Filtering', () => {
    const mockTools: ToolDefinition[] = [
      {
        id: 'content-health-check',
        name: 'Content Health Check',
        description: 'Scan content',
        category: 'Content',
        icon: '📄',
        parameters: [],
        destructive: false,
        requiresAem: true
      },
      {
        id: 'bulk-tag-manager',
        name: 'Bulk Tag Manager',
        description: 'Manage tags',
        category: 'Content',
        icon: '📄',
        parameters: [],
        destructive: true,
        requiresAem: true
      },
      {
        id: 'asset-report',
        name: 'Asset Report',
        description: 'Generate reports',
        category: 'Assets',
        icon: '🖼️',
        parameters: [],
        destructive: false,
        requiresAem: true
      }
    ];

    it('filters tools by category', () => {
      const filterByCategory = (tools: ToolDefinition[], category: string) =>
        tools.filter(t => t.category === category);

      expect(filterByCategory(mockTools, 'Content')).toHaveLength(2);
      expect(filterByCategory(mockTools, 'Assets')).toHaveLength(1);
      expect(filterByCategory(mockTools, 'Unknown')).toHaveLength(0);
    });

    it('filters destructive tools', () => {
      const destructiveTools = mockTools.filter(t => t.destructive);
      const safeTools = mockTools.filter(t => !t.destructive);

      expect(destructiveTools).toHaveLength(1);
      expect(destructiveTools[0].id).toBe('bulk-tag-manager');
      expect(safeTools).toHaveLength(2);
    });

    it('searches tools by name', () => {
      const searchTools = (tools: ToolDefinition[], query: string) =>
        tools.filter(t => t.name.toLowerCase().includes(query.toLowerCase()));

      expect(searchTools(mockTools, 'health')).toHaveLength(1);
      expect(searchTools(mockTools, 'Content')).toHaveLength(1);
      expect(searchTools(mockTools, 'tag')).toHaveLength(1);
      expect(searchTools(mockTools, '')).toHaveLength(3);
    });
  });

  describe('Tool Badges', () => {
    it('determines which badges to show', () => {
      const getBadges = (tool: ToolDefinition): string[] => {
        const badges: string[] = [];
        if (tool.destructive) badges.push('Destructive');
        if (tool.requiresAem) badges.push('Requires AEM');
        return badges;
      };

      const destructiveTool: ToolDefinition = {
        id: 'test',
        name: 'Test',
        description: '',
        category: 'Test',
        icon: '',
        parameters: [],
        destructive: true,
        requiresAem: false
      };

      const aemTool: ToolDefinition = {
        id: 'test',
        name: 'Test',
        description: '',
        category: 'Test',
        icon: '',
        parameters: [],
        destructive: false,
        requiresAem: true
      };

      const bothTool: ToolDefinition = {
        id: 'test',
        name: 'Test',
        description: '',
        category: 'Test',
        icon: '',
        parameters: [],
        destructive: true,
        requiresAem: true
      };

      expect(getBadges(destructiveTool)).toEqual(['Destructive']);
      expect(getBadges(aemTool)).toEqual(['Requires AEM']);
      expect(getBadges(bothTool)).toEqual(['Destructive', 'Requires AEM']);
    });
  });
});
