import { LitElement, html, css } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { ToolDefinition, ToolParameter } from '../types.js';

@customElement('tool-form')
export class ToolForm extends LitElement {
  static styles = css`
    :host {
      display: block;
    }

    .form-group {
      margin-bottom: 20px;
    }

    .form-label {
      display: block;
      font-size: 14px;
      font-weight: 500;
      margin-bottom: 6px;
      color: var(--spectrum-gray-800);
    }

    .form-description {
      font-size: 12px;
      color: var(--spectrum-gray-600);
      margin-bottom: 8px;
    }

    .checkbox-group {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .actions {
      display: flex;
      gap: 12px;
      margin-top: 24px;
      padding-top: 16px;
      border-top: 1px solid var(--spectrum-gray-200);
    }

    .destructive-warning {
      background: var(--spectrum-red-100);
      border: 1px solid var(--spectrum-red-300);
      border-radius: 4px;
      padding: 12px;
      margin-bottom: 16px;
      font-size: 13px;
      color: var(--spectrum-red-800);
    }

    sp-textfield, sp-picker {
      width: 100%;
    }
  `;

  @property({ type: Object }) tool: ToolDefinition | null = null;
  @state() private formValues: Record<string, any> = {};
  @state() private isSubmitting = false;

  updated(changedProperties: Map<string, any>) {
    if (changedProperties.has('tool') && this.tool) {
      // Initialize form with default values
      this.formValues = {};
      for (const param of this.tool.parameters || []) {
        if (param.defaultValue !== undefined) {
          this.formValues[param.name] = param.defaultValue;
        }
      }
    }
  }

  private handleInputChange(name: string, value: any) {
    this.formValues = { ...this.formValues, [name]: value };
  }

  private async handleSubmit() {
    if (!this.tool || this.isSubmitting) return;

    this.isSubmitting = true;

    try {
      const response = await fetch('/api/jobs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          toolId: this.tool.id,
          parameters: this.formValues
        })
      });

      if (!response.ok) {
        throw new Error('Failed to start job');
      }

      const job = await response.json();

      this.dispatchEvent(new CustomEvent('job-started', {
        detail: { job },
        bubbles: true,
        composed: true
      }));
    } catch (error) {
      console.error('Failed to start job:', error);
    } finally {
      this.isSubmitting = false;
    }
  }

  private renderParameter(param: ToolParameter) {
    const value = this.formValues[param.name];

    switch (param.type) {
      case 'STRING':
      case 'PATH':
        return html`
          <sp-textfield
            placeholder="${param.type === 'PATH' ? '/content/...' : ''}"
            value="${value || ''}"
            @input=${(e: Event) => this.handleInputChange(param.name, (e.target as HTMLInputElement).value)}
          ></sp-textfield>
        `;

      case 'NUMBER':
        return html`
          <sp-textfield
            type="number"
            value="${value || ''}"
            @input=${(e: Event) => this.handleInputChange(param.name, parseInt((e.target as HTMLInputElement).value))}
          ></sp-textfield>
        `;

      case 'BOOLEAN':
        return html`
          <sp-checkbox
            ?checked=${value === true}
            @change=${(e: Event) => this.handleInputChange(param.name, (e.target as HTMLInputElement).checked)}
          >${param.label}</sp-checkbox>
        `;

      case 'SELECT':
        return html`
          <sp-picker
            value="${value || ''}"
            @change=${(e: Event) => this.handleInputChange(param.name, (e.target as any).value)}
          >
            ${(param.options || []).map(opt => html`
              <sp-menu-item value="${opt}">${opt}</sp-menu-item>
            `)}
          </sp-picker>
        `;

      case 'MULTISELECT':
        return html`
          <div class="checkbox-group">
            ${(param.options || []).map(opt => html`
              <sp-checkbox
                ?checked=${Array.isArray(value) && value.includes(opt)}
                @change=${(e: Event) => {
                  const checked = (e.target as HTMLInputElement).checked;
                  const current = Array.isArray(value) ? [...value] : [];
                  if (checked && !current.includes(opt)) {
                    current.push(opt);
                  } else if (!checked) {
                    const idx = current.indexOf(opt);
                    if (idx > -1) current.splice(idx, 1);
                  }
                  this.handleInputChange(param.name, current);
                }}
              >${opt}</sp-checkbox>
            `)}
          </div>
        `;

      default:
        return html`<sp-textfield value="${value || ''}"></sp-textfield>`;
    }
  }

  render() {
    if (!this.tool) {
      return html`<p>Select a tool to configure</p>`;
    }

    return html`
      ${this.tool.destructive ? html`
        <div class="destructive-warning">
          ⚠️ This tool can modify or delete content. Make sure to use Dry Run mode first.
        </div>
      ` : ''}

      <p style="color: var(--spectrum-gray-700); margin-bottom: 20px;">
        ${this.tool.description}
      </p>

      ${(this.tool.parameters || []).map(param => html`
        <div class="form-group">
          ${param.type !== 'BOOLEAN' ? html`
            <label class="form-label">
              ${param.label}
              ${param.required ? html`<span style="color: var(--spectrum-red-600)">*</span>` : ''}
            </label>
          ` : ''}
          ${param.description ? html`
            <p class="form-description">${param.description}</p>
          ` : ''}
          ${this.renderParameter(param)}
        </div>
      `)}

      <div class="actions">
        <sp-button
          variant="cta"
          @click=${this.handleSubmit}
          ?disabled=${this.isSubmitting}
        >
          ${this.isSubmitting ? 'Starting...' : 'Run Tool'}
        </sp-button>
      </div>
    `;
  }
}
