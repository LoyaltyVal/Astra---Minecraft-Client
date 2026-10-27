'use strict';

const http = require('http');
const path = require('path');
const { Router, HttpError, sendJson } = require('./http/Router');
const { serveFile } = require('./http/static');
const { Store } = require('./db/Store');
const { PlayerService } = require('./services/Players');
const { CosmeticService } = require('./services/Cosmetics');
const { AuthService } = require('./services/Auth');
const { AuditLog } = require('./services/Audit');
const { publicRoutes } = require('./routes/public');
const { adminRoutes } = require('./routes/admin');

const SECURITY_HEADERS = {
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'same-origin'
};

/** Wires the store, services and routes together and serves HTTP. */
class App {
  constructor(config, { fetchImpl = globalThis.fetch } = {}) {
    this.config = config;
    // Lets index.js check that requests to this port really reach this server (see checkPort).
    this.instanceId = require('crypto').randomBytes(8).toString('hex');
    config.instanceId = this.instanceId;
    this.store = new Store(path.join(config.dataDir, 'db.json')).load();
    const players = new PlayerService(this.store, config);
    const cosmetics = new CosmeticService(this.store, config.dataDir);
    const auth = new AuthService({ store: this.store, players, config, fetchImpl });
    const audit = new AuditLog(this.store);
    this.ctx = { config, store: this.store, players, cosmetics, auth, audit, fetch: fetchImpl };

    const seeded = cosmetics.seed(config.seedDir);
    if (seeded) console.log(`[astra] Added ${seeded} starter cosmetics.`);

    this.router = new Router();
    publicRoutes(this.router, this.ctx);
    adminRoutes(this.router, this.ctx);
  }

  async handle(req, res) {
    for (const [k, v] of Object.entries(SECURITY_HEADERS)) res.setHeader(k, v);
    try {
      if (await this.router.handle(req, res)) return;
      const url = new URL(req.url, 'http://localhost');
      if (url.pathname.startsWith('/assets/') && serveFile(res, this.config.publicDir, url.pathname, req)) return;
      throw new HttpError(404, 'Not found.');
    } catch (e) {
      const status = e instanceof HttpError ? e.status : 500;
      if (status === 500) console.error('[astra]', e);
      if (res.headersSent) {
        res.end();
        return;
      }
      const wantsHtml = String(req.headers.accept || '').includes('text/html') && !req.url.startsWith('/api/');
      if (wantsHtml && status === 404 && serveFile(res, this.config.publicDir, '404.html', req)) return;
      sendJson(res, status, { error: status === 500 ? 'Something went wrong on the server.' : e.message });
    }
  }

  listen(port = this.config.port) {
    this.server = http.createServer((req, res) => this.handle(req, res));
    return new Promise((resolve, reject) => {
      this.server.once('error', reject);
      this.server.listen(port, () => resolve(this.server));
    });
  }

  async close() {
    this.store.flush();
    if (this.server) await new Promise((r) => this.server.close(r));
  }
}

module.exports = { App };
