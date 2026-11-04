'use strict';

const fs = require('fs');
const path = require('path');
const { sendJson, HttpError, readJson, redirect } = require('../http/Router');
const { serveFile } = require('../http/static');
const { RANKS, permissionsOf } = require('../services/Ranks');
const { RARITIES } = require('../services/Cosmetics');
const { normalizeUuid } = require('../services/Players');
const { requirePlayer } = require('./guards');

/** SomaFM and Radio Paradise: free, listener-supported, and they offer plain MP3 streams. */
const DEFAULT_STATIONS = [
  { id: 'groovesalad', name: 'Groove Salad', genre: 'Ambient / downtempo', url: 'https://ice.somafm.com/groovesalad-128-mp3' },
  { id: 'fluid', name: 'Fluid', genre: 'Instrumental hip hop', url: 'https://ice.somafm.com/fluid-128-mp3' },
  { id: 'beatblender', name: 'Beat Blender', genre: 'Deep house', url: 'https://ice.somafm.com/beatblender-128-mp3' },
  { id: 'defcon', name: 'DEF CON Radio', genre: 'Electronic for hacking', url: 'https://ice.somafm.com/defcon-128-mp3' },
  { id: 'lush', name: 'Lush', genre: 'Chill vocals', url: 'https://ice.somafm.com/lush-128-mp3' },
  { id: 'dronezone', name: 'Drone Zone', genre: 'Ambient space', url: 'https://ice.somafm.com/dronezone-128-mp3' },
  { id: 'radioparadise', name: 'Radio Paradise', genre: 'Eclectic rock & more', url: 'https://stream.radioparadise.com/mp3-128' }
];

const INSTALLER = path.join(__dirname, '..', '..', 'downloads', 'Astra-Setup.exe');

/**
 * The current Astra builds, put in server/downloads/mods/<version>/astra.jar by the build tools.
 * Launchers download the newest one before each launch, so players get fixes without a new installer.
 */
const MODS_DIR = path.join(__dirname, '..', '..', 'downloads', 'mods');
const modHashes = new Map(); // version -> { mtime, size, sha1 }

function clientMods() {
  const out = {};
  let versions = [];
  try {
    versions = fs.readdirSync(MODS_DIR).filter((v) => /^\d+(\.\d+){1,2}$/.test(v));
  } catch {
    return out;
  }
  for (const v of versions) {
    const file = path.join(MODS_DIR, v, 'astra.jar');
    let stat;
    try {
      stat = fs.statSync(file);
    } catch {
      continue;
    }
    let h = modHashes.get(v);
    if (!h || h.mtime !== stat.mtimeMs || h.size !== stat.size) {
      h = { mtime: stat.mtimeMs, size: stat.size, sha1: require('crypto').createHash('sha1').update(fs.readFileSync(file)).digest('hex') };
      modHashes.set(v, h);
    }
    out[v] = { sha1: h.sha1, size: h.size, builtAt: new Date(h.mtime).toISOString() };
  }
  return out;
}

/** Launcher files that can sit in server/downloads, and which download button each one is. */
const LOCAL_DOWNLOADS = {
  windows: ['Astra-Setup.exe'],
  macArm: ['Astra-Launcher-mac-arm64.dmg', 'Astra-Launcher-mac-arm64.zip'],
  macIntel: ['Astra-Launcher-mac-x64.dmg', 'Astra-Launcher-mac-x64.zip'],
  linux: ['Astra-Launcher.AppImage', 'Astra-Launcher-linux-x64.tar.gz']
};
const DOWNLOADS_DIR = path.dirname(INSTALLER);
const RELEASE_ASSETS = {
  windows: 'Astra-Launcher-Setup.exe',
  macArm: 'Astra-Launcher-mac-arm64.dmg',
  macIntel: 'Astra-Launcher-mac-x64.dmg',
  linux: 'Astra-Launcher.AppImage'
};

/**
 * Which launcher downloads exist right now: files in server/downloads, plus (when "releasesRepo"
 * is set) the files of the newest published GitHub Release. Only platforms that really have a
 * file get a button, so the website never links to something missing.
 */
