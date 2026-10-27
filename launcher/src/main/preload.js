const { contextBridge, ipcRenderer } = require('electron');

/** The only API the launcher page can use. Each call resolves to { ok, value } or { ok: false, error }. */
contextBridge.exposeInMainWorld('astra', {
  info: () => ipcRenderer.invoke('app:info'),

  account: () => ipcRenderer.invoke('auth:get'),
  login: () => ipcRenderer.invoke('auth:login'),
  logout: () => ipcRenderer.invoke('auth:logout'),

  settings: () => ipcRenderer.invoke('settings:get'),
  saveSettings: (patch) => ipcRenderer.invoke('settings:set', patch),

  launch: (versionId) => ipcRenderer.invoke('game:launch', versionId),
  kill: () => ipcRenderer.invoke('game:kill'),
  openFolder: (versionId) => ipcRenderer.invoke('game:openFolder', versionId),

  openLogs: () => ipcRenderer.invoke('app:openLogs'),

  cosmetics: {
    list: () => ipcRenderer.invoke('cosmetics:list'),
    equip: (type, name) => ipcRenderer.invoke('cosmetics:equip', type, name),
    remove: (type, name) => ipcRenderer.invoke('cosmetics:remove', type, name),
    import: (type) => ipcRenderer.invoke('cosmetics:import', type),
    openFolder: (type) => ipcRenderer.invoke('cosmetics:openFolder', type)
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
