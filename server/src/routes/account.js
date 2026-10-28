'use strict';

const crypto = require('crypto');
const { sendJson, HttpError, readJson, redirect } = require('../http/Router');
const { requirePlayer } = require('./guards');
const { me } = require('./public');

const STATE_MS = 10 * 60 * 1000;

/** Profile settings (/account) and linking a Discord account with Discord's OAuth2. */
function accountRoutes(router, ctx) {
  const { config, store } = ctx;
  const states = new Map(); // OAuth state -> { uuid, expires }

  const discordReady = () => /^\d{15,25}$/.test(String(config.discordClientId || '')) && Boolean(config.discordClientSecret);
  const callbackUrl = () => `${String(config.publicUrl).replace(/\/+$/, '')}/api/discord/callback`;

  router.get('/api/me/settings', requirePlayer(ctx), (req, res) => {
    sendJson(res, 200, { player: me(ctx, req.player), discordAvailable: discordReady() });
  });

  router.post('/api/me/settings', requirePlayer(ctx), async (req, res) => {
    const body = (await readJson(req)) || {};
    const p = req.player;
    p.settings = p.settings || {};
    if (body.bio !== undefined) {
      p.settings.bio = String(body.bio).replace(/[\u0000-\u001f§<>]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 160);
    }
    if (body.hideServer !== undefined) p.settings.hideServer = Boolean(body.hideServer);
    if (body.friendRequests !== undefined) p.friendRequests = body.friendRequests === 'off' ? 'off' : 'on';
    store.save();
    if (body.hideServer !== undefined) ctx.live.presenceChanged(p);
    sendJson(res, 200, { player: me(ctx, p) });
  });

  // ---------- Discord ----------
  router.get('/api/discord/link', requirePlayer(ctx), (req, res) => {
    if (!discordReady()) throw new HttpError(503, 'Discord linking isn\'t set up on this server yet.');
    const now = Date.now();
    for (const [k, v] of states) if (v.expires < now) states.delete(k);
    const state = crypto.randomBytes(18).toString('hex');
    states.set(state, { uuid: req.player.uuid, expires: now + STATE_MS });
    const url = new URL('https://discord.com/oauth2/authorize');
    url.searchParams.set('client_id', String(config.discordClientId));
    url.searchParams.set('redirect_uri', callbackUrl());
    url.searchParams.set('response_type', 'code');
    url.searchParams.set('scope', 'identify');
    url.searchParams.set('state', state);
    url.searchParams.set('prompt', 'none');
    redirect(res, url.toString());
  });

  router.get('/api/discord/callback', requirePlayer(ctx), async (req, res) => {
    const state = String(req.query.get('state') || '');
    const saved = states.get(state);
    states.delete(state);
    if (!saved || saved.expires < Date.now() || saved.uuid !== req.player.uuid) {
      return redirect(res, '/account?discord=expired');
    }
    if (req.query.get('error')) return redirect(res, '/account?discord=cancelled');
    try {
      const tokenRes = await ctx.fetch('https://discord.com/api/oauth2/token', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          client_id: String(config.discordClientId),
          client_secret: String(config.discordClientSecret),
          grant_type: 'authorization_code',
          code: String(req.query.get('code') || ''),
          redirect_uri: callbackUrl()
        }).toString()
      });
      if (!tokenRes.ok) throw new Error(`token ${tokenRes.status}`);
      const { access_token: access } = await tokenRes.json();
      const userRes = await ctx.fetch('https://discord.com/api/users/@me', { headers: { Authorization: `Bearer ${access}` } });
      if (!userRes.ok) throw new Error(`user ${userRes.status}`);
      const user = await userRes.json();
      req.player.discord = {
        id: String(user.id),
        username: String(user.global_name || user.username || 'Discord user').slice(0, 40),
        linkedAt: new Date().toISOString()
      };
      store.save();
      ctx.audit.record(req.player, 'linked Discord', null, req.player.discord.username);
      redirect(res, '/account?discord=linked');
    } catch (e) {
      console.warn('[astra] Discord link failed:', e.message);
      redirect(res, '/account?discord=failed');
    }
  });

  router.post('/api/me/discord/unlink', requirePlayer(ctx), (req, res) => {
    delete req.player.discord;
    store.save();
    sendJson(res, 200, { player: me(ctx, req.player) });
  });
}

module.exports = { accountRoutes };
