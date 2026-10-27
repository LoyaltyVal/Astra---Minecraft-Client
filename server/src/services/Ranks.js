'use strict';

/**
 * Ranks, highest first. `level` decides who can manage whom; `color` is used on the website and
 * `mc` is the Minecraft colour code for in-game rank tags. Add or rename ranks here.
 */
const RANKS = {
  owner: { id: 'owner', name: 'Owner', level: 100, color: '#ff5c7a', mc: 'c', staff: true },
  admin: { id: 'admin', name: 'Admin', level: 80, color: '#ff8a4c', mc: '6', staff: true },
  developer: { id: 'developer', name: 'Developer', level: 70, color: '#9b7bff', mc: 'd', staff: true },
  moderator: { id: 'moderator', name: 'Moderator', level: 50, color: '#4fc3f7', mc: 'b', staff: true },
  media: { id: 'media', name: 'Media', level: 20, color: '#3ddc97', mc: 'a', staff: false },
  player: { id: 'player', name: 'Player', level: 0, color: '#9c99ba', mc: '7', staff: false }
};

/** What each permission needs, as a minimum rank level. */
const PERMISSIONS = {
  'admin.view': 50,        // open the admin panel, see players and the activity log
  'players.ban': 50,       // ban and unban
  'cosmetics.grant': 70,   // give and take cosmetics
  'cosmetics.manage': 80,  // upload, edit and delete cosmetics
  'ranks.set': 80          // change ranks (only below your own, unless you're an Owner)
};

function rankOf(player) {
  return RANKS[player && player.rank] || RANKS.player;
}

function can(player, permission) {
  if (!player || player.banned) return false;
  const need = PERMISSIONS[permission];
  return need !== undefined && rankOf(player).level >= need;
}

/** Whether `actor` may move `target` to `newRank`. Owners can do anything except demote the last Owner. */
function canSetRank(actor, target, newRank, ownerCount) {
  if (!RANKS[newRank] || !can(actor, 'ranks.set')) return false;
  const a = rankOf(actor).level;
  const isOwner = actor.rank === 'owner';
  if (target.uuid === actor.uuid && !isOwner) return false;
  if (target.rank === 'owner' && newRank !== 'owner' && ownerCount <= 1) return false;
  if (isOwner) return true;
  return rankOf(target).level < a && RANKS[newRank].level < a;
}

function permissionsOf(player) {
  return Object.keys(PERMISSIONS).filter((p) => can(player, p));
}

module.exports = { RANKS, PERMISSIONS, rankOf, can, canSetRank, permissionsOf };
