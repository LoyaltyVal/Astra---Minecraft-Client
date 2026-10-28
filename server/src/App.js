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
const { DiscordNotifier } = require('./services/Discord');
const { publicRoutes } = require('./routes/public');
const { adminRoutes } = require('./routes/admin');
const { accountRoutes } = require('./routes/account');
const { uploadRoutes } = require('./routes/uploads');
const { socialRoutes } = require('./routes/social');
const { SocialService } = require('./services/Social');
const { LiveHub } = require('./live/LiveHub');

const SECURITY_HEADERS = {
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'same-origin'
};

/** The player's address; behind Cloudflare Tunnel or a proxy every request comes from 127.0.0.1. */
function clientIp(req, config) {
  const direct = req.socket.remoteAddress || '';
  if (!config.trustProxy) return direct;
  const cf = req.headers['cf-connecting-ip'];
  if (cf) return String(cf).trim();
  const xff = req.headers['x-forwarded-for'];
  return xff ? String(xff).split(',')[0].trim() : direct;
}

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
    const discord = new DiscordNotifier(config, fetchImpl);
    players.onJoin = (p) => discord.newPlayer(p);
    const audit = new AuditLog(this.store, discord);
    const social = new SocialService(this.store, players);
    this.ctx = { config, store: this.store, players, cosmetics, auth, audit, discord, social, fetch: fetchImpl };
    this.ctx.live = new LiveHub(this.ctx);

    const seeded = cosmetics.seed(config.seedDir);
    if (seeded) console.log(`[astra] Added ${seeded} starter cosmetics.`);

    this.router = new Router();
    publicRoutes(this.router, this.ctx);
    adminRoutes(this.router, this.ctx);
    socialRoutes(this.router, this.ctx);
    accountRoutes(this.router, this.ctx);
    uploadRoutes(this.router, this.ctx);
  }

  async handle(req, res) {
    req.clientIp = clientIp(req, this.config);
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
    // WebSocket upgrades (/api/live) for instant messages, friend updates and emotes.
    this.server.on('upgrade', (req, socket) => {
      req.clientIp = clientIp(req, this.config);
      socket.on('error', () => {});
      try {
        this.ctx.live.handleUpgrade(req, socket);
      } catch (e) {
        console.error('[astra] WebSocket upgrade failed:', e);
        socket.destroy();
      }
    });
    return new Promise((resolve, reject) => {
      this.server.once('error', reject);
      this.server.listen(port, () => resolve(this.server));
    });
  }

  async close() {
    this.ctx.live.close();
    this.store.flush();
    if (this.server) await new Promise((r) => this.server.close(r));
  }
}

module.exports = { App };
