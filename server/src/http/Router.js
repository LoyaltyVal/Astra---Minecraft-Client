'use strict';

/** An error that becomes a JSON response with the given status. */
class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

/**
 * A minimal router on top of Node's http module: method + path patterns with :params,
 * async handlers, JSON bodies and JSON/HTML/redirect helpers. No dependencies.
 */
class Router {
  constructor() {
    this.routes = [];
  }

  add(method, pattern, ...handlers) {
    const keys = [];
    const regex = new RegExp('^' + pattern.replace(/\/:([a-zA-Z]+)/g, (_m, key) => {
      keys.push(key);
      return '/([^/]+)';
    }) + '/?$');
    this.routes.push({ method, regex, keys, handlers });
    return this;
  }

  get(p, ...h) { return this.add('GET', p, ...h); }
  post(p, ...h) { return this.add('POST', p, ...h); }
  patch(p, ...h) { return this.add('PATCH', p, ...h); }
  delete(p, ...h) { return this.add('DELETE', p, ...h); }

  /** Finds the route for a request; returns false when nothing matched. */
  async handle(req, res) {
    const url = new URL(req.url, 'http://localhost');
    let pathMatched = false;
    for (const route of this.routes) {
      const m = route.regex.exec(url.pathname);
      if (!m) continue;
      pathMatched = true;
      if (route.method !== req.method && !(req.method === 'HEAD' && route.method === 'GET')) continue;
      req.params = {};
      route.keys.forEach((k, i) => { req.params[k] = decodeURIComponent(m[i + 1]); });
      req.query = url.searchParams;
      req.path = url.pathname;
      for (const handler of route.handlers) {
        await handler(req, res);
        if (res.writableEnded) return true;
      }
      return true;
    }
    if (pathMatched) throw new HttpError(405, 'Method not allowed.');
    return false;
  }
}

/** Reads and parses a JSON request body, refusing anything over `limit` bytes. */
function readJson(req, limit = 64 * 1024) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > limit) {
        reject(new HttpError(413, 'That upload is too large.'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => {
      if (!chunks.length) return resolve({});
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')));
      } catch {
        reject(new HttpError(400, 'The request body isn\'t valid JSON.'));
      }
    });
    req.on('error', reject);
  });
}

function sendJson(res, status, value) {
  const body = JSON.stringify(value);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store'
  });
  res.end(body);
}

function redirect(res, location, headers = {}) {
  res.writeHead(302, { Location: location, ...headers });
  res.end();
}

function parseCookies(req) {
  const out = {};
  for (const part of String(req.headers.cookie || '').split(';')) {
    const i = part.indexOf('=');
    if (i > 0) out[part.slice(0, i).trim()] = decodeURIComponent(part.slice(i + 1).trim());
  }
  return out;
}

module.exports = { Router, HttpError, readJson, sendJson, redirect, parseCookies };
