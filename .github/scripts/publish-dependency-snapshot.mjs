import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';

export const artifactName = 'dependency-graph_dependency_submission-generate.json';
const correlator = 'dependency_submission-generate';
const maxArchiveBytes = 2 * 1024 * 1024;
const maxJsonBytes = 8 * 1024 * 1024;
const shaPattern = /^[a-f0-9]{40}$/;

function requireValue(condition, message) {
  if (!condition) throw new Error(message);
}

function record(value, allowedKeys, requiredKeys, label) {
  requireValue(value !== null && typeof value === 'object' && !Array.isArray(value), `${label}: expected an object`);
  requireValue(Object.keys(value).every(key => allowedKeys.includes(key)), `${label}: unexpected field`);
  requireValue(requiredKeys.every(key => Object.hasOwn(value, key)), `${label}: missing field`);
  return value;
}

function text(value, label, limit = 2048) {
  requireValue(typeof value === 'string' && value.length > 0 && value.length <= limit && !value.includes('\0'), `${label}: invalid text`);
  return value;
}

function timestamp(value, label) {
  text(value, label, 40);
  requireValue(/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z$/.test(value) && Number.isFinite(Date.parse(value)), `${label}: invalid timestamp`);
  return Date.parse(value);
}

export function validateSnapshot(snapshot, source) {
  record(snapshot, ['version', 'job', 'sha', 'ref', 'detector', 'manifests', 'scanned'], ['version', 'job', 'sha', 'ref', 'detector', 'manifests', 'scanned'], 'snapshot');
  requireValue(snapshot.version === 0 && snapshot.sha === source.sha && snapshot.ref === source.ref, 'Snapshot version, SHA or ref does not match the source run');
  record(snapshot.job, ['id', 'correlator'], ['id', 'correlator'], 'job');
  requireValue(snapshot.job.id === String(source.runId) && snapshot.job.correlator === correlator, 'Snapshot job does not match the source run');
  const scanned = timestamp(snapshot.scanned, 'scanned');
  requireValue(scanned >= source.startedAt - 60_000 && scanned <= source.updatedAt + 60_000, 'Snapshot scan time does not match the source run');
  record(snapshot.detector, ['name', 'version', 'url'], ['name', 'version', 'url'], 'detector');
  requireValue(snapshot.detector.name === 'GitHub Dependency Graph Gradle Plugin' && snapshot.detector.url === 'https://github.com/gradle/github-dependency-graph-gradle-plugin', 'Unexpected snapshot detector');
  text(snapshot.detector.version, 'detector version', 80);
  record(snapshot.manifests, Object.keys(snapshot.manifests ?? {}), [], 'manifests');
  const manifests = Object.entries(snapshot.manifests);
  requireValue(manifests.length > 0 && manifests.length <= 100, 'Expected non-empty dependency manifests');
  let dependencyCount = 0;
  for (const [name, manifest] of manifests) {
    text(name, 'manifest name');
    record(manifest, ['name', 'file', 'resolved'], ['name', 'file', 'resolved'], 'manifest');
    requireValue(manifest.name === name, 'Manifest name does not match its key');
    record(manifest.file, ['source_location'], ['source_location'], 'manifest file');
    const location = text(manifest.file.source_location, 'manifest location');
    requireValue(!location.startsWith('/') && !/^[a-z]:/i.test(location) && !location.includes('\\') && !location.split('/').includes('..'), 'Manifest location must be relative');
    record(manifest.resolved, Object.keys(manifest.resolved ?? {}), [], 'resolved dependencies');
    for (const [key, dependency] of Object.entries(manifest.resolved)) {
      text(key, 'dependency key');
      requireValue(++dependencyCount <= 20_000, 'Too many snapshot dependencies');
      record(dependency, ['package_url', 'relationship', 'scope', 'dependencies'], ['package_url', 'dependencies'], 'dependency');
      requireValue(text(dependency.package_url, 'package URL').startsWith('pkg:'), 'Expected a package URL');
      requireValue(dependency.relationship === undefined || ['direct', 'indirect'].includes(dependency.relationship), 'Invalid dependency relationship');
      requireValue(dependency.scope === undefined || ['runtime', 'development'].includes(dependency.scope), 'Invalid dependency scope');
      requireValue(Array.isArray(dependency.dependencies) && dependency.dependencies.length <= 20_000, 'Invalid dependency references');
      for (const reference of dependency.dependencies) {
        text(reference, 'dependency reference');
        requireValue(Object.hasOwn(manifest.resolved, reference), 'Unknown dependency reference');
      }
    }
  }
  requireValue(dependencyCount > 0, 'Expected non-empty resolved dependencies');
  // Build a fresh API body. No artifact field can become a request/URL/header option.
  return {
    version: 0,
    sha: source.sha,
    ref: source.ref,
    job: { id: String(source.runId), correlator },
    detector: { ...snapshot.detector },
    manifests: snapshot.manifests,
    scanned: snapshot.scanned,
  };
}

