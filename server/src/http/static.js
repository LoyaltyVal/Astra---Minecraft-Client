'use strict';

const fs = require('fs');
const path = require('path');

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.webp': 'image/webp',
  '.txt': 'text/plain; charset=utf-8'
};

/**
 * Serves files from `root`. Paths are resolved and checked so nothing outside `root`
 * can be read. Pages are revalidated every load; assets are cached for an hour.
 */
function serveFile(res, root, relPath, req) {
  const file = path.resolve(root, '.' + path.posix.normalize('/' + relPath));
  if (!file.startsWith(path.resolve(root) + path.sep)) return false;
  let stat;
  try {
    stat = fs.statSync(file);
  } catch {
    return false;
  }
  if (!stat.isFile()) return false;

  const ext = path.extname(file).toLowerCase();
  const etag = `"${stat.size.toString(16)}-${Math.floor(stat.mtimeMs).toString(16)}"`;
  const headers = {
    'Content-Type': TYPES[ext] || 'application/octet-stream',
    'Cache-Control': ext === '.html' ? 'no-cache' : 'public, max-age=3600',
    ETag: etag,
    'X-Content-Type-Options': 'nosniff'
  };
  if (req && req.headers['if-none-match'] === etag) {
    res.writeHead(304, headers);
    res.end();
    return true;
  }
  headers['Content-Length'] = stat.size;
  res.writeHead(200, headers);
  if (req && req.method === 'HEAD') {
    res.end();
  } else {
    fs.createReadStream(file).pipe(res);
  }
  return true;
}

module.exports = { serveFile, TYPES };
