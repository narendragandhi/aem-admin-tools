import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { ToolDefinition, Job } from '../types.js';

@customElement('admin-tools-app')
export class AdminToolsApp extends LitElement {
  static styles = css`
    :host {
      display: block;
      height: 100vh;
    }

    .app-container {
      display: flex;
      height: 100%;
    }

    .sidebar {
      width: 280px;
      background: var(--spectrum-gray-100);
      border-right: 1px solid var(--spectrum-gray-300);
      display: flex;
      flex-direction: column;
    }

    .sidebar-header {
      padding: 20px;
      border-bottom: 1px solid var(--spectrum-gray-300);
    }

    .sidebar-header h1 {
      margin: 0;
      font-size: 18px;
      color: var(--spectrum-gray-800);
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .sidebar-header h1::before {
      content: '';
      display: inline-block;
      width: 24px;
      height: 24px;
      background: linear-gradient(135deg, #eb1000 0%, #ff4d4d 100%);
      border-radius: 4px;
    }

    .sidebar-content {
      flex: 1;
      overflow-y: auto;
      padding: 12px;
    }

    .main-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    .header {
      padding: 16px 24px;
      border-bottom: 1px solid var(--spectrum-gray-300);
      background: white;
    }

    .header h2 {
      margin: 0;
      font-size: 20px;
      font-weight: 600;
    }

    .content {
      flex: 1;
      overflow-y: auto;
      padding: 24px;
      background: var(--spectrum-gray-50);
    }

    .content-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 24px;
      max-width: 1400px;
    }

    .panel {
      background: white;
      border-radius: 8px;
      border: 1px solid var(--spectrum-gray-200);
      padding: 20px;
    }

    .panel-title {
      font-size: 16px;
      font-weight: 600;
      margin: 0 0 16px 0;
      color: var(--spectrum-gray-800);
    }

    .full-width {
      grid-column: 1 / -1;
    }

    .category-header {
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: var(--spectrum-gray-600);
      padding: 12px 12px 8px;
      margin-top: 8px;
    }

    .category-header:first-child {
      margin-top: 0;
    }
  `;

  @state() private tools: ToolDefinition[] = [];
  @state() private selectedTool: ToolDefinition | null = null;
  @state() private currentJob: Job | null = null;
  @state() private recentJobs: Job[] = [];

  connectedCallback() {
    super.connectedCallback();
    this.loadTools();
    this.loadRecentJobs();
  }

  async loadTools() {
    try {
      const response = await fetch('/api/tools');
      this.tools = await response.json();
    } catch (error) {
      console.error('Failed to load tools:', error);
    }
  }

  async loadRecentJobs() {
    try {
      const response = await fetch('/api/jobs?limit=10');
      this.recentJobs = await response.json();
    } catch (error) {
      console.error('Failed to load jobs:', error);
    }
  }

  private handleToolSelect(e: CustomEvent) {
    const toolId = e.detail.toolId;
    this.selectedTool = this.tools.find(t => t.id === toolId) || null;
    this.currentJob = null;
  }

  private handleJobStarted(e: CustomEvent) {
    this.currentJob = e.detail.job;
  }

  private handleJobCompleted() {
    this.loadRecentJobs();
  }

  private getToolsByCategory(): Map<string, ToolDefinition[]> {
    const categories = new Map<string, ToolDefinition[]>();
    for (const tool of this.tools) {
      const category = tool.category || 'Other';
      if (!categories.has(category)) {
        categories.set(category, []);
      }
      categories.get(category)!.push(tool);
    }
    return categories;
  }

  render() {
    const toolsByCategory = this.getToolsByCategory();

    return html`
      <sp-theme scale="medium" color="light">
        <div class="app-container">
          <div class="sidebar">
            <div class="sidebar-header">
              <h1>AEM Admin Tools</h1>
            </div>
            <div class="sidebar-content">
              <sp-sidenav @change=${(e: Event) => {
                const target = e.target as HTMLElement;
                const value = (target as any).value;
                if (value) {
                  this.handleToolSelect(new CustomEvent('select', { detail: { toolId: value }}));
                }
              }}>
                ${Array.from(toolsByCategory.entries()).map(([category, categoryTools]) => html`
                  <div class="category-header">${category}</div>
                  ${categoryTools.map(tool => html`
                    <sp-sidenav-item
                      value="${tool.id}"
                      label="${tool.name}"
                      ?selected=${this.selectedTool?.id === tool.id}
                    ></sp-sidenav-item>
                  `)}
                `)}
              </sp-sidenav>
            </div>
          </div>

          <div class="main-content">
            <div class="header">
              <h2>${this.selectedTool ? this.selectedTool.name : 'Select a Tool'}</h2>
            </div>

            <div class="content">
              ${this.selectedTool ? html`
                <div class="content-grid">
                  <div class="panel">
                    <h3 class="panel-title">Configuration</h3>
                    <tool-form
                      .tool=${this.selectedTool}
                      @job-started=${this.handleJobStarted}
                    ></tool-form>
                  </div>

                  <div class="panel">
                    <h3 class="panel-title">Progress</h3>
                    <job-progress
                      .job=${this.currentJob}
                      @job-completed=${this.handleJobCompleted}
                    ></job-progress>
                  </div>

                  ${this.currentJob ? html`
                    <div class="panel full-width">
                      <h3 class="panel-title">Results</h3>
                      <job-results .job=${this.currentJob}></job-results>
                    </div>
                  ` : ''}
                </div>
              ` : html`
                <tool-list
                  .tools=${this.tools}
                  @tool-selected=${this.handleToolSelect}
                ></tool-list>
              `}

              <div class="panel full-width" style="margin-top: 24px;">
                <h3 class="panel-title">Recent Jobs</h3>
                <job-history
                  .jobs=${this.recentJobs}
                  @job-selected=${(e: CustomEvent) => {
                    this.currentJob = e.detail.job;
                    this.selectedTool = this.tools.find(t => t.id === e.detail.job.toolId) || null;
                  }}
                ></job-history>
              </div>
            </div>
          </div>
        </div>
      </sp-theme>
    `;
  }
}
