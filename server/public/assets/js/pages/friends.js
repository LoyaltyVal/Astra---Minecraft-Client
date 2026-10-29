import { api } from '../api.js';
import { SiteChrome, Toast, avatar, el, timeAgo } from '../ui.js';

const status = (f) => {
  if (f.playing) {
    if (!f.playing.server) return `In game · ${f.playing.version}`;
    return f.playing.server === 'singleplayer' ? 'Playing singleplayer' : `On ${f.playing.server}`;
  }
  return f.inClient ? 'In the menus' : f.online ? 'Online' : 'Offline';
};

/** Friends list, requests and a message thread, kept fresh by polling. */
class FriendsPage {
  async init() {
    this.chrome = await new SiteChrome('friends').mount();
    this.root = document.querySelector('[data-friends]');
    if (!this.chrome.me) {
      this.root.replaceChildren(el('div', { class: 'empty' },
        el('b', {}, 'Sign in to see your friends.'), el('br'),
        'Open the Astra app, go to Friends and click "Messages on website". It signs you in here.'));
      return;
    }
    this.me = this.chrome.me;
    this.selected = new URLSearchParams(location.search).get('with');
    this.messages = new Map();
    this.lastId = 0;
    this.build();
    await this.load();
    await this.loadMessages();
    this.connectLive();
    // Backup polling: slow while the live connection is up, quicker if it isn't.
    setInterval(() => { if (!this.live) this.load(); }, 15000);
    setInterval(() => { if (!this.live) this.loadMessages(); }, 3000);
    setInterval(() => { if (this.live) { this.load(); this.loadMessages(); } }, 60000);
  }

