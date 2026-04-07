/**
 * Mock AEM Server for Admin Tools Testing
 * Simulates AEM QueryBuilder, Sling, and content APIs
 */

const express = require('express');
const cors = require('cors');

const app = express();
const PORT = 4502;

app.use(cors());
app.use(express.json());

// Sample mock content
const mockContent = {
  '/content/we-retail': {
    pages: [
      {
        'jcr:path': '/content/we-retail/us/en',
        'jcr:content': {
          'jcr:title': 'English',
          'jcr:description': 'We.Retail US English site',
          'cq:lastModified': Date.now() - 86400000 * 30,
          'cq:lastReplicated': Date.now() - 86400000 * 35
        }
      },
      {
        'jcr:path': '/content/we-retail/us/en/products',
        'jcr:content': {
          'jcr:title': 'Products',
          'cq:lastModified': Date.now() - 86400000 * 120
        }
      },
      {
        'jcr:path': '/content/we-retail/us/en/about',
        'jcr:content': {
          'jcr:title': 'About Us',
          'jcr:description': 'Learn about We.Retail',
          'cq:lastModified': Date.now() - 86400000 * 5,
          'cq:lastReplicated': Date.now() - 86400000 * 5
        }
      },
      {
        'jcr:path': '/content/we-retail/us/en/contact',
        'jcr:content': {
          'cq:lastModified': Date.now() - 86400000 * 200
        }
      },
      {
        'jcr:path': '/content/we-retail/us/en/blog',
        'jcr:content': {
          'jcr:title': 'Blog',
          'jcr:description': 'Company blog',
          'cq:lastModified': Date.now() - 86400000 * 2
        }
      }
    ]
  },
  '/content/dam/we-retail': {
    assets: [
      {
        'jcr:path': '/content/dam/we-retail/hero.jpg',
        'jcr:content': {
          'dc:title': 'Hero Image',
          'dc:description': 'Main hero banner',
          'dam:size': 2500000
        }
      },
      {
        'jcr:path': '/content/dam/we-retail/logo.png',
        'jcr:content': {
          'dc:title': 'Logo',
          'dam:size': 50000
        }
      },
      {
        'jcr:path': '/content/dam/we-retail/product-1.jpg',
        'jcr:content': {
          'dam:size': 1800000
        }
      }
    ]
  }
};

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'healthy', timestamp: new Date().toISOString() });
});

// Login page (for health checks)
app.get('/libs/granite/core/content/login.html', (req, res) => {
  res.send('<html><body><h1>AEM Login</h1></body></html>');
});

// QueryBuilder API
app.get('/bin/querybuilder.json', (req, res) => {
  const path = req.query.path || req.query['path'];
  const limit = parseInt(req.query['p.limit'] || '100');

  let results = [];

  // Find pages under the given path
  for (const [contentPath, content] of Object.entries(mockContent)) {
    if (path && contentPath.startsWith(path.replace('/jcr:content', ''))) {
      if (content.pages) {
        results = results.concat(content.pages.filter(p =>
          p['jcr:path'].startsWith(path.replace('/jcr:content', ''))
        ));
      }
    }
  }

  // Also check for partial path matches
  if (results.length === 0 && path) {
    const basePath = path.split('/').slice(0, 3).join('/');
    const content = mockContent[basePath];
    if (content && content.pages) {
      results = content.pages.filter(p => p['jcr:path'].startsWith(path));
    }
  }

  res.json({
    success: true,
    results: results.slice(0, limit),
    total: results.length
  });
});

// Get page properties
app.get('/*.json', (req, res) => {
  const path = req.path.replace('.json', '').replace('/jcr:content', '');

  // Find the page
  for (const content of Object.values(mockContent)) {
    if (content.pages) {
      const page = content.pages.find(p =>
        p['jcr:path'] === path || p['jcr:path'] === path.replace('/jcr:content', '')
      );
      if (page) {
        return res.json(page['jcr:content'] || {});
      }
    }
    if (content.assets) {
      const asset = content.assets.find(a => a['jcr:path'] === path);
      if (asset) {
        return res.json(asset['jcr:content'] || {});
      }
    }
  }

  res.status(404).json({ error: 'Not found' });
});

// Tag management endpoints
app.get('/content/_cq_tags.json', (req, res) => {
  res.json({
    tags: [
      { id: 'marketing:product', title: 'Product' },
      { id: 'marketing:campaign', title: 'Campaign' },
      { id: 'marketing:seasonal', title: 'Seasonal' }
    ]
  });
});

// Asset metadata endpoint
app.get('/api/assets/*', (req, res) => {
  const path = '/content/dam' + req.path.replace('/api/assets', '');

  for (const content of Object.values(mockContent)) {
    if (content.assets) {
      const asset = content.assets.find(a => a['jcr:path'] === path);
      if (asset) {
        return res.json({
          properties: asset['jcr:content'],
          path: asset['jcr:path']
        });
      }
    }
  }

  res.status(404).json({ error: 'Asset not found' });
});

// Sling POST servlet (for modifications)
app.post('/*', (req, res) => {
  console.log(`Mock: POST to ${req.path}`, req.body);
  res.json({ success: true, message: 'Mock operation completed' });
});

// Start server
app.listen(PORT, '0.0.0.0', () => {
  console.log(`Mock AEM server running on port ${PORT}`);
  console.log('Available endpoints:');
  console.log('  GET  /health');
  console.log('  GET  /bin/querybuilder.json');
  console.log('  GET  /*.json (page/asset properties)');
  console.log('  GET  /content/_cq_tags.json');
  console.log('  GET  /api/assets/*');
  console.log('  POST /* (mock modifications)');
});
