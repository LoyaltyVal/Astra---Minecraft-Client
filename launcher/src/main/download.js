const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

async function fetchJson(url) {
  const res = await fetch(url, { headers: { 'User-Agent': 'AstraLauncher' } });
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText} from ${new URL(url).host}`);
  }
  return res.json();
}

function sha1File(file) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('sha1');
    fs.createReadStream(file)
      .on('data', (chunk) => hash.update(chunk))
      .on('error', reject)
      .on('end', () => resolve(hash.digest('hex')));
  });
}

async function fileMatches(file, sha1) {
  try {
    if (!fs.statSync(file).isFile()) return false;
    return !sha1 || (await sha1File(file)) === sha1;
  } catch {
    return false;
  }
}

/** Downloads url to dest (via a .part file), checking sha1 when given. Retries twice. */
async function downloadFile(url, dest, sha1) {
  if (await fileMatches(dest, sha1)) {
    return false;
  }
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  let lastError;
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const res = await fetch(url, { headers: { 'User-Agent': 'AstraLauncher' } });
      if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
      const data = Buffer.from(await res.arrayBuffer());
      if (sha1) {
        const got = crypto.createHash('sha1').update(data).digest('hex');
        if (got !== sha1) throw new Error(`checksum mismatch for ${path.basename(dest)}`);
      }
      const part = dest + '.part';
      fs.writeFileSync(part, data);
      fs.renameSync(part, dest);
      return true;
    } catch (e) {
      lastError = e;
    }
  }
  throw new Error(`Could not download ${path.basename(dest)}: ${lastError.message}`);
}

/** Runs async jobs with a concurrency limit, reporting progress after each one. */
async function runPool(jobs, limit, onDone) {
  let next = 0;
  let done = 0;
  async function worker() {
    while (next < jobs.length) {
      const job = jobs[next++];
      await job();
      done++;
      if (onDone) onDone(done, jobs.length);
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, jobs.length) }, worker));
}

module.exports = { fetchJson, downloadFile, sha1File, fileMatches, runPool };
