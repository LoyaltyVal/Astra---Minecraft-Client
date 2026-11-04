// How the launcher is packaged for Windows, macOS and Linux, and where updates come from.
//
//   npm run dist          Windows installer (what "Build Installer.bat" runs)
//   npm run dist:mac      macOS .dmg (+ .zip for updates). Must run on a Mac.
//   npm run dist:linux    Linux AppImage. Run on Linux (or let GitHub Actions do it).
//   npm run release       build for this OS and upload to a GitHub Release (needs GH_TOKEN)
//
// The easy way to get all three: push a tag like v1.0.1 and .github/workflows/release.yml builds
// every platform on GitHub's machines and publishes the release that launchers update from.
//
// Code signing turns itself on when its secrets are set, and is skipped (unsigned build) otherwise:
//   Windows, Azure Artifact Signing: AZURE_TENANT_ID, AZURE_CLIENT_ID, AZURE_CLIENT_SECRET,
//     ASTRA_AZURE_ENDPOINT (e.g. https://eus.codesigning.azure.net/), ASTRA_AZURE_ACCOUNT,
//     ASTRA_AZURE_PROFILE, ASTRA_PUBLISHER_NAME (the name on the certificate)
//   Windows, a .pfx certificate instead: CSC_LINK + CSC_KEY_PASSWORD (+ ASTRA_PUBLISHER_NAME)
//   macOS, Developer ID: CSC_LINK + CSC_KEY_PASSWORD, and for notarization APPLE_ID,
//     APPLE_APP_SPECIFIC_PASSWORD, APPLE_TEAM_ID
const pkg = require('./package.json');

const env = process.env;
const updates = pkg.astraUpdates || {};
const azure = Boolean(env.AZURE_TENANT_ID && env.AZURE_CLIENT_ID && env.AZURE_CLIENT_SECRET
  && env.ASTRA_AZURE_ENDPOINT && env.ASTRA_AZURE_ACCOUNT && env.ASTRA_AZURE_PROFILE);
const pfx = Boolean(env.CSC_LINK || env.WIN_CSC_LINK);
const winSigned = azure || pfx;
const macSigned = process.platform === 'darwin' && Boolean(env.CSC_LINK || env.CSC_NAME);
const notarize = macSigned && Boolean(env.APPLE_ID && env.APPLE_APP_SPECIFIC_PASSWORD && env.APPLE_TEAM_ID);

if (winSigned || macSigned) {
  console.log(`[astra] Code signing: ${[azure && 'Windows (Azure Artifact Signing)', !azure && pfx && 'Windows (.pfx)', macSigned && `macOS${notarize ? ' + notarization' : ''}`].filter(Boolean).join(', ')}`);
} else {
  console.log('[astra] Code signing: off (no signing secrets set). Windows will show a SmartScreen warning.');
}

module.exports = {
  appId: 'dev.astra.launcher',
  productName: 'Astra Launcher',
  directories: { output: 'dist', buildResources: 'build' },
  files: ['src/**/*', 'build/icon.*', 'package.json', 'build-config.json'],
  extraResources: [{ from: 'mods', to: 'mods' }],
  // Read by the updater at run time: whether this Mac build can install updates itself.
  extraMetadata: { astraMacSigned: macSigned },

  // Updates: electron-updater reads latest.yml / latest-mac.yml / latest-linux.yml from the newest
  // GitHub Release. The repo (or a public releases-only repo) must be public for players to read it.
  publish: updates.owner && updates.repo
    ? [{ provider: 'github', owner: updates.owner, repo: updates.repo, releaseType: env.ASTRA_RELEASE_TYPE || 'release' }]
    : null,

  win: {
    target: [{ target: 'nsis', arch: ['x64'] }],
    icon: 'build/icon.ico',
    // Stable file names, so https://github.com/<owner>/<repo>/releases/latest/download/<name> always works.
    artifactName: 'Astra-Launcher-Setup.${ext}',
    signAndEditExecutable: winSigned,
    ...(azure ? {
      azureSignOptions: {
        publisherName: env.ASTRA_PUBLISHER_NAME || 'Astra',
        endpoint: env.ASTRA_AZURE_ENDPOINT,
        codeSigningAccountName: env.ASTRA_AZURE_ACCOUNT,
        certificateProfileName: env.ASTRA_AZURE_PROFILE
      }
    } : {}),
    // A .pfx certificate (CSC_LINK / CSC_KEY_PASSWORD). The publisher name defaults to the
    // certificate's; electron-updater only installs updates signed by that same publisher.
    ...(!azure && pfx ? {
      signtoolOptions: {
        ...(env.ASTRA_PUBLISHER_NAME ? { publisherName: [env.ASTRA_PUBLISHER_NAME] } : {}),
        signingHashAlgorithms: ['sha256']
      }
    } : {})
  },
  nsis: {
    oneClick: false,
    allowToChangeInstallationDirectory: true,
    shortcutName: 'Astra Launcher',
    installerIcon: 'build/icon.ico',
    uninstallerIcon: 'build/icon.ico'
  },

  mac: {
    // dmg to install; zip because macOS updates are delivered as a zip. Both Intel and Apple Silicon.
    target: [{ target: 'dmg', arch: ['x64', 'arm64'] }, { target: 'zip', arch: ['x64', 'arm64'] }],
    icon: 'build/icon.png',
    category: 'public.app-category.games',
    artifactName: 'Astra-Launcher-mac-${arch}.${ext}',
    // Unsigned builds get an ad-hoc signature ('-'): Apple Silicon refuses to run an app with none.
    identity: macSigned ? undefined : '-',
    hardenedRuntime: macSigned,
    notarize: notarize ? { teamId: env.APPLE_TEAM_ID } : false
  },
  dmg: { title: 'Astra Launcher' },

  linux: {
    target: [{ target: 'AppImage', arch: ['x64'] }],
    icon: 'build/icon.png',
    category: 'Game',
    artifactName: 'Astra-Launcher.${ext}',
    synopsis: 'Launcher for Astra Client'
  }
};
