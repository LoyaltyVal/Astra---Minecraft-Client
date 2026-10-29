const fs = require('fs');
const path = require('path');
const { app, safeStorage } = require('electron');
const { Auth } = require('msmc');

/**
 * Microsoft sign-in through msmc, using the same Microsoft login the official launcher uses.
 * Several accounts can be saved (the account switcher); for each, only the refresh token and the
 * public profile (name + UUID) are stored, the token encrypted with the OS keychain (DPAPI on
 * Windows) via Electron's safeStorage.
 */

function file() {
  return path.join(app.getPath('userData'), 'accounts.json');
}

/** Before the account switcher there was a single account.json. */
function legacyFile() {
  return path.join(app.getPath('userData'), 'account.json');
}

function protect(text) {
  if (safeStorage.isEncryptionAvailable()) {
    return { encrypted: true, data: safeStorage.encryptString(text).toString('base64') };
  }
  return { encrypted: false, data: text };
}

function unprotect(blob) {
  if (!blob) return null;
  if (blob.encrypted) {
    return safeStorage.decryptString(Buffer.from(blob.data, 'base64'));
  }
  return blob.data;
}

/** { active: uuid, accounts: [{ profile: { id, name }, token }] } */
function readAll() {
  try {
    const data = JSON.parse(fs.readFileSync(file(), 'utf8'));
    if (data && Array.isArray(data.accounts)) return data;
  } catch {
    // fall through to the old single-account file
  }
  try {
    const old = JSON.parse(fs.readFileSync(legacyFile(), 'utf8'));
    if (old && old.profile) return { active: old.profile.id, accounts: [old] };
  } catch {
    // no saved accounts
  }
  return { active: null, accounts: [] };
}

function writeAll(data) {
  fs.mkdirSync(path.dirname(file()), { recursive: true });
  fs.writeFileSync(file(), JSON.stringify(data, null, 2));
  fs.rmSync(legacyFile(), { force: true });
}

function readSaved() {
  const data = readAll();
  return data.accounts.find((a) => a.profile.id === data.active) || null;
}

/**
 * Saves (or updates) an account: the Microsoft refresh token and, when given, the Minecraft token
 * (valid ~23 hours) so the launcher doesn't log in to Minecraft again for every request.
 */
function writeSaved(xbox, profile, { activate = true, mc = null } = {}) {
  const data = readAll();
  const entry = { profile: { id: profile.id, name: profile.name }, token: protect(xbox.save()) };
  if (mc) entry.mc = protect(JSON.stringify(mc));
  const i = data.accounts.findIndex((a) => a.profile.id === profile.id);
  if (i >= 0) data.accounts[i] = entry;
  else data.accounts.push(entry);
  if (activate || !data.active) data.active = profile.id;
  writeAll(data);
  return entry.profile;
}

/**
 * Turns msmc's errors into something a player can act on. msmc throws either a code string
 * ("error.gui.closed") or { ts: code, response } for failed HTTP calls.
 *   err.expired   Microsoft rejected the saved sign-in: the player has to sign in again.
 *   err.retry     Temporary (rate limit, Microsoft/Xbox/Mojang hiccup, no internet): try later.
 */
function friendlyError(e) {
  const code = typeof e === 'string' ? e : (e && (e.ts || e.code || e.message)) || String(e);
  const status = e && e.response && e.response.status;
  const make = (message, extra = {}) => Object.assign(new Error(message), { code, status }, extra);

  if (/gui\.closed|cancel/i.test(code)) return make('Sign-in was cancelled.', { cancelled: true });
  if (status === 429) {
    return make("Minecraft's sign-in servers are busy (too many sign-ins in a row). Wait a minute and try again.", { retry: true });
  }
  if (status >= 500 || /ETIMEDOUT|ECONNRESET|socket hang up/i.test(code)) {
    return make('Microsoft or Minecraft sign-in is having problems right now. Try again in a minute.', { retry: true });
  }
  if (/network|fetch|ENOTFOUND|ECONN|EAI_AGAIN/i.test(code)) {
    return make("Couldn't reach Microsoft. Check your internet connection.", { retry: true });
  }
  if (/^error\.auth\.microsoft/.test(code) && (status === 400 || status === 401)) {
    return make('Your Microsoft sign-in expired or was revoked. Sign in to this account again.', { expired: true });
  }
  if (/xsts\.userNotFound/.test(code)) return make("This Microsoft account has no Xbox profile yet. Sign in once at xbox.com, then try again.");
  if (/xsts\.bannedCountry/.test(code)) return make("Xbox Live isn't available in this account's country.");
  if (/xsts\.child/.test(code)) return make('This is a child account: an adult needs to add it to a Microsoft family first.');
  if (/profile|entitlement/i.test(code)) return make("This Microsoft account doesn't own Minecraft: Java Edition.");
  if (/xsts|xbox/i.test(code)) return make('Xbox Live rejected the sign-in. Try again, or sign in once at xbox.com.', { retry: true });
  if (/minecraft\.login/.test(code)) {
    return make("Minecraft's sign-in didn't work this time. Wait a minute and try again.", { retry: true });
  }
  return make(`Sign-in failed (${code}${status ? ` ${status}` : ''}).`);
}

