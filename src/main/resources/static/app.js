const STORAGE_KEY = 'chaos-platform.active-environment';

const environments = [
  {
    id: 'local-dev',
    name: 'Local Dev',
    cluster: 'docker-desktop',
    region: 'localhost',
    posture: 'sandbox',
  },
  {
    id: 'staging-west',
    name: 'Staging West',
    cluster: 'gke-staging-west',
    region: 'us-west-2',
    posture: 'guarded',
  },
  {
    id: 'prod-shadow',
    name: 'Prod Shadow',
    cluster: 'eks-prod-shadow',
    region: 'us-east-1',
    posture: 'approval-gated',
  },
];

const routes = {
  dashboard: {
    path: '/',
    navLabel: 'Dashboard',
    eyebrow: 'Command overview',
    title: 'Reliability posture at a glance',
    description:
      'Pin active experiments, watch service health, and spot drift before a run turns into a wider incident.',
    metrics: [
      { label: 'Active injections', value: '03', note: '2 latency tests, 1 HTTP fault' },
      { label: 'Healthy agents', value: '18/19', note: '1 node lagging heartbeat by 44s' },
      { label: 'Guardrail breaches', value: '01', note: 'Pending approval for prod-shadow scope' },
    ],
    activityTitle: 'Current watchpoints',
    activityItems: [
      'Payment-service latency test trending 12% above expected blast radius.',
      'Checkout-worker rollback timer is within safe budget.',
      'Agent quorum in staging-west remains healthy after a restart cycle.',
    ],
    decisionTitle: 'Operator decisions',
    decisionItems: [
      'Escalate prod-shadow runs when approval is still pending after 10 minutes.',
      'Keep agent health and safety posture visible in the shell, not buried in page content.',
      'Default every view to the active environment so downstream pages inherit the same scope.',
    ],
    listSurfaceTitle: 'Active experiment queue',
    listEmptyMessage: 'No active experiments match the selected environment yet.',
    listErrorMessage: 'Unable to refresh the active queue from the control plane.',
    detailSurfaceTitle: 'Run impact snapshot',
    detailEmptyMessage: 'Pick an active run to inspect its blast radius and recovery path.',
    detailErrorMessage: 'Impact metrics failed to load for the selected run.',
  },
  experiments: {
    path: '/experiments/',
    navLabel: 'Experiments',
    eyebrow: 'Design and targeting',
    title: 'Shape experiments before they reach agents',
    description:
      'Define target selectors, fault profiles, and guardrails with enough context to make safe execution decisions.',
    metrics: [
      { label: 'Draft templates', value: '14', note: '6 latency, 4 HTTP, 4 mixed fault profiles' },
      { label: 'Pending approvals', value: '02', note: 'Both target approval-gated environments' },
      { label: 'Reusable selectors', value: '09', note: 'namespace, tag, and service-group presets' },
    ],
    activityTitle: 'Builder expectations',
    activityItems: [
      'Target selectors need room for service tags, namespaces, and environment overlays.',
      'Guardrails should stay adjacent to the fault configuration rather than hidden in a modal.',
      'Route inventory must leave space for future detail pages without reworking the shell.',
    ],
    decisionTitle: 'Context carried into this page',
    decisionItems: [
      'The selected environment narrows templates and safe defaults before a form is opened.',
      'Project identity remains in the header so an operator can jump between design and execution views.',
      'Empty states should teach the user what to create next, not just announce that nothing exists.',
    ],
    listSurfaceTitle: 'Experiment catalog',
    listEmptyMessage: 'Create the first experiment template for this environment to unlock repeatable runs.',
    listErrorMessage: 'The experiment list could not be loaded from the API.',
    detailSurfaceTitle: 'Experiment detail draft',
    detailEmptyMessage: 'Select a template to preview selectors, fault policy, and rollout notes.',
    detailErrorMessage: 'The selected experiment definition could not be resolved.',
  },
  'live-runs': {
    path: '/live-runs/',
    navLabel: 'Live Runs',
    eyebrow: 'Execution control',
    title: 'Track blast radius while runs are still underway',
    description:
      'Give operators a clear path from agent progress to stop control, with enough environment context to act quickly.',
    metrics: [
      { label: 'Runs streaming', value: '05', note: '3 staging-west, 2 local-dev' },
      { label: 'Agents mid-plan', value: '11', note: '2 waiting on retries, 9 executing cleanly' },
      { label: 'Median stop latency', value: '4.8s', note: 'Within the 8s safety budget' },
    ],
    activityTitle: 'Live run priorities',
    activityItems: [
      'Keep stop control inside the same frame as telemetry and agent progress.',
      'Treat the run timeline as a first-class detail surface, not secondary content.',
      'Reserve persistent space for emergency notes and execution warnings.',
    ],
    decisionTitle: 'Operational cues',
    decisionItems: [
      'The shell keeps the environment banner visible so a stop action cannot lose scope.',
      'Navigation order mirrors the operator workflow: dashboard to design to execution to results.',
      'Loading states need to preserve layout stability because live telemetry updates frequently.',
    ],
    listSurfaceTitle: 'Streaming run list',
    listEmptyMessage: 'There are no live runs in the current environment right now.',
    listErrorMessage: 'Live run updates are not reaching the browser.',
    detailSurfaceTitle: 'Execution timeline',
    detailEmptyMessage: 'Choose a run to inspect agent progress, fault phase, and stop controls.',
    detailErrorMessage: 'The execution timeline could not be recovered for this run.',
  },
  results: {
    path: '/results/',
    navLabel: 'Results',
    eyebrow: 'Outcome review',
    title: 'Compare system behavior before, during, and after a run',
    description:
      'Summarize impact, recovery, and service-level anomalies in a layout that can expand into deeper analysis later.',
    metrics: [
      { label: 'Completed runs today', value: '12', note: '9 completed, 2 stopped, 1 failed' },
      { label: 'Avg. recovery time', value: '2m 14s', note: 'Fastest path seen on payment-service' },
      { label: 'Flagged anomalies', value: '04', note: '2 latency spikes, 2 elevated 5xx windows' },
    ],
    activityTitle: 'Review workflow',
    activityItems: [
      'Results need fast comparison blocks that can grow into detailed telemetry panes.',
      'The detail surface should keep incident notes and experiment metadata in the same frame.',
      'Error states must still expose the last-known run identity so operators can retry safely.',
    ],
    decisionTitle: 'Why the shell matters here',
    decisionItems: [
      'Global environment context prevents cross-environment result confusion.',
      'History and results stay separate because one is analytical and the other is operational.',
      'The same empty and error language should repeat across pages to keep recovery obvious.',
    ],
    listSurfaceTitle: 'Recent outcomes',
    listEmptyMessage: 'No completed runs have been captured for this environment yet.',
    listErrorMessage: 'Outcome summaries could not be retrieved.',
    detailSurfaceTitle: 'Impact comparison',
    detailEmptyMessage: 'Select a completed run to compare before, during, and after telemetry.',
    detailErrorMessage: 'Comparison data is unavailable for the selected result set.',
  },
  history: {
    path: '/history/',
    navLabel: 'History',
    eyebrow: 'Operational memory',
    title: 'Search the trail of previous experiments and reruns',
    description:
      'Preserve enough structure for filters, comparisons, and replay actions without rebuilding the shell later.',
    metrics: [
      { label: 'Runs retained', value: '248', note: 'Across local-dev, staging-west, and prod-shadow' },
      { label: 'Saved filters', value: '07', note: 'team, service, environment, and fault presets' },
      { label: 'Rerun candidates', value: '19', note: 'Stable templates with consistent telemetry coverage' },
    ],
    activityTitle: 'History UX rules',
    activityItems: [
      'Search, environment scope, and date slicing should fit without collapsing on mobile.',
      'The list surface needs affordances for compare and rerun actions even before backend wiring exists.',
      'History detail should make it obvious whether a run can be replayed safely.',
    ],
    decisionTitle: 'Persistence across routes',
    decisionItems: [
      'Operators should never wonder which environment a search result belongs to.',
      'Route shells stay consistent so deeper pages can be added without changing the frame.',
      'List and detail states match the rest of the product to reduce operator relearning.',
    ],
    listSurfaceTitle: 'Run archive',
    listEmptyMessage: 'No archived runs match the current filter set.',
    listErrorMessage: 'The run archive search service is unavailable.',
    detailSurfaceTitle: 'Historical run dossier',
    detailEmptyMessage: 'Select an archived run to review notes, telemetry, and rerun readiness.',
    detailErrorMessage: 'Historical details failed to load for the selected run.',
  },
};

