'use strict';

const { sendJson, HttpError, readJson } = require('../http/Router');
const { requirePlayer, requirePermission } = require('./guards');

const DATA_URL = /^data:image\/(png|jpe?g);base64,([A-Za-z0-9+/=]+)$/;

/**
 * Player uploads: anyone signed in can upload their own cape or wings. Staff (Moderator and up)
 * approve or reject them in the admin panel; approved ones belong to the uploader only.
 */
function uploadRoutes(router, ctx) {
  const { cosmetics, players, audit, config } = ctx;
  const lastUpload = new Map();

  router.get('/api/me/uploads', requirePlayer(ctx), (req, res) => {
    sendJson(res, 200, {
      enabled: config.playerUploads !== false,
      uploads: cosmetics.uploadsOf(req.player).map((c) => cosmetics.uploadView(c))
    });
  });

  router.post('/api/me/uploads', requirePlayer(ctx), async (req, res) => {
    if (config.playerUploads === false) throw new HttpError(403, 'Uploads are switched off on this server.');
    const last = lastUpload.get(req.player.uuid) || 0;
    if (Date.now() - last < 20_000) throw new HttpError(429, 'Wait a few seconds before uploading again.');
    const body = await readJson(req, 3 * 1024 * 1024);
    const m = DATA_URL.exec(String(body.image || ''));
    if (!m) throw new HttpError(400, 'Choose a PNG or JPEG image.');
    let c;
    try {
      c = cosmetics.submit(req.player, { name: body.name, type: body.type, image: Buffer.from(m[2], 'base64') });
    } catch (e) {
      throw new HttpError(400, e.message);
    }
    lastUpload.set(req.player.uuid, Date.now());
    audit.record(req.player, 'submitted a cosmetic for review', null, `${c.name} (${c.type})`);
    sendJson(res, 201, { upload: cosmetics.uploadView(c) });
  });

  /** Deletes your own upload (pending, rejected or approved). */
  router.delete('/api/me/uploads/:id', requirePlayer(ctx), (req, res) => {
    const c = cosmetics.get(req.params.id);
    if (!c || c.uploadedBy !== req.player.uuid) throw new HttpError(404, 'No such upload.');
    cosmetics.remove(c.id);
    sendJson(res, 200, { ok: true });
  });

  // ---------- review ----------
  router.get('/api/admin/uploads', requirePermission(ctx, 'uploads.review'), (req, res) => {
    sendJson(res, 200, {
      uploads: cosmetics.pendingUploads().map((c) => {
        const p = players.get(c.uploadedBy);
        return { ...cosmetics.uploadView(c), uploader: p ? { uuid: p.uuid, name: p.name } : null };
      })
    });
  });

  router.post('/api/admin/uploads/:id/approve', requirePermission(ctx, 'uploads.review'), (req, res) => {
    const c0 = cosmetics.get(req.params.id);
    const owner = c0 && players.get(c0.uploadedBy);
    let c;
    try {
      c = cosmetics.approve(req.params.id, req.player, owner);
    } catch (e) {
      throw new HttpError(400, e.message);
    }
    audit.record(req.player, 'approved an upload', owner ? owner.name : null, `${c.name} (${c.type})`);
    sendJson(res, 200, { upload: cosmetics.uploadView(c) });
  });

  router.post('/api/admin/uploads/:id/reject', requirePermission(ctx, 'uploads.review'), async (req, res) => {
    const { reason } = await readJson(req);
    const c0 = cosmetics.get(req.params.id);
    const owner = c0 && players.get(c0.uploadedBy);
    let c;
    try {
      c = cosmetics.reject(req.params.id, req.player, reason);
    } catch (e) {
      throw new HttpError(400, e.message);
    }
    audit.record(req.player, 'rejected an upload', owner ? owner.name : null, `${c.name}${c.reason ? ` (${c.reason})` : ''}`);
    sendJson(res, 200, { upload: cosmetics.uploadView(c) });
  });
}

module.exports = { uploadRoutes };
