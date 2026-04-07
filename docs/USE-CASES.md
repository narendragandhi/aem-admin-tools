# AEM Admin Tools Use Cases

Practical examples of how to use AEM Admin Tools for common AEM administration tasks.

## Table of Contents

1. [Content Health Check](#1-content-health-check)
2. [Bulk Tag Management](#2-bulk-tag-management)
3. [Asset Reporting](#3-asset-reporting)
4. [SEO Optimization](#4-seo-optimization)
5. [Content Migration Prep](#5-content-migration-prep)
6. [Compliance Auditing](#6-compliance-auditing)

---

## 1. Content Health Check

### Use Case: Pre-Launch Content Audit

Before launching a new site or campaign, run a comprehensive health check.

**Parameters:**
- Root Path: `/content/mysite/campaign-2024`
- Checks: All (missing-title, missing-description, broken-references, stale-content, unpublished-changes)
- Stale After: 30 days
- Max Pages: 500

**Expected Results:**
- Pages with missing SEO metadata
- Unpublished content
- Broken internal references
- Content not updated for launch

**API Example:**
```bash
curl -X POST http://localhost:10004/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "toolId": "content-health-check",
    "parameters": {
      "rootPath": "/content/mysite/campaign-2024",
      "checks": ["missing-title", "missing-description", "broken-references", "stale-content", "unpublished-changes"],
      "staleDays": 30,
      "maxPages": 500
    }
  }'
```

### Use Case: Quarterly Content Review

Identify stale content that needs updating.

**Parameters:**
- Root Path: `/content/corporate`
- Checks: stale-content
- Stale After: 90 days
- Max Pages: 1000

**Action Items:**
- Review and update stale pages
- Archive obsolete content
- Refresh seasonal content

---

## 2. Bulk Tag Management

### Use Case: Taxonomy Migration

Migrate from old tag structure to new taxonomy.

**Scenario:** Replace `marketing:legacy-campaign` with `campaigns:2024:spring`

**Parameters:**
- Root Path: `/content/site`
- Action: Replace
- Old Tag: `marketing:legacy-campaign`
- New Tag: `campaigns:2024:spring`

**API Example:**
```bash
curl -X POST http://localhost:10004/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "toolId": "bulk-tag-manager",
    "parameters": {
      "rootPath": "/content/site",
      "action": "replace",
      "oldTag": "marketing:legacy-campaign",
      "newTag": "campaigns:2024:spring"
    }
  }'
```

### Use Case: Content Categorization

Find all untagged content and apply appropriate tags.

**Parameters:**
- Root Path: `/content/blog`
- Action: Find
- Tag: (empty - find untagged)

**Follow-up:**
- Review results
- Apply appropriate tags based on content

---

## 3. Asset Reporting

### Use Case: DAM Cleanup

Identify large unused assets consuming storage.

**Parameters:**
- Root Path: `/content/dam/corporate`
- Report Type: Large Files
- Min Size: 5 MB
- Check Usage: true

**Expected Output:**
| Asset Path | Size | Last Modified | Used By |
|------------|------|---------------|---------|
| /content/dam/corporate/video.mp4 | 250MB | 2022-01-15 | None |
| /content/dam/corporate/archive.zip | 150MB | 2021-06-20 | None |

**Actions:**
- Archive unused large files
- Compress or optimize large images
- Remove duplicates

### Use Case: Missing Metadata Audit

Ensure all assets have required metadata for DAM governance.

**Parameters:**
- Root Path: `/content/dam/products`
- Report Type: Missing Metadata
- Required Fields: dc:title, dc:description, dam:keywords

**Expected Output:**
- List of assets missing required metadata
- Priority based on asset usage

---

## 4. SEO Optimization

### Use Case: AI-Powered Meta Description Generation

Generate SEO-optimized meta descriptions for pages missing them.

**Parameters:**
- Root Path: `/content/site/products`
- Max Pages: 100
- Generate Suggestions: true

**Output:**
| Page | Current Description | Suggested Description |
|------|---------------------|----------------------|
| /content/site/products/widget | (missing) | "Discover our premium widget with advanced features..." |

**API Example:**
```bash
curl -X POST http://localhost:10004/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "toolId": "seo-optimizer",
    "parameters": {
      "rootPath": "/content/site/products",
      "maxPages": 100,
      "generateSuggestions": true
    }
  }'
```

### Use Case: Keyword Analysis

Analyze content for target keyword optimization.

**Parameters:**
- Root Path: `/content/site/services`
- Target Keywords: "cloud services, enterprise solutions"
- Check Title Tags: true
- Check H1 Tags: true
- Check Meta Descriptions: true

---

## 5. Content Migration Prep

### Use Case: Pre-Migration Analysis

Before migrating content to AEM as a Cloud Service.

**Workflow:**

1. **Content Health Check**
   - Identify broken references
   - Find orphaned pages
   - List custom components

2. **Asset Report**
   - Inventory all assets
   - Check for unsupported formats
   - Identify large files to optimize

3. **Tag Audit**
   - Export tag taxonomy
   - Identify unused tags
   - Plan tag cleanup

**Combined Report:**
```
Migration Readiness Report
=========================
Pages to Migrate: 2,500
Assets to Migrate: 15,000
Issues Found: 127
  - 45 broken references
  - 32 missing metadata
  - 50 oversized images

Recommended Actions:
1. Fix broken references before migration
2. Add missing metadata
3. Optimize images >2MB
```

---

## 6. Compliance Auditing

### Use Case: GDPR Content Audit

Find pages with potentially sensitive content.

**Parameters:**
- Root Path: `/content/site`
- Check For: Email patterns, phone numbers, personal data mentions
- Max Pages: 5000

**Output:**
| Page | Issues Found |
|------|-------------|
| /content/site/contact | Contains email: support@... |
| /content/site/team | Contains personal names and titles |

### Use Case: Accessibility Check

Identify pages with accessibility issues.

**Parameters:**
- Root Path: `/content/site`
- Checks: Missing alt text, color contrast issues, heading structure

**Integration:**
Can be combined with external accessibility tools for comprehensive WCAG compliance reporting.

---

## Automation Workflows

### Scheduled Health Checks

Set up automated weekly health checks:

```yaml
# Example cron job configuration
schedule:
  - name: weekly-content-health
    cron: "0 0 * * 0"  # Every Sunday midnight
    tool: content-health-check
    parameters:
      rootPath: /content/corporate
      checks: [missing-title, missing-description, stale-content]
      staleDays: 90
    notify:
      email: content-team@company.com
      slack: #content-alerts
```

### Pre-Release Checklist

Automated checks before content release:

```javascript
// Integration with CI/CD pipeline
const preReleaseChecks = async (contentPath) => {
  const checks = [
    { tool: 'content-health-check', params: { rootPath: contentPath, checks: ['missing-title', 'broken-references'] } },
    { tool: 'seo-optimizer', params: { rootPath: contentPath, generateSuggestions: true } }
  ];

  for (const check of checks) {
    const job = await createJob(check.tool, check.params);
    const result = await waitForCompletion(job.id);

    if (result.errorCount > 0) {
      throw new Error(`Pre-release check failed: ${result.errorCount} issues found`);
    }
  }

  return { status: 'passed' };
};
```

---

## Best Practices

1. **Start Small**: Test with limited scope before running on entire site
2. **Review Results**: Always review before making bulk changes
3. **Backup First**: Ensure backups before destructive operations
4. **Monitor Progress**: Use SSE streaming to monitor long-running jobs
5. **Schedule Off-Peak**: Run large scans during low-traffic periods
6. **Document Changes**: Keep records of bulk operations for audit trails

---

For API documentation, see the [README](../README.md).
