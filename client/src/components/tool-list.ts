import { LitElement, html, css } from 'lit';
import { customElement, property } from 'lit/decorators.js';
import { ToolDefinition } from '../types.js';

@customElement('tool-list')
export class ToolList extends LitElement {
  static styles = css`
    :host {
      display: block;
    }

    .tools-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 16px;
    }

    .tool-card {
      background: white;
      border: 1px solid var(--spectrum-gray-200);
      border-radius: 8px;
      padding: 20px;
      cursor: pointer;
      transition: all 0.2s ease;
    }

    .tool-card:hover {
      border-color: var(--spectrum-blue-500);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    }

    .tool-header {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      margin-bottom: 12px;
    }

    .tool-icon {
      width: 40px;
      height: 40px;
      background: var(--spectrum-gray-100);
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
    }

    .tool-info h3 {
      margin: 0 0 4px 0;
      font-size: 16px;
      font-weight: 600;
    }

    .tool-category {
      font-size: 11px;
      color: var(--spectrum-gray-600);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .tool-description {
      color: var(--spectrum-gray-700);
      font-size: 14px;
      line-height: 1.5;
      margin: 0;
    }

    .tool-badges {
      display: flex;
      gap: 8px;
      margin-top: 12px;
    }

    .badge {
      font-size: 11px;
      padding: 2px 8px;
      border-radius: 4px;
      background: var(--spectrum-gray-100);
      color: var(--spectrum-gray-700);
    }

    .badge.destructive {
      background: var(--spectrum-red-100);
      color: var(--spectrum-red-700);
    }

    .badge.requires-aem {
      background: var(--spectrum-blue-100);
      color: var(--spectrum-blue-700);
    }

    .welcome {
      text-align: center;
      padding: 40px;
      color: var(--spectrum-gray-700);
    }

    .welcome h2 {
      margin: 0 0 8px 0;
      color: var(--spectrum-gray-800);
    }
  `;

  @property({ type: Array }) tools: ToolDefinition[] = [];

  private getIconForCategory(category: string): string {
    const icons: Record<string, string> = {
      'Content': '📄',
      'Assets': '🖼️',
      'Workflows': '⚙️',
      'Security': '🔒',
      'Performance': '⚡',
    };
    return icons[category] || '🔧';
  }

  private handleToolClick(tool: ToolDefinition) {
    this.dispatchEvent(new CustomEvent('tool-selected', {
      detail: { toolId: tool.id },
      bubbles: true,
      composed: true
    }));
  }

  render() {
    if (this.tools.length === 0) {
      return html`
        <div class="welcome">
          <h2>Welcome to AEM Admin Tools</h2>
          <p>Loading available tools...</p>
        </div>
      `;
    }

    return html`
      <div class="tools-grid">
        ${this.tools.map(tool => html`
          <div class="tool-card" @click=${() => this.handleToolClick(tool)}>
            <div class="tool-header">
              <div class="tool-icon">${this.getIconForCategory(tool.category)}</div>
              <div class="tool-info">
                <h3>${tool.name}</h3>
                <span class="tool-category">${tool.category}</span>
              </div>
            </div>
            <p class="tool-description">${tool.description}</p>
            <div class="tool-badges">
              ${tool.destructive ? html`<span class="badge destructive">Destructive</span>` : ''}
              ${tool.requiresAem ? html`<span class="badge requires-aem">Requires AEM</span>` : ''}
            </div>
          </div>
        `)}
      </div>
    `;
  }
}