const previewState = {
  list: 'loading',
  detail: 'empty',
};

const routeKey = document.body.dataset.route || 'dashboard';
const sidebarNode = document.getElementById('sidebar');
const contentNode = document.getElementById('content');

document.addEventListener('change', (event) => {
  const target = event.target;

  if (target instanceof HTMLSelectElement && target.id === 'environment-select') {
    localStorage.setItem(STORAGE_KEY, target.value);
    render();
  }
});

document.addEventListener('click', (event) => {
  const target = event.target;

  if (!(target instanceof HTMLElement)) {
    return;
  }

  const button = target.closest('[data-surface][data-state]');

  if (!button) {
    return;
  }

  previewState[button.dataset.surface] = button.dataset.state;
  renderContent();
});

render();

function render() {
  renderSidebar();
  renderContent();
}

function renderSidebar() {
  const environment = getActiveEnvironment();

  sidebarNode.innerHTML = `
    <div class="sidebar-panel">
      <p class="sidebar-kicker">Project context</p>
      <h1>Chaos Platform</h1>
      <p class="sidebar-copy">Distributed resilience lab for controlled failures, live telemetry, and run history.</p>
    </div>

    <nav class="nav" aria-label="Primary navigation">
      ${Object.keys(routes)
        .map((key) => {
          const route = routes[key];
          const activeClass = key === routeKey ? ' active' : '';

          return `
            <a class="nav-link${activeClass}" href="${route.path}">
              <span class="nav-title">${route.navLabel}</span>
              <span class="nav-copy">${route.eyebrow}</span>
            </a>
          `;
        })
        .join('')}
    </nav>

    <div class="sidebar-panel status-panel">
      <p class="sidebar-kicker">Environment scope</p>
      <label class="field-label" for="environment-select">Active environment</label>
      <select id="environment-select" class="environment-select">
        ${environments
          .map(
            (item) =>
              `<option value="${item.id}"${item.id === environment.id ? ' selected' : ''}>${item.name}</option>`,
          )
          .join('')}
      </select>

      <dl class="status-list">
        <div>
          <dt>Cluster</dt>
          <dd>${environment.cluster}</dd>
        </div>
        <div>
          <dt>Region</dt>
          <dd>${environment.region}</dd>
        </div>
        <div>
          <dt>Safety posture</dt>
          <dd>${environment.posture}</dd>
        </div>
      </dl>
    </div>
  `;
}

