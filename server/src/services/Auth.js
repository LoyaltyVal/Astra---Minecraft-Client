'use strict';

const crypto = require('crypto');
const { HttpError, parseCookies } = require('../http/Router');
const { normalizeUuid, NAME_RE } = require('./Players');

const CHALLENGE_MS = 2 * 60 * 1000;
const WEB_CODE_MS = 2 * 60 * 1000;
const GAME_SESSION_MS = 30 * 24 * 60 * 60 * 1000;
const WEB_SESSION_MS = 7 * 24 * 60 * 60 * 1000;
const COOKIE = 'astra_session';

const sha256 = (s) => crypto.createHash('sha256').update(s).digest('hex');
const randomToken = (bytes = 32) => crypto.randomBytes(bytes).toString('base64url');

/**
 * Sign-in without passwords, the same way Minecraft servers verify players:
 *
 *  1. The launcher asks for a challenge (a random "serverId").
 *  2. The launcher tells Mojang "I'm joining serverId" using the player's own Minecraft token.
 *  3. This server asks Mojang "did <name> join <serverId>?" Mojang answers with the real profile.
 *
 * The Minecraft token never reaches this server. A successful check returns an Astra session token
 * (only its SHA-256 is stored). The website signs in with a one-time code minted by the launcher.
 */
class AuthService {
  constructor({ store, players, config, fetchImpl = globalThis.fetch }) {
    this.store = store;
    this.players = players;
    this.config = config;
    this.fetch = fetchImpl;
    this.challenges = new Map();
    this.webCodes = new Map();
    this.attempts = new Map();
  }

  challenge() {
    this.sweep();
    const serverId = crypto.randomBytes(16).toString('hex');
    this.challenges.set(serverId, Date.now() + CHALLENGE_MS);
    return { serverId };
  }

  /** Step 3: checks the join with Mojang and returns { token, player }. */
  async verify({ username, serverId, uuid }, ip) {
    this.rateLimit(ip);
    if (!NAME_RE.test(String(username || ''))) throw new HttpError(400, 'Invalid username.');
    const expires = this.challenges.get(serverId);
    this.challenges.delete(serverId);
    if (!expires || expires < Date.now()) throw new HttpError(400, 'That sign-in attempt expired. Try again.');

    let profile;
    if (this.config.devAuth) {
      // Testing only: trust the name and UUID the client sends.
      const u = normalizeUuid(uuid) || crypto.createHash('md5').update('OfflinePlayer:' + username).digest('hex');
      profile = { id: u, name: username };
    } else {
      const url = `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${encodeURIComponent(username)}&serverId=${encodeURIComponent(serverId)}`;
      let res;
      try {
        res = await this.fetch(url, { headers: { 'User-Agent': 'AstraServer' } });
      } catch {
        throw new HttpError(502, 'Couldn\'t reach Mojang to verify your account. Try again in a moment.');
      }
      if (res.status !== 200) throw new HttpError(401, 'Mojang couldn\'t confirm this account. Make sure you own Minecraft: Java Edition.');
      profile = await res.json();
    }

    const player = this.players.upsert(profile.id, profile.name);
    if (player.banned) throw new HttpError(403, 'This account is banned from Astra.');
    const token = this.createSession(player, 'game', GAME_SESSION_MS);
    return { token, player };
  }

  createSession(player, kind, ttl) {
    const token = randomToken();
    this.store.sessions[sha256(token)] = { uuid: player.uuid, kind, createdAt: Date.now(), expiresAt: Date.now() + ttl };
    this.store.save();
    return token;
  }

  /** Resolves the player behind a request (Bearer token from the launcher/game, or the website cookie). */
  playerFor(req) {
    const header = String(req.headers.authorization || '');
    let token = header.startsWith('Bearer ') ? header.slice(7).trim() : null;
    let viaCookie = false;
    if (!token) {
      token = parseCookies(req)[COOKIE];
      viaCookie = Boolean(token);
    }
    if (!token) return null;
    const key = sha256(token);
    const session = this.store.sessions[key];
    if (!session) return null;
    if (session.expiresAt < Date.now()) {
      delete this.store.sessions[key];
      this.store.save();
      return null;
    }
    const player = this.players.get(session.uuid);
    if (!player || player.banned) return null;
    req.viaCookie = viaCookie;
    return player;
  }

  /** One-time code the launcher opens in the browser to sign the website in. */
  webCode(player) {
    this.sweep();
    const code = randomToken(18);
    this.webCodes.set(code, { uuid: player.uuid, expiresAt: Date.now() + WEB_CODE_MS });
    return { code, url: `${this.config.publicUrl}/login?code=${encodeURIComponent(code)}` };
  }

  /** Exchanges a code for a website session; returns { player, cookie } or null. */
  redeemWebCode(code) {
    const entry = this.webCodes.get(code);
    this.webCodes.delete(code);
    if (!entry || entry.expiresAt < Date.now()) return null;
    const player = this.players.get(entry.uuid);
    if (!player || player.banned) return null;
    const token = this.createSession(player, 'web', WEB_SESSION_MS);
    return { player, cookie: this.cookie(token, WEB_SESSION_MS / 1000) };
  }

  logout(req) {
    const token = parseCookies(req)[COOKIE];
    if (token) {
      delete this.store.sessions[sha256(token)];
      this.store.save();
    }
    return this.cookie('', 0);
  }

  /** Signs a player out everywhere (used when they're banned). */
  revokeAll(uuid) {
    for (const [key, s] of Object.entries(this.store.sessions)) {
      if (s.uuid === uuid) delete this.store.sessions[key];
    }
    this.store.save();
  }

  cookie(value, maxAge) {
    const parts = [`${COOKIE}=${encodeURIComponent(value)}`, 'Path=/', 'HttpOnly', 'SameSite=Lax', `Max-Age=${Math.floor(maxAge)}`];
    if (this.config.secureCookies) parts.push('Secure');
    return parts.join('; ');
  }

  rateLimit(ip) {
    const now = Date.now();
    const recent = (this.attempts.get(ip) || []).filter((t) => now - t < 60_000);
    if (recent.length >= 20) throw new HttpError(429, 'Too many sign-in attempts. Wait a minute and try again.');
    recent.push(now);
    this.attempts.set(ip, recent);
  }

  sweep() {
    const now = Date.now();
    for (const [k, v] of this.challenges) if (v < now) this.challenges.delete(k);
    for (const [k, v] of this.webCodes) if (v.expiresAt < now) this.webCodes.delete(k);
  }
}

module.exports = { AuthService, COOKIE };
