import { api } from '../api.js';
import { SiteChrome, Toast, cosmeticTile, el } from '../ui.js';

/**
 * The cosmetics catalog. Anyone can browse; a signed-in player sees what they own and can equip
 * it from here (the change shows in game for everyone within a few seconds).
 */
class CosmeticsPage {
  constructor() {
    this.filter = 'all';
    this.items = [];
    this.me = null;
  }

  async init() {
    const chrome = await new SiteChrome('cosmetics').mount();
    this.me = chrome.me;
    this.grid = document.querySelector('[data-grid]');
    this.note = document.querySelector('[data-note]');
    document.querySelectorAll('[data-filter]').forEach((b) => b.addEventListener('click', () => {
      this.filter = b.dataset.filter;
      document.querySelectorAll('[data-filter]').forEach((x) => x.setAttribute('aria-selected', String(x === b)));
      this.render();
    }));
    this.note.textContent = this.me
      ? `Signed in as ${this.me.name}. Click Equip on anything you own.`
      : 'To equip from the website, open the Astra app and click Open on website. You can also equip in the app or in game.';
    try {
      this.items = (await api.cosmetics()).cosmetics;
    } catch (e) {
      this.grid.replaceChildren(el('div', { class: 'empty' }, e.message));
      return;
    }
    this.render();
  }

  owns(c) {
    return this.me && (c.free || this.me.owned.some((o) => o.id === c.id));
  }

  equipped(c) {
    return this.me && this.me.equippedIds[c.type] === c.id;
  }

  render() {
    const list = this.items.filter((c) => this.filter === 'all' || c.type === this.filter);
    if (!list.length) {
      this.grid.replaceChildren(el('div', { class: 'empty', style: { gridColumn: '1 / -1' } }, 'Nothing here yet.'));
      return;
    }
    this.grid.replaceChildren(...list.map((c) => cosmeticTile(c, this.action(c))));
  }

  action(c) {
    if (!this.me) return null;
    if (!this.owns(c)) return el('small', { class: 'muted' }, 'Not owned');
    const on = this.equipped(c);
    return el('button', {
      class: `btn btn-sm ${on ? 'btn-primary' : 'btn-quiet'}`,
      onclick: (e) => this.toggle(c, e.currentTarget)
    }, on ? 'Equipped' : 'Equip');
  }

  async toggle(c, button) {
    button.disabled = true;
    try {
      const id = this.equipped(c) ? null : c.id;
      const { player } = await api.post('/api/me/equip', { type: c.type, id });
      this.me = player;
      Toast.show(id ? `${c.name} equipped. Everyone on Astra will see it.` : `${c.name} taken off.`);
      this.render();
    } catch (e) {
      Toast.show(e.message, { error: true });
      button.disabled = false;
    }
  }
}

new CosmeticsPage().init();
