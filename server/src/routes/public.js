'use strict';

const fs = require('fs');
const { sendJson, HttpError, readJson, redirect } = require('../http/Router');
const { serveFile } = require('../http/static');
const { RANKS, permissionsOf } = require('../services/Ranks');
const { RARITIES } = require('../services/Cosmetics');
const { normalizeUuid } = require('../services/Players');
const { requirePlayer } = require('./guards');

/** Public API (website, game cosmetics lookup) plus sign-in and the player's own endpoints. */
function publicRoutes(router, ctx) {
  const { players, cosmetics, auth, config } = ctx;

  // ---------- site data ----------
  router.get('/api/meta', (req, res) => sendJson(res, 200, {
    ranks: Object.values(RANKS),
    rarities: RARITIES,
    publicUrl: config.publicUrl,
    downloadUrl: config.downloadUrl || ''
  }));

  router.get('/api/stats', (req, res) => sendJson(res, 200, {
    players: players.count(),
    online: players.onlineCount(),
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
      out[id] = { name: r.name, rank: r.rank, cape: p.equipped.cape, wings: p.equipped.wings };
    }
    sendJson(res, 200, { players: out });
  });

  // ---------- sign-in ----------
  router.post('/api/auth/challenge', (req, res) => sendJson(res, 200, auth.challenge()));

  router.post('/api/auth/verify', async (req, res) => {
    const body = await readJson(req);
    const { token, player } = await auth.verify(body, req.socket.remoteAddress);
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
    redirect(res, staff ? '/admin' : `/player/${encodeURIComponent(result.player.name)}`, { 'Set-Cookie': result.cookie });
  });

  router.get('/logout', (req, res) => redirect(res, '/', { 'Set-Cookie': auth.logout(req) }));

  // ---------- the signed-in player ----------
  router.get('/api/me', requirePlayer(ctx), (req, res) => {
    players.touch(req.player);
    sendJson(res, 200, { player: me(ctx, req.player) });
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
  router.get('/admin', page('admin.html'));
  router.get('/player/:name', page('player.html'));
  router.get('/favicon.ico', page('assets/img/favicon.svg'));
  router.get('/healthz', (req, res) => sendJson(res, 200, { ok: true }));

  if (!fs.existsSync(config.publicDir)) throw new Error(`Website folder missing: ${config.publicDir}`);
}

/** The signed-in player's own view: public profile + permissions + everything they own. */
function me(ctx, player) {
  return {
    ...ctx.players.publicView(player, ctx.cosmetics),
    permissions: permissionsOf(player),
    equippedIds: { ...player.equipped },
    owned: ctx.cosmetics.ownedBy(player).map((c) => ctx.cosmetics.publicView(c))
  };
}

module.exports = { publicRoutes, me };
