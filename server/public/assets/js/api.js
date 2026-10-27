// Talks to the Astra server. Every page imports this one client.

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export class AstraApi {
  async request(method, path, body) {
    const res = await fetch(path, {
      method,
      credentials: 'same-origin',
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined
    });
    let data = null;
    try {
      data = await res.json();
    } catch {
      data = null;
    }
    if (!res.ok) throw new ApiError(res.status, (data && data.error) || `Request failed (${res.status}).`);
    return data;
  }

  get(path) { return this.request('GET', path); }
  post(path, body = {}) { return this.request('POST', path, body); }
  patch(path, body = {}) { return this.request('PATCH', path, body); }
  delete(path) { return this.request('DELETE', path); }

  // ---- public ----
  meta() { return this.get('/api/meta'); }
  stats() { return this.get('/api/stats'); }
  cosmetics(type) { return this.get('/api/cosmetics' + (type ? `?type=${type}` : '')); }
  team() { return this.get('/api/team'); }
  online() { return this.get('/api/online'); }
  profile(name) { return this.get(`/api/profile/${encodeURIComponent(name)}`); }

  /** The signed-in player, or null when nobody is signed in on this browser. */
  async me() {
    try {
      return (await this.get('/api/me')).player;
    } catch (e) {
      if (e.status === 401) return null;
      throw e;
    }
  }

  // ---- admin ----
  whoami() { return this.get('/api/admin/whoami'); }
  players(q = '') { return this.get(`/api/admin/players?q=${encodeURIComponent(q)}`); }
  addPlayer(name) { return this.post('/api/admin/players', { name }); }
  setRank(uuid, rank) { return this.post(`/api/admin/players/${uuid}/rank`, { rank }); }
  setBanned(uuid, banned) { return this.post(`/api/admin/players/${uuid}/ban`, { banned }); }
  grant(uuid, id) { return this.post(`/api/admin/players/${uuid}/cosmetics`, { id }); }
  revoke(uuid, id) { return this.delete(`/api/admin/players/${uuid}/cosmetics/${id}`); }
  allCosmetics() { return this.get('/api/admin/cosmetics'); }
  createCosmetic(data) { return this.post('/api/admin/cosmetics', data); }
  updateCosmetic(id, patch) { return this.patch(`/api/admin/cosmetics/${id}`, patch); }
  deleteCosmetic(id) { return this.delete(`/api/admin/cosmetics/${id}`); }
  audit() { return this.get('/api/admin/audit'); }
}

export const api = new AstraApi();
