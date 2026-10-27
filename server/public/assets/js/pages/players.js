import { api } from '../api.js';
import { SiteChrome, avatar, el, rankBadge } from '../ui.js';

/** Who's in game and where: a search box, the busiest servers, and everyone online. */
class PlayersPage {
  async init() {
    await new SiteChrome('players').mount();
    this.list = document.querySelector('[data-online]');
    this.servers = document.querySelector('[data-servers]');
    this.count = document.querySelector('[data-count]');
    this.filter = '';
    const form = document.querySelector('[data-find]');
    const input = form.querySelector('input');
    input.addEventListener('input', () => {
      this.filter = input.value.trim().toLowerCase();
      this.render();
    });
    // Enter opens the profile, which works for players who are offline too.
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      if (input.value.trim()) location.href = `/player/${encodeURIComponent(input.value.trim())}`;
    });
    await this.load();
    setInterval(() => this.load(), 30000);
  }

  async load() {
    try {
      this.players = (await api.online()).players;
    } catch (e) {
      this.list.replaceChildren(el('div', { class: 'empty', style: { gridColumn: '1 / -1' } }, e.message));
      return;
    }
    this.render();
  }

  render() {
    const all = this.players || [];
    this.count.textContent = `· ${all.length}`;

    // The busiest servers, as chips that filter the list.
    const counts = new Map();
    for (const p of all) {
      const s = p.playing.server;
      if (s) counts.set(s, (counts.get(s) || 0) + 1);
    }
    const top = [...counts].sort((a, b) => b[1] - a[1]).slice(0, 8);
    this.servers.replaceChildren(...top.map(([server, n]) => el('button', {
      class: `chip${this.filter === server ? ' on' : ''}`,
      onclick: () => {
        const input = document.querySelector('[data-find] input');
        input.value = this.filter === server ? '' : server;
        this.filter = input.value.toLowerCase();
        this.render();
      }
    }, PlayersPage.serverLabel(server), el('b', {}, String(n)))));

    const shown = all.filter((p) => !this.filter || p.name.toLowerCase().includes(this.filter)
      || (p.playing.server || '').includes(this.filter));
    if (!shown.length) {
      this.list.replaceChildren(el('div', { class: 'empty', style: { gridColumn: '1 / -1' } },
        all.length ? 'Nobody in game matches that. Press Enter to open their profile anyway.' : 'Nobody is in game right now.'));
      return;
    }
    this.list.replaceChildren(...shown.map((p) => el('a', { class: 'person', href: `/player/${encodeURIComponent(p.name)}` },
      el('img', { src: avatar(p.uuid, 96), alt: '' }),
      el('span', { class: 'who' },
        el('b', {}, p.name, p.rank.id !== 'player' ? rankBadge(p.rank) : null),
        el('small', { class: 'where' }, PlayersPage.where(p.playing))))));
  }

  static serverLabel(server) {
    return server === 'singleplayer' ? 'Singleplayer' : server;
  }

  static where(playing) {
    if (!playing) return 'Offline';
    if (!playing.server) return `In game · ${playing.version}`;
    return `${playing.server === 'singleplayer' ? 'Singleplayer' : `On ${playing.server}`} · ${playing.version}`;
  }
}

export { PlayersPage };
new PlayersPage().init();
