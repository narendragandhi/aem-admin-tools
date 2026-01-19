import { LitElement, html, css } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { Job, JobLogEntry } from '../types.js';

@customElement('job-progress')
export class JobProgress extends LitElement {
  static styles = css`
    :host {
      display: block;
    }

    .no-job {
      text-align: center;
      padding: 40px;
      color: var(--spectrum-gray-600);
    }

    .status-header {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 16px;
    }

    .status-badge {
      padding: 4px 12px;
      border-radius: 4px;
      font-size: 12px;
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

    .progress-section {
      margin-bottom: 20px;
    }

    .progress-info {
      display: flex;
      justify-content: space-between;
      margin-bottom: 8px;
      font-size: 13px;
      color: var(--spectrum-gray-700);
    }

    .stats {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 12px;
      margin-bottom: 20px;
    }

    .stat {
      text-align: center;
      padding: 12px;
      background: var(--spectrum-gray-50);
      border-radius: 6px;
    }

    .stat-value {
      font-size: 24px;
      font-weight: 600;
      color: var(--spectrum-gray-800);
    }

    .stat-label {
      font-size: 11px;
      color: var(--spectrum-gray-600);
      text-transform: uppercase;
      margin-top: 4px;
    }

    .stat.success .stat-value {
      color: var(--spectrum-green-600);
    }

    .stat.error .stat-value {
      color: var(--spectrum-red-600);
    }

    .stat.skipped .stat-value {
      color: var(--spectrum-orange-600);
    }

    .logs {
      background: var(--spectrum-gray-900);
      border-radius: 6px;
      padding: 12px;
      max-height: 200px;
      overflow-y: auto;
      font-family: monospace;
      font-size: 12px;
    }

    .log-entry {
      padding: 4px 0;
      display: flex;
      gap: 8px;
    }

    .log-time {
      color: var(--spectrum-gray-500);
      flex-shrink: 0;
    }

    .log-level {
      flex-shrink: 0;
      width: 50px;
    }

    .log-level.INFO { color: var(--spectrum-blue-400); }
    .log-level.WARN { color: var(--spectrum-orange-400); }
    .log-level.ERROR { color: var(--spectrum-red-400); }
    .log-level.DEBUG { color: var(--spectrum-gray-500); }

    .log-message {
      color: var(--spectrum-gray-200);
    }

    .error-message {
      background: var(--spectrum-red-100);
      border: 1px solid var(--spectrum-red-300);
      border-radius: 6px;
      padding: 12px;
      margin-top: 16px;
      color: var(--spectrum-red-800);
    }
  `;

  @property({ type: Object }) job: Job | null = null;
  @state() private streamingJob: Job | null = null;
  private eventSource: EventSource | null = null;

  updated(changedProperties: Map<string, any>) {
    if (changedProperties.has('job') && this.job) {
      this.streamingJob = this.job;

      // Start streaming if job is running
      if (this.job.status === 'PENDING' || this.job.status === 'RUNNING') {
        this.startStreaming(this.job.id);
      }
    }
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    this.stopStreaming();
  }

  private startStreaming(jobId: string) {
    this.stopStreaming();

    this.eventSource = new EventSource(`/api/jobs/${jobId}/stream`);

    this.eventSource.onmessage = (event) => {
      const job = JSON.parse(event.data);
      this.streamingJob = job;

      if (job.status === 'COMPLETED' || job.status === 'FAILED' || job.status === 'CANCELLED') {
        this.stopStreaming();
        this.dispatchEvent(new CustomEvent('job-completed', {
          detail: { job },
          bubbles: true,
          composed: true
        }));
      }
    };

    this.eventSource.onerror = () => {
      this.stopStreaming();
    };
  }

  private stopStreaming() {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
  }

  private formatTime(timestamp: string): string {
    const date = new Date(timestamp);
    return date.toLocaleTimeString();
  }

  render() {
    const job = this.streamingJob || this.job;

    if (!job) {
      return html`
        <div class="no-job">
          <p>Run a tool to see progress here</p>
        </div>
      `;
    }

    const progress = job.totalItems > 0
      ? (job.processedItems / job.totalItems) * 100
      : 0;

    return html`
      <div class="status-header">
        <span class="status-badge ${job.status.toLowerCase()}">${job.status}</span>
        <span style="color: var(--spectrum-gray-600)">${job.toolName}</span>
      </div>

      <div class="progress-section">
        <div class="progress-info">
          <span>Progress</span>
          <span>${job.processedItems} / ${job.totalItems} items</span>
        </div>
        <sp-progress-bar
          value="${progress}"
          label="Processing..."
        ></sp-progress-bar>
      </div>

      <div class="stats">
        <div class="stat">
          <div class="stat-value">${job.totalItems}</div>
          <div class="stat-label">Total</div>
        </div>
        <div class="stat success">
          <div class="stat-value">${job.successCount}</div>
          <div class="stat-label">Success</div>
        </div>
        <div class="stat error">
          <div class="stat-value">${job.errorCount}</div>
          <div class="stat-label">Errors</div>
        </div>
        <div class="stat skipped">
          <div class="stat-value">${job.skippedCount}</div>
          <div class="stat-label">Skipped</div>
        </div>
      </div>

      ${(job.logs || []).length > 0 ? html`
        <div class="logs">
          ${job.logs.map((log: JobLogEntry) => html`
            <div class="log-entry">
              <span class="log-time">${this.formatTime(log.timestamp)}</span>
              <span class="log-level ${log.level}">${log.level}</span>
              <span class="log-message">${log.message}</span>
            </div>
          `)}
        </div>
      ` : ''}

      ${job.errorMessage ? html`
        <div class="error-message">
          <strong>Error:</strong> ${job.errorMessage}
        </div>
      ` : ''}
    `;
  }
}