function createDownloads(config, fetchImpl) {
  let release = { at: 0, assets: {}, page: null };
  let refreshing = null;

  async function refreshRelease() {
    const repo = String(config.releasesRepo || '').trim();
    if (!/^[\w.-]+\/[\w.-]+$/.test(repo)) return;
    try {
      const res = await fetchImpl(`https://api.github.com/repos/${repo}/releases/latest`, {
        headers: { 'User-Agent': 'AstraServer', Accept: 'application/vnd.github+json' },
        signal: AbortSignal.timeout(5000)
      });
      if (res.status === 404) {
        release = { at: Date.now(), assets: {}, page: null }; // no release published yet
        return;
      }
      if (!res.ok) throw new Error(`GitHub answered ${res.status}`);
      const data = await res.json();
      const assets = {};
      for (const [key, name] of Object.entries(RELEASE_ASSETS)) {
        const a = (data.assets || []).find((x) => x.name === name);
        if (a && a.browser_download_url) assets[key] = a.browser_download_url;
      }
      release = { at: Date.now(), assets, page: data.html_url || null, version: String(data.tag_name || '').replace(/^v/, '') };
    } catch (e) {
      release.at = Date.now() - 9 * 60 * 1000; // keep what we had; try again in a minute
    }
  }

  return {
    /** { windows?, macArm?, macIntel?, linux?, page? } (URLs), or null when there's nothing. */
    async current() {
      if (Date.now() - release.at > 10 * 60 * 1000) {
        refreshing = refreshing || refreshRelease().finally(() => { refreshing = null; });
        if (!release.at) await Promise.race([refreshing, new Promise((r) => setTimeout(r, 4000))]);
      }
      const out = { ...release.assets };
      for (const [key, names] of Object.entries(LOCAL_DOWNLOADS)) {
        if (out[key]) continue;
        const name = names.find((n) => fs.existsSync(path.join(DOWNLOADS_DIR, n)));
        if (name) out[key] = `/download/${name}`;
      }
      if (release.page) out.page = release.page;
      if (release.version) out.version = release.version;
      return Object.keys(out).some((k) => ['windows', 'macArm', 'macIntel', 'linux'].includes(k)) ? out : null;
    }
  };
}

/** {version, size, versions} written by "Build Installer.bat" next to the installer, or null. */
function installerInfo() {
  try {
    const info = JSON.parse(fs.readFileSync(INSTALLER.replace(/\.exe$/, '.json'), 'utf8'));
    return {
      version: /^[\w.+-]{1,32}$/.test(String(info.version)) ? String(info.version) : '',
      size: Number(info.size) || fs.statSync(INSTALLER).size,
      versions: Array.isArray(info.versions) ? info.versions.filter((v) => /^\d+(\.\d+){1,2}$/.test(v)) : []
    };
  } catch {
    try {
      return { version: '', size: fs.statSync(INSTALLER).size, versions: [] };
    } catch {
      return null;
    }
  }
}

