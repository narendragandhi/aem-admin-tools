import { LitElement, html, css } from 'lit';
import { customElement, property } from 'lit/decorators.js';
import { Job } from '../types.js';

@customElement('job-history')
export class JobHistory extends LitElement {
  static styles = css`
    :host {
      display: block;
    }

    .history-table {
      width: 100%;
      border-collapse: collapse;
    }

    .history-table th {
      text-align: left;
      padding: 12px;
      background: var(--spectrum-gray-100);
      font-size: 12px;
      font-weight: 600;
      text-transform: uppercase;
      color: var(--spectrum-gray-700);
      border-bottom: 1px solid var(--spectrum-gray-200);
    }

    .history-table td {
      padding: 12px;
      border-bottom: 1px solid var(--spectrum-gray-200);
      font-size: 13px;
    }

    .history-table tr:hover {
      background: var(--spectrum-gray-50);
      cursor: pointer;
    }

    .status-badge {
      padding: 2px 8px;
      border-radius: 4px;
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
    }

    .status-badge.pending {
      background: var(--spectrum-gray-200);
      color: var(--spectrum-gray-700);
    }

    .status-badge.running {
      background: var(--spectrum-blue-100);
      color: var(--spectrum-blue-700);
    }

    .status-badge.completed {
      background: var(--spectrum-green-100);
      color: var(--spectrum-green-700);
    }

    .status-badge.failed {
      background: var(--spectrum-red-100);
      color: var(--spectrum-red-700);
    }

    .status-badge.cancelled {
      background: var(--spectrum-orange-100);
      color: var(--spectrum-orange-700);
    }

    .stats-cell {
      font-size: 12px;
      color: var(--spectrum-gray-600);
    }

    .stats-cell span {
      margin-right: 12px;
    }

    .stats-cell .success { color: var(--spectrum-green-600); }
    .stats-cell .error { color: var(--spectrum-red-600); }
    .stats-cell .skipped { color: var(--spectrum-orange-600); }

    .no-history {
      text-align: center;
      padding: 40px;
      color: var(--spectrum-gray-600);
    }

    .time-cell {
      color: var(--spectrum-gray-600);
      font-size: 12px;
    }
  `;

  @property({ type: Array }) jobs: Job[] = [];

  private formatTime(timestamp: string | null): string {
    if (!timestamp) return '-';
    const date = new Date(timestamp);
    return date.toLocaleString();
  }

  private handleRowClick(job: Job) {
    this.dispatchEvent(new CustomEvent('job-selected', {
      detail: { job },
      bubbles: true,
      composed: true
    }));
  }

  render() {
    if (this.jobs.length === 0) {
      return html`
        <div class="no-history">
          <p>No job history yet</p>
        </div>
      `;
    }

    return html`
      <table class="history-table">
        <thead>
          <tr>
            <th>Tool</th>
            <th>Status</th>
            <th>Results</th>
            <th>Started</th>
            <th>Completed</th>
          </tr>
        </thead>
        <tbody>
          ${this.jobs.map(job => html`
            <tr @click=${() => this.handleRowClick(job)}>
              <td>${job.toolName}</td>
              <td>
                <span class="status-badge ${job.status.toLowerCase()}">${job.status}</span>
              </td>
              <td class="stats-cell">
                <span class="success">✓ ${job.successCount}</span>
                <span class="error">✗ ${job.errorCount}</span>
                <span class="skipped">○ ${job.skippedCount}</span>
              </td>
              <td class="time-cell">${this.formatTime(job.startedAt)}</td>
              <td class="time-cell">${this.formatTime(job.completedAt)}</td>
            </tr>
          `)}
        </tbody>
      </table>
    `;
  }
}
