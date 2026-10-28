import { api } from '../api.js';
import { SiteChrome, body, el, rankBadge, timeAgo } from '../ui.js';

/** A player's public profile: skin, rank, online status and what they're wearing. */
class PlayerPage {
  async init() {
    await new SiteChrome(null).mount();
    const root = document.querySelector('[data-profile]');
    const name = decodeURIComponent(location.pathname.split('/').filter(Boolean)[1] || '');
    let player;
    try {
      ({ player } = await api.profile(name));
    } catch (e) {
      document.title = 'Player not found · Astra';
      root.replaceChildren(el('div', { class: 'empty' },
        el('h1', { style: { fontSize: '28px', marginBottom: '8px', color: 'var(--star)' } }, 'Player not found'),
        `${name || 'That player'} hasn't signed in to Astra yet.`));
      return;
    }
    document.title = `${player.name} · Astra`;
    const all = (await api.cosmetics().catch(() => ({ cosmetics: [] }))).cosmetics;
    const worn = ['cape', 'wings'].map((type) => {
      const eq = player.equipped[type];
      const full = eq && all.find((c) => c.id === eq.id);
      return el('div', { class: 'worn' },
        el('span', { class: 'kicker' }, type === 'cape' ? 'Cape' : 'Wings'),
        full
          ? el('div', { class: 'worn-item' }, el('img', { src: full.image, alt: '' }),
            el('div', {}, el('b', {}, full.name), el('span', { class: `rarity rarity-${full.rarity}` }, full.rarity)))
          : el('p', { class: 'muted' }, 'None'));
    });
    root.replaceChildren(el('div', { class: 'profile' },
      el('div', { class: 'skin' }, el('img', { src: body(player.uuid, 260), alt: `${player.name}'s skin` })),
      el('div', { class: 'profile-info' },
        rankBadge(player.rank),
        el('h1', {}, player.name),
        el('p', { class: 'muted' },
          el('span', { class: `dot${player.online ? ' on' : ''}` }), ' ',
          player.playing ? 'In game' : player.online ? 'Online' : 'Offline',
          ` · Joined ${timeAgo(player.joinedAt)}`,
          ` · ${player.ownedCount} cosmetic${player.ownedCount === 1 ? '' : 's'}`),
        player.playing
          ? el('p', { class: 'playing' }, el('span', { class: 'dot on' }),
            player.playing.server
              ? (player.playing.server === 'singleplayer' ? `Playing singleplayer · ${player.playing.version}` : `Playing on ${player.playing.server} · ${player.playing.version}`)
              : `In game · ${player.playing.version}`)
          : null,
        player.bio ? el('p', { class: 'bio' }, player.bio) : null,
        player.discord ? el('p', { class: 'muted discord-tag' }, 'Discord · ', el('b', {}, player.discord.username)) : null,
        player.banned ? el('p', { class: 'banned' }, 'This player is banned from Astra.') : null,
        el('div', { class: 'worn-row' }, worn))));
  }
}

new PlayerPage().init();
