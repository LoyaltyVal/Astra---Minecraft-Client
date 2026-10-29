'use strict';

const { sendJson, HttpError, readJson } = require('../http/Router');
const { requirePlayer } = require('./guards');
const { normalizeUuid } = require('../services/Players');
const { SocialService } = require('../services/Social');

/** Friends, requests, private messages and emotes (game, app and website all use these). */
function socialRoutes(router, ctx) {
  const { players, cosmetics, social } = ctx;

  const view = (p) => ({ ...players.publicView(p, cosmetics) });
  const find = (nameOrUuid) => {
    const p = players.byName(nameOrUuid) || players.get(normalizeUuid(nameOrUuid));
    if (!p || !p.joinedAt) throw new HttpError(404, `${nameOrUuid} isn't on Astra yet. They need to launch Astra once.`);
    if (p.banned) throw new HttpError(404, `${nameOrUuid} can't be added.`);
    return p;
  };
  const wrap = (fn) => {
    try {
      return fn();
    } catch (e) {
      if (e instanceof HttpError) throw e;
      throw new HttpError(400, e.message);
    }
  };

  router.get('/api/friends', requirePlayer(ctx), (req, res) => {
    const me = SocialService.ensure(req.player);
    players.touch(me);
    const list = me.friends.map((u) => players.get(u)).filter(Boolean)
      .sort((a, b) => Number(Boolean(players.playing(b))) - Number(Boolean(players.playing(a))) || a.name.localeCompare(b.name));
    sendJson(res, 200, {
      friends: list.map(view),
      incoming: me.requests.map((u) => players.get(u)).filter(Boolean).map(view),
      outgoing: social.outgoing(me).map(view),
      lastMessageId: ctx.store.data.nextMessageId - 1,
      online: players.onlineNow().length // everyone with Astra open, for "N on Astra" headers
    });
  });

  router.post('/api/friends/request', requirePlayer(ctx), async (req, res) => {
    const { name } = await readJson(req);
    const other = find(String(name || '').trim());
    const result = wrap(() => social.request(req.player, other));
    ctx.live.friendsChanged(req.player.uuid, other.uuid);
    sendJson(res, 200, { result, player: view(other) });
  });

  router.post('/api/friends/accept', requirePlayer(ctx), async (req, res) => {
    const { uuid } = await readJson(req);
    const other = find(String(uuid || ''));
    wrap(() => social.accept(req.player, other));
    ctx.live.friendsChanged(req.player.uuid, other.uuid);
    sendJson(res, 200, { ok: true, player: view(other) });
  });

  router.post('/api/friends/remove', requirePlayer(ctx), async (req, res) => {
    const { uuid } = await readJson(req);
    const other = players.get(normalizeUuid(String(uuid || '')));
    if (!other) throw new HttpError(404, 'No such player.');
    social.remove(req.player, other);
    ctx.live.friendsChanged(req.player.uuid, other.uuid);
    sendJson(res, 200, { ok: true });
  });

  router.get('/api/friends/messages', requirePlayer(ctx), (req, res) => {
    const since = Number(req.query.get('since')) || 0;
    const withUuid = normalizeUuid(req.query.get('with') || '') || null;
    const names = {};
    const list = social.messagesFor(req.player, since, withUuid).map((m) => {
      for (const u of [m.from, m.to]) {
        if (!names[u]) names[u] = (players.get(u) || { name: '?' }).name;
      }
      return { ...m, fromName: names[m.from], toName: names[m.to] };
    });
    sendJson(res, 200, { messages: list });
  });

  router.post('/api/friends/messages', requirePlayer(ctx), async (req, res) => {
    const { to, text } = await readJson(req);
    const other = find(String(to || ''));
    const msg = wrap(() => social.send(req.player, other, text));
    const full = { ...msg, fromName: req.player.name, toName: other.name };
    ctx.live.message(full);
    sendJson(res, 200, { message: full });
  });

  router.post('/api/me/emote', requirePlayer(ctx), async (req, res) => {
    const { emote } = await readJson(req);
    wrap(() => social.emote(req.player, String(emote || '')));
    ctx.live.emote(req.player, String(emote));
    sendJson(res, 200, { ok: true });
  });
}

module.exports = { socialRoutes };
