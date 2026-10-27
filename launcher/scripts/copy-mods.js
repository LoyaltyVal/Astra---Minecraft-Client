// Copies the built Astra jars (from `gradlew build`) into launcher/mods/<mc>/astra.jar
// so they get packed into the installer. Run automatically by `npm run dist`.
const fs = require('fs');
const path = require('path');

const VERSIONS = ['1.8.9', '1.7.10'];
const projectDir = path.join(__dirname, '..', '..');
const outDir = path.join(__dirname, '..', 'mods');

let copied = 0;
for (const mc of VERSIONS) {
  const libs = path.join(projectDir, 'versions', mc, 'build', 'libs');
  let jars = [];
  try {
    jars = fs
      .readdirSync(libs)
      .filter((n) => n.startsWith('astra-') && n.endsWith(`+${mc}.jar`))
      .map((n) => path.join(libs, n))
      .sort((a, b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs);
  } catch {
    // not built
  }
  fs.mkdirSync(path.join(outDir, mc), { recursive: true });
  if (!jars.length) {
    console.warn(`! No jar for ${mc}. Run "gradlew build" in ${projectDir} first.`);
    continue;
  }
  fs.copyFileSync(jars[0], path.join(outDir, mc, 'astra.jar'));
  console.log(`✓ ${mc}: ${path.basename(jars[0])}`);
  copied++;
}

if (!copied) {
  console.error('No Astra jars found; the installer would launch without the mod.');
  process.exit(1);
}