  /** WebSocket to /api/live: messages and friend changes arrive instantly. Reconnects by itself. */
  connectLive(delay = 1000) {
    let ws;
    try {
      ws = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/api/live`, ['astra']);
    } catch {
      return;
    }
    ws.onopen = () => {
      this.live = true;
      this.setLive(true);
      delay = 1000;
      this.load();
      this.loadMessages();
    };
    ws.onmessage = (e) => {
      let event;
      try { event = JSON.parse(e.data); } catch { return; }
      if (event.type === 'message') this.loadMessages();
      else if (event.type === 'friends') this.load();
    };
    ws.onclose = () => {
      this.live = false;
      this.setLive(false);
      setTimeout(() => this.connectLive(Math.min(delay * 2, 30000)), delay);
    };
  }

  setLive(on) {
    if (!this.liveDot) return;
    this.liveDot.className = `fr-live${on ? ' on' : ''}`;
    this.liveDot.title = on ? 'Live: messages arrive instantly' : 'Reconnecting…';
  }

  build() {
    this.side = el('aside', { class: 'fr-side' });
    this.addForm = el('form', { class: 'fr-add' },
      el('input', { name: 'name', placeholder: 'Add a friend by name', maxlength: '16', autocomplete: 'off', 'aria-label': 'Minecraft name' }),
      el('button', { class: 'btn btn-primary btn-sm', type: 'submit' }, 'Add'));
    this.addForm.addEventListener('submit', (e) => this.add(e));
    this.list = el('div', { class: 'fr-list' });
    this.liveDot = el('span', { class: 'fr-live', title: 'Connecting…' });
    this.requests = el('div', { class: 'fr-requests' });
    this.side.append(this.addForm, this.requests, this.list);

    this.chat = el('section', { class: 'fr-chat' });
    this.root.replaceChildren(el('div', { class: 'fr' }, this.side, this.chat));
  }

  async load() {
    try {
      this.data = await api.get('/api/friends');
    } catch (e) {
      this.list.replaceChildren(el('div', { class: 'empty' }, e.message));
      return;
    }
    this.renderSide();
    this.renderChat();
  }

  async loadMessages() {
    try {
      const { messages } = await api.get(`/api/friends/messages?since=${this.lastId}`);
      let changed = false;
      for (const m of messages) {
        if (m.id <= this.lastId) continue;
        this.lastId = m.id;
        const other = m.from === this.me.uuid ? m.to : m.from;
        if (!this.messages.has(other)) this.messages.set(other, []);
        this.messages.get(other).push(m);
        changed = true;
      }
      if (changed) this.renderChat(true);
    } catch {
      // try again on the next poll
    }
  }

  async action(path, body, done) {
    try {
      await api.post(path, body);
      if (done) Toast.show(done);
      await this.load();
    } catch (e) {
      Toast.show(e.message, { error: true });
    }
  }

  add(e) {
    e.preventDefault();
    const input = this.addForm.elements.name;
    const name = input.value.trim();
    if (!name) return;
    input.value = '';
    this.action('/api/friends/request', { name }, `Friend request sent to ${name}.`);
  }

  renderSide() {
    const { friends, incoming, outgoing } = this.data;
    this.requests.replaceChildren();
    if (incoming.length || outgoing.length) {
      this.requests.append(el('p', { class: 'fr-label' }, 'Requests'));
      for (const f of incoming) {
        this.requests.append(el('div', { class: 'fr-req' },
          el('img', { src: avatar(f.uuid, 48), alt: '' }),
          el('span', {}, el('b', {}, f.name), el('small', {}, 'wants to be friends')),
          el('button', { class: 'btn btn-primary btn-sm', onclick: () => this.action('/api/friends/accept', { uuid: f.uuid }, `You and ${f.name} are now friends.`) }, 'Accept'),
          el('button', { class: 'btn btn-quiet btn-sm', onclick: () => this.action('/api/friends/remove', { uuid: f.uuid }) }, 'Decline')));
      }
      for (const f of outgoing) {
        this.requests.append(el('div', { class: 'fr-req' },
          el('img', { src: avatar(f.uuid, 48), alt: '' }),
          el('span', {}, el('b', {}, f.name), el('small', {}, 'request sent')),
          el('button', { class: 'btn btn-quiet btn-sm', onclick: () => this.action('/api/friends/remove', { uuid: f.uuid }) }, 'Cancel')));
      }
    }
    const playing = friends.filter((f) => f.playing).length;
    this.list.replaceChildren(el('p', { class: 'fr-label' }, `Friends · ${playing} in game`, this.liveDot));
    if (!friends.length) {
      this.list.append(el('p', { class: 'fr-none' }, 'No friends yet. Add someone by their Minecraft name. They need to have launched Astra once.'));
    }
    for (const f of friends) {
      const b = el('button', { class: `fr-item${f.uuid === this.selected ? ' on' : ''}${f.playing ? ' playing' : ''}`, type: 'button' },
        el('span', { class: 'fr-head' }, el('img', { src: avatar(f.uuid, 64), alt: '' }), el('i', { class: `dot${f.playing ? ' on' : ''}` })),
        el('span', { class: 'fr-who' }, el('b', { style: { color: f.rank.color } }, f.name), el('small', {}, status(f))));
      b.addEventListener('click', () => {
        this.selected = f.uuid;
        history.replaceState(null, '', `/friends?with=${f.uuid}`);
        this.renderSide();
        this.renderChat(true);
      });
      this.list.append(b);
    }
  }

  renderChat(scroll = false) {
    const f = this.data && this.data.friends.find((x) => x.uuid === this.selected);
    if (!f) {
      this.chat.replaceChildren(el('div', { class: 'fr-pick' }, el('b', {}, 'Pick a friend'), el('span', {}, 'See what server they’re on and send them a message.')));
      return;
    }
    const thread = el('div', { class: 'fr-thread' });
    const list = this.messages.get(f.uuid) || [];
    if (!list.length) thread.append(el('p', { class: 'fr-none' }, `Say hi to ${f.name}.`));
    for (const m of list) {
      const mine = m.from === this.me.uuid;
      thread.append(el('div', { class: `fr-msg${mine ? ' mine' : ''}`, title: timeAgo(m.at) }, m.text));
    }
    const input = el('input', { maxlength: '256', placeholder: `Message ${f.name}`, autocomplete: 'off', 'aria-label': 'Message' });
    const form = el('form', { class: 'fr-send' }, input, el('button', { class: 'btn btn-primary btn-sm', type: 'submit' }, 'Send'));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const text = input.value.trim();
      if (!text) return;
      input.value = '';
      try {
        await api.post('/api/friends/messages', { to: f.uuid, text });
        await this.loadMessages();
      } catch (err) {
        Toast.show(err.message, { error: true });
      }
      this.chat.querySelector('.fr-send input')?.focus();
    });
    const oldThread = this.chat.querySelector('.fr-thread');
    const atBottom = !oldThread || oldThread.scrollHeight - oldThread.scrollTop - oldThread.clientHeight < 40;
    const oldTop = oldThread ? oldThread.scrollTop : 0;
    const old = this.chat.querySelector('.fr-send input');
    const draft = old && old.value;
    const hadFocus = old && document.activeElement === old;
    this.chat.replaceChildren(
      el('header', { class: 'fr-top' },
        el('img', { src: avatar(f.uuid, 64), alt: '' }),
        el('span', {}, el('a', { href: `/player/${encodeURIComponent(f.name)}`, style: { color: f.rank.color } }, f.name), el('small', {}, status(f))),
        el('button', { class: 'btn btn-danger btn-sm', onclick: () => {
          if (confirm(`Remove ${f.name} from your friends?`)) {
            this.selected = null;
            this.action('/api/friends/remove', { uuid: f.uuid }, `Removed ${f.name}.`);
          }
        } }, 'Remove')),
      thread, form);
    if (draft) input.value = draft;
    if (hadFocus) input.focus();
    thread.scrollTop = scroll || atBottom ? thread.scrollHeight : oldTop;
  }
}

new FriendsPage().init();
