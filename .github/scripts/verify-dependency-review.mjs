import { pathToFileURL } from 'node:url';

const apiUrl = 'https://api.github.com';
const warningHeader = 'x-github-dependency-graph-snapshot-warnings';
const severities = new Set(['low', 'moderate', 'high', 'critical']);

function requireCompleteSnapshot(headers) {
  const encoded = headers.get(warningHeader);
  if (!encoded) return;
  if (!/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(encoded)) {
    throw new Error('Invalid dependency snapshot warning header');
  }
  let warning;
  try {
    warning = new TextDecoder('utf-8', { fatal: true }).decode(Buffer.from(encoded, 'base64'));
  } catch {
    throw new Error('Invalid dependency snapshot warning header');
  }
  if (warning.trim()) {
    throw new Error('Dependency comparison is incomplete: snapshot warnings remain. Wait for graph publication and rerun the check.');
  }
}

function validateChanges(changes) {
  if (!Array.isArray(changes)) throw new Error('Invalid dependency comparison response');
  for (const change of changes) {
    if (!change || !['added', 'removed'].includes(change.change_type) ||
        !['manifest', 'ecosystem', 'name', 'version', 'package_url'].every(key => typeof change[key] === 'string') ||
        (change.scope !== undefined && !['runtime', 'development', 'unknown'].includes(change.scope)) ||
        (change.vulnerabilities !== undefined && !Array.isArray(change.vulnerabilities))) {
      throw new Error('Invalid dependency comparison response');
    }
    for (const vulnerability of change.vulnerabilities ?? []) {
      if (!vulnerability || !severities.has(vulnerability.severity)) {
        throw new Error('Invalid dependency vulnerability response');
      }
    }
  }
}

function hasNextPage(header, endpoint, page) {
  if (!header) return false;
  let next = false;
  for (const entry of header.split(/,\s*(?=<)/)) {
    const match = entry.trim().match(/^<([^>]+)>;\s*rel="([^"]+)"(?:;.*)?$/);
    if (!match) throw new Error('Invalid dependency comparison pagination');
    if (!match[2].toLowerCase().split(/\s+/).includes('next')) continue;
    const url = new URL(match[1]);
    if (next || url.origin !== apiUrl || url.pathname !== endpoint || url.username || url.password || url.hash ||
        url.searchParams.get('per_page') !== '100' || url.searchParams.get('page') !== String(page + 1) ||
        [...url.searchParams.keys()].some(key => !['per_page', 'page'].includes(key))) {
      throw new Error('Invalid dependency comparison pagination');
    }
    next = true;
  }
  return next;
}

/** Supplements the pinned action: a green result without a complete snapshot is not a pass. */
export async function verifyDependencyReview({ repository, baseSha, headSha, token, request = fetch }) {
  if (!/^[\w.-]+\/[\w.-]+$/.test(repository ?? '') || !/^[a-f0-9]{40}$/.test(baseSha ?? '') ||
      !/^[a-f0-9]{40}$/.test(headSha ?? '') || !token) {
    throw new Error('Repository, exact base/head SHAs, and GH_TOKEN are required');
  }
  const endpoint = `/repos/${repository}/dependency-graph/compare/${baseSha}...${headSha}`;
  let added = 0;
  let removed = 0;
  for (let page = 1; page <= 100; page++) {
    const response = await request(`${apiUrl}${endpoint}?per_page=100&page=${page}`, {
      headers: {
        Accept: 'application/vnd.github+json',
        Authorization: `Bearer ${token}`,
        'X-GitHub-Api-Version': '2022-11-28',
      },
      redirect: 'error',
      signal: AbortSignal.timeout(30_000),
    });
    if (!response.ok) throw new Error(`Dependency comparison returned HTTP ${response.status}`);
    requireCompleteSnapshot(response.headers);
    const changes = await response.json();
    validateChanges(changes);
    for (const change of changes) {
      if (change.change_type === 'added') added++;
      else removed++;
      // Match the pinned action's high threshold and default runtime scope (missing = runtime).
      if (change.change_type === 'added' && (change.scope || 'runtime') === 'runtime' &&
          (change.vulnerabilities ?? []).some(vulnerability => ['high', 'critical'].includes(vulnerability.severity))) {
        throw new Error('Dependency comparison found a high or critical runtime vulnerability');
      }
    }
    if (!hasNextPage(response.headers.get('link'), endpoint, page)) {
      return { baseSha, headSha, pages: page, added, removed };
    }
  }
  throw new Error('Dependency comparison exceeded the pagination limit');
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const result = await verifyDependencyReview({
      repository: process.env.GITHUB_REPOSITORY,
      baseSha: process.env.PR_BASE_SHA,
      headSha: process.env.PR_HEAD_SHA,
      token: process.env.GH_TOKEN,
    });
    console.log(`Verified complete dependency comparison: +${result.added}/-${result.removed} across ${result.pages} page(s)`);
  } catch (error) {
    console.error(`Dependency review blocked: ${error.message}`);
    process.exitCode = 1;
  }
}
