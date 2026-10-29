import { api } from '../api.js';
import { SiteChrome, avatar, el, rankBadge } from '../ui.js';

/**
 * Who's online: how many players have Astra open, and which of your friends are on and where.
 * (Only friends are listed: who else is online, and where, isn't shared.)
 */
class PlayersPage {
  async init() {
    await new SiteChrome('players').mount();
    this.list = document.querySelector('[data-online]');
    this.count = document.querySelector('[data-count]');
    this.total = document.querySelector('[data-total]');
    this.totalLabel = document.querySelector('[data-total-label]');
    this.filter = '';
    const form = document.querySelector('[data-find]');
    const input = form.querySelector('input');
    input.addEventListener('input', () => {
      this.filter = input.value.trim().toLowerCase();
      this.render();
    });
    // Enter opens the profile, which works for anyone (friends or not, online or not).
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      if (input.value.trim()) location.href = `/player/${encodeURIComponent(input.value.trim())}`;
    });
    await this.load();
    setInterval(() => this.load(), 30000);
  }

  async load() {
    try {
      const data = await api.online();
      this.players = data.players;
      this.signedIn = data.signedIn;
      this.total.textContent = Number(data.online).toLocaleString();
      this.totalLabel.textContent = data.online === 1 ? 'player online on Astra' : 'players online on Astra';
    } catch (e) {
      this.empty(e.message);
      return;
    }
    this.render();
  }

  empty(...content) {
    this.list.replaceChildren(el('div', { class: 'empty', style: { gridColumn: '1 / -1' } }, ...content));
  }

  render() {
    if (!this.signedIn) {
      this.count.textContent = '';
      this.empty('Sign in to see which of your friends are online. ', el('a', { href: '/account' }, 'Sign in'));
      return;
    }
    const all = this.players || [];
    this.count.textContent = `· ${all.length}`;
    if (!all.length) {
      this.empty('None of your friends are online right now. ', el('a', { href: '/friends' }, 'Add friends'));
      return;
    }
    const shown = all.filter((p) => !this.filter || p.name.toLowerCase().includes(this.filter)
      || ((p.playing && p.playing.server) || '').includes(this.filter));
    if (!shown.length) {
      this.empty('None of your online friends match that. Press Enter to open their profile anyway.');
      return;
    }
    this.list.replaceChildren(...shown.map((p) => el('a', { class: 'person', href: `/player/${encodeURIComponent(p.name)}` },
      el('img', { src: avatar(p.uuid, 96), alt: '' }),
      el('span', { class: 'who' },
        el('b', {}, p.name, p.rank.id !== 'player' ? rankBadge(p.rank) : null),
        el('small', { class: 'where' }, PlayersPage.where(p))))));
  }

  static where(p) {
    const playing = p.playing;
    if (!playing) return p.inClient ? 'In the menus' : 'Online';
    if (!playing.server) return `In game · ${playing.version}`;
    return `${playing.server === 'singleplayer' ? 'Singleplayer' : `On ${playing.server}`} · ${playing.version}`;
  }
}

export { PlayersPage };
new PlayersPage().init();