export function readSnapshotArchive(bytes) {
  const python = process.platform === 'win32' ? 'python' : 'python3';
  const helper = fileURLToPath(new URL('./read-dependency-snapshot.py', import.meta.url));
  try {
    return JSON.parse(execFileSync(python, [helper], { input: bytes, maxBuffer: maxJsonBytes + 1024, timeout: 15_000, stdio: ['pipe', 'pipe', 'pipe'] }).toString('utf8'));
  } catch {
    throw new Error('Invalid dependency snapshot archive or JSON');
  }
}

async function boundedBytes(response) {
  requireValue(response.ok, `Artifact download failed: HTTP ${response.status}`);
  const chunks = [];
  let length = 0;
  for await (const chunk of response.body) {
    length += chunk.length;
    requireValue(length <= maxArchiveBytes, 'Artifact archive exceeds size limit');
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

export async function publishDependencySnapshot({ repository, runId, runAttempt, token, apiUrl = 'https://api.github.com', request = fetch, readArchive = readSnapshotArchive }) {
  requireValue(/^[\w-]+\/[\w.-]+$/.test(repository ?? '') && !repository.split('/').includes('..'), 'Invalid repository');
  requireValue(Number.isSafeInteger(runId) && runId > 0 && Number.isSafeInteger(runAttempt) && runAttempt > 0 && token, 'Source run, attempt and token are required');
  const api = new URL(apiUrl);
  requireValue(api.protocol === 'https:' && !api.username && !api.password && !api.search && !api.hash, 'Invalid GitHub API URL');
  const headers = { Accept: 'application/vnd.github+json', Authorization: `Bearer ${token}`, 'X-GitHub-Api-Version': '2022-11-28' };
  async function get(path) {
    const response = await request(`${apiUrl}/repos/${repository}${path ? `/${path}` : ''}`, { headers, redirect: 'error', signal: AbortSignal.timeout(30_000) });
    requireValue(response.ok, `GitHub API read failed: HTTP ${response.status}`);
    return response.json();
  }
  async function readSourceRun() {
    const value = await get(`actions/runs/${runId}`);
    requireValue(value.id === runId && value.run_attempt === runAttempt && value.name === 'Dependency Submission' && value.path === '.github/workflows/dependency-submission.yml' && value.repository?.full_name === repository && typeof value.head_repository?.full_name === 'string' && value.status === 'completed' && value.conclusion === 'success' && shaPattern.test(value.head_sha), 'Unexpected or stale source workflow run');
    return value;
  }
  const run = await readSourceRun();
  const repo = await get('');
  requireValue(repo.full_name === repository && typeof repo.default_branch === 'string', 'Unexpected repository metadata');
  async function readSourcePr(number) {
    const pr = await get(`pulls/${number}`);
    requireValue(pr.number === number && pr.base?.repo?.full_name === repository && pr.base.ref === repo.default_branch && pr.head?.sha === run.head_sha && pr.head.ref === run.head_branch && pr.head.repo?.full_name === run.head_repository.full_name, 'Pull request head no longer matches the source run');
    return pr;
  }
  let prNumber;
  let ref;
  if (run.event === 'pull_request') {
    // workflow_run.pull_requests can be empty, including for fork/Dependabot runs.
    const candidates = await get(`commits/${run.head_sha}/pulls?per_page=100`);
    requireValue(Array.isArray(candidates) && candidates.length < 100, 'Cannot uniquely resolve source pull request');
    const matching = candidates.filter(pr => pr.base?.repo?.full_name === repository && pr.base.ref === repo.default_branch && pr.head?.sha === run.head_sha && pr.head.ref === run.head_branch && pr.head.repo?.full_name === run.head_repository?.full_name);
    requireValue(matching.length === 1 && Number.isSafeInteger(matching[0].number), 'Cannot bind source run to one pull request');
    const pr = await readSourcePr(matching[0].number);
    prNumber = pr.number;
    ref = `refs/pull/${pr.number}/merge`;
  } else {
    requireValue(['push', 'workflow_dispatch'].includes(run.event) && run.head_branch === repo.default_branch && run.head_repository?.full_name === repository, 'Unsupported source workflow event or branch');
    ref = `refs/heads/${repo.default_branch}`;
  }
  const source = { runId, sha: run.head_sha, ref, startedAt: timestamp(run.run_started_at, 'run start'), updatedAt: timestamp(run.updated_at, 'run update') };
  const artifacts = [];
  for (let page = 1; page <= 10; page++) {
    const result = await get(`actions/runs/${runId}/artifacts?per_page=100&page=${page}`);
    requireValue(Array.isArray(result.artifacts), 'Invalid artifact listing');
    artifacts.push(...result.artifacts.filter(artifact => artifact.name === artifactName));
    if (result.artifacts.length < 100) break;
    requireValue(page < 10, 'Too many source run artifacts');
  }
  const current = artifacts.filter(artifact => !artifact.expired && timestamp(artifact.created_at, 'artifact time') >= source.startedAt);
  requireValue(current.length === 1, 'Expected exactly one current source-run snapshot artifact');
  const artifact = current[0];
  requireValue(Number.isSafeInteger(artifact.id) && artifact.id > 0 && artifact.workflow_run?.id === runId && artifact.workflow_run.head_sha === source.sha && artifact.size_in_bytes > 0 && artifact.size_in_bytes <= maxArchiveBytes && /^sha256:[a-f0-9]{64}$/.test(artifact.digest ?? ''), 'Invalid source snapshot artifact');
  const download = await request(`${apiUrl}/repos/${repository}/actions/artifacts/${artifact.id}/zip`, { headers, redirect: 'manual', signal: AbortSignal.timeout(30_000) });
  let bytes;
  if ([302, 303].includes(download.status)) {
    const target = new URL(download.headers.get('location'));
    requireValue(target.protocol === 'https:' && !target.username && !target.password, 'Unsafe artifact download location');
    // The signed artifact URL receives no GitHub token or other request headers.
    bytes = await boundedBytes(await request(target.href, { redirect: 'error', signal: AbortSignal.timeout(30_000) }));
  } else {
    bytes = await boundedBytes(download);
  }
  requireValue(`sha256:${createHash('sha256').update(bytes).digest('hex')}` === artifact.digest, 'Artifact digest mismatch');
  const body = validateSnapshot(readArchive(bytes), source);
  // A rerun or PR update during the download invalidates the earlier source check.
  // Re-read both immediately before POST, using only trusted API responses.
  const [finalRun] = await Promise.all([
    readSourceRun(),
    prNumber === undefined ? Promise.resolve() : readSourcePr(prNumber),
  ]);
  requireValue(finalRun.event === run.event && finalRun.head_sha === run.head_sha && finalRun.head_branch === run.head_branch && finalRun.head_repository.full_name === run.head_repository.full_name && finalRun.run_started_at === run.run_started_at, 'Source workflow changed before snapshot publication');
  const response = await request(`${apiUrl}/repos/${repository}/dependency-graph/snapshots`, { method: 'POST', headers: { ...headers, 'Content-Type': 'application/json' }, body: JSON.stringify(body), redirect: 'error', signal: AbortSignal.timeout(30_000) });
  requireValue(response.ok, `Dependency snapshot submission failed: HTTP ${response.status}`);
  return { runId, sha: source.sha, ref, artifactId: artifact.id, snapshot: await response.json() };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    requireValue(process.env.GITHUB_EVENT_NAME === 'workflow_run', 'Expected the trusted workflow_run event');
    const event = JSON.parse(readFileSync(process.env.GITHUB_EVENT_PATH, 'utf8'));
    const result = await publishDependencySnapshot({ repository: process.env.GITHUB_REPOSITORY, runId: event.workflow_run?.id, runAttempt: event.workflow_run?.run_attempt, token: process.env.GH_TOKEN, apiUrl: process.env.GITHUB_API_URL });
    console.log(`Submitted validated dependency snapshot for ${result.sha} (${result.ref}), source run ${result.runId}, artifact ${result.artifactId}`);
  } catch (error) {
    console.error(`Dependency publication blocked: ${error.message}`);
    process.exitCode = 1;
  }
}
