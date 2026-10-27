// Static launcher configuration: which Minecraft versions Astra ships for.

/** Legacy Fabric loader version installed for every game version (keep in sync with gradle.properties). */
const LOADER_VERSION = '0.18.3';

/** Legacy Fabric's metadata server (same API as Fabric's meta.fabricmc.net). */
const LEGACY_FABRIC_META = 'https://meta.legacyfabric.net/v2';

/** Mojang's Java runtime index (used by the official launcher). */
const JAVA_RUNTIME_INDEX =
  'https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json';

const VERSIONS = [
  {
    id: '1.8.9',
    title: '1.8.9',
    subtitle: 'The PvP standard. Hypixel, Minemen and most practice servers.',
    javaComponent: 'jre-legacy'
  },
  {
    id: '1.7.10',
    title: '1.7.10',
    subtitle: 'Old-school combat with classic animations.',
    javaComponent: 'jre-legacy'
  }
];

function getVersion(id) {
  return VERSIONS.find((v) => v.id === id) || null;
}

module.exports = { LOADER_VERSION, LEGACY_FABRIC_META, JAVA_RUNTIME_INDEX, VERSIONS, getVersion };
