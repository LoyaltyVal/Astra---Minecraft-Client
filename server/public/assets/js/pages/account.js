import { api } from '../api.js';
import { SiteChrome, Toast, avatar, el, rankBadge } from '../ui.js';

const DISCORD_RESULTS = {
  linked: ['Discord linked. It now shows on your profile.', false],
  cancelled: ['Discord linking was cancelled.', true],
  expired: ['That Discord link took too long. Try again.', true],
  failed: ['Discord didn’t accept the link. Try again in a moment.', true]
};

/** Profile settings: bio, privacy, friend requests and Discord. */
class AccountPage {
  async init() {
    this.chrome = await new SiteChrome('account').mount();
    this.root = document.querySelector('[data-account-page]');
    const result = DISCORD_RESULTS[new URLSearchParams(location.search).get('discord')];
    if (result) {
      Toast.show(result[0], { error: result[1] });
      history.replaceState(null, '', '/account');
    }
    if (!this.chrome.me) return this.signedOut();
    try {
      const data = await api.get('/api/me/settings');
      this.player = data.player;
      this.discordAvailable = data.discordAvailable;
      const up = await api.get('/api/me/uploads');
      this.uploads = up.uploads;
      this.uploadsEnabled = up.enabled;
    } catch (e) {
      this.root.replaceChildren(el('div', { class: 'empty' }, e.message));
      return;
    }
    this.render();
  }

  signedOut() {
    this.root.replaceChildren(el('div', { class: 'acc-signin' },
      el('h2', {}, 'Sign in with the Astra app'),
      el('ol', {},
        el('li', {}, 'Open the Astra app and sign in with your Microsoft account.'),
        el('li', {}, 'Click Cosmetics → “Open on website” (or Friends → “Messages on website”).'),
        el('li', {}, 'This site opens already signed in. Come back to Account from the footer.')),
      el('p', { class: 'muted' }, 'There are no passwords here: your Minecraft account is your Astra account.')));
  }

  async save(patch, message) {
    try {
      this.player = (await api.post('/api/me/settings', patch)).player;
      Toast.show(message || 'Saved.');
    } catch (e) {
      Toast.show(e.message, { error: true });
    }
    this.render();
  }

  async reloadUploads() {
    try {
      this.uploads = (await api.get('/api/me/uploads')).uploads;
    } catch {
      // keep what we had
    }
    this.render();
  }

