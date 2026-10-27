import { api } from '../api.js';
import { SiteChrome, avatar, el, rankBadge } from '../ui.js';

/** The team page: staff grouped by rank, highest first. */
class TeamPage {
  async init() {
    await new SiteChrome('team').mount();
    const root = document.querySelector('[data-team]');
    let team;
    let ranks;
    try {
      [{ team }, { ranks }] = await Promise.all([api.team(), api.meta()]);
    } catch (e) {
      root.replaceChildren(el('div', { class: 'empty' }, e.message));
      return;
    }
    if (!team.length) {
      root.replaceChildren(el('div', { class: 'empty' }, 'No team members yet. The server owner can add them from the admin panel.'));
      return;
    }
    const groups = ranks.filter((r) => r.id !== 'player').sort((a, b) => b.level - a.level)
      .map((rank) => ({ rank, people: team.filter((p) => p.rank.id === rank.id) }))
      .filter((g) => g.people.length);
    root.replaceChildren(...groups.map(({ rank, people }) => el('section', { class: 'team-group' },
      el('h2', {}, el('span', { class: 'swatch', style: { background: rank.color } }), `${rank.name}${people.length > 1 ? 's' : ''}`),
      el('div', { class: 'people' }, people.map((p) => TeamPage.person(p))))));
  }

  static person(p) {
    return el('a', { class: 'person', href: `/player/${encodeURIComponent(p.name)}` },
      el('img', { src: avatar(p.uuid, 96), alt: '' }),
      el('span', { class: 'who' },
        el('b', {}, p.name, el('span', { class: `dot${p.online ? ' on' : ''}`, title: p.online ? 'Online' : 'Offline' })),
        rankBadge(p.rank)));
  }
}

new TeamPage().init();
