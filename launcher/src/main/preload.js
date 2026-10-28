const { contextBridge, ipcRenderer } = require('electron');

/** The only API the launcher page can use. Each call resolves to { ok, value } or { ok: false, error }. */
contextBridge.exposeInMainWorld('astra', {
  info: () => ipcRenderer.invoke('app:info'),

  account: () => ipcRenderer.invoke('auth:get'),
  login: () => ipcRenderer.invoke('auth:login'),
  logout: () => ipcRenderer.invoke('auth:logout'),
  accounts: () => ipcRenderer.invoke('auth:list'),
  switchAccount: (id) => ipcRenderer.invoke('auth:switch', id),
  removeAccount: (id) => ipcRenderer.invoke('auth:remove', id),

  settings: () => ipcRenderer.invoke('settings:get'),
  saveSettings: (patch) => ipcRenderer.invoke('settings:set', patch),

  launch: (versionId) => ipcRenderer.invoke('game:launch', versionId),
  kill: () => ipcRenderer.invoke('game:kill'),
  openFolder: (versionId) => ipcRenderer.invoke('game:openFolder', versionId),

  openLogs: () => ipcRenderer.invoke('app:openLogs'),

  server: {
    account: () => ipcRenderer.invoke('astra:account'),
    equip: (type, id) => ipcRenderer.invoke('astra:equip', type, id),
    openWebsite: (next) => ipcRenderer.invoke('astra:openWebsite', next),
    friends: () => ipcRenderer.invoke('astra:friends'),
    live: () => ipcRenderer.invoke('astra:live'),
    friendAction: (action, body) => ipcRenderer.invoke('astra:friendAction', action, body)
  },

  onState: (fn) => ipcRenderer.on('launch:state', (_e, data) => fn(data)),
  onProgress: (fn) => ipcRenderer.on('launch:progress', (_e, data) => fn(data)),
  onLog: (fn) => ipcRenderer.on('launch:log', (_e, line) => fn(line)),

  window: {
    minimize: () => ipcRenderer.send('window:minimize'),
    maximize: () => ipcRenderer.send('window:maximize'),
    close: () => ipcRenderer.send('window:close')
  }
});
