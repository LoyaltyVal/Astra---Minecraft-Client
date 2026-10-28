'use strict';

const { sendJson, HttpError, readJson } = require('../http/Router');
const { RANKS, canSetRank, can, rankOf } = require('../services/Ranks');
const { NAME_RE } = require('../services/Players');
const { requirePermission } = require('./guards');

/** Staff endpoints used by the admin panel. Every change is written to the activity log. */
function adminRoutes(router, ctx) {
  const { players, cosmetics, audit, auth } = ctx;
  const target = (req) => {
    const p = players.get(req.params.uuid);
    if (!p) throw new HttpError(404, 'No such player.');
    return p;
  };
  const detail = (p) => ({
    ...players.publicView(p, cosmetics),
    lastSeen: p.lastSeen,
    ownedIds: p.owned,
    equippedIds: { ...p.equipped }
  });

  // ---------- players ----------
  router.get('/api/admin/players', requirePermission(ctx, 'admin.view'), (req, res) => {
    sendJson(res, 200, { players: players.search(req.query.get('q')).map(detail), total: players.count() });
  });

  router.get('/api/admin/players/:uuid', requirePermission(ctx, 'admin.view'), (req, res) => {
    sendJson(res, 200, { player: detail(target(req)) });
  });

  /** Adds a player by Minecraft name before they've signed in (looked up on Mojang). */
  router.post('/api/admin/players', requirePermission(ctx, 'cosmetics.grant'), async (req, res) => {
    const { name } = await readJson(req);
    if (!NAME_RE.test(String(name || ''))) throw new HttpError(400, 'That isn\'t a valid Minecraft name.');
    const existing = players.byName(name);
    if (existing) return sendJson(res, 200, { player: detail(existing) });
    let profile;
    try {
      const r = await ctx.fetch(`https://api.mojang.com/users/profiles/minecraft/${encodeURIComponent(name)}`);
      if (r.status !== 200) throw new HttpError(404, `Mojang has no player called ${name}.`);
      profile = await r.json();
    } catch (e) {
      if (e instanceof HttpError) throw e;
      throw new HttpError(502, 'Couldn\'t reach Mojang to look that name up.');
    }
    const p = players.addPlaceholder(profile.id, profile.name);
    audit.record(req.player, 'added player', p.name, null);
    sendJson(res, 200, { player: detail(p) });
  });

  router.post('/api/admin/players/:uuid/rank', requirePermission(ctx, 'ranks.set'), async (req, res) => {
    const p = target(req);
    const { rank } = await readJson(req);
    if (!canSetRank(req.player, p, rank, players.ownerCount())) {
      throw new HttpError(403, `You can't make ${p.name} ${RANKS[rank] ? RANKS[rank].name : rank}.`);
    }
    const before = rankOf(p).name;
    p.rank = rank;
    ctx.store.save();
    audit.record(req.player, 'changed rank', p.name, `${before} → ${RANKS[rank].name}`);
    sendJson(res, 200, { player: detail(p) });
  });

  router.post('/api/admin/players/:uuid/ban', requirePermission(ctx, 'players.ban'), async (req, res) => {
    const p = target(req);
    const { banned } = await readJson(req);
    if (p.uuid === req.player.uuid) throw new HttpError(400, 'You can\'t ban yourself.');
    if (rankOf(p).level >= rankOf(req.player).level && req.player.rank !== 'owner') {
      throw new HttpError(403, `You can't ban ${p.name}.`);
    }
    p.banned = Boolean(banned);
    if (p.banned) auth.revokeAll(p.uuid);
    ctx.store.save();
    audit.record(req.player, p.banned ? 'banned' : 'unbanned', p.name, null);
    sendJson(res, 200, { player: detail(p) });
  });

  router.post('/api/admin/players/:uuid/cosmetics', requirePermission(ctx, 'cosmetics.grant'), async (req, res) => {
    const p = target(req);
    const { id } = await readJson(req);
    const c = cosmetics.get(id);
    if (!c) throw new HttpError(404, 'No such cosmetic.');
    cosmetics.grant(p, id);
    audit.record(req.player, 'gave cosmetic', p.name, c.name);
    sendJson(res, 200, { player: detail(p) });
  });

  router.delete('/api/admin/players/:uuid/cosmetics/:id', requirePermission(ctx, 'cosmetics.grant'), (req, res) => {
    const p = target(req);
    const c = cosmetics.get(req.params.id);
    cosmetics.revoke(p, req.params.id);
    audit.record(req.player, 'took cosmetic', p.name, c ? c.name : req.params.id);
    sendJson(res, 200, { player: detail(p) });
  });

  // ---------- cosmetics ----------
  router.get('/api/admin/cosmetics', requirePermission(ctx, 'admin.view'), (req, res) => {
    sendJson(res, 200, { cosmetics: cosmetics.all({ includeHidden: true }).map((c) => cosmetics.publicView(c)) });
  });

  /** Upload: JSON with the image as a data: URL (keeps the server free of multipart parsing). */
  router.post('/api/admin/cosmetics', requirePermission(ctx, 'cosmetics.manage'), async (req, res) => {
    const body = await readJson(req, 6 * 1024 * 1024);
    const m = /^data:image\/(png|jpe?g);base64,([A-Za-z0-9+/=]+)$/.exec(String(body.image || ''));
    if (!m) throw new HttpError(400, 'Choose a PNG or JPEG image.');
    let c;
    try {
      c = cosmetics.create({ ...body, image: Buffer.from(m[2], 'base64'), createdBy: req.player.uuid });
    } catch (e) {
      throw new HttpError(400, e.message);
    }
    audit.record(req.player, 'uploaded cosmetic', null, `${c.name} (${c.type})`);
    sendJson(res, 201, { cosmetic: cosmetics.publicView(c) });
  });

  router.patch('/api/admin/cosmetics/:id', requirePermission(ctx, 'cosmetics.manage'), async (req, res) => {
    const patch = await readJson(req);
    let c;
    try {
      c = cosmetics.update(req.params.id, patch);
    } catch (e) {
      throw new HttpError(400, e.message);
    }
    audit.record(req.player, 'edited cosmetic', null, c.name);
    sendJson(res, 200, { cosmetic: cosmetics.publicView(c) });
  });

  router.delete('/api/admin/cosmetics/:id', requirePermission(ctx, 'cosmetics.manage'), (req, res) => {
    const c = cosmetics.get(req.params.id);
    if (!c) throw new HttpError(404, 'No such cosmetic.');
    cosmetics.remove(c.id);
    audit.record(req.player, 'deleted cosmetic', null, c.name);
    sendJson(res, 200, { ok: true });
  });

  // ---------- activity ----------
  router.get('/api/admin/audit', requirePermission(ctx, 'admin.view'), (req, res) => {
    sendJson(res, 200, { entries: audit.recent(150) });
  });

  router.get('/api/admin/whoami', requirePermission(ctx, 'admin.view'), (req, res) => {
    sendJson(res, 200, {
      player: players.publicView(req.player, cosmetics),
      can: Object.fromEntries(['admin.view', 'players.ban', 'uploads.review', 'cosmetics.grant', 'cosmetics.manage', 'ranks.set'].map((k) => [k, can(req.player, k)]))
    });
  });
}

module.exports = { adminRoutes };
