const fs = require('fs');
const path = require('path');
const { app, safeStorage, shell } = require('electron');
const auth = require('./auth');
const store = require('./store');

/**
 * Your Astra account: signs in to the Astra server (cosmetics, ranks, the admin panel).
 *
 * Sign-in proves you own the Minecraft account without sending your Microsoft or Minecraft token
 * to the Astra server, the same way joining a Minecraft server does:
 *   1. the Astra server hands out a random serverId          (POST /api/auth/challenge)
 *   2. the launcher tells Mojang "I'm joining serverId"       (sessionserver /session/minecraft/join)
 *   3. the Astra server asks Mojang whether you did           (POST /api/auth/verify → hasJoined)
 * The Astra session token that comes back is stored encrypted and passed to the game.
 */

/** Free Cloudflare quick-tunnel addresses change every time hosting restarts. */
function isQuickTunnel(url) {
  return /\.trycloudflare\.com$/i.test(String(url).replace(/^https?:\/\//, '').replace(/[/:].*$/, ''));
}

/**
 * On the PC that hosts Astra (running the launcher from the project folder), the address that
 * "Host Astra Online" last printed, from server/data/online-address.txt.
 */
function hostedAddress() {
  if (app.isPackaged) return null;
  try {
    const url = fs.readFileSync(path.join(__dirname, '..', '..', '..', 'server', 'data', 'online-address.txt'), 'utf8').trim();
    return /^https?:\/\/\S+$/.test(url) ? url.replace(/\/+$/, '') : null;
  } catch {
    return null;
  }
}

function serverUrl() {
  const configured = String(store.get().serverUrl || '').replace(/\/+$/, '');
  // A quick-tunnel address in Settings goes stale when hosting restarts; follow the new one.
  const hosted = hostedAddress();
  if (hosted && hosted !== configured && (!configured || isQuickTunnel(configured))) {
    store.set({ serverUrl: hosted });
    return hosted;
  }
  return configured;
}

/** Astra sessions, one per Minecraft account and server, so switching accounts is instant. */
function file() {
  return path.join(app.getPath('userData'), 'astra-sessions.json');
}

function readAll() {
  try {
    return JSON.parse(fs.readFileSync(file(), 'utf8')) || {};
  } catch {
    return {};
  }
}

function writeAll(data) {
  fs.mkdirSync(path.dirname(file()), { recursive: true });
  fs.writeFileSync(file(), JSON.stringify(data));
}

function readSession(server, uuid) {
  const s = readAll()[`${server}|${uuid}`];
  if (!s) return null;
  try {
    return { token: s.encrypted ? safeStorage.decryptString(Buffer.from(s.token, 'base64')) : s.token };
  } catch {
    return null;
  }
}

function writeSession(server, uuid, token) {
  const enc = safeStorage.isEncryptionAvailable();
  const data = readAll();
  data[`${server}|${uuid}`] = { encrypted: enc, token: enc ? safeStorage.encryptString(token).toString('base64') : token };
  writeAll(data);
}

/** Forgets the Astra sessions of one account (or every account when uuid is omitted). */
function forget(uuid) {
  fs.rmSync(path.join(app.getPath('userData'), 'astra-session.json'), { force: true }); // pre-switcher file
  if (!uuid) {
    fs.rmSync(file(), { force: true });
    return;
  }
  const id = String(uuid).replace(/-/g, '');
  const data = readAll();
  for (const k of Object.keys(data)) if (k.endsWith(`|${id}`)) delete data[k];
  writeAll(data);
}

async function request(method, p, { token, body } = {}) {
  const base = serverUrl();
  if (!/^https?:\/\//.test(base)) throw new Error('Set the Astra server address in Settings.');
  let res;
  try {
    res = await fetch(base + p, {
      method,
      headers: {
        'Content-Type': 'application/json',
        'User-Agent': 'AstraLauncher',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(10000)
    });
  } catch {
    const err = new Error(`Can't reach the Astra server at ${base}.`);
    err.offline = true;
    throw err;
  }
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    // Cloudflare answers 52x/530 when the tunnel (or the PC hosting the server) is offline.
    const offline = [502, 503, 504, 520, 521, 522, 523, 524, 530].includes(res.status);
    const expired = res.status === 530 && isQuickTunnel(base);
    const err = new Error((data && data.error)
      || (expired
        ? 'This temporary server address has expired (it changes every time hosting restarts). Paste the new one in Settings, or set up a permanent address.'
        : offline
        ? 'The Astra server is offline right now. Cosmetics and friends come back when it does.'
        : `The Astra server answered ${res.status}.`));
    err.offline = offline;
    err.status = res.status;
    throw err;
  }
  return data;
}

/** Signs in to the Astra server with the Minecraft account (see the comment at the top). */
async function signIn(accountId) {
  let mc = await auth.getLaunchAuth({ id: accountId }); // cached Minecraft token (refreshed when needed)
  const uuid = String(mc.uuid).replace(/-/g, '');
  const { serverId } = await request('POST', '/api/auth/challenge');
  const join = async (token) => {
    try {
      return await fetch('https://sessionserver.mojang.com/session/minecraft/join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ accessToken: token, selectedProfile: uuid, serverId }),
        signal: AbortSignal.timeout(10000)
      });
    } catch {
      throw new Error("Couldn't reach Mojang to confirm your account.");
    }
  };
  let joined = await join(mc.access_token);
  if (joined.status === 401 || joined.status === 403) {
    // The saved Minecraft token was revoked early (e.g. signed in elsewhere): get a fresh one once.
    mc = await auth.getLaunchAuth({ id: accountId, force: true });
    joined = await join(mc.access_token);
  }
  if (joined.status === 429) {
    throw new Error("Mojang is rate-limiting sign-ins right now. Wait a minute and try again.");
  }
  if (joined.status !== 204 && joined.status !== 200) {
    throw new Error(`Mojang didn't accept the sign-in (${joined.status}). Try again in a minute.`);
  }
  const { token, player } = await request('POST', '/api/auth/verify', { body: { username: mc.name, uuid, serverId } });
  writeSession(serverUrl(), uuid, token);
  return { token, player };
}

const signingIn = new Map(); // "server|uuid" -> Promise, so parallel requests share one sign-in

/**
 * A working Astra session for an account (the active one by default), signing in again when
 * needed. Returns { token, player }.
 */
async function session(accountId) {
  const account = accountId ? auth.getProfile(accountId) : auth.getAccount();
  if (!account) throw new Error('Sign in with Microsoft first.');
  const uuid = String(account.id).replace(/-/g, '');
  const key = `${serverUrl()}|${uuid}`;
  const saved = readSession(serverUrl(), uuid);
  if (saved) {
    try {
      const { player } = await request('GET', '/api/me', { token: saved.token });
      return { token: saved.token, player };
    } catch (e) {
      if (e.status !== 401) throw e;
    }
  }
  if (!signingIn.has(key)) {
    signingIn.set(key, signIn(account.id).finally(() => signingIn.delete(key)));
  }
  return signingIn.get(key);
}

// ---- what the launcher page uses ----

const imageCache = new Map();

/** A data: URL for a cosmetic image (the launcher page can't load from arbitrary servers). */
async function image(id) {
  const key = `${serverUrl()}|${id}`;
  if (imageCache.has(key)) return imageCache.get(key);
  const res = await fetch(`${serverUrl()}/api/cosmetics/${encodeURIComponent(id)}/image`, { signal: AbortSignal.timeout(10000) });
  if (!res.ok) return null;
  const type = res.headers.get('content-type') || 'image/png';
  const url = `data:${type};base64,${Buffer.from(await res.arrayBuffer()).toString('base64')}`;
  imageCache.set(key, url);
  return url;
}

/** Your Astra account for the Cosmetics tab: rank, everything you own (with images), what you wear. */
async function account() {
  const { player } = await session();
  const owned = await Promise.all(player.owned.map(async (c) => ({ ...c, image: await image(c.id).catch(() => null) })));
  return {
    server: serverUrl(),
    name: player.name,
    rank: player.rank,
    staff: player.permissions.includes('admin.view'),
    equipped: player.equippedIds,
    owned
  };
}

async function equip(type, id) {
  const { token } = await session();
  const { player } = await request('POST', '/api/me/equip', { token, body: { type, id } });
  return player.equippedIds;
}

/** Opens the website signed in (the admin panel for staff, your profile for everyone else). */
async function openWebsite(next) {
  const { token } = await session();
  let { url } = await request('POST', '/api/auth/web-code', { token });
  if (typeof next === 'string' && /^\/[a-z][a-z0-9/_#-]*$/i.test(next)) url += `&next=${encodeURIComponent(next)}`;
  const base = new URL(serverUrl());
  const target = new URL(url);
  if (target.origin !== base.origin && target.hostname !== base.hostname) {
    throw new Error('The server sent an unexpected sign-in link.');
  }
  await shell.openExternal(target.toString());
}

/** For the launcher's live connection (WebSocket): the server address and this account's token. */
async function live() {
  const { token } = await session();
  const base = serverUrl();
  return { url: base.replace(/^http/, 'ws') + '/api/live', token };
}

// ---------- friends (same lists the game and website show) ----------

async function friends() {
  const { token } = await session();
  return request('GET', '/api/friends', { token });
}

async function friendAction(action, body) {
  const allowed = { request: '/api/friends/request', accept: '/api/friends/accept', remove: '/api/friends/remove' };
  if (!allowed[action]) throw new Error('Unknown friend action.');
  const { token } = await session();
  await request('POST', allowed[action], { token, body });
  return friends();
}

/** For the game: -Dastra.api / -Dastra.token. Never fails; without a session others' cosmetics still show. */
async function gameArgs(log) {
  const args = [];
  const base = serverUrl();
  if (!/^https?:\/\//.test(base)) return args;
  args.push(`-Dastra.api=${base}`);
  try {
    const { token } = await session();
    args.push(`-Dastra.token=${token}`);
  } catch (e) {
    log(`[Astra] Not signed in to the Astra server (${e.message}). Your cosmetics won't load this time.`);
  }
  return args;
}

/** Public server settings (Discord application ID, website links). */
async function meta() {
  return request('GET', '/api/meta');
}

/** Where the game last said you are: { server, version, hidden } or null (from /api/me). */
async function playing() {
  const account = auth.getAccount();
  const saved = account && readSession(serverUrl(), String(account.id).replace(/-/g, ''));
  if (!saved) return null;
  const { player } = await request('GET', '/api/me', { token: saved.token });
  return player.playing || null;
}

module.exports = { account, equip, openWebsite, friends, friendAction, live, gameArgs, forget, serverUrl, meta, playing };
