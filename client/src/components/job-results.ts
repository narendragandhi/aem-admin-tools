import { LitElement, html, css } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { Job, JobResult } from '../types.js';

@customElement('job-results')
export class JobResults extends LitElement {
  static styles = css`
    :host {
      display: block;
    }

    .filters {
      display: flex;
      gap: 12px;
      margin-bottom: 16px;
      align-items: center;
    }

    .filter-label {
      font-size: 13px;
      color: var(--spectrum-gray-700);
    }

    .results-table {
      width: 100%;
      border-collapse: collapse;
    }

    .results-table th {
      text-align: left;
      padding: 12px;
      background: var(--spectrum-gray-100);
      font-size: 12px;
      font-weight: 600;
      text-transform: uppercase;
      color: var(--spectrum-gray-700);
      border-bottom: 1px solid var(--spectrum-gray-200);
    }

    .results-table td {
      padding: 12px;
      border-bottom: 1px solid var(--spectrum-gray-200);
      font-size: 13px;
    }

    .results-table tr:hover {
      background: var(--spectrum-gray-50);
    }

    .path-cell {
      font-family: monospace;
      font-size: 12px;
      color: var(--spectrum-gray-800);
      max-width: 400px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .status-indicator {
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }

    .status-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
    }

    .status-dot.SUCCESS { background: var(--spectrum-green-500); }
    .status-dot.ERROR { background: var(--spectrum-red-500); }
    .status-dot.SKIPPED { background: var(--spectrum-orange-500); }

    .message-cell {
      color: var(--spectrum-gray-700);
    }

    .no-results {
      text-align: center;
      padding: 40px;
      color: var(--spectrum-gray-600);
    }

    .pagination {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 16px;
      padding-top: 16px;
      border-top: 1px solid var(--spectrum-gray-200);
    }

    .page-info {
      font-size: 13px;
      color: var(--spectrum-gray-600);
    }
  `;

  @property({ type: Object }) job: Job | null = null;
  @state() private filter: 'all' | 'SUCCESS' | 'ERROR' | 'SKIPPED' = 'all';
  @state() private page = 1;
  private pageSize = 20;

  private get filteredResults(): JobResult[] {
    if (!this.job?.results) return [];

    if (this.filter === 'all') {
      return this.job.results;
    }

    return this.job.results.filter(r => r.status === this.filter);
  }

  private get paginatedResults(): JobResult[] {
    const start = (this.page - 1) * this.pageSize;
    return this.filteredResults.slice(start, start + this.pageSize);
  }

  private get totalPages(): number {
    return Math.ceil(this.filteredResults.length / this.pageSize);
  }

  render() {
    if (!this.job?.results || this.job.results.length === 0) {
      return html`
        <div class="no-results">
          <p>No results yet</p>
        </div>
      `;
    }

    return html`
      <div class="filters">
        <span class="filter-label">Show:</span>
        <sp-picker
          value="${this.filter}"
          @change=${(e: Event) => {
            this.filter = (e.target as any).value;
            this.page = 1;
          }}
        >
          <sp-menu-item value="all">All (${this.job.results.length})</sp-menu-item>
          <sp-menu-item value="SUCCESS">Success (${this.job.successCount})</sp-menu-item>
          <sp-menu-item value="ERROR">Errors (${this.job.errorCount})</sp-menu-item>
          <sp-menu-item value="SKIPPED">Skipped (${this.job.skippedCount})</sp-menu-item>
        </sp-picker>
      </div>

      <table class="results-table">
        <thead>
          <tr>
            <th>Status</th>
            <th>Path</th>
            <th>Message</th>
          </tr>
        </thead>
        <tbody>
          ${this.paginatedResults.map(result => html`
            <tr>
              <td>
                <span class="status-indicator">
                  <span class="status-dot ${result.status}"></span>
                  ${result.status}
                </span>
              </td>
              <td class="path-cell" title="${result.path}">${result.path}</td>
              <td class="message-cell">${result.message}</td>
            </tr>
          `)}
        </tbody>
      </table>

      ${this.totalPages > 1 ? html`
        <div class="pagination">
          <span class="page-info">
            Showing ${(this.page - 1) * this.pageSize + 1} - ${Math.min(this.page * this.pageSize, this.filteredResults.length)}
            of ${this.filteredResults.length}
          </span>
          <div>
            <sp-button
              variant="secondary"
              size="s"
              ?disabled=${this.page === 1}
              @click=${() => this.page--}
            >Previous</sp-button>
            <sp-button
              variant="secondary"
              size="s"
              ?disabled=${this.page >= this.totalPages}
              @click=${() => this.page++}
            >Next</sp-button>
          </div>
        </div>
      ` : ''}
    `;
  }
}
