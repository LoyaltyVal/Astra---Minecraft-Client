const fs = require('fs');
const { Client } = require('minecraft-launcher-core');
const auth = require('./auth');
const store = require('./store');
const paths = require('./paths');
const { getVersion } = require('./config');
const { ensureJava } = require('./java');
const { ensureLoader } = require('./fabric');
const { installMod } = require('./mods');
const astraServer = require('./astraServer');

let running = null;

function isRunning() {
  return running !== null;
}

/**
 * Prepares and launches Astra for a game version.
 * `ui` receives: state(name, detail), progress({label, current, total}), log(line).
 */
async function launchGame(versionId, ui) {
  if (running) {
    throw new Error('Minecraft is already running.');
  }
  const version = getVersion(versionId);
  if (!version) {
    throw new Error(`Unknown version ${versionId}.`);
  }

  const settings = store.set({ lastVersion: versionId });
  ui.state('preparing');

  ui.progress({ label: 'Signing in', current: 0, total: 1 });
  const authorization = await auth.getLaunchAuth();

  const javaPath = settings.javaPath || (await ensureJava(version.javaComponent, ui.progress));
  if (settings.javaPath && !fs.existsSync(settings.javaPath)) {
    throw new Error(`The Java path in Settings doesn't exist: ${settings.javaPath}`);
  }

  const custom = await ensureLoader(version.id, ui.progress);
  installMod(version.id, ui.log);

  // Cosmetics server + your Astra session for the game. The token is kept out of the logs.
  ui.progress({ label: 'Connecting to Astra', current: 0, total: 1 });
  const astraArgs = await astraServer.gameArgs(ui.log);
  const secret = (astraArgs.find((a) => a.startsWith('-Dastra.token=')) || '').slice('-Dastra.token='.length);
  const log = (line) => ui.log(secret ? String(line).split(secret).join('********') : line);

  const gameDirectory = paths.instanceDir(version.id);
  fs.mkdirSync(gameDirectory, { recursive: true });

  const client = new Client();
  let started = false;

  client.on('debug', (line) => log(String(line).trim()));
  client.on('progress', (e) => {
    ui.progress({ label: `Downloading Minecraft ${e.type}`, current: e.task, total: e.total });
  });
  client.on('data', (chunk) => {
    for (const line of String(chunk).split(/\r?\n/)) {
      if (line.trim()) log(line);
    }
    if (!started) {
      started = true;
      ui.state('running');
    }
  });
  client.on('close', (code) => {
    running = null;
    ui.state('closed', { code });
  });

  ui.progress({ label: 'Starting Minecraft', current: 0, total: 0 });
  const proc = await client.launch({
    root: paths.root(),
    authorization,
    version: { number: version.id, type: 'release', custom },
    memory: { max: `${settings.memoryGb}G`, min: '1G' },
    javaPath,
    overrides: {
      gameDirectory,
      detached: false
    },
    customArgs: [
      ...astraArgs,
      '-XX:+UseG1GC',
      '-XX:+UnlockExperimentalVMOptions',
      '-XX:G1NewSizePercent=20',
      '-XX:MaxGCPauseMillis=50'
    ]
  });

  if (!proc) {
    throw new Error('Minecraft failed to start. Open the Logs tab for details.');
  }
  running = proc;
  return { pid: proc.pid };
}

function killGame() {
  if (running) {
    running.kill();
  }
}

module.exports = { launchGame, isRunning, killGame };
