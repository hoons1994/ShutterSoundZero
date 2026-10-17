import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import test from 'node:test';
import { artifactName, publishDependencySnapshot, readSnapshotArchive, validateSnapshot } from './publish-dependency-snapshot.mjs';

const repository = 'owner/repo';
const sha = 'a'.repeat(40);
const source = { runId: 123, sha, ref: 'refs/pull/210/merge', startedAt: Date.parse('2026-10-17T01:00:00Z'), updatedAt: Date.parse('2026-10-17T01:02:00Z') };
const snapshot = () => ({
  version: 0, sha, ref: source.ref,
  job: { id: '123', correlator: 'dependency_submission-generate' },
  detector: { name: 'GitHub Dependency Graph Gradle Plugin', version: '1.5.0', url: 'https://github.com/gradle/github-dependency-graph-gradle-plugin' },
  scanned: '2026-10-17T01:01:00Z',
  manifests: { dependency_submission: {
    name: 'dependency_submission', file: { source_location: 'settings.gradle.kts' },
    resolved: { 'kotlin:2.4.21': { package_url: 'pkg:maven/org.jetbrains.kotlin/kotlin-stdlib@2.4.21', relationship: 'direct', scope: 'runtime', dependencies: [] } },
  } },
});

function archive(entries = [{ name: 'dependency-graph-reports/dependency_submission-generate.json', contents: JSON.stringify(snapshot()) }]) {
  // Synthetic ZIP bytes stay in memory. Python's standard library writes the fixtures.
  return execFileSync(process.platform === 'win32' ? 'python' : 'python3', ['-c', `
import io, json, sys, zipfile
buffer = io.BytesIO()
with zipfile.ZipFile(buffer, 'w') as target:
    for item in json.load(sys.stdin):
        entry = zipfile.ZipInfo(item['name'])
        entry.compress_type = item.get('compression', zipfile.ZIP_DEFLATED)
        if 'mode' in item:
            entry.create_system = 3
            entry.external_attr = item['mode'] << 16
        target.writestr(entry, item['contents'])
sys.stdout.buffer.write(buffer.getvalue())
`], { input: JSON.stringify(entries), maxBuffer: 3 * 1024 * 1024, stdio: ['pipe', 'pipe', 'pipe'] });
}

const validArchive = archive();

function fixture({ graph, event = 'pull_request', fork = false } = {}) {
  const bytes = graph ? archive([{ name: 'dependency-graph-reports/dependency_submission-generate.json', contents: JSON.stringify(graph) }]) : validArchive;
  const run = {
    id: 123, run_attempt: 1, name: 'Dependency Submission', path: '.github/workflows/dependency-submission.yml',
    repository: { full_name: repository }, head_repository: { full_name: fork ? 'contributor/fork' : repository },
    status: 'completed', conclusion: 'success', event, head_sha: sha,
    head_branch: event === 'pull_request' ? 'feature' : 'main', pull_requests: [],
    run_started_at: '2026-10-17T01:00:00Z', updated_at: '2026-10-17T01:02:00Z',
  };
  const pr = { number: 210, base: { repo: { full_name: repository }, ref: 'main' }, head: { sha, ref: 'feature', repo: { full_name: run.head_repository.full_name } } };
  const artifact = { id: 456, name: artifactName, size_in_bytes: bytes.length, expired: false, created_at: '2026-10-17T01:01:30Z', digest: `sha256:${createHash('sha256').update(bytes).digest('hex')}`, workflow_run: { id: 123, head_sha: sha } };
  const state = { run, pr, candidates: [pr], artifacts: [artifact], bytes, submitted: [], calls: [], redirect: true };
  const request = async (url, options = {}) => {
    state.calls.push({ url, options });
    const path = new URL(url).pathname;
    if (url.startsWith('https://artifacts.example.test/')) {
      assert.equal(options.headers, undefined, 'GitHub credentials must not reach artifact storage');
      state.onDownload?.(state);
      return new Response(state.bytes);
    }
    assert.ok(url.startsWith('https://api.github.com/repos/owner/repo'), 'Only the expected repository API can receive the token');
    if (options.method === 'POST') {
      assert.equal(path, '/repos/owner/repo/dependency-graph/snapshots');
      state.submitted.push(JSON.parse(options.body));
      return Response.json({ id: 789, message: 'accepted' }, { status: 201 });
    }
    if (path === '/repos/owner/repo') return Response.json({ full_name: repository, default_branch: 'main' });
    if (path.endsWith('/actions/runs/123')) return Response.json(state.run);
    if (path.endsWith(`/commits/${sha}/pulls`)) return Response.json(state.candidates);
    if (path.endsWith('/pulls/210')) return Response.json(state.pr);
    if (path.endsWith('/actions/runs/123/artifacts')) return Response.json({ artifacts: state.artifacts });
    if (path.endsWith('/actions/artifacts/456/zip')) return state.redirect ? new Response(null, { status: 302, headers: { location: 'https://artifacts.example.test/snapshot.zip' } }) : new Response(state.bytes);
    throw new Error(`Unexpected mock API path: ${path}`);
  };
  state.publish = () => publishDependencySnapshot({ repository, runId: 123, runAttempt: 1, token: 'mock-token', request });
  return state;
}

