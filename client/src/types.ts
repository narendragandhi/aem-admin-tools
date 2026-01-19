export interface ToolDefinition {
  id: string;
  name: string;
  description: string;
  category: string;
  icon: string;
  parameters: ToolParameter[];
  destructive: boolean;
  requiresAem: boolean;
}

export interface ToolParameter {
  name: string;
  label: string;
  description: string;
  type: 'STRING' | 'PATH' | 'NUMBER' | 'BOOLEAN' | 'SELECT' | 'MULTISELECT';
  required: boolean;
  defaultValue?: any;
  options?: string[];
}

export interface Job {
  id: string;
  toolId: string;
  toolName: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  parameters: Record<string, any>;
  startedAt: string | null;
  completedAt: string | null;
  totalItems: number;
  processedItems: number;
  successCount: number;
  errorCount: number;
  skippedCount: number;
  logs: JobLogEntry[];
  results: JobResult[];
  errorMessage?: string;
}

export interface JobLogEntry {
  timestamp: string;
  level: 'INFO' | 'WARN' | 'ERROR' | 'DEBUG';
  message: string;
}

export interface JobResult {
  path: string;
  status: 'SUCCESS' | 'ERROR' | 'SKIPPED';
  message: string;
  details?: Record<string, any>;
}