function renderContent() {
  const environment = getActiveEnvironment();
  const route = routes[routeKey];

  contentNode.innerHTML = `
    <header class="topbar">
      <div>
        <p class="topbar-label">Global project and environment context</p>
        <h2>Chaos Platform / ${environment.name}</h2>
      </div>
      <div class="context-strip" aria-label="Current environment details">
        <span class="context-pill">cluster: ${environment.cluster}</span>
        <span class="context-pill">region: ${environment.region}</span>
        <span class="context-pill">posture: ${environment.posture}</span>
      </div>
    </header>

    <section class="page">
      <header class="hero-card">
        <div class="hero-copy">
          <p class="hero-kicker">${route.eyebrow}</p>
          <h2>${route.title}</h2>
          <p>${route.description}</p>
        </div>
        <div class="hero-actions">
          <div class="hero-context">
            <span>Environment</span>
            <strong>${environment.name}</strong>
          </div>
          <div class="hero-context">
            <span>Cluster</span>
            <strong>${environment.cluster}</strong>
          </div>
          <div class="hero-context">
            <span>Region</span>
            <strong>${environment.region}</strong>
          </div>
        </div>
      </header>

      <section class="metric-grid" aria-label="${route.navLabel} summary metrics">
        ${route.metrics
          .map(
            (metric) => `
              <article class="metric-card">
                <p class="metric-label">${metric.label}</p>
                <h3>${metric.value}</h3>
                <p class="metric-note">${metric.note}</p>
              </article>
            `,
          )
          .join('')}
      </section>

      <section class="story-grid">
        <article class="story-card">
          <div class="section-heading compact">
            <div>
              <p class="section-label">Page intent</p>
              <h3>${route.activityTitle}</h3>
            </div>
          </div>
          <ul class="detail-list">
            ${route.activityItems.map((item) => `<li>${item}</li>`).join('')}
          </ul>
        </article>

        <article class="story-card">
          <div class="section-heading compact">
            <div>
              <p class="section-label">Shell decisions</p>
              <h3>${route.decisionTitle}</h3>
            </div>
          </div>
          <ul class="detail-list">
            ${route.decisionItems.map((item) => `<li>${item}</li>`).join('')}
          </ul>
        </article>
      </section>

      <section class="state-showcase">
        <div class="section-heading">
          <div>
            <p class="section-label">Reusable surfaces</p>
            <h2>Loading, empty, and error states for both list and detail views</h2>
          </div>
          <p class="section-copy">
            Each route inherits the same state language so future API wiring can slot into a consistent frame.
          </p>
        </div>

        <div class="surface-grid">
          <article class="surface-card">
            <div class="surface-header">
              <div>
                <p class="surface-label">List surface</p>
                <h3>${route.listSurfaceTitle}</h3>
              </div>
              ${renderStateSwitch('list', previewState.list)}
            </div>
            <div class="surface-body">
              ${renderSurfaceMarkup('list', previewState.list, route)}
            </div>
          </article>

          <article class="surface-card">
            <div class="surface-header">
              <div>
                <p class="surface-label">Detail surface</p>
                <h3>${route.detailSurfaceTitle}</h3>
              </div>
              ${renderStateSwitch('detail', previewState.detail)}
            </div>
            <div class="surface-body">
              ${renderSurfaceMarkup('detail', previewState.detail, route)}
            </div>
          </article>
        </div>
      </section>
    </section>
  `;
}