test('the exact source run artifact is decoded once and its validated body is submitted', async () => {
  const state = fixture();
  const result = await state.publish();
  assert.equal(result.sha, sha);
  assert.equal(result.ref, source.ref);
  assert.equal(result.artifactId, 456);
  assert.deepEqual(state.submitted, [snapshot()]);
  assert.equal(state.calls.filter(call => new URL(call.url).pathname.endsWith('/zip')).length, 1);
  assert.equal(state.calls.filter(call => new URL(call.url).pathname.endsWith('/actions/runs/123')).length, 2);
  assert.equal(state.calls.filter(call => new URL(call.url).pathname.endsWith('/pulls/210')).length, 2);
});

test('fork PRs use their verified PR head and cannot inherit a main ref', async () => {
  const state = fixture({ fork: true });
  await state.publish();
  assert.equal(state.submitted[0].ref, 'refs/pull/210/merge');
});

test('push and manual main source runs preserve the main snapshot flow', async () => {
  for (const event of ['push', 'workflow_dispatch']) {
    const graph = snapshot();
    graph.ref = 'refs/heads/main';
    const state = fixture({ graph, event });
    await state.publish();
    assert.equal(state.submitted[0].ref, 'refs/heads/main');
    assert.equal(state.calls.some(call => call.url.includes('/pulls')), false);
  }
});

test('PR snapshots specifying main, another SHA or another source job never reach POST', async () => {
  for (const mutate of [graph => { graph.ref = 'refs/heads/main'; }, graph => { graph.sha = 'b'.repeat(40); }, graph => { graph.job.id = '999'; }, graph => { graph.job.correlator = 'other-job'; }]) {
    const graph = snapshot();
    mutate(graph);
    const state = fixture({ graph });
    await assert.rejects(state.publish(), /does not match/);
    assert.equal(state.submitted.length, 0);
  }
});

test('artifact request, headers, URL and repository fields cannot become API options', () => {
  for (const key of ['request', 'headers', 'url', 'baseUrl', 'method', 'owner', 'repo', 'body']) {
    assert.throws(() => validateSnapshot({ ...snapshot(), [key]: 'attacker-controlled' }, source), /unexpected field/);
  }
  const graph = snapshot();
  graph.detector.request = { url: 'https://untrusted.example.test' };
  assert.throws(() => validateSnapshot(graph, source), /unexpected field/);
});

test('changed PR heads, unrelated repositories, unsupported events and stale attempts are blocked', async () => {
  for (const mutate of [state => { state.pr.head.sha = 'b'.repeat(40); }, state => { state.run.repository.full_name = 'other/repo'; }, state => { state.run.run_attempt = 2; }, state => { state.run.event = 'pull_request_target'; }, state => { state.run.conclusion = 'failure'; }, state => { state.run.path = '.github/workflows/other.yml'; }]) {
    const state = fixture();
    mutate(state);
    await assert.rejects(state.publish());
    assert.equal(state.submitted.length, 0);
  }
});

