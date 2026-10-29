const fs = require('fs');
const { Bridge } = require('./bridge');
const { Client } = require('minecraft-launcher-core');
const auth = require('./auth');
const store = require('./store');
const paths = require('./paths');
const { getVersion } = require('./config');
const { ensureJava, javaComponentFor } = require('./java');
const { ensureLoader } = require('./fabric');
const { installMod, ensureFabricApi } = require('./mods');
const astraServer = require('./astraServer');
const shared = require('./shared');
const { DiscordPresence } = require('./discord');

let running = null;

/**
 * Garbage collector settings tuned for short pauses (the widely used "Aikar" G1 flags; they work
 * on Java 8 and every newer Java). A long GC pause freezes the game; when it resumes, the
 * movement it owes the server arrives in one burst, and servers answer that with a lag back.
 */
const GC_ARGS = [
  '-XX:+UseG1GC',
  '-XX:+ParallelRefProcEnabled',
  '-XX:MaxGCPauseMillis=40',
  '-XX:+UnlockExperimentalVMOptions',
  '-XX:+DisableExplicitGC',
  '-XX:G1NewSizePercent=30',
  '-XX:G1MaxNewSizePercent=40',
  '-XX:G1HeapRegionSize=8M',
  '-XX:G1ReservePercent=20',
  '-XX:G1MixedGCCountTarget=4',
  '-XX:InitiatingHeapOccupancyPercent=15',
  '-XX:G1MixedGCLiveThresholdPercent=90',
  '-XX:SurvivorRatio=32',
  '-XX:MaxTenuringThreshold=1',
  '-XX:+PerfDisableSharedMem'
];

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
  // A Minecraft token with a few hours left, so joining servers keeps working through a long session.
  const authorization = await auth.getLaunchAuth({ minValidMs: 3 * 60 * 60 * 1000 });

  // Mojang says which Java each version needs (8 for 1.7-1.16, 17 for 1.18, 21 for 1.20.5+, ...).
  // The Java path in Settings is a Java 8 override, so it's only used for versions that run on Java 8.
  const component = await javaComponentFor(version.id);
  const customJava = component === 'jre-legacy' ? settings.javaPath : '';
  if (customJava && !fs.existsSync(customJava)) {
    throw new Error(`The Java path in Settings doesn't exist: ${customJava}`);
  }
  const javaPath = customJava || (await ensureJava(component, ui.progress));

  const custom = await ensureLoader(version.id, ui.progress);
  installMod(version.id, ui.log);
  if (version.fabricApi) await ensureFabricApi(version.id, ui.progress, ui.log);

  // Cosmetics server + your Astra session for the game. The token is kept out of the logs.
  ui.progress({ label: 'Connecting to Astra', current: 0, total: 1 });
  const astraArgs = await astraServer.gameArgs(ui.log);
  // The in-game account switcher talks to the launcher through this (see bridge.js).
  const bridge = new Bridge({ onAccountChanged: (a) => ui.accountChanged && ui.accountChanged(a), log: ui.log });
  let bridgeArgs = [];
  try {
    bridgeArgs = await bridge.start();
  } catch (e) {
    ui.log(`[Astra] The in-game account switcher is unavailable this time (${e.message}).`);
  }
  // Keep both the Astra token and the Minecraft access token out of the logs.
  const secrets = [
    (astraArgs.find((a) => a.startsWith('-Dastra.token=')) || '').slice('-Dastra.token='.length),
    authorization && authorization.access_token,
    (bridgeArgs.find((a) => a.startsWith('-Dastra.bridgeToken=')) || '').slice('-Dastra.bridgeToken='.length)
  ].filter((s) => s && s.length > 8);
  const log = (line) => {
    let text = String(line);
    for (const s of secrets) text = text.split(s).join('********');
    ui.log(text);
  };

  const gameDirectory = paths.instanceDir(version.id);
  fs.mkdirSync(gameDirectory, { recursive: true });
  // Settings, resource packs, screenshots and servers shared with the normal .minecraft folder.
  const share = settings.shareMinecraft;
  if (share) shared.prepare(gameDirectory, ui.log);

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
    bridge.stop();
    stopDiscord();
    if (share) shared.save(gameDirectory, ui.log);
    ui.state('closed', { code });
  });

  ui.progress({ label: 'Starting Minecraft', current: 0, total: 0 });
  const proc = await client.launch({
    root: paths.root(),
    authorization,
    version: { number: version.id, type: 'release', custom },
    // Same min and max, so the heap never has to grow mid-fight (growing it means a full pause).
    // Only when the PC has the RAM to spare; otherwise Java would refuse to start.
    memory: { max: `${settings.memoryGb}G`, min: settings.memoryGb <= (require('os').totalmem() / 1024 ** 3) * 0.6 ? `${settings.memoryGb}G` : '1G' },
    javaPath,
    overrides: {
      gameDirectory,
      detached: false
    },
    customArgs: [
      ...astraArgs,
      ...bridgeArgs,
      ...GC_ARGS
    ]
  });

  if (!proc) {
    bridge.stop();
    throw new Error('Minecraft failed to start. Open the Logs tab for details.');
  }
  running = proc;
  if (settings.discordStatus) startDiscord(version.id, ui.log);
  return { pid: proc.pid };
}

// ---- Discord status while the game runs ----

let discord = null;
let discordTimer = null;

async function startDiscord(versionId, log) {
  stopDiscord();
  try {
    const meta = await astraServer.meta();
    if (!meta.discordClientId) return;
    discord = new DiscordPresence(log);
    if (!(await discord.start(meta.discordClientId))) {
      discord = null;
      return;
    }
    const website = meta.publicUrl;
    const tick = async () => {
      const playing = await astraServer.playing().catch(() => null);
      if (!discord) return;
      discord.update({
        version: (playing && playing.version) || versionId,
        server: playing ? playing.server : null,
        hidden: Boolean(playing && playing.hidden),
        website
      });
    };
    await tick();
    discordTimer = setInterval(tick, 15000);
    log('[Astra] Discord status is on.');
  } catch (e) {
    log(`[Astra] Discord status unavailable: ${e.message}`);
  }
}

function stopDiscord() {
  if (discordTimer) clearInterval(discordTimer);
  discordTimer = null;
  if (discord) discord.stop();
  discord = null;
}

function killGame() {
  if (running) {
    running.kill();
  }
}

module.exports = { launchGame, isRunning, killGame };
