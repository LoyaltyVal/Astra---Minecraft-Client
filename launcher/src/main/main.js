const path = require('path');
const fs = require('fs');
const { app, BrowserWindow, ipcMain, shell, dialog } = require('electron');
const auth = require('./auth');
const store = require('./store');
const paths = require('./paths');
const log = require('./log');
const astraServer = require('./astraServer');
const { VERSIONS } = require('./config');
const { launchGame, isRunning, killGame } = require('./launch');

let win = null;

if (!app.requestSingleInstanceLock()) {
  app.quit();
}

app.on('second-instance', () => {
  if (win) {
    if (win.isMinimized()) win.restore();
    win.show();
    win.focus();
  }
});

process.on('uncaughtException', (e) => log.write(`Uncaught: ${e.stack || e}`));
process.on('unhandledRejection', (e) => log.write(`Unhandled rejection: ${(e && e.stack) || e}`));

function iconPath() {
  const file = process.platform === 'win32' ? 'icon.ico' : 'icon.png';
  return path.join(__dirname, '..', '..', 'build', file);
}

function createWindow() {
  win = new BrowserWindow({
    width: 1080,
    height: 660,
    minWidth: 920,
    minHeight: 580,
    frame: false,
    show: false,
    backgroundColor: '#0a0a12',
    title: 'Astra Launcher',
    icon: fs.existsSync(iconPath()) ? iconPath() : undefined,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });
  win.loadFile(path.join(__dirname, '..', 'renderer', 'index.html'));
  win.once('ready-to-show', () => win.show());

  // Links open in the real browser, never inside the launcher.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https:\/\//.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (e) => e.preventDefault());
}

function send(channel, payload) {
  if (win && !win.isDestroyed()) {
    win.webContents.send(channel, payload);
  }
}

/** Bridges launch progress to the renderer and hides/shows the window around the game. */
const ui = {
  state(name, detail) {
    log.write(`state: ${name}${detail ? ' ' + JSON.stringify(detail) : ''}`);
    send('launch:state', { name, detail });
    const { hideOnLaunch } = store.get();
    if (name === 'running' && hideOnLaunch && win) win.hide();
    if (name === 'closed' && win) {
      win.show();
      win.focus();
    }
  },
  progress(p) {
    send('launch:progress', p);
  },
  log(line) {
    log.write(line);
    send('launch:log', line);
  }
};

function handle(channel, fn) {
  ipcMain.handle(channel, async (_event, ...args) => {
    try {
      return { ok: true, value: await fn(...args) };
    } catch (e) {
      log.write(`${channel} failed: ${e.stack || e}`);
      return { ok: false, error: e.message || String(e), cancelled: Boolean(e.cancelled) };
    }
  });
}

handle('app:info', () => ({
  version: app.getVersion(),
  versions: VERSIONS,
  running: isRunning(),
  gameFolder: paths.root()
}));

handle('auth:get', () => auth.getAccount());
handle('auth:login', () => auth.login());
handle('auth:logout', () => {
  auth.logout();
  astraServer.forget();
});

handle('settings:get', () => store.get());
handle('settings:set', (patch) => store.set(patch || {}));

handle('game:launch', async (versionId) => {
  try {
    return await launchGame(String(versionId), ui);
  } catch (e) {
    send('launch:state', { name: 'error', detail: { message: e.message } });
    throw e;
  }
});
handle('game:kill', () => killGame());
handle('game:openFolder', (versionId) => {
  const dir = versionId ? paths.instanceDir(String(versionId)) : paths.root();
  fs.mkdirSync(dir, { recursive: true });
  return shell.openPath(dir);
});
handle('app:openLogs', () => shell.openPath(log.dir()));

handle('astra:account', () => astraServer.account());
handle('astra:equip', (type, id) => astraServer.equip(String(type), id === null ? null : String(id)));
handle('astra:openWebsite', () => astraServer.openWebsite());

ipcMain.on('window:minimize', () => win && win.minimize());
ipcMain.on('window:maximize', () => {
  if (!win) return;
  if (win.isMaximized()) win.unmaximize();
  else win.maximize();
});
ipcMain.on('window:close', () => win && win.close());

app.whenReady().then(() => {
  log.open();
  createWindow();
});

app.on('window-all-closed', () => {
  // Closing the launcher doesn't kill a running game (it runs as its own process).
  app.quit();
});