test('source attempts and source identity changed during download never reach POST', async () => {
  for (const mutate of [
    state => { state.run.run_attempt = 2; state.run.status = 'in_progress'; state.run.conclusion = null; },
    state => { state.run.run_attempt = 2; },
    state => { state.run.status = 'in_progress'; },
    state => { state.run.conclusion = 'failure'; },
    state => { state.run.head_sha = 'b'.repeat(40); },
    state => { state.run.repository.full_name = 'other/repo'; },
    state => { state.run.path = '.github/workflows/other.yml'; },
    state => { state.run.event = 'push'; },
    state => { state.run.head_repository.full_name = 'other/fork'; },
    state => { state.run.run_started_at = '2026-10-17T01:01:00Z'; },
  ]) {
    const state = fixture();
    state.onDownload = mutate;
    await assert.rejects(state.publish(), /source|Source/);
    assert.equal(state.submitted.length, 0);
  }
});

test('PR heads changed during download never reach POST', async () => {
  for (const mutate of [
    state => { state.pr.head.sha = 'b'.repeat(40); },
    state => { state.pr.head.ref = 'other-branch'; },
    state => { state.pr.head.repo.full_name = 'other/fork'; },
    state => { state.pr.base.ref = 'other-base'; },
  ]) {
    const state = fixture({ fork: true });
    state.onDownload = mutate;
    await assert.rejects(state.publish(), /Pull request head/);
    assert.equal(state.submitted.length, 0);
  }
});

test('missing, ambiguous, expired, previous-attempt and wrong-run artifacts are rejected', async () => {
  for (const mutate of [state => { state.artifacts = []; }, state => { state.artifacts.push({ ...state.artifacts[0], id: 457 }); }, state => { state.artifacts[0].expired = true; }, state => { state.artifacts[0].created_at = '2026-10-17T00:59:59Z'; }, state => { state.artifacts[0].workflow_run.id = 999; }, state => { state.artifacts[0].workflow_run.head_sha = 'b'.repeat(40); }, state => { state.artifacts[0].digest = `sha256:${'0'.repeat(64)}`; }]) {
    const state = fixture();
    mutate(state);
    await assert.rejects(state.publish());
    assert.equal(state.submitted.length, 0);
  }
});

test('unknown or ambiguous source PR associations fail closed', async () => {
  for (const candidates of [[], [{ number: 210, base: { repo: { full_name: 'other/repo' } } }]]) {
    const state = fixture();
    state.candidates = candidates;
    await assert.rejects(state.publish(), /pull request/);
    assert.equal(state.submitted.length, 0);
  }
});

test('empty dependencies, unknown schema fields, dangling references and unrelated scan time are rejected', () => {
  for (const mutate of [graph => { graph.manifests = {}; }, graph => { graph.manifests.dependency_submission.resolved = {}; }, graph => { graph.manifests.dependency_submission.file.source_location = '../outside'; }, graph => { graph.manifests.dependency_submission.resolved['kotlin:2.4.21'].dependencies = ['missing']; }, graph => { graph.scanned = '2026-10-16T01:01:00Z'; }]) {
    const graph = snapshot();
    mutate(graph);
    assert.throws(() => validateSnapshot(graph, source));
  }
});

test('ZIP parsing uses bounded standard-library reads and does not extract entry paths', () => {
  assert.deepEqual(readSnapshotArchive(validArchive), snapshot());
  for (const entries of [
    [{ name: '../dependency_submission-generate.json', contents: '{}' }],
    [{ name: 'dependency-graph-reports/dependency_submission-generate.json', contents: '{}', mode: 0o120777 }],
    [{ name: 'dependency-graph-reports/dependency_submission-generate.json', contents: '{}', compression: 12 }],
    [{ name: 'dependency-graph-reports/dependency_submission-generate.json', contents: '{}' }, { name: 'unexpected.json', contents: '{}' }],
  ]) assert.throws(() => readSnapshotArchive(archive(entries)), /Invalid dependency snapshot/);
  assert.throws(() => readSnapshotArchive(Buffer.alloc(2 * 1024 * 1024 + 1)), /Invalid dependency snapshot/);
  assert.throws(() => readSnapshotArchive(Buffer.from('not a ZIP')), /Invalid dependency snapshot/);
  const corrupt = archive([{ name: 'dependency-graph-reports/dependency_submission-generate.json', contents: JSON.stringify(snapshot()), compression: 0 }]);
  corrupt[corrupt.indexOf(Buffer.from('{"version"'))] ^= 1;
  assert.throws(() => readSnapshotArchive(corrupt), /Invalid dependency snapshot/);
});