function renderStateSwitch(surface, activeState) {
  return `
    <div class="state-switch" role="tablist" aria-label="Preview surface states">
      ${['loading', 'empty', 'error']
        .map((state) => {
          const activeClass = state === activeState ? ' active' : '';
          return `<button type="button" class="state-option${activeClass}" data-surface="${surface}" data-state="${state}">${state}</button>`;
        })
        .join('')}
    </div>
  `;
}

function renderSurfaceMarkup(surface, state, route) {
  if (state === 'loading') {
    return `
      <div class="preview-stack" aria-label="${surface} loading state preview">
        ${surface === 'detail' ? '<div class="skeleton-panel"></div>' : ''}
        <div class="skeleton-row wide"></div>
        <div class="skeleton-row medium"></div>
        <div class="skeleton-row medium"></div>
        <div class="skeleton-row short"></div>
      </div>
    `;
  }

  if (state === 'empty') {
    const copy = surface === 'list' ? route.listEmptyMessage : route.detailEmptyMessage;
    const buttonLabel = surface === 'list' ? 'Seed sample data' : 'Browse available records';

    return `
      <div class="preview-message">
        <p class="preview-title">${surface === 'list' ? 'Nothing to show yet' : 'No detail selected'}</p>
        <p class="preview-copy">${copy}</p>
        <button type="button" class="ghost-button">${buttonLabel}</button>
      </div>
    `;
  }

  const copy = surface === 'list' ? route.listErrorMessage : route.detailErrorMessage;
  const buttonLabel = surface === 'list' ? 'Retry fetch' : 'Reload panel';

  return `
    <div class="preview-message error">
      <p class="preview-title">${surface === 'list' ? 'Request failed' : 'Detail unavailable'}</p>
      <p class="preview-copy">${copy}</p>
      <button type="button" class="ghost-button">${buttonLabel}</button>
    </div>
  `;
}

function getActiveEnvironment() {
  const savedEnvironmentId = localStorage.getItem(STORAGE_KEY) || environments[1].id;

  return environments.find((environment) => environment.id === savedEnvironmentId) || environments[0];
}
