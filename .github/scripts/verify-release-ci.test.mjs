import assert from 'node:assert/strict';
import test from 'node:test';
import { requiredWorkflows, selectRun, verifyJobs, verifyReleaseCi } from './verify-release-ci.mjs';

const sha = 'a'.repeat(40);
const passedRun = (overrides = {}) => ({
  id: 20, head_sha: sha, head_branch: 'main', event: 'push',
  path: '.github/workflows/android-ci.yml', status: 'completed', conclusion: 'success', ...overrides,
});
const passedJobs = names => names.map(name => ({ name, status: 'completed', conclusion: 'success' }));

test('only the exact main source and workflow can authorize a release', () => {
  const good = passedRun();
  assert.equal(selectRun([
    passedRun({ id: 40, head_sha: 'b'.repeat(40) }),
    passedRun({ id: 41, head_branch: 'feature' }),
    passedRun({ id: 42, event: 'pull_request' }),
    passedRun({ id: 43, path: '.github/workflows/unrelated.yml' }), good,
  ], sha, 'android-ci.yml'), good);
});

test('a newer pending, failed, or cancelled run cannot reuse an older green run', () => {
  for (const state of [
    { status: 'in_progress', conclusion: null },
    { status: 'completed', conclusion: 'failure' },
    { status: 'completed', conclusion: 'cancelled' },
  ]) {
    assert.throws(() => selectRun([passedRun(), passedRun({ id: 21, ...state })], sha, 'android-ci.yml'));
  }
});

test('missing analysis is rejected', () => {
  assert.throws(() => selectRun([], sha, 'android-ci.yml'));
});

test('a green aggregate with skipped or missing Android tests is rejected', () => {
  const workflow = requiredWorkflows[0];
  for (const conclusion of ['skipped', 'failure']) {
    const jobs = passedJobs(workflow.jobs);
    jobs[2].conclusion = conclusion;
    assert.throws(() => verifyJobs(jobs, workflow.jobs, workflow.path));
  }
  assert.throws(() => verifyJobs(passedJobs(workflow.jobs.slice(1)), workflow.jobs, workflow.path));
  verifyJobs(passedJobs(workflow.jobs), workflow.jobs, workflow.path);
});

test('dependency publication is required as well as snapshot generation', () => {
  const publication = requiredWorkflows.find(workflow => workflow.path === 'publish-dependency-graph.yml');
  assert.throws(() => verifyJobs([], publication.jobs, publication.path));
  verifyJobs(passedJobs(publication.jobs), publication.jobs, publication.path);
});

test('publication must belong to the exact main snapshot, not a PR sharing the workflow source', () => {
  const publication = overrides => passedRun({
    event: 'workflow_run', path: '.github/workflows/publish-dependency-graph.yml',
    display_title: `Publish dependency graph for ${sha} (push)`, ...overrides,
  });
  const good = publication({});
  const wrongSource = publication({ id: 30, display_title: `Publish dependency graph for ${'b'.repeat(40)} (push)` });
  const prSnapshot = publication({ id: 31, display_title: `Publish dependency graph for ${sha} (pull_request)` });
  assert.equal(selectRun([wrongSource, prSnapshot, good], sha, 'publish-dependency-graph.yml'), good);
  assert.throws(() => selectRun([wrongSource, prSnapshot], sha, 'publish-dependency-graph.yml'));
});

test('verification aborts when main moves or the API cannot be read', async () => {
  const options = { repository: 'owner/repo', sha, token: 'test-token' };
  await assert.rejects(verifyReleaseCi({ ...options, request: async () => ({ ok: true, json: async () => ({ object: { sha: 'b'.repeat(40) } }) }) }), /main moved/);
  await assert.rejects(verifyReleaseCi({ ...options, request: async () => ({ ok: false, status: 403 }) }), /HTTP 403/);
});

test('a fully checked exact commit is accepted', async () => {
  const request = async url => {
    let result;
    if (url.endsWith('git/ref/heads/main')) result = { object: { sha } };
    else if (url.includes('/runs?')) {
      const workflow = requiredWorkflows.find(candidate => url.includes(`/${candidate.path}/`));
      result = { workflow_runs: [passedRun({
        id: requiredWorkflows.indexOf(workflow) + 1, path: `.github/workflows/${workflow.path}`,
        display_title: `Publish dependency graph for ${sha} (push)`,
      })] };
    } else {
      const id = Number(url.match(/runs\/(\d+)\/jobs/)[1]);
      result = { jobs: passedJobs(requiredWorkflows[id - 1].jobs) };
    }
    return { ok: true, json: async () => result };
  };
  const verified = await verifyReleaseCi({ repository: 'owner/repo', sha, token: 'test-token', request });
  assert.equal(verified.length, requiredWorkflows.length);
});
