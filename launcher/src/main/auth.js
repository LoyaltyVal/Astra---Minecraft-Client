const fs = require('fs');
const path = require('path');
const { app, safeStorage } = require('electron');
const { Auth } = require('msmc');

/**
 * Microsoft sign-in through msmc, using the same Microsoft login the official launcher uses.
 * Only the refresh token and the public profile (name + UUID) are stored, encrypted with the
 * OS keychain (DPAPI on Windows) via Electron's safeStorage.
 */

function file() {
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

function readSaved() {
  try {
    return JSON.parse(fs.readFileSync(file(), 'utf8'));
  } catch {
    return null;
  }
}

function writeSaved(xbox, profile) {
  const saved = {
    profile: { id: profile.id, name: profile.name },
    token: protect(xbox.save())
  };
  fs.mkdirSync(path.dirname(file()), { recursive: true });
  fs.writeFileSync(file(), JSON.stringify(saved, null, 2));
  return saved.profile;
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

function logout() {
  fs.rmSync(file(), { force: true });
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
    if (!err.cancelled) {
      err.message += ' You may need to sign in again.';
    }
    throw err;
  }
}

module.exports = { getAccount, login, logout, getLaunchAuth };