  uploadsCard() {
    const card = el('div', { class: 'acc-card', id: 'uploads' }, el('h3', {}, 'Your own capes and wings'));
    if (!this.uploadsEnabled) {
      card.append(el('p', { class: 'muted' }, 'Uploads are switched off on this server.'));
      return card;
    }
    card.append(el('p', { class: 'muted' }, 'Upload a picture and staff check it first. Once approved, only you can wear it, and it goes on straight away. Wings: upload one wing (the left one); Astra mirrors it.'));
    const file = el('input', { type: 'file', accept: 'image/png,image/jpeg', 'aria-label': 'Image' });
    const name = el('input', { maxlength: '40', placeholder: 'Name, e.g. Midnight', 'aria-label': 'Name', required: true });
    const type = el('select', { 'aria-label': 'Type' }, el('option', { value: 'cape' }, 'Cape'), el('option', { value: 'wings' }, 'Wings'));
    const preview = el('div', { class: 'up-prev' }, el('span', {}, 'Preview'));
    const submit = el('button', { class: 'btn btn-primary btn-sm', type: 'submit' }, 'Send for review');
    let dataUrl = '';
    file.addEventListener('change', () => {
      const f = file.files[0];
      if (!f) return;
      if (f.size > 2 * 1024 * 1024) {
        Toast.show('Images must be 2 MB or smaller.', { error: true });
        file.value = '';
        return;
      }
      if (!name.value) name.value = f.name.replace(/\.[^.]+$/, '').replace(/[-_]+/g, ' ').slice(0, 40);
      const reader = new FileReader();
      reader.onload = () => {
        dataUrl = reader.result;
        preview.replaceChildren(el('img', { src: dataUrl, alt: 'Preview' }));
      };
      reader.readAsDataURL(f);
    });
    const form = el('form', { class: 'up-form' }, preview,
      el('div', { class: 'up-fields' }, file, el('div', { class: 'acc-row gap' }, name, type), submit));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (!dataUrl) return Toast.show('Choose an image first.', { error: true });
      submit.disabled = true;
      try {
        await api.post('/api/me/uploads', { name: name.value, type: type.value, image: dataUrl });
        Toast.show('Sent. Staff will review it soon.');
        await this.reloadUploads();
      } catch (err) {
        Toast.show(err.message, { error: true });
        submit.disabled = false;
      }
    });
    card.append(form);

    if (this.uploads.length) {
      const label = { pending: 'Waiting for review', approved: 'Approved · yours to wear', rejected: 'Rejected' };
      card.append(el('div', { class: 'up-list' }, this.uploads.map((u) => el('div', { class: `up-item ${u.status}` },
        u.image ? el('img', { src: u.image, alt: '' }) : el('span', { class: 'up-gone' }, '✕'),
        el('span', { class: 'up-text' }, el('b', {}, u.name),
          el('small', {}, `${u.type === 'cape' ? 'Cape' : 'Wings'} · ${label[u.status]}${u.reason ? `: ${u.reason}` : ''}`)),
        el('button', { class: 'btn btn-quiet btn-sm', type: 'button', onclick: async () => {
          if (!confirm(`Delete ${u.name}?`)) return;
          try {
            await api.delete(`/api/me/uploads/${u.id}`);
            Toast.show('Deleted.');
          } catch (err) {
            Toast.show(err.message, { error: true });
          }
          this.reloadUploads();
        } }, 'Delete')))));
    }
    return card;
  }

  toggle(label, help, on, fn) {
    const input = el('input', { type: 'checkbox', role: 'switch' });
    input.checked = on;
    input.addEventListener('change', () => fn(input.checked));
    return el('label', { class: 'acc-toggle' },
      el('span', {}, el('b', {}, label), el('small', {}, help)), input, el('i', { 'aria-hidden': 'true' }));
  }

  render() {
    const p = this.player;
    const s = p.settings;

    const bio = el('textarea', { maxlength: '160', rows: '3', placeholder: 'A line about you, your main server, your clan…' });
    bio.value = s.bio;
    const count = el('small', { class: 'muted' }, `${s.bio.length}/160`);
    bio.addEventListener('input', () => { count.textContent = `${bio.value.length}/160`; });
    const bioForm = el('form', { class: 'acc-card' },
      el('h3', {}, 'Bio'),
      el('p', { class: 'muted' }, 'Shown on your public profile.'),
      bio,
      el('div', { class: 'acc-row' }, count, el('button', { class: 'btn btn-primary btn-sm', type: 'submit' }, 'Save bio')));
    bioForm.addEventListener('submit', (e) => {
      e.preventDefault();
      this.save({ bio: bio.value }, 'Bio saved.');
    });

    const privacy = el('div', { class: 'acc-card' },
      el('h3', {}, 'Privacy'),
      this.toggle('Hide my server', 'Friends, the Players page and Discord see “In game” instead of the server address.',
        s.hideServer, (v) => this.save({ hideServer: v }, v ? 'Your server is hidden.' : 'Your server is visible again.')),
      this.toggle('Allow friend requests', 'Turn off to stop new requests. Current friends stay.',
        s.friendRequests !== 'off', (v) => this.save({ friendRequests: v ? 'on' : 'off' }, v ? 'Friend requests are on.' : 'Friend requests are off.')));

    const discord = el('div', { class: 'acc-card' }, el('h3', {}, 'Discord'));
    if (p.discord) {
      discord.append(
        el('p', {}, 'Linked as ', el('b', {}, p.discord.username), '. It shows on your profile.'),
        el('button', { class: 'btn btn-danger btn-sm', onclick: async () => {
          try {
            this.player = (await api.post('/api/me/discord/unlink')).player;
            Toast.show('Discord unlinked.');
          } catch (e) {
            Toast.show(e.message, { error: true });
          }
          this.render();
        } }, 'Unlink'));
    } else if (this.discordAvailable) {
      discord.append(el('p', { class: 'muted' }, 'Show your Discord on your Astra profile so friends can find you.'),
        el('a', { class: 'btn btn-discord btn-sm', href: '/api/discord/link' }, 'Link Discord'));
    } else {
      discord.append(el('p', { class: 'muted' }, 'Discord linking isn’t switched on for this server yet. (Server owner: set discordClientId and discordClientSecret in config.json.)'));
    }

    this.root.replaceChildren(el('div', { class: 'acc' },
      el('aside', { class: 'acc-me' },
        el('img', { src: avatar(p.uuid, 128), alt: '' }),
        rankBadge(p.rank),
        el('h2', {}, p.name),
        el('a', { class: 'btn btn-quiet btn-sm', href: `/player/${encodeURIComponent(p.name)}` }, 'View my profile'),
        el('a', { class: 'btn btn-quiet btn-sm', href: '/friends' }, 'Friends')),
      el('div', { class: 'acc-main' }, bioForm, this.uploadsCard(), privacy, discord)));
    if (location.hash === '#uploads' && !this.scrolled) {
      this.scrolled = true;
      document.getElementById('uploads').scrollIntoView({ behavior: 'smooth' });
    }
  }
}

new AccountPage().init();