/** Public API (website, game cosmetics lookup) plus sign-in and the player's own endpoints. */
function publicRoutes(router, ctx) {
  const { players, cosmetics, auth, config } = ctx;

  // ---------- site data ----------
  const downloads = createDownloads(config, ctx.fetch || globalThis.fetch);
  router.get('/api/meta', async (req, res) => {
    const all = config.downloadUrl ? null : await downloads.current();
    sendJson(res, 200, {
      ranks: Object.values(RANKS),
      rarities: RARITIES,
      publicUrl: config.publicUrl,
      downloadUrl: config.downloadUrl || (all && all.windows) || '',
      download: config.downloadUrl ? null : installerInfo(),
      downloads: all,
      discordInvite: /^https:\/\/(discord\.gg|discord\.com\/invite)\//.test(config.discordInvite || '') ? config.discordInvite : '',
      discordClientId: /^\d{15,25}$/.test(String(config.discordClientId || '')) ? String(config.discordClientId) : ''
    });
  });

  /** The in-game radio's stations: config.radioStations, or the built-in list. */
  router.get('/api/radio', (req, res) => {
    const custom = Array.isArray(config.radioStations) ? config.radioStations.filter((s) => s && /^https?:\/\//.test(String(s.url || ''))) : [];
    const list = (custom.length ? custom : DEFAULT_STATIONS).map((s, i) => ({
      id: String(s.id || `station-${i}`), name: String(s.name || s.url).slice(0, 40), genre: String(s.genre || '').slice(0, 40), url: String(s.url)
    }));
    sendJson(res, 200, { stations: list });
  });

  /**
   * Who's online: everyone sees how many players have Astra open; the list itself only holds the
   * signed-in player's friends (with the server they're on, unless they hide it).
   */
  router.get('/api/online', (req, res) => {
    const viewer = auth.playerFor(req);
    const online = players.onlineNow();
    const friends = new Set((viewer && viewer.friends) || []);
    sendJson(res, 200, {
      online: online.length,
      signedIn: Boolean(viewer),
      players: viewer ? online.filter((p) => friends.has(p.uuid)).map((p) => players.publicView(p, cosmetics)) : []
    });
  });

  router.get('/api/stats', (req, res) => sendJson(res, 200, {
    players: players.count(),
    online: players.onlineNow().length,
    cosmetics: cosmetics.all().length,
    staff: players.staff().length
  }));

  router.get('/api/cosmetics', (req, res) => {
    const type = req.query.get('type');
    const list = cosmetics.all().filter((c) => !type || c.type === type).map((c) => cosmetics.publicView(c));
    sendJson(res, 200, { cosmetics: list });
  });

  router.get('/api/cosmetics/:id/image', (req, res) => {
    const c = cosmetics.get(req.params.id);
    if (!c || !serveFile(res, cosmetics.dir, c.file, req)) throw new HttpError(404, 'No such cosmetic.');
  });

  router.get('/api/team', (req, res) => {
    sendJson(res, 200, { team: players.staff().map((p) => players.publicView(p, cosmetics)) });
  });

  router.get('/api/profile/:name', (req, res) => {
    const p = players.byName(req.params.name) || players.get(req.params.name);
    if (!p || !p.joinedAt) throw new HttpError(404, 'No Astra player with that name.');
    sendJson(res, 200, { player: players.publicView(p, cosmetics) });
  });

  /**
   * Batch lookup for the game: ?uuids=a,b,c (up to 200). Returns only Astra players, with their
   * rank and equipped cosmetics, so every Astra client can draw everyone else's cape and wings.
   */
  router.get('/api/players', (req, res) => {
    const ids = String(req.query.get('uuids') || '').split(',').map(normalizeUuid).filter(Boolean).slice(0, 200);
    const viewer = auth.playerFor(req);
    if (viewer) players.touch(viewer);
    const out = {};
    for (const id of ids) {
      const p = players.get(id);
      if (!p || !p.joinedAt || p.banned) continue;
      const r = players.publicView(p, cosmetics);
      out[id] = { name: r.name, rank: r.rank, cape: p.equipped.cape, wings: p.equipped.wings, emote: ctx.social.currentEmote(p) };
    }
    sendJson(res, 200, { players: out });
  });

  // ---------- sign-in ----------
  router.post('/api/auth/challenge', (req, res) => sendJson(res, 200, auth.challenge()));

  router.post('/api/auth/verify', async (req, res) => {
    const body = await readJson(req);
    const { token, player } = await auth.verify(body, req.clientIp);
    ctx.audit.record(player, 'signed in', null, null);
    sendJson(res, 200, { token, player: me(ctx, player) });
  });

  router.post('/api/auth/web-code', requirePlayer(ctx), (req, res) => {
    sendJson(res, 200, auth.webCode(req.player));
  });

  router.get('/login', (req, res) => {
    const result = auth.redeemWebCode(String(req.query.get('code') || ''));
    if (!result) return redirect(res, '/?signin=expired');
    const staff = permissionsOf(result.player).includes('admin.view');
    // ?next=/friends (from the app's Friends tab); only plain site paths are allowed.
    const next = String(req.query.get('next') || '');
    const safeNext = /^\/[a-z][a-z0-9/_-]*(#[a-z-]+)?$/i.test(next) ? next : null;
    redirect(res, safeNext || (staff ? '/admin' : `/player/${encodeURIComponent(result.player.name)}`), { 'Set-Cookie': result.cookie });
  });

  router.get('/logout', (req, res) => redirect(res, '/', { 'Set-Cookie': auth.logout(req) }));

  // ---------- the signed-in player ----------
  router.get('/api/me', requirePlayer(ctx), (req, res) => {
    players.touch(req.player);
    sendJson(res, 200, { player: me(ctx, req.player) });
  });

  /** The game reports where you are every 30 seconds (see CosmeticsManager#tick in the client). */
  router.post('/api/me/presence', requirePlayer(ctx), async (req, res) => {
    const body = await readJson(req);
    const before = JSON.stringify(players.playing(req.player));
    players.setPresence(req.player, body || {});
    if (JSON.stringify(players.playing(req.player)) !== before) ctx.live.presenceChanged(req.player);
    sendJson(res, 200, { ok: true });
  });

  router.post('/api/me/equip', requirePlayer(ctx), async (req, res) => {
    const { type, id } = await readJson(req);
    try {
      cosmetics.equip(req.player, type, id === null || id === undefined || id === '' ? null : String(id));
    } catch (e) {
      throw new HttpError(400, e.message);
    }
    sendJson(res, 200, { player: me(ctx, req.player) });
  });

  // ---------- pages ----------
  const page = (file) => (req, res) => {
    if (!serveFile(res, config.publicDir, file, req)) throw new HttpError(404, 'Page not found.');
  };
  router.get('/', page('index.html'));
  router.get('/cosmetics', page('cosmetics.html'));
  router.get('/team', page('team.html'));
  router.get('/players', page('players.html'));
  router.get('/friends', page('friends.html'));
  router.get('/account', page('account.html'));
  router.get('/admin', page('admin.html'));
  router.get('/player/:name', page('player.html'));
  // "Build Installer.bat" copies the installer here, so the website's Download button just works.
  router.get('/download/Astra-Setup.exe', (req, res) => {
    const info = installerInfo();
    const name = info && info.version ? `Astra-Setup-${info.version}.exe` : 'Astra-Setup.exe';
    res.setHeader('Content-Disposition', `attachment; filename="${name}"`);
    if (!serveFile(res, path.dirname(INSTALLER), path.basename(INSTALLER), req)) {
      throw new HttpError(404, 'The installer hasn\'t been built yet. Run "Build Installer.bat" on the server PC.');
    }
  });
  // The current Astra builds, for launchers (see clientMods).
  router.get('/api/client/mods', (req, res) => sendJson(res, 200, { mods: clientMods() }));
  router.get('/api/client/mods/:version/astra.jar', (req, res) => {
    const v = req.params.version;
    if (!/^\d+(\.\d+){1,2}$/.test(v) || !serveFile(res, path.join(MODS_DIR, v), 'astra.jar', req)) {
      throw new HttpError(404, 'No Astra build for that version on this server.');
    }
  });

  // The macOS and Linux launchers, when they're placed in server/downloads.
  router.get('/download/:file', (req, res) => {
    const name = req.params.file;
    const allowed = Object.entries(LOCAL_DOWNLOADS).filter(([k]) => k !== 'windows').flatMap(([, n]) => n);
    if (!allowed.includes(name)) throw new HttpError(404, 'No such download.');
    res.setHeader('Content-Disposition', `attachment; filename="${name}"`);
    if (!serveFile(res, DOWNLOADS_DIR, name, req)) throw new HttpError(404, 'That download isn\'t on this server.');
  });
  router.get('/favicon.ico', page('assets/img/favicon.svg'));
  router.get('/healthz', (req, res) => sendJson(res, 200, { ok: true, server: 'astra', id: config.instanceId }));

  if (!fs.existsSync(config.publicDir)) throw new Error(`Website folder missing: ${config.publicDir}`);
}

/** The signed-in player's own view: public profile + permissions + everything they own. */
function me(ctx, player) {
  return {
    ...ctx.players.publicView(player, ctx.cosmetics),
    permissions: permissionsOf(player),
    equippedIds: { ...player.equipped },
    settings: {
      bio: (player.settings && player.settings.bio) || '',
      hideServer: Boolean(player.settings && player.settings.hideServer),
      friendRequests: player.friendRequests === 'off' ? 'off' : 'on'
    },
    owned: ctx.cosmetics.ownedBy(player).map((c) => ctx.cosmetics.publicView(c))
  };
}

module.exports = { publicRoutes, me };
