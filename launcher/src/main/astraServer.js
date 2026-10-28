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

function serverUrl() {
  return String(store.get().serverUrl || '').replace(/\/+$/, '');
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
    const err = new Error((data && data.error) || `The Astra server answered ${res.status}.`);
    err.status = res.status;
    throw err;
  }
  return data;
}

/** Signs in to the Astra server with the Minecraft account (see the comment at the top). */
async function signIn() {
  const mc = await auth.getLaunchAuth(); // refreshed Minecraft token + profile
  const uuid = String(mc.uuid).replace(/-/g, '');
  const { serverId } = await request('POST', '/api/auth/challenge');
  let joined;
  try {
    joined = await fetch('https://sessionserver.mojang.com/session/minecraft/join', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ accessToken: mc.access_token, selectedProfile: uuid, serverId }),
      signal: AbortSignal.timeout(10000)
    });
  } catch {
    throw new Error("Couldn't reach Mojang to confirm your account.");
  }
  if (joined.status !== 204 && joined.status !== 200) {
    throw new Error(`Mojang didn't accept the sign-in (${joined.status}). Try signing out and in again.`);
  }
  const { token, player } = await request('POST', '/api/auth/verify', { body: { username: mc.name, uuid, serverId } });
  writeSession(serverUrl(), uuid, token);
  return { token, player };
}

/**
 * A working Astra session for the signed-in Minecraft account, signing in again when needed.
 * Returns { token, player }.
 */
async function session() {
  const account = auth.getAccount();
  if (!account) throw new Error('Sign in with Microsoft first.');
  const uuid = String(account.id).replace(/-/g, '');
  const saved = readSession(serverUrl(), uuid);
  if (saved) {
    try {
      const { player } = await request('GET', '/api/me', { token: saved.token });
      return { token: saved.token, player };
    } catch (e) {
      if (e.status !== 401) throw e;
    }
  }
  return signIn();
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
