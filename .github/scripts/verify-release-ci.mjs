import { pathToFileURL } from 'node:url';

export const requiredWorkflows = [
  {
    path: 'android-ci.yml',
    jobs: [
      'JVM, Build & Lint',
      'Build, Test & Lint',
      ...[
        ['Android 11', 30], ['Android 12', 31], ['Android 12L', 32],
        ['Android 13', 33], ['Android 14', 34], ['Android 15', 35],
        ['Android 16', 36], ['Android 17', 37],
      ].map(([version, api]) => `${version} / API ${api} instrumented tests`),
    ],
  },
  { path: 'codeql.yml', jobs: ['CodeQL Java/Kotlin'] },
  { path: 'dependency-submission.yml', jobs: ['Generate dependency graph'] },
  { path: 'publish-dependency-graph.yml', jobs: ['Submit dependency graph'] },
];

export function selectRun(runs, sha, workflowPath) {
  const candidates = runs.filter(run =>
    run.head_sha === sha && run.head_branch === 'main' &&
    ['push', 'workflow_dispatch', 'workflow_run'].includes(run.event) &&
    run.path === `.github/workflows/${workflowPath}` &&
    // workflow_run uses the default branch SHA even when publishing a PR snapshot.
    // Match its parent source and event, rather than accepting unrelated PR publication.
    (workflowPath !== 'publish-dependency-graph.yml' ||
      ['push', 'workflow_dispatch'].some(event =>
        run.display_title === `Publish dependency graph for ${sha} (${event})`)),
  );
  const run = candidates.sort((a, b) => b.id - a.id)[0];
  if (!run) throw new Error(`${workflowPath}: no main workflow run for ${sha}`);
  if (run.status !== 'completed' || run.conclusion !== 'success') {
    throw new Error(`${workflowPath}: latest run ${run.id} is ${run.status}/${run.conclusion}`);
  }
  return run;
}

export function verifyJobs(jobs, requiredNames, workflowPath) {
  for (const name of requiredNames) {
    const job = jobs.find(candidate => candidate.name === name);
    if (!job || job.status !== 'completed' || job.conclusion !== 'success') {
      throw new Error(`${workflowPath}: required job '${name}' did not pass (skipped is not sufficient)`);
    }
  }
  if (jobs.some(job => ['failure', 'cancelled', 'timed_out', 'action_required'].includes(job.conclusion))) {
    throw new Error(`${workflowPath}: a workflow job did not pass`);
  }
}

export async function verifyReleaseCi({ repository, sha, token, apiUrl = 'https://api.github.com', request = fetch }) {
  if (!/^[\w.-]+\/[\w.-]+$/.test(repository ?? '') || !/^[a-f0-9]{40}$/.test(sha ?? '') || !token) {
    throw new Error('Repository, full source SHA, and GH_TOKEN are required');
  }
  async function api(path) {
    const response = await request(`${apiUrl}/repos/${repository}/${path}`, {
      headers: {
        Accept: 'application/vnd.github+json',
        Authorization: `Bearer ${token}`,
        'X-GitHub-Api-Version': '2022-11-28',
      },
      signal: AbortSignal.timeout(30_000),
    });
    if (!response.ok) throw new Error(`GitHub API returned HTTP ${response.status}`);
    return response.json();
  }
  const branch = await api('git/ref/heads/main');
  if (branch.object.sha !== sha) throw new Error('main moved; rerun the release workflow for the intended current commit');
  const verified = [];
  for (const workflow of requiredWorkflows) {
    const query = new URLSearchParams({ branch: 'main', head_sha: sha, per_page: '100' });
    const result = await api(`actions/workflows/${workflow.path}/runs?${query}`);
    const run = selectRun(result.workflow_runs, sha, workflow.path);
    const jobs = [];
    for (let page = 1; ; page++) {
      const result = await api(`actions/runs/${run.id}/jobs?per_page=100&page=${page}`);
      jobs.push(...result.jobs);
      if (result.jobs.length < 100) break;
    }
    verifyJobs(jobs, workflow.jobs, workflow.path);
    verified.push({ workflow: workflow.path, runId: run.id, url: run.html_url });
  }
  return verified;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const verified = await verifyReleaseCi({
      repository: process.env.GITHUB_REPOSITORY,
      sha: process.env.GITHUB_SHA,
      token: process.env.GH_TOKEN,
      apiUrl: process.env.GITHUB_API_URL,
    });
    for (const result of verified) console.log(`${result.workflow}: passed (${result.url})`);
  } catch (error) {
    console.error(`Release blocked: ${error.message}`);
    process.exitCode = 1;
  }
}
