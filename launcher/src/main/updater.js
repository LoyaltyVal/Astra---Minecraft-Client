// Launcher auto-update from GitHub Releases (electron-updater).
//
// Windows (NSIS) and Linux (AppImage) download the update in the background and install it the
// next time the launcher closes, or right away when you press "Restart". macOS can only install
// updates into a signed app, so an unsigned Mac build just says a new version is out and opens the
// download page.
const { app, shell } = require('electron');

const CHECK_EVERY_MS = 4 * 60 * 60 * 1000; // also re-check every 4 hours while the launcher stays open

let autoUpdater = null;
let status = { state: 'idle' };
let notify = () => {};
let log = () => {};
let isGameRunning = () => false;
let timer = null;

function set(next) {
  status = next;
  notify(status);
}

/** Auto-install only works where the platform allows it (see the top of this file). */
function canInstall() {
  if (process.platform === 'win32') return true;
  if (process.platform === 'linux') return Boolean(process.env.APPIMAGE); // a .deb/.tar.gz install can't self-update
  if (process.platform === 'darwin') return isSignedMacBuild();
  return false;
}

/** Settings electron-builder puts into the packaged package.json (see electron-builder.config.js). */
function pkg() {
  try {
    return require('../../package.json');
  } catch {
    return {};
  }
}

function isSignedMacBuild() {
  return Boolean(pkg().astraMacSigned);
}

function releasesPage() {
  const { owner, repo } = pkg().astraUpdates || {};
  return owner && repo ? `https://github.com/${owner}/${repo}/releases/latest` : null;
}

/**
 * Starts checking for updates (packaged builds only; `npm start` never updates itself).
 * onStatus receives { state: 'idle'|'checking'|'available'|'downloading'|'ready'|'none'|'error', version?, percent?, error?, manual? }.
 */
function start({ onStatus, logger, gameRunning }) {
  notify = onStatus || notify;
  log = logger || log;
  isGameRunning = gameRunning || isGameRunning;
  if (!app.isPackaged) {
    set({ state: 'dev' });
    return;
  }
  try {
    ({ autoUpdater } = require('electron-updater'));
  } catch (e) {
    log(`[Updater] electron-updater is missing: ${e.message}`);
    set({ state: 'error', error: 'The updater is missing from this build.' });
    return;
  }
  autoUpdater.logger = { info: (m) => log(`[Updater] ${m}`), warn: (m) => log(`[Updater] ${m}`), error: (m) => log(`[Updater] ${m}`), debug: () => {} };
  autoUpdater.autoDownload = canInstall();
  autoUpdater.autoInstallOnAppQuit = true;
  autoUpdater.allowPrerelease = false;

  autoUpdater.on('checking-for-update', () => set({ state: 'checking' }));
  autoUpdater.on('update-not-available', () => set({ state: 'none' }));
  autoUpdater.on('update-available', (info) => {
    set(canInstall()
      ? { state: 'downloading', version: info.version, percent: 0 }
      : { state: 'available', version: info.version, manual: true });
  });
  autoUpdater.on('download-progress', (p) => set({ state: 'downloading', version: status.version, percent: Math.round(p.percent || 0) }));
  autoUpdater.on('update-downloaded', (info) => set({ state: 'ready', version: info.version }));
  autoUpdater.on('error', (e) => {
    // Offline, GitHub down or rate-limited: try again later, quietly.
    log(`[Updater] ${e && e.message ? e.message : e}`);
    set({ state: 'error', error: friendly(e) });
  });

  check();
  timer = setInterval(check, CHECK_EVERY_MS);
}

function friendly(e) {
  const m = String((e && e.message) || e || '');
  if (/ENOTFOUND|ECONNREFUSED|ETIMEDOUT|EAI_AGAIN|net::/i.test(m)) return 'No internet connection.';
  if (/404/.test(m)) return 'No release has been published yet.';
  if (/403|rate limit/i.test(m)) return 'GitHub is busy. Trying again later.';
  if (/signature|not signed|publisher/i.test(m)) return 'The update failed its signature check and was not installed.';
  return 'Couldn\'t check for updates.';
}

function check() {
  if (!autoUpdater) return Promise.resolve(status);
  if (status.state === 'downloading' || status.state === 'ready') return Promise.resolve(status);
  return autoUpdater.checkForUpdates().then(() => status).catch(() => status);
}

/** Restarts into the downloaded update. Refused while Minecraft is running, so a game is never cut off. */
function install() {
  if (status.state === 'available' && status.manual) {
    const page = releasesPage();
    if (page) shell.openExternal(page);
    return { ok: true, opened: true };
  }
  if (!autoUpdater || status.state !== 'ready') throw new Error('No update is ready yet.');
  if (isGameRunning()) throw new Error('Close Minecraft first; the update installs when the launcher restarts.');
  setImmediate(() => autoUpdater.quitAndInstall(false, true));
  return { ok: true };
}

function getStatus() {
  return status;
}

function stop() {
  if (timer) clearInterval(timer);
}

module.exports = { start, check, install, getStatus, stop };