/** The signed-in profile from disk (no network), or null. */
function getAccount() {
  const saved = readSaved();
  return saved && saved.profile ? saved.profile : null;
}

/** Opens Microsoft's sign-in window and saves the account. */
async function login({ activate = true } = {}) {
  try {
    const manager = new Auth('select_account');
    const xbox = await manager.launch('electron', {
      width: 520,
      height: 680,
      resizable: false,
      title: 'Sign in with Microsoft',
      autoHideMenuBar: true,
      backgroundColor: '#ffffff'
    });
    const mc = await xbox.getMinecraft();
    return writeSaved(xbox, mc.profile, { mc: mc.mclc(), activate });
  } catch (e) {
    throw friendlyError(e);
  }
}

/** Every saved account, for the account switcher. */
function listAccounts() {
  const data = readAll();
  return data.accounts.map((a) => ({
    id: a.profile.id, name: a.profile.name, active: a.profile.id === data.active, expired: Boolean(a.expired)
  }));
}

function switchAccount(id) {
  const data = readAll();
  if (!data.accounts.some((a) => a.profile.id === id)) throw new Error('That account isn\'t saved any more.');
  data.active = id;
  writeAll(data);
  return getAccount();
}

/** Removes an account (the active one by default); another saved account becomes active. */
function removeAccount(id) {
  const data = readAll();
  const target = id || data.active;
  data.accounts = data.accounts.filter((a) => a.profile.id !== target);
  if (data.active === target) data.active = data.accounts.length ? data.accounts[0].profile.id : null;
  writeAll(data);
  return getAccount();
}

function logout() {
  return removeAccount(null);
}

const inflight = new Map(); // account id -> Promise, so parallel requests share one sign-in
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function decodeMc(entry) {
  try {
    return entry && entry.mc ? JSON.parse(unprotect(entry.mc)) : null;
  } catch {
    return null;
  }
}

/**
 * minecraft-launcher-core's authorization object for an account (the active one by default).
 * Uses the saved Minecraft token while it has at least `minValidMs` left; otherwise refreshes
 * once (shared by everyone asking at the same time) with a retry for rate limits.
 */
async function getLaunchAuth({ id = null, minValidMs = 5 * 60 * 1000, force = false } = {}) {
  const data = readAll();
  const entry = data.accounts.find((a) => a.profile.id === (id || data.active));
  if (!entry || !entry.token) throw new Error('Sign in with Microsoft first.');
  const accountId = entry.profile.id;

  const cached = decodeMc(entry);
  if (!force && cached && cached.meta && cached.meta.exp - Date.now() > minValidMs) return cached;

  if (inflight.has(accountId)) return inflight.get(accountId);
  const job = (async () => {
    let lastError;
    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        const current = readAll().accounts.find((a) => a.profile.id === accountId) || entry;
        const manager = new Auth();
        const xbox = await manager.refresh(unprotect(current.token));
        const mc = await xbox.getMinecraft();
        const auth = mc.mclc();
        writeSaved(xbox, mc.profile, { activate: false, mc: auth }); // also clears "expired"
        return auth;
      } catch (e) {
        lastError = friendlyError(e);
        if (!lastError.retry || attempt === 2) break;
        await sleep(lastError.status === 429 ? 15000 * (attempt + 1) : 2000 * (attempt + 1));
      }
    }
    if (lastError.expired) {
      const all = readAll();
      const saved = all.accounts.find((a) => a.profile.id === accountId);
      if (saved) {
        saved.expired = true;
        delete saved.mc;
        writeAll(all);
      }
    }
    // A cached token that's still valid beats an error (e.g. a short rate limit).
    if (cached && cached.meta && cached.meta.exp > Date.now() + 60 * 1000) return cached;
    throw lastError;
  })();
  inflight.set(accountId, job);
  try {
    return await job;
  } finally {
    inflight.delete(accountId);
  }
}

/** The profile (id, name) of an account without any network calls. */
function getProfile(id) {
  const data = readAll();
  const entry = data.accounts.find((a) => a.profile.id === id);
  return entry ? entry.profile : null;
}

module.exports = { getAccount, getProfile, login, logout, getLaunchAuth, listAccounts, switchAccount, removeAccount, friendlyError };
