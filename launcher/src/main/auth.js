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

/** Saves (or updates) an account and makes it the active one. */
function writeSaved(xbox, profile) {
  const data = readAll();
  const entry = { profile: { id: profile.id, name: profile.name }, token: protect(xbox.save()) };
  const i = data.accounts.findIndex((a) => a.profile.id === profile.id);
  if (i >= 0) data.accounts[i] = entry;
  else data.accounts.push(entry);
  data.active = profile.id;
  writeAll(data);
  return entry.profile;
}

/** Turns msmc's error codes into something a player can act on. */
function friendlyError(e) {
  const code = typeof e === 'string' ? e : (e && (e.message || e.ts || e.code || String(e))) || 'unknown';
  if (/gui\.closed|cancel/i.test(code)) {
    const err = new Error('Sign-in was cancelled.');
    err.cancelled = true;
    return err;
  }
  if (/profile|entitlement|owner/i.test(code)) {
    return new Error("This Microsoft account doesn't own Minecraft: Java Edition.");
  }
  if (/xsts|xbox/i.test(code)) {
    return new Error('Xbox Live rejected the sign-in. Make sure the account has an Xbox profile (sign in once at xbox.com).');
  }
  if (/network|fetch|ENOTFOUND|ECONN/i.test(code)) {
    return new Error("Couldn't reach Microsoft. Check your internet connection.");
  }
  return new Error(`Sign-in failed (${code}).`);
}

/** The signed-in profile from disk (no network), or null. */
function getAccount() {
  const saved = readSaved();
  return saved && saved.profile ? saved.profile : null;
}

/** Opens Microsoft's sign-in window and saves the account. */
async function login() {
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
    return writeSaved(xbox, mc.profile);
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

/** Refreshes the saved tokens and returns minecraft-launcher-core's authorization object. */
async function getLaunchAuth() {
  const saved = readSaved();
  if (!saved || !saved.token) {
    throw new Error('Sign in with Microsoft first.');
  }
  try {
    const manager = new Auth();
    const xbox = await manager.refresh(unprotect(saved.token));
    const mc = await xbox.getMinecraft();
    writeSaved(xbox, mc.profile);
    return mc.mclc();
  } catch (e) {
    const err = friendlyError(e);
    if (!err.cancelled && !/reach Microsoft/.test(err.message)) {
      // Microsoft refused the saved sign-in (expired or revoked): mark it for the account switcher.
      const data = readAll();
      const entry = data.accounts.find((a) => a.profile.id === saved.profile.id);
      if (entry) {
        entry.expired = true;
        writeAll(data);
      }
      err.message += ' Sign in to this account again (Switch account → Sign in again).';
      err.expired = true;
    }
    throw err;
  }
}

module.exports = { getAccount, login, logout, getLaunchAuth, listAccounts, switchAccount, removeAccount };
