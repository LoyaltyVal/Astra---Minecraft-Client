'use strict';

const { HttpError } = require('../http/Router');
const { can } = require('../services/Ranks');

/** Requires a signed-in player; sets req.player. */
function requirePlayer(ctx) {
  return (req) => {
    const player = ctx.auth.playerFor(req);
    if (!player) throw new HttpError(401, 'Sign in through the Astra Launcher first.');
    req.player = player;
    checkCsrf(req);
  };
}

/** Requires a permission (see Ranks.PERMISSIONS). */
function requirePermission(ctx, permission) {
  const auth = requirePlayer(ctx);
  return (req) => {
    auth(req);
    if (!can(req.player, permission)) throw new HttpError(403, 'Your rank can\'t do that.');
  };
}

/**
 * Website requests are authenticated by cookie, so state-changing ones must come from this site.
 * (The launcher and game use Bearer tokens, which browsers never attach on their own.)
 */
function checkCsrf(req) {
  if (!req.viaCookie || req.method === 'GET' || req.method === 'HEAD') return;
  const origin = req.headers.origin;
  let ok = false;
  try {
    ok = Boolean(origin) && new URL(origin).host === req.headers.host;
  } catch {
    ok = false;
  }
  if (!ok) throw new HttpError(403, 'Request blocked: it didn\'t come from the Astra website.');
}

module.exports = { requirePlayer, requirePermission };
