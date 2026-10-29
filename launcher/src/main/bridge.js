const http = require('http');
const crypto = require('crypto');
const auth = require('./auth');
const astraServer = require('./astraServer');

/**
 * The in-game account switcher's link to the launcher. While Minecraft runs, the launcher listens
 * on 127.0.0.1 (a random port) and the game gets the address and a random secret on its command
 * line. Only the launcher can sign in to Microsoft, so the game asks it for:
 *   GET  /accounts          saved accounts (no tokens)
 *   POST /switch {id}       a fresh session for that account: name, uuid, Minecraft + Astra tokens
 *   POST /add               opens Microsoft's sign-in window to add another account
 *
 * Safety: loopback only, the secret must match (compared in constant time), and requests that look
 * like they come from a web page (Origin header, wrong Host) are refused.
 */
class Bridge {
  constructor({ onAccountChanged, log }) {
    this.onAccountChanged = onAccountChanged || (() => {});
    this.log = log || (() => {});
    this.server = null;
    this.secret = null;
    this.port = 0;
  }

  async start() {
    if (this.server) return this.args();
    this.secret = crypto.randomBytes(24).toString('hex');
    this.server = http.createServer((req, res) => this.handle(req, res).catch((e) => {
      this.reply(res, 500, { error: e.message || 'Something went wrong.' });
    }));
    await new Promise((resolve, reject) => {
      this.server.once('error', reject);
      this.server.listen(0, '127.0.0.1', resolve);
    });
    this.port = this.server.address().port;
    return this.args();
  }

  /** -D arguments for the game. */
  args() {
    return [`-Dastra.bridge=http://127.0.0.1:${this.port}`, `-Dastra.bridgeToken=${this.secret}`];
  }

  stop() {
    if (this.server) this.server.close();
    this.server = null;
    this.secret = null;
  }

  reply(res, status, value) {
    const body = JSON.stringify(value);
    res.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body), 'Cache-Control': 'no-store' });
    res.end(body);
  }

  authorized(req) {
    if (req.headers.origin || req.headers.referer) return false; // never from a browser page
    if (req.headers.host !== `127.0.0.1:${this.port}`) return false; // blocks DNS rebinding
    const given = Buffer.from(String(req.headers['x-astra-bridge'] || ''));
    const expected = Buffer.from(this.secret || '');
    return given.length === expected.length && crypto.timingSafeEqual(given, expected);
  }

  readBody(req) {
    return new Promise((resolve) => {
      let data = '';
      req.on('data', (c) => {
        data += c;
        if (data.length > 4096) req.destroy();
      });
      req.on('end', () => {
        try {
          resolve(data ? JSON.parse(data) : {});
        } catch {
          resolve({});
        }
      });
    });
  }

  async handle(req, res) {
    if (!this.authorized(req)) return this.reply(res, 403, { error: 'Not allowed.' });
    const path = req.url.split('?')[0];

    if (req.method === 'GET' && path === '/accounts') {
      return this.reply(res, 200, { accounts: auth.listAccounts() });
    }

    if (req.method === 'POST' && path === '/switch') {
      const { id } = await this.readBody(req);
      const profile = auth.getProfile(String(id || ''));
      if (!profile) return this.reply(res, 404, { error: 'That account isn\'t saved in the launcher.' });
      let mc;
      try {
        // Enough time left for a long session on servers.
        mc = await auth.getLaunchAuth({ id: profile.id, minValidMs: 3 * 60 * 60 * 1000 });
      } catch (e) {
        return this.reply(res, e.expired ? 401 : 503, { error: e.message, expired: Boolean(e.expired) });
      }
      let astraToken = null;
      try {
        astraToken = (await astraServer.session(profile.id)).token;
      } catch (e) {
        this.log(`[Astra] Switched account, but the Astra server sign-in failed: ${e.message}`);
      }
      auth.switchAccount(profile.id);
      this.onAccountChanged(auth.getAccount());
      this.log(`[Astra] Switched to ${mc.name} in game.`);
      return this.reply(res, 200, {
        name: mc.name,
        uuid: String(mc.uuid).replace(/-/g, ''),
        accessToken: mc.access_token,
        astraToken
      });
    }

    if (req.method === 'POST' && path === '/add') {
      try {
        const account = await auth.login({ activate: false }); // the game switches to it itself
        this.onAccountChanged(auth.getAccount());
        return this.reply(res, 200, { account });
      } catch (e) {
        return this.reply(res, e.cancelled ? 409 : 400, { error: e.message, cancelled: Boolean(e.cancelled) });
      }
    }

    return this.reply(res, 404, { error: 'Unknown request.' });
  }
}

module.exports = { Bridge };
