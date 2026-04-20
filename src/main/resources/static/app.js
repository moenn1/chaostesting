const STORAGE_KEY = 'chaos-platform.active-environment';
const AUTO_REFRESH_KEY = 'chaos-platform.dashboard-auto-refresh';
const EXPERIMENT_STORAGE_KEY = 'chaos-platform.experiment-builder';
const DASHBOARD_REFRESH_MS = 15000;

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
    title: 'Active runs, outcomes, and fleet health',
    description:
      'Track the experiment queue, recent outcomes, failed drills, and the agent fleet from one operator surface.',
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
  agents: {
    path: '/agents/',
    navLabel: 'Agents',
    eyebrow: 'Fleet detail',
    title: 'Inspect the agent fleet that carries each experiment',
    description:
      'Keep heartbeat drift, workload, and recent findings visible before a single unhealthy node turns into a blind spot.',
    metrics: [
      { label: 'Responding agents', value: '19/20', note: '1 node is inside the drift window' },
      { label: 'Capacity headroom', value: '27%', note: 'Enough room for two more guarded runs' },
      { label: 'Recovered today', value: '03', note: 'Agents returned cleanly after node churn' },
    ],
    activityTitle: 'Fleet management cues',
    activityItems: [
      'Keep heartbeat, workload, and the linked run in the same row.',
      'Make degraded agents obvious without forcing a modal or drill-down first.',
      'Show enough context to decide whether to drain, retry, or let the agent continue.',
    ],
    decisionTitle: 'How this page supports the dashboard',
    decisionItems: [
      'Dashboard health links should land on a real detail view, not a dead end.',
      'Environment context remains visible so operators do not inspect the wrong fleet.',
      'The same detail surface can expand later into logs, traces, and lease metadata.',
    ],
    listSurfaceTitle: 'Fleet summary',
    listEmptyMessage: 'No registered agents are visible in this environment yet.',
    listErrorMessage: 'The fleet registry did not return agent data.',
    detailSurfaceTitle: 'Agent status detail',
    detailEmptyMessage: 'Choose an agent to inspect health, workload, and recent findings.',
    detailErrorMessage: 'The selected agent detail could not be resolved.',
    showInNav: false,
  },
};

const primaryRouteKeys = ['dashboard', 'experiments', 'live-runs', 'results', 'history'];

const linkedDetails = {
  experiments: {
    param: 'experiment',
    records: {
      'exp-checkout-latency': {
        title: 'Checkout latency envelope',
        status: 'Guarded',
        summary:
          'Inject 350ms latency into 30% of checkout traffic to verify retry budgets after the payment-service topology change.',
        facts: [
          { label: 'Selector', value: 'service=checkout-api, namespace=payments' },
          { label: 'Fault', value: '350ms latency for 8 minutes' },
          { label: 'Guardrail', value: 'Abort if 5xx > 2.5% for 90 seconds' },
          { label: 'Rollout', value: 'Two pods first, then namespace slice' },
        ],
        bullets: [
          'Built to validate checkout retries before prod-shadow approval is opened wider.',
          'Links directly to the active dashboard run and the fleet agent carrying the canary lane.',
        ],
        links: [
          { href: '/live-runs/?run=run-stg-401', label: 'Open linked live run' },
          { href: '/agents/?agent=agent-stg-07', label: 'Inspect lead agent' },
        ],
      },
      'exp-catalog-cpu': {
        title: 'Catalog CPU saturation',
        status: 'Stable',
        summary:
          'Burn CPU in the catalog service long enough to force autoscaling and verify that checkout read paths stay insulated.',
        facts: [
          { label: 'Selector', value: 'service=catalog-api, namespace=shop' },
          { label: 'Fault', value: '75% CPU burn for 6 minutes' },
          { label: 'Guardrail', value: 'Abort if checkout p95 grows above 400ms' },
          { label: 'Rollout', value: 'One pod, then AZ pair' },
        ],
        bullets: [
          'Recent outcome recovered within the two-minute budget.',
          'Used as the reference template for local-dev rehearsal before guarded environments.',
        ],
        links: [
          { href: '/results/?result=result-stg-398', label: 'Open latest result' },
          { href: '/', label: 'Return to dashboard' },
        ],
      },
      'exp-inventory-packet-loss': {
        title: 'Inventory broker packet loss',
        status: 'Needs review',
        summary:
          'Apply packet loss across the inventory broker path to check consumer lag recovery and rollback confirmation behavior.',
        facts: [
          { label: 'Selector', value: 'kafka-topic=inventory.events' },
          { label: 'Fault', value: '12% packet loss for 10 minutes' },
          { label: 'Guardrail', value: 'Abort if lag remains above 180 seconds' },
          { label: 'Rollout', value: 'Single partition leader first' },
        ],
        bullets: [
          'The last failed run exposed a rollback confirmation timeout after leader re-election.',
          'The degraded staging agent is still tied to the failed result investigation.',
        ],
        links: [
          { href: '/results/?result=result-stg-392', label: 'Open failed result' },
          { href: '/agents/?agent=agent-stg-07', label: 'Inspect linked agent' },
        ],
      },
      'exp-shadow-db-saturation': {
        title: 'Shadow DB saturation',
        status: 'Approval gated',
        summary:
          'Drive connection churn against the shadow database tier to prove rollback speed before wider prod-shadow replay.',
        facts: [
          { label: 'Selector', value: 'service=shadow-db, cluster=eks-prod-shadow' },
          { label: 'Fault', value: 'Burst connection opens for 5 minutes' },
          { label: 'Guardrail', value: 'Stop if read latency exceeds 220ms' },
          { label: 'Rollout', value: 'Synthetic shadow traffic only' },
        ],
        bullets: [
          'This template remains approval gated and is visible in the dashboard watchlist.',
          'Agent links land on the dedicated fleet detail view so operators can inspect lease health before promoting.',
        ],
        links: [
          { href: '/agents/?agent=agent-prod-02', label: 'Inspect shadow agent' },
          { href: '/', label: 'Return to dashboard' },
        ],
      },
      'exp-local-queue-backlog': {
        title: 'Local queue backlog rehearsal',
        status: 'Sandbox',
        summary:
          'Slow local queue consumers to validate that rerun controls, badges, and recovery copy stay legible on a small fixture.',
        facts: [
          { label: 'Selector', value: 'service=local-queue, cluster=docker-desktop' },
          { label: 'Fault', value: 'Pause consumers for 90 seconds' },
          { label: 'Guardrail', value: 'Abort if end-to-end wait exceeds 6 minutes' },
          { label: 'Rollout', value: 'Single worker process' },
        ],
        bullets: [
          'Used to rehearse the experiment builder and dashboard links without backend pressure.',
          'The linked result stays in local-dev so UI work can iterate safely.',
        ],
        links: [
          { href: '/results/?result=result-dev-122', label: 'Open local result' },
          { href: '/', label: 'Return to dashboard' },
        ],
      },
    },
  },
  'live-runs': {
    param: 'run',
    records: {
      'run-dev-118': {
        title: 'Local queue backlog rehearsal',
        status: 'Running',
        summary:
          'The local queue drill is still underway in sandbox scope and is being used to rehearse refresh and recovery UX without backend risk.',
        facts: [
          { label: 'Service', value: 'local-queue' },
          { label: 'Phase', value: 'Consumer pause with backlog growth' },
          { label: 'Blast radius', value: 'Single local worker process' },
          { label: 'Lead agent', value: 'agent-dev-01' },
        ],
        bullets: [
          'This run anchors the dashboard local-dev active-runs card.',
          'Its linked result stays available for comparison once recovery completes.',
        ],
        links: [
          { href: '/experiments/?experiment=exp-local-queue-backlog', label: 'View experiment' },
          { href: '/agents/?agent=agent-dev-01', label: 'Inspect lead agent' },
        ],
      },
      'run-dev-119': {
        title: 'Catalog CPU saturation',
        status: 'Recovering',
        summary:
          'The local catalog rehearsal has already passed the fault phase and is now validating that recovery copy remains readable as the run winds down.',
        facts: [
          { label: 'Service', value: 'catalog-api' },
          { label: 'Phase', value: 'Recovery verification' },
          { label: 'Blast radius', value: 'Single local pod' },
          { label: 'Lead agent', value: 'agent-dev-01' },
        ],
        bullets: [
          'Local-dev keeps one recovering run visible so progress treatment can be checked against healthy and failed cases.',
          'The linked agent page exposes the collector workload rather than dropping the user into a dead end.',
        ],
        links: [
          { href: '/experiments/?experiment=exp-catalog-cpu', label: 'View experiment' },
          { href: '/agents/?agent=agent-dev-01', label: 'Inspect linked agent' },
        ],
      },
      'run-stg-401': {
        title: 'Checkout latency envelope',
        status: 'Running',
        summary:
          'The latency canary is still active in staging-west, with four agents carrying the route split and rollback rails intact.',
        facts: [
          { label: 'Service', value: 'checkout-api' },
          { label: 'Phase', value: 'Latency injected across 30% of traffic' },
          { label: 'Blast radius', value: '2 checkout pods and 1 payment worker' },
          { label: 'Lead agent', value: 'agent-stg-07' },
        ],
        bullets: [
          'Progress remains inside the safe latency budget window.',
          'Stop latency stayed below the 5-second goal on the last heartbeat.',
        ],
        links: [
          { href: '/experiments/?experiment=exp-checkout-latency', label: 'View experiment' },
          { href: '/agents/?agent=agent-stg-07', label: 'Inspect lead agent' },
        ],
      },
      'run-stg-402': {
        title: 'Checkout retry storm',
        status: 'Holding',
        summary:
          'The staging retry drill is paused behind an approval gate, keeping the live-runs page useful for linked detail even when execution is not fully active yet.',
        facts: [
          { label: 'Service', value: 'checkout-worker' },
          { label: 'Phase', value: 'Approval hold before retry amplification' },
          { label: 'Blast radius', value: 'Single canary worker shard' },
          { label: 'Lead agent', value: 'agent-stg-03' },
        ],
        bullets: [
          'Approval-held runs still belong in the dashboard because they affect operator decisions and queue visibility.',
          'The lead agent link makes it clear whether the hold is caused by fleet health or policy.',
        ],
        links: [
          { href: '/experiments/?experiment=exp-checkout-latency', label: 'View experiment' },
          { href: '/agents/?agent=agent-stg-03', label: 'Inspect lead agent' },
        ],
      },
      'run-stg-403': {
        title: 'Inventory broker packet loss',
        status: 'Recovering',
        summary:
          'Rollback steps have been issued, but recovery confirmation is still being validated after the broker leader change.',
        facts: [
          { label: 'Service', value: 'inventory-broker' },
          { label: 'Phase', value: 'Rollback verification' },
          { label: 'Blast radius', value: 'inventory.events partition leader' },
          { label: 'Lead agent', value: 'agent-stg-11' },
        ],
        bullets: [
          'One related failed result is still pinned on the dashboard for follow-up.',
          'Agent health remains green, but the degraded coordinator is visible from the fleet view.',
        ],
        links: [
          { href: '/results/?result=result-stg-392', label: 'Open failed result' },
          { href: '/experiments/?experiment=exp-inventory-packet-loss', label: 'View experiment' },
        ],
      },
      'run-prod-102': {
        title: 'Shadow DB saturation',
        status: 'Holding',
        summary:
          'Prod-shadow is waiting on approval before the connection burst expands beyond the initial synthetic lane.',
        facts: [
          { label: 'Service', value: 'shadow-db' },
          { label: 'Phase', value: 'Approval hold' },
          { label: 'Blast radius', value: 'Synthetic replay only' },
          { label: 'Lead agent', value: 'agent-prod-02' },
        ],
        bullets: [
          'Guardrail posture is explicit in both the dashboard metric card and this detail view.',
          'The dashboard refresh timestamp lets operators confirm the hold state is still current.',
        ],
        links: [
          { href: '/experiments/?experiment=exp-shadow-db-saturation', label: 'View experiment' },
          { href: '/agents/?agent=agent-prod-02', label: 'Inspect agent' },
        ],
      },
    },
  },
  results: {
    param: 'result',
    records: {
      'result-stg-398': {
        title: 'Catalog CPU saturation',
        status: 'Recovered',
        summary:
          'Catalog scaled out in time, checkout stayed inside its latency budget, and the recovery window closed in two minutes.',
        facts: [
          { label: 'Service', value: 'catalog-api' },
          { label: 'Outcome', value: 'Recovered inside guardrails' },
          { label: 'Recovery', value: '2m 14s' },
          { label: 'Anomaly', value: 'Checkout p95 rose 19% for 90 seconds' },
        ],
        bullets: [
          'This result is one of the recent clean outcomes promoted on the dashboard.',
          'The linked experiment remains available for rerun from the builder page.',
        ],
        links: [
          { href: '/experiments/?experiment=exp-catalog-cpu', label: 'View experiment' },
          { href: '/', label: 'Return to dashboard' },
        ],
      },
      'result-stg-392': {
        title: 'Inventory broker packet loss',
        status: 'Failed',
        summary:
          'The experiment exited with unresolved consumer lag because rollback confirmation timed out after a leader swap.',
        facts: [
          { label: 'Service', value: 'inventory-broker' },
          { label: 'Outcome', value: 'Failed rollback confirmation' },
          { label: 'Recovery', value: 'Needs operator review' },
          { label: 'Anomaly', value: 'Consumer lag stayed above 180 seconds' },
        ],
        bullets: [
          'This result remains pinned in the dashboard failed-runs widget.',
          'The same agent is linked into the fleet view for immediate inspection.',
        ],
        links: [
          { href: '/experiments/?experiment=exp-inventory-packet-loss', label: 'View experiment' },
          { href: '/agents/?agent=agent-stg-07', label: 'Inspect linked agent' },
        ],
      },
      'result-prod-208': {
        title: 'Shadow DB saturation rehearsal',
        status: 'Stopped',
        summary:
          'The shadow replay was stopped early when read latency brushed the guardrail, but recovery returned within one minute.',
        facts: [
          { label: 'Service', value: 'shadow-db' },
          { label: 'Outcome', value: 'Stopped before threshold breach' },
          { label: 'Recovery', value: '56s' },
          { label: 'Anomaly', value: 'Read latency peaked at 214ms' },
        ],
        bullets: [
          'Stopped outcomes still appear in the recent-outcomes widget because they shape promotion decisions.',
          'This detail view preserves the experiment and agent links needed for follow-up.',
        ],
        links: [
          { href: '/experiments/?experiment=exp-shadow-db-saturation', label: 'View experiment' },
          { href: '/agents/?agent=agent-prod-02', label: 'Inspect agent' },
        ],
      },
      'result-dev-122': {
        title: 'Local queue backlog rehearsal',
        status: 'Recovered',
        summary:
          'Local queue pressure cleared without intervention, giving the UI a safe result fixture for development.',
        facts: [
          { label: 'Service', value: 'local-queue' },
          { label: 'Outcome', value: 'Recovered inside sandbox limits' },
          { label: 'Recovery', value: '47s' },
          { label: 'Anomaly', value: 'Queue wait grew by 2m 08s' },
        ],
        bullets: [
          'This result is intentionally simple and links back into the local experiment template.',
          'The dashboard uses it as a recent outcome when local-dev is active.',
        ],
        links: [
          { href: '/experiments/?experiment=exp-local-queue-backlog', label: 'View experiment' },
          { href: '/', label: 'Return to dashboard' },
        ],
      },
    },
  },
  agents: {
    param: 'agent',
    records: {
      'agent-dev-02': {
        title: 'agent-dev-02',
        status: 'Healthy',
        summary:
          'The local trace collector is idle and clean, which makes it a good control sample when comparing healthy fleet cards against degraded staging agents.',
        facts: [
          { label: 'Role', value: 'Trace collector' },
          { label: 'Zone', value: 'docker-desktop' },
          { label: 'Heartbeat drift', value: '1s lag' },
          { label: 'Linked run', value: 'Catalog CPU saturation' },
        ],
        bullets: [
          'Healthy control agents matter because the dashboard needs more than one severity state.',
          'This route gives local-dev a concrete fleet detail target for every linked agent card.',
        ],
        links: [
          { href: '/live-runs/?run=run-dev-119', label: 'Open linked run' },
          { href: '/', label: 'Return to dashboard' },
        ],
      },
      'agent-stg-11': {
        title: 'agent-stg-11',
        status: 'Healthy',
        summary:
          'The rollback verifier remains healthy while closing the inventory recovery path, which helps separate fleet health from workflow state.',
        facts: [
          { label: 'Role', value: 'Rollback verifier' },
          { label: 'Zone', value: 'us-west-2c' },
          { label: 'Heartbeat drift', value: '6s lag' },
          { label: 'Linked run', value: 'Inventory broker packet loss' },
        ],
        bullets: [
          'Healthy verification agents still need a detail route because they carry the last step of critical recoveries.',
          'The dashboard fleet widget can now send this card to a real destination.',
        ],
        links: [
          { href: '/live-runs/?run=run-stg-403', label: 'Open linked run' },
          { href: '/results/?result=result-stg-392', label: 'Open related failed result' },
        ],
      },
      'agent-stg-07': {
        title: 'agent-stg-07',
        status: 'Degraded',
        summary:
          'The staging coordinator recovered after a node restart, but heartbeat jitter is still above the preferred envelope.',
        facts: [
          { label: 'Role', value: 'Fault coordinator' },
          { label: 'Zone', value: 'us-west-2b' },
          { label: 'Heartbeat drift', value: '44s lag' },
          { label: 'Linked run', value: 'Checkout latency envelope' },
        ],
        bullets: [
          'The same agent is highlighted by the dashboard fleet widget and watchlist.',
          'Degraded is intentional here so the dashboard can surface a real attention state.',
        ],
        links: [
          { href: '/live-runs/?run=run-stg-401', label: 'Open linked run' },
          { href: '/results/?result=result-stg-392', label: 'Open related failed result' },
        ],
      },
      'agent-stg-03': {
        title: 'agent-stg-03',
        status: 'Healthy',
        summary:
          'Traffic routing remains clean, lease renewals are steady, and the agent is carrying a checkout approval-hold lane.',
        facts: [
          { label: 'Role', value: 'Traffic router' },
          { label: 'Zone', value: 'us-west-2a' },
          { label: 'Heartbeat drift', value: '4s lag' },
          { label: 'Linked run', value: 'Checkout retry storm' },
        ],
        bullets: [
          'Healthy agents still expose workload so operators can judge spare capacity.',
          'The dashboard agent-health overview links here directly.',
        ],
        links: [
          { href: '/live-runs/?run=run-stg-402', label: 'Open linked run' },
          { href: '/', label: 'Return to dashboard' },
        ],
      },
      'agent-prod-02': {
        title: 'agent-prod-02',
        status: 'Guarded',
        summary:
          'The prod-shadow runner is healthy, but its workload stays approval gated until the shadow replay expands.',
        facts: [
          { label: 'Role', value: 'Synthetic replay runner' },
          { label: 'Zone', value: 'us-east-1c' },
          { label: 'Heartbeat drift', value: '7s lag' },
          { label: 'Linked run', value: 'Shadow DB saturation' },
        ],
        bullets: [
          'This view makes the gating posture explicit instead of hiding it inside a generic card.',
          'Dashboard links use this page as the agent detail target for approval-gated environments.',
        ],
        links: [
          { href: '/live-runs/?run=run-prod-102', label: 'Open linked run' },
          { href: '/experiments/?experiment=exp-shadow-db-saturation', label: 'View experiment' },
        ],
      },
      'agent-prod-04': {
        title: 'agent-prod-04',
        status: 'Healthy',
        summary:
          'The shadow metrics collector is idle but important, because promotion decisions rely on a clean observer path before approval widens the replay.',
        facts: [
          { label: 'Role', value: 'Metrics collector' },
          { label: 'Zone', value: 'us-east-1a' },
          { label: 'Heartbeat drift', value: '5s lag' },
          { label: 'Linked run', value: 'Shadow DB saturation' },
        ],
        bullets: [
          'Healthy support agents still receive detail pages so dashboard links remain consistent.',
          'The current shadow hold means collector capacity is visible even while the runner is gated.',
        ],
        links: [
          { href: '/live-runs/?run=run-prod-102', label: 'Open linked run' },
          { href: '/experiments/?experiment=exp-shadow-db-saturation', label: 'View experiment' },
        ],
      },
      'agent-dev-01': {
        title: 'agent-dev-01',
        status: 'Healthy',
        summary:
          'The local sandbox agent is idle enough to keep UI rehearsal safe while still surfacing a realistic fleet card.',
        facts: [
          { label: 'Role', value: 'Local sandbox runner' },
          { label: 'Zone', value: 'docker-desktop' },
          { label: 'Heartbeat drift', value: '2s lag' },
          { label: 'Linked run', value: 'Local queue backlog rehearsal' },
        ],
        bullets: [
          'Local-dev uses this record to exercise the fleet detail route without risking guarded environments.',
          'The dashboard can link here from the agent-health grid in one click.',
        ],
        links: [
          { href: '/results/?result=result-dev-122', label: 'Open recent result' },
          { href: '/', label: 'Return to dashboard' },
        ],
      },
    },
  },
};

const dashboardSnapshots = {
  'local-dev': [
    {
      summary: {
        recentRuns: 5,
        recentNote: 'Recent rehearsals all recovered cleanly in sandbox scope.',
        failedRuns: 0,
        failedNote: 'No failed drills are blocking local UI work.',
        healthyAgents: 4,
        totalAgents: 4,
        agentNote: 'All local agents are responding within 3 seconds.',
      },
      spotlight:
        'Local Dev keeps a compact queue so shell changes can be rehearsed without waiting on guarded approvals.',
      watchlist: [
        { label: 'Queue backlog rehearsal is ready for another pass.', href: '/experiments/?experiment=exp-local-queue-backlog' },
        { label: 'Local sandbox agent remains the cleanest reference fleet node.', href: '/agents/?agent=agent-dev-01' },
      ],
      activeRuns: [
        {
          id: 'run-dev-118',
          title: 'Local queue backlog rehearsal',
          experimentId: 'exp-local-queue-backlog',
          service: 'local-queue',
          status: 'Running',
          progress: 68,
          blastRadius: 'single worker process',
          agents: '1 active agent',
          startedAt: '4 min ago',
          guardrail: 'Queue wait is still below the 6-minute stop line.',
          leadAgentId: 'agent-dev-01',
        },
        {
          id: 'run-dev-119',
          title: 'Catalog CPU saturation',
          experimentId: 'exp-catalog-cpu',
          service: 'catalog-api',
          status: 'Recovering',
          progress: 87,
          blastRadius: 'one pod in docker-desktop',
          agents: '1 active agent',
          startedAt: '9 min ago',
          guardrail: 'Autoscaling fallback stayed inside rehearsal limits.',
          leadAgentId: 'agent-dev-01',
        },
      ],
      recentOutcomes: [
        {
          id: 'result-dev-122',
          title: 'Local queue backlog rehearsal',
          status: 'Recovered',
          recovery: '47s',
          finishedAt: '12 min ago',
          impact: 'Queue wait briefly grew by 2m 08s',
          experimentId: 'exp-local-queue-backlog',
          summary: 'Consumers recovered without operator intervention.',
        },
        {
          id: 'result-stg-398',
          title: 'Catalog CPU saturation',
          status: 'Recovered',
          recovery: '2m 14s',
          finishedAt: '37 min ago',
          impact: 'Catalog p95 rose 19% during autoscaling',
          experimentId: 'exp-catalog-cpu',
          summary: 'Recovery stayed under the two-minute budget.',
        },
      ],
      failedRuns: [],
      agents: [
        {
          id: 'agent-dev-01',
          name: 'agent-dev-01',
          status: 'Healthy',
          role: 'Local sandbox runner',
          zone: 'docker-desktop',
          heartbeat: '2s lag',
          healthScore: 98,
          load: '2 active drills',
          linkedRunId: 'run-dev-118',
          summary: 'UI rehearsal fixtures are stable and fast to reset.',
        },
        {
          id: 'agent-dev-02',
          name: 'agent-dev-02',
          status: 'Healthy',
          role: 'Trace collector',
          zone: 'docker-desktop',
          heartbeat: '1s lag',
          healthScore: 97,
          load: 'idle',
          linkedRunId: 'run-dev-119',
          summary: 'Collector backlog is empty after the last rehearsal.',
        },
      ],
    },
    {
      summary: {
        recentRuns: 6,
        recentNote: 'A fresh backlog rehearsal joined the recent clean outcomes.',
        failedRuns: 0,
        failedNote: 'Sandbox is still clear of failed drills.',
        healthyAgents: 4,
        totalAgents: 4,
        agentNote: 'Local fleet health stayed flat after the last refresh.',
      },
      spotlight:
        'Local Dev advanced one queue drill into recovery, which makes the refresh timestamp useful even without backend wiring.',
      watchlist: [
        { label: 'Local queue drill is near completion and safe to rerun.', href: '/live-runs/?run=run-dev-118' },
        { label: 'The local agent remains a stable reference node.', href: '/agents/?agent=agent-dev-01' },
      ],
      activeRuns: [
        {
          id: 'run-dev-118',
          title: 'Local queue backlog rehearsal',
          experimentId: 'exp-local-queue-backlog',
          service: 'local-queue',
          status: 'Recovering',
          progress: 92,
          blastRadius: 'single worker process',
          agents: '1 active agent',
          startedAt: '6 min ago',
          guardrail: 'Drain time is falling back into the nominal range.',
          leadAgentId: 'agent-dev-01',
        },
      ],
      recentOutcomes: [
        {
          id: 'result-dev-122',
          title: 'Local queue backlog rehearsal',
          status: 'Recovered',
          recovery: '47s',
          finishedAt: '14 min ago',
          impact: 'Queue wait briefly grew by 2m 08s',
          experimentId: 'exp-local-queue-backlog',
          summary: 'Consumers recovered without operator intervention.',
        },
        {
          id: 'result-stg-398',
          title: 'Catalog CPU saturation',
          status: 'Recovered',
          recovery: '2m 14s',
          finishedAt: '39 min ago',
          impact: 'Catalog p95 rose 19% during autoscaling',
          experimentId: 'exp-catalog-cpu',
          summary: 'Recovery stayed under the two-minute budget.',
        },
      ],
      failedRuns: [],
      agents: [
        {
          id: 'agent-dev-01',
          name: 'agent-dev-01',
          status: 'Healthy',
          role: 'Local sandbox runner',
          zone: 'docker-desktop',
          heartbeat: '2s lag',
          healthScore: 99,
          load: '1 active drill',
          linkedRunId: 'run-dev-118',
          summary: 'The local runner is nearing idle again after the recovery stage.',
        },
        {
          id: 'agent-dev-02',
          name: 'agent-dev-02',
          status: 'Healthy',
          role: 'Trace collector',
          zone: 'docker-desktop',
          heartbeat: '1s lag',
          healthScore: 97,
          load: 'idle',
          linkedRunId: 'run-dev-118',
          summary: 'Collector backlog is still empty after the latest refresh.',
        },
      ],
    },
  ],
  'staging-west': [
    {
      summary: {
        recentRuns: 8,
        recentNote: 'Six recovered cleanly; one stopped; one failed for review.',
        failedRuns: 1,
        failedNote: 'Inventory packet loss still needs rollback investigation.',
        healthyAgents: 18,
        totalAgents: 19,
        agentNote: 'One degraded coordinator is lagging by 44 seconds.',
      },
      spotlight:
        'Staging West is carrying the live operator load, so dashboard links need to land on concrete detail views, not placeholders.',
      watchlist: [
        { label: 'Prod Shadow approval is still pending before replay expansion.', href: '/experiments/?experiment=exp-shadow-db-saturation' },
        { label: 'agent-stg-07 still shows heartbeat jitter after a node restart.', href: '/agents/?agent=agent-stg-07' },
        { label: 'Inventory packet loss remains the single failed drill on deck.', href: '/results/?result=result-stg-392' },
      ],
      activeRuns: [
        {
          id: 'run-stg-401',
          title: 'Checkout latency envelope',
          experimentId: 'exp-checkout-latency',
          service: 'checkout-api',
          status: 'Running',
          progress: 72,
          blastRadius: '2 checkout pods and 1 payment worker',
          agents: '4 active agents',
          startedAt: '6 min ago',
          guardrail: 'Checkout 5xx remains under the 2.5% guardrail.',
          leadAgentId: 'agent-stg-07',
        },
        {
          id: 'run-stg-402',
          title: 'Checkout retry storm',
          experimentId: 'exp-checkout-latency',
          service: 'checkout-worker',
          status: 'Holding',
          progress: 38,
          blastRadius: '1 worker shard in canary mode',
          agents: '3 active agents',
          startedAt: '14 min ago',
          guardrail: 'Approval timer expires in 4 minutes.',
          leadAgentId: 'agent-stg-03',
        },
        {
          id: 'run-stg-403',
          title: 'Inventory broker packet loss',
          experimentId: 'exp-inventory-packet-loss',
          service: 'inventory-broker',
          status: 'Recovering',
          progress: 91,
          blastRadius: 'inventory.events partition leader',
          agents: '2 active agents',
          startedAt: '22 min ago',
          guardrail: 'Rollback has been issued and is awaiting confirmation.',
          leadAgentId: 'agent-stg-11',
        },
      ],
      recentOutcomes: [
        {
          id: 'result-stg-398',
          title: 'Catalog CPU saturation',
          status: 'Recovered',
          recovery: '2m 14s',
          finishedAt: '11 min ago',
          impact: 'Checkout p95 rose 19% during autoscaling',
          experimentId: 'exp-catalog-cpu',
          summary: 'Autoscaling recovered before downstream checkout degradation spread.',
        },
        {
          id: 'result-prod-208',
          title: 'Shadow DB saturation rehearsal',
          status: 'Stopped',
          recovery: '56s',
          finishedAt: '27 min ago',
          impact: 'Read latency approached the promotion threshold',
          experimentId: 'exp-shadow-db-saturation',
          summary: 'Operator stopped early before the guardrail turned into a breach.',
        },
        {
          id: 'result-stg-392',
          title: 'Inventory broker packet loss',
          status: 'Failed',
          recovery: 'Needs review',
          finishedAt: '1h 04m ago',
          impact: 'Consumer lag stayed above 180 seconds',
          experimentId: 'exp-inventory-packet-loss',
          summary: 'Rollback confirmation timed out after broker leader re-election.',
        },
      ],
      failedRuns: [
        {
          id: 'result-stg-392',
          title: 'Inventory broker packet loss',
          cause: 'Rollback confirmation timed out after broker leader re-election.',
          impact: 'Consumer lag remained elevated past the recovery budget.',
          experimentId: 'exp-inventory-packet-loss',
          agentId: 'agent-stg-07',
        },
      ],
      agents: [
        {
          id: 'agent-stg-07',
          name: 'agent-stg-07',
          status: 'Degraded',
          role: 'Fault coordinator',
          zone: 'us-west-2b',
          heartbeat: '44s lag',
          healthScore: 71,
          load: '2 active drills',
          linkedRunId: 'run-stg-401',
          summary: 'Recovered after node restart, but heartbeat jitter is still visible.',
        },
        {
          id: 'agent-stg-03',
          name: 'agent-stg-03',
          status: 'Healthy',
          role: 'Traffic router',
          zone: 'us-west-2a',
          heartbeat: '4s lag',
          healthScore: 96,
          load: '1 active drill',
          linkedRunId: 'run-stg-402',
          summary: 'Route splits remain steady while the run is on approval hold.',
        },
        {
          id: 'agent-stg-11',
          name: 'agent-stg-11',
          status: 'Healthy',
          role: 'Rollback verifier',
          zone: 'us-west-2c',
          heartbeat: '6s lag',
          healthScore: 93,
          load: '1 active drill',
          linkedRunId: 'run-stg-403',
          summary: 'Verifier still holds the inventory rollback lane.',
        },
      ],
    },
    {
      summary: {
        recentRuns: 9,
        recentNote: 'A fresh stopped outcome joined the last-hour review set.',
        failedRuns: 1,
        failedNote: 'The inventory failure is still the only unresolved result.',
        healthyAgents: 18,
        totalAgents: 19,
        agentNote: 'The degraded node improved slightly but is still outside target.',
      },
      spotlight:
        'The refresh state advanced one live run and tightened the degraded heartbeat, so operators can see the dashboard reacting over time.',
      watchlist: [
        { label: 'Checkout retry storm cleared approval and resumed execution.', href: '/live-runs/?run=run-stg-402' },
        { label: 'The degraded coordinator still needs a fleet-level follow-up.', href: '/agents/?agent=agent-stg-07' },
        { label: 'Failed inventory rollback remains pinned for review.', href: '/results/?result=result-stg-392' },
      ],
      activeRuns: [
        {
          id: 'run-stg-401',
          title: 'Checkout latency envelope',
          experimentId: 'exp-checkout-latency',
          service: 'checkout-api',
          status: 'Running',
          progress: 89,
          blastRadius: '2 checkout pods and 1 payment worker',
          agents: '4 active agents',
          startedAt: '9 min ago',
          guardrail: 'Error rate stayed below the stop threshold during the last cycle.',
          leadAgentId: 'agent-stg-07',
        },
        {
          id: 'run-stg-402',
          title: 'Checkout retry storm',
          experimentId: 'exp-checkout-latency',
          service: 'checkout-worker',
          status: 'Running',
          progress: 52,
          blastRadius: '1 worker shard in canary mode',
          agents: '3 active agents',
          startedAt: '16 min ago',
          guardrail: 'Approval landed; canary is now exercising retry paths.',
          leadAgentId: 'agent-stg-03',
        },
        {
          id: 'run-stg-403',
          title: 'Inventory broker packet loss',
          experimentId: 'exp-inventory-packet-loss',
          service: 'inventory-broker',
          status: 'Recovering',
          progress: 96,
          blastRadius: 'inventory.events partition leader',
          agents: '2 active agents',
          startedAt: '24 min ago',
          guardrail: 'Recovery confirmation is the final open step.',
          leadAgentId: 'agent-stg-11',
        },
      ],
      recentOutcomes: [
        {
          id: 'result-stg-398',
          title: 'Catalog CPU saturation',
          status: 'Recovered',
          recovery: '2m 14s',
          finishedAt: '13 min ago',
          impact: 'Checkout p95 rose 19% during autoscaling',
          experimentId: 'exp-catalog-cpu',
          summary: 'Autoscaling recovered before downstream checkout degradation spread.',
        },
        {
          id: 'result-prod-208',
          title: 'Shadow DB saturation rehearsal',
          status: 'Stopped',
          recovery: '56s',
          finishedAt: '29 min ago',
          impact: 'Read latency approached the promotion threshold',
          experimentId: 'exp-shadow-db-saturation',
          summary: 'Operator stopped early before the guardrail turned into a breach.',
        },
        {
          id: 'result-stg-392',
          title: 'Inventory broker packet loss',
          status: 'Failed',
          recovery: 'Needs review',
          finishedAt: '1h 06m ago',
          impact: 'Consumer lag stayed above 180 seconds',
          experimentId: 'exp-inventory-packet-loss',
          summary: 'Rollback confirmation timed out after broker leader re-election.',
        },
      ],
      failedRuns: [
        {
          id: 'result-stg-392',
          title: 'Inventory broker packet loss',
          cause: 'Rollback confirmation timed out after broker leader re-election.',
          impact: 'Consumer lag remained elevated past the recovery budget.',
          experimentId: 'exp-inventory-packet-loss',
          agentId: 'agent-stg-07',
        },
      ],
      agents: [
        {
          id: 'agent-stg-07',
          name: 'agent-stg-07',
          status: 'Degraded',
          role: 'Fault coordinator',
          zone: 'us-west-2b',
          heartbeat: '31s lag',
          healthScore: 78,
          load: '2 active drills',
          linkedRunId: 'run-stg-401',
          summary: 'Heartbeat recovered somewhat, but the node is still outside the steady-state envelope.',
        },
        {
          id: 'agent-stg-03',
          name: 'agent-stg-03',
          status: 'Healthy',
          role: 'Traffic router',
          zone: 'us-west-2a',
          heartbeat: '4s lag',
          healthScore: 97,
          load: '1 active drill',
          linkedRunId: 'run-stg-402',
          summary: 'Route splits remain stable after the approval hold cleared.',
        },
        {
          id: 'agent-stg-11',
          name: 'agent-stg-11',
          status: 'Healthy',
          role: 'Rollback verifier',
          zone: 'us-west-2c',
          heartbeat: '5s lag',
          healthScore: 94,
          load: '1 active drill',
          linkedRunId: 'run-stg-403',
          summary: 'Verifier is almost done closing the last rollback step.',
        },
      ],
    },
  ],
  'prod-shadow': [
    {
      summary: {
        recentRuns: 3,
        recentNote: 'Stopped shadow replays dominate the latest guarded outcomes.',
        failedRuns: 0,
        failedNote: 'No failed shadow drills are pinned right now.',
        healthyAgents: 6,
        totalAgents: 6,
        agentNote: 'All shadow agents are healthy, but one is still approval gated.',
      },
      spotlight:
        'Prod Shadow stays intentionally quiet, but the dashboard still needs to surface approval posture, recent stopped outcomes, and lead-agent links.',
      watchlist: [
        { label: 'Shadow DB saturation is still approval gated before wider replay.', href: '/experiments/?experiment=exp-shadow-db-saturation' },
        { label: 'agent-prod-02 is the lead runner for the approval hold.', href: '/agents/?agent=agent-prod-02' },
      ],
      activeRuns: [
        {
          id: 'run-prod-102',
          title: 'Shadow DB saturation',
          experimentId: 'exp-shadow-db-saturation',
          service: 'shadow-db',
          status: 'Holding',
          progress: 24,
          blastRadius: 'synthetic replay only',
          agents: '2 active agents',
          startedAt: '7 min ago',
          guardrail: 'Approval is still required before wider replay promotion.',
          leadAgentId: 'agent-prod-02',
        },
      ],
      recentOutcomes: [
        {
          id: 'result-prod-208',
          title: 'Shadow DB saturation rehearsal',
          status: 'Stopped',
          recovery: '56s',
          finishedAt: '41 min ago',
          impact: 'Read latency approached the promotion threshold',
          experimentId: 'exp-shadow-db-saturation',
          summary: 'The shadow replay was stopped before a threshold breach.',
        },
      ],
      failedRuns: [],
      agents: [
        {
          id: 'agent-prod-02',
          name: 'agent-prod-02',
          status: 'Guarded',
          role: 'Synthetic replay runner',
          zone: 'us-east-1c',
          heartbeat: '7s lag',
          healthScore: 95,
          load: '1 guarded drill',
          linkedRunId: 'run-prod-102',
          summary: 'Healthy node, but workload stays approval gated.',
        },
        {
          id: 'agent-prod-04',
          name: 'agent-prod-04',
          status: 'Healthy',
          role: 'Metrics collector',
          zone: 'us-east-1a',
          heartbeat: '5s lag',
          healthScore: 97,
          load: 'idle',
          linkedRunId: 'run-prod-102',
          summary: 'Collector is idle and ready if the shadow replay expands.',
        },
      ],
    },
    {
      summary: {
        recentRuns: 4,
        recentNote: 'Approval state stayed intact while one stopped shadow result aged out.',
        failedRuns: 0,
        failedNote: 'Shadow remains clear of failed drills.',
        healthyAgents: 6,
        totalAgents: 6,
        agentNote: 'Healthy fleet with one guarded runner still paused by approval.',
      },
      spotlight:
        'Refresh only nudged the approval-hold progress here, but that is enough for an operator to see the dashboard timestamp and state controls working together.',
      watchlist: [
        { label: 'Approval hold still blocks wider shadow replay.', href: '/live-runs/?run=run-prod-102' },
        { label: 'The guarded runner remains the key agent to inspect before promotion.', href: '/agents/?agent=agent-prod-02' },
      ],
      activeRuns: [
        {
          id: 'run-prod-102',
          title: 'Shadow DB saturation',
          experimentId: 'exp-shadow-db-saturation',
          service: 'shadow-db',
          status: 'Holding',
          progress: 31,
          blastRadius: 'synthetic replay only',
          agents: '2 active agents',
          startedAt: '10 min ago',
          guardrail: 'Approval still blocks promotion to the wider lane.',
          leadAgentId: 'agent-prod-02',
        },
      ],
      recentOutcomes: [
        {
          id: 'result-prod-208',
          title: 'Shadow DB saturation rehearsal',
          status: 'Stopped',
          recovery: '56s',
          finishedAt: '43 min ago',
          impact: 'Read latency approached the promotion threshold',
          experimentId: 'exp-shadow-db-saturation',
          summary: 'The shadow replay was stopped before a threshold breach.',
        },
      ],
      failedRuns: [],
      agents: [
        {
          id: 'agent-prod-02',
          name: 'agent-prod-02',
          status: 'Guarded',
          role: 'Synthetic replay runner',
          zone: 'us-east-1c',
          heartbeat: '6s lag',
          healthScore: 96,
          load: '1 guarded drill',
          linkedRunId: 'run-prod-102',
          summary: 'Healthy node, workload still blocked by approval rather than fleet health.',
        },
        {
          id: 'agent-prod-04',
          name: 'agent-prod-04',
          status: 'Healthy',
          role: 'Metrics collector',
          zone: 'us-east-1a',
          heartbeat: '5s lag',
          healthScore: 97,
          load: 'idle',
          linkedRunId: 'run-prod-102',
          summary: 'Collector remains ready for a wider replay if approval lands.',
        },
      ],
    },
  ],
};

const experimentNamespaceOptions = [
  { value: 'payments', label: 'payments' },
  { value: 'shop', label: 'shop' },
  { value: 'fulfillment', label: 'fulfillment' },
  { value: 'shadow', label: 'shadow' },
  { value: 'sandbox', label: 'sandbox' },
];

const experimentTagOptions = [
  'team:payments',
  'team:platform',
  'team:orders',
  'tier:frontend',
  'tier:critical',
  'service-group:checkout',
  'service-group:inventory',
  'traffic:shadow',
];

const experimentRolloutOptions = [
  { value: 'two-pod-canary', label: 'Two-pod canary' },
  { value: 'single-service-slice', label: 'Single service slice' },
  { value: 'namespace-wave', label: 'Namespace wave' },
  { value: 'synthetic-shadow-only', label: 'Synthetic shadow only' },
];

const experimentBuilderTemplates = [
  {
    id: 'exp-checkout-latency',
    title: 'Checkout latency envelope',
    status: 'Guarded',
    description: 'Inject fixed latency into checkout traffic before the wider staging replay opens up.',
    target: {
      service: 'checkout-api',
      namespace: 'payments',
      tags: ['team:payments', 'tier:frontend', 'service-group:checkout'],
      environments: ['staging-west'],
    },
    fault: {
      type: 'latency',
      latencyMs: 350,
      statusCode: '500',
      injectionRate: 30,
    },
    safety: {
      durationMinutes: 8,
      allowlist: ['staging-west'],
      approvalRequired: true,
      guardrail: 'Abort if 5xx stays above 2.5% for 90 seconds.',
      rollout: 'two-pod-canary',
    },
  },
  {
    id: 'exp-catalog-cpu',
    title: 'Catalog fallback latency check',
    status: 'Stable',
    description: 'Use a smaller fixed latency profile to verify catalog fallback paths without widening the blast radius.',
    target: {
      service: 'catalog-api',
      namespace: 'shop',
      tags: ['team:platform', 'tier:critical'],
      environments: ['local-dev', 'staging-west'],
    },
    fault: {
      type: 'latency',
      latencyMs: 220,
      statusCode: '500',
      injectionRate: 20,
    },
    safety: {
      durationMinutes: 6,
      allowlist: ['local-dev', 'staging-west'],
      approvalRequired: false,
      guardrail: 'Abort if checkout p95 crosses 400ms for two consecutive checks.',
      rollout: 'single-service-slice',
    },
  },
  {
    id: 'exp-inventory-packet-loss',
    title: 'Inventory gateway 503 surge',
    status: 'Draft',
    description: 'Shape an HTTP-failure experiment that returns 503s while consumer lag recovery is observed.',
    target: {
      service: 'inventory-gateway',
      namespace: 'fulfillment',
      tags: ['team:orders', 'service-group:inventory'],
      environments: ['staging-west'],
    },
    fault: {
      type: 'http-error',
      latencyMs: 180,
      statusCode: '503',
      injectionRate: 18,
    },
    safety: {
      durationMinutes: 10,
      allowlist: ['staging-west'],
      approvalRequired: true,
      guardrail: 'Abort if consumer lag remains above 180 seconds after rollback.',
      rollout: 'single-service-slice',
    },
  },
  {
    id: 'exp-shadow-db-saturation',
    title: 'Shadow API 500 fallback probe',
    status: 'Approval gated',
    description: 'Return HTTP 500s on shadow traffic only so rollback speed can be verified before replay promotion.',
    target: {
      service: 'shadow-api',
      namespace: 'shadow',
      tags: ['team:platform', 'traffic:shadow'],
      environments: ['prod-shadow'],
    },
    fault: {
      type: 'http-error',
      latencyMs: 120,
      statusCode: '500',
      injectionRate: 12,
    },
    safety: {
      durationMinutes: 5,
      allowlist: ['prod-shadow'],
      approvalRequired: true,
      guardrail: 'Stop if read latency exceeds 220ms for longer than one minute.',
      rollout: 'synthetic-shadow-only',
    },
  },
  {
    id: 'exp-local-queue-backlog',
    title: 'Local queue latency rehearsal',
    status: 'Sandbox',
    description: 'Keep the rehearsal path simple while validating the builder flow against the local environment.',
    target: {
      service: 'local-queue',
      namespace: 'sandbox',
      tags: ['team:platform'],
      environments: ['local-dev'],
    },
    fault: {
      type: 'latency',
      latencyMs: 150,
      statusCode: '500',
      injectionRate: 40,
    },
    safety: {
      durationMinutes: 4,
      allowlist: ['local-dev'],
      approvalRequired: false,
      guardrail: 'Abort if end-to-end queue wait grows above six minutes.',
      rollout: 'single-service-slice',
    },
  },
];

const previewState = {
  list: 'loading',
  detail: 'empty',
};

const builderState = getInitialExperimentBuilderState();

const dashboardState = {
  autoRefreshEnabled: localStorage.getItem(AUTO_REFRESH_KEY) !== 'off',
  refreshCount: 0,
  lastRefreshAt: new Date(),
  refreshing: false,
};

const routeKey = routes[document.body.dataset.route] ? document.body.dataset.route : 'dashboard';
const sidebarNode = document.getElementById('sidebar');
const contentNode = document.getElementById('content');

document.addEventListener('change', (event) => {
  const target = event.target;

  if (target instanceof HTMLSelectElement && target.id === 'environment-select') {
    localStorage.setItem(STORAGE_KEY, target.value);
    render();
    return;
  }

  if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement) {
    if (handleExperimentBuilderFieldChange(target)) {
      renderContent();
    }
  }
});

document.addEventListener('click', (event) => {
  const target = event.target;

  if (!(target instanceof HTMLElement)) {
    return;
  }

  const stateButton = target.closest('[data-surface][data-state]');

  if (stateButton instanceof HTMLElement) {
    previewState[stateButton.dataset.surface] = stateButton.dataset.state;
    renderContent();
    return;
  }

  const actionButton = target.closest('[data-action]');

  if (!(actionButton instanceof HTMLElement)) {
    return;
  }

  if (actionButton.dataset.action === 'select-experiment-draft') {
    selectExperimentDraft(actionButton.dataset.draftId);
    renderContent();
    return;
  }

  if (actionButton.dataset.action === 'create-experiment-draft') {
    createExperimentDraft();
    renderContent();
    return;
  }

  if (actionButton.dataset.action === 'duplicate-experiment-draft') {
    duplicateExperimentDraft();
    renderContent();
    return;
  }

  if (actionButton.dataset.action === 'save-experiment-draft') {
    saveExperimentDraft();
    renderContent();
    return;
  }

  if (actionButton.dataset.action === 'refresh-dashboard') {
    triggerDashboardRefresh();
    return;
  }

  if (actionButton.dataset.action === 'toggle-auto-refresh') {
    dashboardState.autoRefreshEnabled = !dashboardState.autoRefreshEnabled;
    localStorage.setItem(AUTO_REFRESH_KEY, dashboardState.autoRefreshEnabled ? 'on' : 'off');
    renderContent();
  }
});

if (routeKey === 'dashboard') {
  window.setInterval(() => {
    if (dashboardState.autoRefreshEnabled && !dashboardState.refreshing) {
      triggerDashboardRefresh();
    }
  }, DASHBOARD_REFRESH_MS);
}

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
      <p class="sidebar-copy">Distributed resilience lab for controlled failures, live telemetry, and historical review.</p>
    </div>

    <nav class="nav" aria-label="Primary navigation">
      ${primaryRouteKeys
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

  if (routeKey === 'dashboard') {
    contentNode.innerHTML = renderDashboard(environment, route);
    return;
  }

  if (routeKey === 'experiments') {
    contentNode.innerHTML = renderExperimentsRoute(environment, route);
    return;
  }

  if (routeKey === 'live-runs') {
    contentNode.innerHTML = renderLiveRuns(environment, route);
    return;
  }

  contentNode.innerHTML = renderStandardRoute(environment, route);
}

function renderDashboard(environment, route) {
  const snapshot = getDashboardSnapshot(environment.id);
  const metrics = getDashboardMetrics(snapshot);

  return `
    ${renderTopbar(environment)}

    <section class="page">
      <header class="hero-card dashboard-hero">
        <div class="hero-copy">
          <p class="hero-kicker">${route.eyebrow}</p>
          <h2>${route.title}</h2>
          <p>${route.description}</p>
          <p class="hero-emphasis">${snapshot.spotlight}</p>
        </div>
        <div class="hero-actions">
          <div class="hero-context refresh-context">
            <span>Last updated</span>
            <strong>${formatTime(dashboardState.lastRefreshAt)}</strong>
            <p class="hero-context-copy">
              ${dashboardState.autoRefreshEnabled ? 'Auto refresh every 15s' : 'Manual refresh only'}
            </p>
          </div>
          <div class="refresh-actions">
            <button
              type="button"
              class="primary-button"
              data-action="refresh-dashboard"
              ${dashboardState.refreshing ? 'disabled' : ''}
            >
              ${dashboardState.refreshing ? 'Refreshing...' : 'Refresh now'}
            </button>
            <button type="button" class="ghost-button" data-action="toggle-auto-refresh">
              ${dashboardState.autoRefreshEnabled ? 'Pause auto refresh' : 'Resume auto refresh'}
            </button>
          </div>
        </div>
      </header>

      <section class="metric-grid dashboard-metrics" aria-label="Dashboard overview metrics">
        ${metrics
          .map(
            (metric) => `
              <a class="metric-card metric-link" href="${metric.href}">
                <p class="metric-label">${metric.label}</p>
                <h3>${metric.value}</h3>
                <p class="metric-note">${metric.note}</p>
              </a>
            `,
          )
          .join('')}
      </section>

      <section class="section-shell">
        <div class="section-heading">
          <div>
            <p class="section-label">Live execution</p>
            <h2>Active runs</h2>
          </div>
          <p class="section-copy">Each card keeps the linked run, experiment, and lead-agent detail views one click away.</p>
        </div>
        <div class="run-grid">
          ${snapshot.activeRuns.map(renderRunCard).join('')}
        </div>
      </section>

      <section class="dashboard-grid">
        <article class="surface-card dashboard-panel">
          <div class="section-heading compact">
            <div>
              <p class="section-label">Recent outcomes</p>
              <h3>Most recent completed drills</h3>
            </div>
          </div>
          <div class="dashboard-list">
            ${snapshot.recentOutcomes.map(renderOutcomeCard).join('')}
          </div>
        </article>

        <article class="surface-card dashboard-panel">
          <div class="section-heading compact">
            <div>
              <p class="section-label">Failed runs</p>
              <h3>Runs that still need action</h3>
            </div>
          </div>
          ${
            snapshot.failedRuns.length
              ? `<div class="dashboard-list">${snapshot.failedRuns.map(renderFailureCard).join('')}</div>`
              : `
                <div class="empty-panel">
                  <p class="preview-title">No failed runs in the current window</p>
                  <p class="preview-copy">${snapshot.summary.failedNote}</p>
                </div>
              `
          }
        </article>
      </section>

      <section class="section-shell">
        <div class="section-heading">
          <div>
            <p class="section-label">Agent health overview</p>
            <h2>Fleet health and watchpoints</h2>
          </div>
          <p class="section-copy">Health cards land on a dedicated fleet detail route instead of trapping the user in the dashboard.</p>
        </div>

        <div class="dashboard-grid">
          <article class="surface-card dashboard-panel">
            <div class="dashboard-list">
              ${snapshot.agents.map(renderAgentCard).join('')}
            </div>
          </article>

          <article class="surface-card dashboard-panel">
            <div class="section-heading compact">
              <div>
                <p class="section-label">Operator watchlist</p>
                <h3>What to keep in frame</h3>
              </div>
            </div>
            <ul class="detail-list">
              ${snapshot.watchlist
                .map((item) => `<li><a class="resource-link" href="${item.href}">${item.label}</a></li>`)
                .join('')}
            </ul>
          </article>
        </div>
      </section>
    </section>
  `;
}

function renderExperimentsRoute(environment, route) {
  const draft = getActiveExperimentDraft();
  const warnings = getExperimentWarnings(draft);
  const builderMetrics = getExperimentBuilderMetrics(draft, warnings);

  return `
    ${renderTopbar(environment)}

    <section class="page">
      <header class="hero-card">
        <div class="hero-copy">
          <p class="hero-kicker">${route.eyebrow}</p>
          <h2>${route.title}</h2>
          <p>${route.description}</p>
          <p class="hero-emphasis">${escapeHtml(getExperimentNarrative(draft))}</p>
        </div>
        <div class="hero-actions builder-hero-actions">
          <div class="hero-context">
            <span>Active draft</span>
            <strong>${escapeHtml(draft.title)}</strong>
            <p class="hero-context-copy">${escapeHtml(getExperimentSaveCopy(warnings))}</p>
          </div>
          <div class="refresh-actions">
            <button type="button" class="ghost-button" data-action="create-experiment-draft">New draft</button>
            <button type="button" class="ghost-button" data-action="duplicate-experiment-draft">Duplicate draft</button>
            <button
              type="button"
              class="primary-button"
              data-action="save-experiment-draft"
              ${warnings.length > 0 ? 'disabled' : ''}
            >
              Save draft
            </button>
          </div>
          <div class="builder-save-strip">
            ${renderStatusChip(draft.status)}
            <span class="context-pill">${escapeHtml(getExperimentSaveStateLabel())}</span>
          </div>
        </div>
      </header>

      <section class="metric-grid" aria-label="${route.navLabel} builder metrics">
        ${builderMetrics
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

      <section class="builder-shell">
        <article class="surface-card builder-sidebar">
          <div class="surface-header">
            <div>
              <p class="surface-label">Template catalog</p>
              <h3>Start from a saved experiment or branch a fresh draft</h3>
            </div>
            <span class="context-pill">${padMetric(builderState.drafts.length)} drafts</span>
          </div>
          <div class="builder-catalog">
            ${builderState.drafts.map((builderDraft) => renderExperimentDraftCard(builderDraft)).join('')}
          </div>
        </article>

        <div class="builder-main">
          <article class="surface-card builder-workspace">
            <div class="surface-header">
              <div>
                <p class="surface-label">Experiment composer</p>
                <h3>${escapeHtml(draft.title)}</h3>
              </div>
              <div class="builder-header-meta">
                ${renderStatusChip(draft.status)}
                <span class="context-pill">Editing ${escapeHtml(draft.target.service || 'unspecified service')}</span>
              </div>
            </div>

            <div class="builder-form-grid">
              <section class="builder-section">
                <div class="section-heading compact">
                  <div>
                    <p class="section-label">Identity</p>
                    <h3>Name and describe the draft</h3>
                  </div>
                </div>
                <div class="builder-field-grid">
                  <label class="builder-field">
                    <span class="field-label">Experiment name</span>
                    <input class="builder-input" type="text" data-draft-field="title" value="${escapeHtml(draft.title)}" />
                  </label>
                  <label class="builder-field builder-field-wide">
                    <span class="field-label">Operator context</span>
                    <input
                      class="builder-input"
                      type="text"
                      data-draft-field="description"
                      value="${escapeHtml(draft.description)}"
                    />
                  </label>
                </div>
              </section>

              <section class="builder-section">
                <div class="section-heading compact">
                  <div>
                    <p class="section-label">Target selector</p>
                    <h3>Scope the service, tags, namespace, and environments</h3>
                  </div>
                </div>
                <div class="builder-field-grid">
                  <label class="builder-field">
                    <span class="field-label">Service name</span>
                    <input
                      class="builder-input"
                      type="text"
                      data-draft-field="target.service"
                      value="${escapeHtml(draft.target.service)}"
                    />
                  </label>
                  <label class="builder-field">
                    <span class="field-label">Namespace</span>
                    <select class="builder-input" data-draft-field="target.namespace">
                      ${experimentNamespaceOptions
                        .map(
                          (option) => `
                            <option value="${option.value}" ${option.value === draft.target.namespace ? 'selected' : ''}>
                              ${option.label}
                            </option>
                          `,
                        )
                        .join('')}
                    </select>
                  </label>
                </div>
                <div class="builder-choice-block">
                  <p class="field-label">Target environments</p>
                  ${renderChipGroup(environments, draft.target.environments, {
                    arrayField: 'target.environments',
                    getValue: (item) => item.id,
                    getLabel: (item) => item.name,
                  })}
                </div>
                <div class="builder-choice-block">
                  <p class="field-label">Service tags</p>
                  ${renderChipGroup(experimentTagOptions, draft.target.tags, {
                    arrayField: 'target.tags',
                    getValue: (item) => item,
                    getLabel: (item) => item,
                  })}
                </div>
              </section>

              <section class="builder-section">
                <div class="section-heading compact">
                  <div>
                    <p class="section-label">Fault configuration</p>
                    <h3>Choose fixed latency or an HTTP 500/503 failure mode</h3>
                  </div>
                </div>
                <div class="builder-choice-block">
                  <p class="field-label">Fault type</p>
                  ${renderRadioGroup(
                    'fault.type',
                    draft.fault.type,
                    [
                      { value: 'latency', label: 'Fixed latency' },
                      { value: 'http-error', label: 'HTTP error' },
                    ],
                  )}
                </div>
                <div class="builder-field-grid">
                  ${
                    draft.fault.type === 'latency'
                      ? `
                        <label class="builder-field">
                          <span class="field-label">Latency (ms)</span>
                          <input
                            class="builder-input"
                            type="number"
                            min="25"
                            step="25"
                            data-draft-field="fault.latencyMs"
                            value="${draft.fault.latencyMs}"
                          />
                        </label>
                      `
                      : `
                        <div class="builder-field">
                          <span class="field-label">HTTP status</span>
                          ${renderRadioGroup(
                            'fault.statusCode',
                            String(draft.fault.statusCode),
                            [
                              { value: '500', label: 'HTTP 500' },
                              { value: '503', label: 'HTTP 503' },
                            ],
                          )}
                        </div>
                      `
                  }
                  <label class="builder-field">
                    <span class="field-label">Traffic share (%)</span>
                    <input
                      class="builder-input"
                      type="number"
                      min="1"
                      max="100"
                      data-draft-field="fault.injectionRate"
                      value="${draft.fault.injectionRate}"
                    />
                  </label>
                </div>
              </section>

              <section class="builder-section">
                <div class="section-heading compact">
                  <div>
                    <p class="section-label">Safety constraints</p>
                    <h3>Keep guardrails and environment allowlists editable before save</h3>
                  </div>
                </div>
                <div class="builder-field-grid">
                  <label class="builder-field">
                    <span class="field-label">Duration limit (minutes)</span>
                    <input
                      class="builder-input"
                      type="number"
                      min="1"
                      max="30"
                      data-draft-field="safety.durationMinutes"
                      value="${draft.safety.durationMinutes}"
                    />
                  </label>
                  <label class="builder-field">
                    <span class="field-label">Rollout strategy</span>
                    <select class="builder-input" data-draft-field="safety.rollout">
                      ${experimentRolloutOptions
                        .map(
                          (option) => `
                            <option value="${option.value}" ${option.value === draft.safety.rollout ? 'selected' : ''}>
                              ${option.label}
                            </option>
                          `,
                        )
                        .join('')}
                    </select>
                  </label>
                  <label class="builder-field builder-field-wide">
                    <span class="field-label">Guardrail / stop condition</span>
                    <input
                      class="builder-input"
                      type="text"
                      data-draft-field="safety.guardrail"
                      value="${escapeHtml(draft.safety.guardrail)}"
                    />
                  </label>
                </div>
                <div class="builder-choice-block">
                  <p class="field-label">Environment allowlist</p>
                  ${renderChipGroup(environments, draft.safety.allowlist, {
                    arrayField: 'safety.allowlist',
                    getValue: (item) => item.id,
                    getLabel: (item) => item.name,
                  })}
                </div>
                <label class="builder-toggle">
                  <input
                    type="checkbox"
                    data-draft-field="safety.approvalRequired"
                    ${draft.safety.approvalRequired ? 'checked' : ''}
                  />
                  <span>Require explicit approval before this draft can run outside sandbox scope.</span>
                </label>
              </section>
            </div>
          </article>

          <div class="surface-grid builder-support-grid">
            <article class="surface-card">
              <div class="surface-header">
                <div>
                  <p class="surface-label">Save checklist</p>
                  <h3>${warnings.length === 0 ? 'Draft is ready to save' : 'Resolve safety gaps before save'}</h3>
                </div>
                ${renderStatusChip(warnings.length === 0 ? 'Stable' : 'Draft')}
              </div>
              <div class="builder-summary-stack">
                <div class="linked-fact-grid builder-fact-grid">
                  <div class="linked-fact">
                    <span>Target selector</span>
                    <strong>${escapeHtml(getExperimentTargetSummary(draft))}</strong>
                  </div>
                  <div class="linked-fact">
                    <span>Fault profile</span>
                    <strong>${escapeHtml(getExperimentFaultSummary(draft))}</strong>
                  </div>
                  <div class="linked-fact">
                    <span>Duration</span>
                    <strong>${draft.safety.durationMinutes} minute max</strong>
                  </div>
                  <div class="linked-fact">
                    <span>Allowlist</span>
                    <strong>${escapeHtml(formatEnvironmentNames(draft.safety.allowlist))}</strong>
                  </div>
                </div>
                ${
                  warnings.length === 0
                    ? `
                      <div class="builder-callout">
                        <p class="preview-title">Safety posture aligned</p>
                        <p class="preview-copy">Target environments, allowlist coverage, and the selected fault mode are all ready for a save.</p>
                      </div>
                    `
                    : `
                      <ul class="detail-list">
                        ${warnings.map((warning) => `<li>${escapeHtml(warning)}</li>`).join('')}
                      </ul>
                    `
                }
              </div>
            </article>

            <article class="surface-card">
              <div class="surface-header">
                <div>
                  <p class="surface-label">Payload preview</p>
                  <h3>What the save request would contain</h3>
                </div>
                <span class="context-pill">${escapeHtml(draft.fault.type === 'latency' ? 'Latency' : 'HTTP error')}</span>
              </div>
              <pre class="builder-code"><code>${escapeHtml(JSON.stringify(getExperimentPayloadPreview(draft), null, 2))}</code></pre>
            </article>
          </div>
        </div>
      </section>
    </section>
  `;
}

function renderLiveRuns(environment, route) {
  const view = getLiveRunView(environment);
  const selectedRun = view.selected;
  const selectedAgents = selectedRun ? buildLiveRunAgents(selectedRun) : [];
  const selectedTimeline = selectedRun ? buildLiveRunTimeline(selectedRun) : [];
  const metrics = getLiveRunMetrics(view, selectedAgents);

  return `
    ${renderTopbar(environment)}

    <section class="page">
      <header class="hero-card live-hero">
        <div class="hero-copy">
          <p class="hero-kicker">${route.eyebrow}</p>
          <h2>${route.title}</h2>
          <p>${route.description}</p>
          <p class="hero-emphasis">
            ${selectedRun ? escapeHtml(selectedRun.summary) : route.detailEmptyMessage}
          </p>
          ${view.banner ? `<p class="live-route-note">${escapeHtml(view.banner)}</p>` : ''}
        </div>
        <div class="hero-actions">
          <div class="hero-context">
            <span>Selected run</span>
            <strong>${selectedRun ? escapeHtml(selectedRun.title) : 'No run selected'}</strong>
            <p class="hero-context-copy">
              ${selectedRun ? escapeHtml(selectedRun.targetSnapshot) : route.listEmptyMessage}
            </p>
          </div>
          <div class="hero-context">
            <span>Last updated</span>
            <strong>${formatTime(liveRunsState.lastUpdatedAt)}</strong>
            <p class="hero-context-copy">
              ${liveRunsState.refreshing ? 'Refreshing stubbed run state.' : 'Stubbed live pulse updates every 10 seconds.'}
            </p>
          </div>
          <div class="hero-context">
            <span>Stop control</span>
            <strong>${selectedRun ? escapeHtml(selectedRun.stopLabel) : 'Unavailable'}</strong>
            <p class="hero-context-copy">
              ${selectedRun ? escapeHtml(selectedRun.stopNote) : 'Select or create a run to issue a stop.'}
            </p>
          </div>
        </div>
      </header>

      <section class="metric-grid" aria-label="${route.navLabel} summary metrics">
        ${metrics
          .map(
            (metric) => `
              <article class="metric-card">
                <p class="metric-label">${metric.label}</p>
                <h3>${escapeHtml(metric.value)}</h3>
                <p class="metric-note">${escapeHtml(metric.note)}</p>
              </article>
            `,
          )
          .join('')}
      </section>

      <section class="dashboard-grid live-run-shell">
        <article class="surface-card live-run-list-surface">
          <div class="surface-header">
            <div>
              <p class="surface-label">Streaming run list</p>
              <h3>Runs in ${escapeHtml(environment.name)}</h3>
            </div>
            <button
              type="button"
              class="ghost-button"
              data-action="refresh-live-runs"
              ${liveRunsState.refreshing ? 'disabled' : ''}
            >
              ${liveRunsState.refreshing ? 'Refreshing...' : 'Refresh now'}
            </button>
          </div>
          <div class="surface-body live-run-list-body">
            ${renderLiveRunList(view, route)}
          </div>
        </article>

        <article class="surface-card live-run-detail-surface">
          <div class="surface-header">
            <div>
              <p class="surface-label">Run detail</p>
              <h3>${selectedRun ? escapeHtml(selectedRun.title) : route.detailSurfaceTitle}</h3>
            </div>
            ${selectedRun ? renderStatusChip(selectedRun.statusLabel) : ''}
          </div>
          <div class="surface-body live-run-detail-body">
            ${renderLiveRunDetail(selectedRun, route)}
          </div>
        </article>
      </section>

      <section class="dashboard-grid live-run-shell">
        <article class="surface-card live-run-agent-surface">
          <div class="surface-header">
            <div>
              <p class="surface-label">Per-agent execution</p>
              <h3>Progress by execution lane</h3>
            </div>
            ${selectedRun ? `<span class="context-pill">${escapeHtml(String(selectedAgents.length))} agents in frame</span>` : ''}
          </div>
          <div class="surface-body live-run-agents-body">
            ${renderLiveRunAgents(selectedAgents)}
          </div>
        </article>

        <article class="surface-card live-run-timeline-surface">
          <div class="surface-header">
            <div>
              <p class="surface-label">Execution timeline</p>
              <h3>Fault phase and rollback path</h3>
            </div>
            ${selectedRun ? `<span class="context-pill">${escapeHtml(selectedRun.phase)}</span>` : ''}
          </div>
          <div class="surface-body live-run-timeline-body">
            ${renderLiveRunTimeline(selectedTimeline, route)}
          </div>
        </article>
      </section>
    </section>
  `;
}

function renderLiveRunList(view, route) {
  if (liveRunsState.refreshing && liveRunsState.refreshCount === 0) {
    return `
      <div class="preview-stack" aria-label="Live run list loading">
        <div class="skeleton-panel"></div>
        <div class="skeleton-panel"></div>
        <div class="skeleton-panel"></div>
      </div>
    `;
  }

  if (!view.list.length) {
    return `
      <div class="preview-message">
        <p class="preview-title">No runs in scope</p>
        <p class="preview-copy">${escapeHtml(route.listEmptyMessage)}</p>
        <button type="button" class="ghost-button" data-action="refresh-live-runs">Refresh preview</button>
      </div>
    `;
  }

  return `
    <div class="live-run-list">
      ${view.list
        .map((run) => renderLiveRunListItem(run, view.selected && view.selected.id === run.id))
        .join('')}
    </div>
  `;
}

function renderLiveRunListItem(run, isSelected) {
  return `
    <a class="live-run-item${isSelected ? ' selected' : ''}" href="/live-runs/?run=${encodeURIComponent(run.id)}">
      <div class="run-card-header">
        <div>
          <p class="run-card-eyebrow">${escapeHtml(run.sourceLabel)}</p>
          <h3>${escapeHtml(run.title)}</h3>
        </div>
        ${renderStatusChip(run.statusLabel)}
      </div>
      <div class="meta-row">
        <span>${escapeHtml(run.environmentLabel)}</span>
        <span>${escapeHtml(run.runtimeLabel)}</span>
      </div>
      <p class="run-note">${escapeHtml(run.targetSnapshot)}</p>
      <div class="link-row">
        <span>${escapeHtml(run.phase)}</span>
        <span>${escapeHtml(run.stopLabel)}</span>
      </div>
    </a>
  `;
}

function renderLiveRunDetail(run, route) {
  if (!run) {
    return `
      <div class="preview-message">
        <p class="preview-title">No detail selected</p>
        <p class="preview-copy">${route.detailEmptyMessage}</p>
      </div>
    `;
  }

  const facts = buildLiveRunFacts(run);

  return `
    <article class="linked-detail live-run-detail-card">
      ${liveRunsState.stopFeedback ? `<div class="live-callout success">${escapeHtml(liveRunsState.stopFeedback)}</div>` : ''}
      ${liveRunsState.stopError ? `<div class="live-callout error">${escapeHtml(liveRunsState.stopError)}</div>` : ''}
      <p class="linked-detail-copy">${escapeHtml(run.summary)}</p>
      <div class="linked-fact-grid live-fact-grid">
        ${facts
          .map(
            (fact) => `
              <div class="linked-fact">
                <span>${escapeHtml(fact.label)}</span>
                <strong>${escapeHtml(fact.value)}</strong>
              </div>
            `,
          )
          .join('')}
      </div>
      ${
        run.bullets.length
          ? `
            <ul class="detail-list">
              ${run.bullets.map((item) => `<li>${escapeHtml(item)}</li>`).join('')}
            </ul>
          `
          : ''
      }
      <div class="link-row">
        ${run.links.map((link) => `<a class="resource-link" href="${link.href}">${escapeHtml(link.label)}</a>`).join('')}
      </div>
      ${renderStopControl(run)}
    </article>
  `;
}

function renderStopControl(run) {
  if (run.stopCommandIssuedAt) {
    return `
      <section class="live-stop-panel live-stop-panel-confirmed">
        <div class="section-heading compact">
          <div>
            <p class="section-label">Stop control</p>
            <h3>Stop confirmation captured</h3>
          </div>
          ${renderStatusChip('Stopped')}
        </div>
        <p class="preview-copy">
          Stop requested by ${escapeHtml(run.stopCommandIssuedBy || 'unknown operator')}
          ${run.stopCommandIssuedAt ? ` at ${escapeHtml(formatDateTime(run.stopCommandIssuedAt))}` : ''}.
        </p>
        <p class="preview-copy">${escapeHtml(run.stopCommandReason || 'No operator reason was recorded.')}</p>
      </section>
    `;
  }

  return `
    <section class="live-stop-panel">
      <div class="section-heading compact">
        <div>
          <p class="section-label">Stop control</p>
          <h3>Request rollback now</h3>
        </div>
        ${renderStatusChip('Running')}
      </div>
      <div class="live-control-grid">
        <label class="live-control-field">
          <span class="field-label">Operator</span>
          <input
            type="text"
            data-live-run-field="operator"
            value="${escapeHtml(liveRunsState.operator)}"
            autocomplete="off"
          />
        </label>
        <label class="live-control-field">
          <span class="field-label">Reason</span>
          <textarea data-live-run-field="reason" rows="3">${escapeHtml(liveRunsState.reason)}</textarea>
        </label>
      </div>
      <div class="refresh-actions">
        <button
          type="button"
          class="primary-button danger-button"
          data-action="stop-live-run"
          data-run-id="${run.id}"
          ${liveRunsState.stopPendingId === run.id ? 'disabled' : ''}
        >
          ${liveRunsState.stopPendingId === run.id ? 'Requesting stop...' : 'Stage stop confirmation'}
        </button>
        <button type="button" class="ghost-button" data-action="refresh-live-runs">Refresh status</button>
      </div>
      <p class="preview-copy">
        Uses stubbed interaction state so the confirmation path is visible before the backend timeline contract lands.
      </p>
    </section>
  `;
}

function renderLiveRunAgents(agentCards) {
  if (!agentCards.length) {
    return `
      <div class="preview-message">
        <p class="preview-title">No agents to show</p>
        <p class="preview-copy">Select a run to inspect assigned, injecting, stopped, completed, or failed execution lanes.</p>
      </div>
    `;
  }

  return `
    <div class="live-agent-grid">
      ${agentCards
        .map(
          (agent) => `
            <article class="agent-card live-agent-card">
              <div class="list-card-header">
                <div>
                  <p class="run-card-eyebrow">${escapeHtml(agent.role)}</p>
                  <h3>${escapeHtml(agent.name)}</h3>
                </div>
                ${renderStatusChip(agent.status)}
              </div>
              <div class="health-row">
                <div class="health-track">
                  <span style="width: ${agent.progress}%;"></span>
                </div>
                <span>${escapeHtml(String(agent.progress))}% complete</span>
              </div>
              <p class="run-note">${escapeHtml(agent.summary)}</p>
            </article>
          `,
        )
        .join('')}
    </div>
  `;
}

function renderLiveRunTimeline(timelineItems, route) {
  if (!timelineItems.length) {
    return `
      <div class="preview-message">
        <p class="preview-title">Timeline unavailable</p>
        <p class="preview-copy">${route.detailEmptyMessage}</p>
      </div>
    `;
  }

  return `
    <ol class="timeline-list">
      ${timelineItems
        .map(
          (item) => `
            <li class="timeline-item ${toToken(item.state)}">
              <div class="timeline-marker"></div>
              <div class="timeline-copy">
                <div class="list-card-header">
                  <div>
                    <p class="run-card-eyebrow">${escapeHtml(item.kicker)}</p>
                    <h3>${escapeHtml(item.title)}</h3>
                  </div>
                  ${renderStatusChip(item.state)}
                </div>
                <p class="run-note">${escapeHtml(item.summary)}</p>
              </div>
            </li>
          `,
        )
        .join('')}
    </ol>
  `;
}

function getLiveRunView(environment) {
  const queryRunId = new URLSearchParams(window.location.search).get('run');
  const scenarioRuns = getScenarioRuns(environment.id);
  const selected = queryRunId ? scenarioRuns.find((run) => run.id === queryRunId) || scenarioRuns[0] : scenarioRuns[0] || null;

  return {
    banner: 'Timeline and stop flows on this screen are currently stubbed so layout and operator interactions can ship ahead of the blocked event contract.',
    list: scenarioRuns,
    selected,
  };
}

function getLiveRunMetrics(view, selectedAgents) {
  const injectingCount = selectedAgents.filter((agent) => agent.status === 'Injecting').length;
  const activeCount = selectedAgents.filter((agent) => ['Assigned', 'Injecting'].includes(agent.status)).length;
  const selectedRun = view.selected;

  return [
    {
      label: 'Runs streaming',
      value: padMetric(view.list.length),
      note: 'Stubbed run cards keep the layout stable while the backend event contract is still blocked.',
    },
    {
      label: 'Agents mid-plan',
      value: padMetric(activeCount),
      note: selectedRun
        ? `${injectingCount} injecting, ${Math.max(selectedAgents.length - injectingCount, 0)} in verification or rollback lanes.`
        : 'Select a run to inspect per-agent execution progress.',
    },
    {
      label: 'Stop control',
      value: selectedRun ? selectedRun.stopLabel : 'Unavailable',
      note: selectedRun ? selectedRun.stopNote : 'Select a run to inspect the stop confirmation path.',
    },
  ];
}

function getScenarioRuns(environmentId) {
  const scenarioConfig = linkedDetails['live-runs'];
  const prefixes = {
    'local-dev': 'run-dev-',
    'staging-west': 'run-stg-',
    'prod-shadow': 'run-prod-',
  };
  const prefix = prefixes[environmentId];

  return Object.entries(scenarioConfig.records)
    .filter(([id]) => id.startsWith(prefix))
    .map(([id, record]) => normalizeScenarioRun(id, record, environmentId));
}

function normalizeScenarioRun(id, record, environmentId) {
  const facts = Object.fromEntries(record.facts.map((fact) => [fact.label, fact.value]));
  const links = record.links || [];
  const override = liveRunsState.runOverrides[id] || {};
  const statusLabel = override.statusLabel || record.status;
  const lifecycle = override.lifecycle || toToken(statusLabel);
  const pulse = liveRunsState.refreshCount + 1;

  return {
    id,
    sourceLabel: override.sourceLabel || `Simulated pulse ${padMetric(pulse)}`,
    environmentId,
    environmentLabel: environments.find((item) => item.id === environmentId)?.name || environmentId,
    title: record.title,
    statusLabel,
    summary: override.summary || record.summary,
    targetSnapshot: `${facts.Service || record.title} / ${facts['Blast radius'] || 'targeted slice'}`,
    runtimeLabel: override.runtimeLabel || `Heartbeat ${padMetric(pulse)}`,
    phase: override.phase || facts.Phase || record.status,
    stopLabel: override.stopLabel || getStopLabel(statusLabel),
    stopNote: override.stopNote || getStopNote(statusLabel),
    bullets: override.bullets || record.bullets || [],
    links,
    lifecycle,
    faultType: inferFaultType(record.title, record.summary),
    requestedDurationSeconds: inferDurationSeconds(record.summary),
    stopCommandIssuedAt: override.stopCommandIssuedAt || null,
    stopCommandIssuedBy: override.stopCommandIssuedBy || null,
    stopCommandReason: override.stopCommandReason || null,
  };
}

function buildLiveRunFacts(run) {
  return [
    { label: 'Experiment', value: run.title },
    { label: 'Environment', value: run.environmentLabel },
    { label: 'Target snapshot', value: run.targetSnapshot },
    { label: 'Current phase', value: run.phase },
    {
      label: 'Requested duration',
      value: run.requestedDurationSeconds ? formatDuration(run.requestedDurationSeconds) : 'Scenario fixture',
    },
    { label: 'Status', value: run.statusLabel },
  ];
}

function buildLiveRunAgents(run) {
  const plans = {
    running: [
      ['Lease coordinator', 'Control plane', 'Assigned', 42, 'Keeps the run lease and stop budget current.'],
      ['Fault injector', 'Failure lane', 'Injecting', 78, `Applying ${formatFaultName(run.faultType).toLowerCase()} to the selected slice.`],
      ['Guardrail verifier', 'Safety lane', 'Completed', 100, 'Initial guardrail checks passed before injection widened.'],
      ['Rollback watcher', 'Recovery lane', 'Assigned', 26, 'Ready to converge the run back to steady state on operator stop.'],
    ],
    'stop-requested': [
      ['Lease coordinator', 'Control plane', 'Stopped', 100, 'Accepted the stop request and closed the active lease.'],
      ['Fault injector', 'Failure lane', 'Stopped', 100, 'Injection lane has been told to drain and stand down.'],
      ['Guardrail verifier', 'Safety lane', 'Completed', 100, 'Latest guardrail snapshot is preserved for the rollback record.'],
      ['Rollback watcher', 'Recovery lane', 'Assigned', 58, 'Validating that the target returns to baseline after the stop.'],
    ],
    recovering: [
      ['Lease coordinator', 'Control plane', 'Completed', 100, 'Execution ownership has shifted to recovery validation.'],
      ['Fault injector', 'Failure lane', 'Completed', 100, 'The active fault phase has already ended.'],
      ['Guardrail verifier', 'Safety lane', 'Assigned', 52, 'Watching recovery SLOs while the service settles.'],
      ['Rollback watcher', 'Recovery lane', 'Completed', 100, 'Rollback commands have already cleared.'],
    ],
    holding: [
      ['Lease coordinator', 'Control plane', 'Assigned', 33, 'Run is staged but not widening without explicit approval.'],
      ['Fault injector', 'Failure lane', 'Completed', 100, 'Injector is prepared and waiting for the policy gate.'],
      ['Guardrail verifier', 'Safety lane', 'Assigned', 24, 'Guardrail checks are pinned before the next phase can begin.'],
      ['Rollback watcher', 'Recovery lane', 'Assigned', 18, 'Rollback path is armed even though the run is paused.'],
    ],
    failed: [
      ['Lease coordinator', 'Control plane', 'Failed', 91, 'The coordinator recorded a terminal exception during the run.'],
      ['Fault injector', 'Failure lane', 'Stopped', 100, 'Injector was halted once the failure path was detected.'],
      ['Guardrail verifier', 'Safety lane', 'Completed', 100, 'Last safe checkpoint was captured before the failure.'],
      ['Rollback watcher', 'Recovery lane', 'Assigned', 61, 'Recovery actions remain active while the incident is reviewed.'],
    ],
    default: [
      ['Lease coordinator', 'Control plane', 'Assigned', 35, 'Run ownership is active in the control plane.'],
      ['Fault injector', 'Failure lane', 'Assigned', 35, 'Injector is waiting for the next execution step.'],
      ['Guardrail verifier', 'Safety lane', 'Assigned', 35, 'Guardrail checks remain attached to the run.'],
      ['Rollback watcher', 'Recovery lane', 'Assigned', 35, 'Rollback path is on standby.'],
    ],
  };
  const lifecycle = plans[run.lifecycle] ? run.lifecycle : 'default';

  return plans[lifecycle].map(([name, role, status, progress, summary]) => ({
    name,
    role,
    status,
    progress,
    summary,
  }));
}

function buildLiveRunTimeline(run) {
  if (run.lifecycle === 'stop-requested') {
    return [
      {
        kicker: run.environmentLabel,
        title: 'Run authorized',
        state: 'Completed',
        summary: `The control plane authorized ${run.title.toLowerCase()} for ${run.targetSnapshot}.`,
      },
      {
        kicker: 'Fault active',
        title: 'Injection phase started',
        state: 'Completed',
        summary: `The failure lane widened on ${run.targetSnapshot} before the operator requested a rollback.`,
      },
      {
        kicker: 'Operator action',
        title: 'Stop request accepted',
        state: 'Stopped',
        summary: run.stopCommandReason || 'A manual stop request was captured for this run.',
      },
      {
        kicker: 'Recovery',
        title: 'Rollback confirmation in progress',
        state: 'Assigned',
        summary: 'Recovery lanes are now validating that the target returns to its baseline.',
      },
    ];
  }

  if (run.lifecycle === 'holding') {
    return [
      {
        kicker: run.environmentLabel,
        title: 'Run staged',
        state: 'Completed',
        summary: `The run has been prepared for ${run.targetSnapshot}.`,
      },
      {
        kicker: 'Policy gate',
        title: 'Approval hold remains active',
        state: 'Assigned',
        summary: 'Execution is intentionally paused while approval and guardrail checks stay visible.',
      },
      {
        kicker: 'Failure lane',
        title: 'Injection is waiting',
        state: 'Assigned',
        summary: 'The injector will only widen after the policy gate is cleared.',
      },
    ];
  }

  if (run.lifecycle === 'failed') {
    return [
      {
        kicker: run.environmentLabel,
        title: 'Run authorized',
        state: 'Completed',
        summary: `The run started normally on ${run.targetSnapshot}.`,
      },
      {
        kicker: 'Failure lane',
        title: 'Execution fault detected',
        state: 'Failed',
        summary: 'The run hit an execution error and rolled into the incident path.',
      },
      {
        kicker: 'Recovery',
        title: 'Rollback remains active',
        state: 'Assigned',
        summary: 'Operators still need the stop and recovery context in frame while follow-up continues.',
      },
    ];
  }

  if (run.lifecycle === 'recovering') {
    return [
      {
        kicker: run.environmentLabel,
        title: 'Run authorized',
        state: 'Completed',
        summary: `The run completed its fault phase on ${run.targetSnapshot}.`,
      },
      {
        kicker: 'Rollback',
        title: 'Recovery commands finished',
        state: 'Completed',
        summary: 'Rollback commands were issued and cleared successfully.',
      },
      {
        kicker: 'Observation',
        title: 'Recovery verification remains active',
        state: 'Assigned',
        summary: 'Verifier lanes are still watching the service return to steady state.',
      },
    ];
  }

  return [
    {
      kicker: run.environmentLabel,
      title: 'Run authorized',
      state: 'Completed',
      summary: `The control plane opened ${run.title.toLowerCase()} for ${run.targetSnapshot}.`,
    },
    {
      kicker: 'Execution',
      title: 'Agents assigned and streaming',
      state: 'Assigned',
      summary: 'Per-agent cards show the current execution lane without leaving the live run page.',
    },
    {
      kicker: 'Failure lane',
      title: 'Fault injection underway',
      state: 'Injecting',
      summary: run.phase,
    },
    {
      kicker: 'Safety',
      title: 'Rollback path remains armed',
      state: 'Completed',
      summary: 'Stop control and recovery verification stay adjacent to the active timeline.',
    },
  ];
}

function renderStandardRoute(environment, route) {
  const linkedDetail = getLinkedDetail(routeKey);
  const detailTitle = linkedDetail ? linkedDetail.title : route.detailSurfaceTitle;

  return `
    ${renderTopbar(environment)}

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
            <h2>${linkedDetail ? 'Linked detail view from the dashboard' : 'Loading, empty, and error states for both list and detail views'}</h2>
          </div>
          <p class="section-copy">
            ${
              linkedDetail
                ? 'Dashboard links now land on a concrete run, result, experiment, or agent detail surface.'
                : 'Each route inherits the same state language so future API wiring can slot into a consistent frame.'
            }
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
                <h3>${detailTitle}</h3>
              </div>
              ${linkedDetail ? '<span class="context-pill context-pill-strong">Linked from dashboard</span>' : renderStateSwitch('detail', previewState.detail)}
            </div>
            <div class="surface-body">
              ${linkedDetail ? renderLinkedDetail(linkedDetail) : renderSurfaceMarkup('detail', previewState.detail, route)}
            </div>
          </article>
        </div>
      </section>
    </section>
  `;
}

function renderTopbar(environment) {
  return `
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
  `;
}

function renderRunCard(run) {
  return `
    <article class="run-card">
      <div class="run-card-header">
        <div>
          <p class="run-card-eyebrow">${run.service}</p>
          <h3>${run.title}</h3>
        </div>
        ${renderStatusChip(run.status)}
      </div>
      <div class="meta-row">
        <span>${run.startedAt}</span>
        <span>${run.blastRadius}</span>
        <span>${run.agents}</span>
      </div>
      <div class="progress-row">
        <div class="progress-track">
          <span style="width: ${run.progress}%;"></span>
        </div>
        <span>${run.progress}% complete</span>
      </div>
      <p class="run-note">${run.guardrail}</p>
      <div class="link-row">
        <a class="resource-link" href="/live-runs/?run=${run.id}">Open run</a>
        <a class="resource-link" href="/experiments/?experiment=${run.experimentId}">View experiment</a>
        <a class="resource-link" href="/agents/?agent=${run.leadAgentId}">Inspect agent</a>
      </div>
    </article>
  `;
}

function renderOutcomeCard(result) {
  return `
    <article class="list-card">
      <div class="list-card-header">
        <div>
          <p class="run-card-eyebrow">${result.finishedAt}</p>
          <h3>${result.title}</h3>
        </div>
        ${renderStatusChip(result.status)}
      </div>
      <div class="meta-row">
        <span>Recovery ${result.recovery}</span>
        <span>${result.impact}</span>
      </div>
      <p class="run-note">${result.summary}</p>
      <div class="link-row">
        <a class="resource-link" href="/results/?result=${result.id}">Open result</a>
        <a class="resource-link" href="/experiments/?experiment=${result.experimentId}">View experiment</a>
      </div>
    </article>
  `;
}

function renderFailureCard(result) {
  return `
    <article class="list-card list-card-alert">
      <div class="list-card-header">
        <div>
          <p class="run-card-eyebrow">Needs operator follow-up</p>
          <h3>${result.title}</h3>
        </div>
        ${renderStatusChip('Failed')}
      </div>
      <div class="meta-row">
        <span>${result.impact}</span>
      </div>
      <p class="run-note">${result.cause}</p>
      <div class="link-row">
        <a class="resource-link" href="/results/?result=${result.id}">Open failed result</a>
        <a class="resource-link" href="/experiments/?experiment=${result.experimentId}">View experiment</a>
        <a class="resource-link" href="/agents/?agent=${result.agentId}">Inspect agent</a>
      </div>
    </article>
  `;
}

function renderAgentCard(agent) {
  return `
    <article class="agent-card">
      <div class="list-card-header">
        <div>
          <p class="run-card-eyebrow">${agent.role}</p>
          <h3>${agent.name}</h3>
        </div>
        ${renderStatusChip(agent.status)}
      </div>
      <div class="meta-row">
        <span>${agent.zone}</span>
        <span>${agent.heartbeat}</span>
        <span>${agent.load}</span>
      </div>
      <div class="health-row">
        <div class="health-track">
          <span style="width: ${agent.healthScore}%;"></span>
        </div>
        <span>${agent.healthScore}% health</span>
      </div>
      <p class="run-note">${agent.summary}</p>
      <div class="link-row">
        <a class="resource-link" href="/agents/?agent=${agent.id}">Open agent detail</a>
        <a class="resource-link" href="/live-runs/?run=${agent.linkedRunId}">Linked run</a>
      </div>
    </article>
  `;
}

function renderLinkedDetail(record) {
  return `
    <article class="linked-detail">
      <div class="linked-detail-header">
        <div>
          <p class="surface-label">Selected record</p>
          <h3>${record.title}</h3>
        </div>
        ${renderStatusChip(record.status)}
      </div>
      <p class="linked-detail-copy">${record.summary}</p>
      <div class="linked-fact-grid">
        ${record.facts
          .map(
            (fact) => `
              <div class="linked-fact">
                <span>${fact.label}</span>
                <strong>${fact.value}</strong>
              </div>
            `,
          )
          .join('')}
      </div>
      <ul class="detail-list">
        ${record.bullets.map((item) => `<li>${item}</li>`).join('')}
      </ul>
      <div class="link-row">
        ${record.links.map((link) => `<a class="resource-link" href="${link.href}">${link.label}</a>`).join('')}
      </div>
    </article>
  `;
}

function renderExperimentDraftCard(draft) {
  const activeClass = draft.id === builderState.activeDraftId ? ' active' : '';

  return `
    <button
      type="button"
      class="draft-card${activeClass}"
      data-action="select-experiment-draft"
      data-draft-id="${draft.id}"
    >
      <div class="draft-card-header">
        <div>
          <p class="run-card-eyebrow">${escapeHtml(draft.target.service || 'New draft')}</p>
          <h3>${escapeHtml(draft.title)}</h3>
        </div>
        ${renderStatusChip(draft.status)}
      </div>
      <p class="draft-card-copy">${escapeHtml(draft.description)}</p>
      <div class="meta-row">
        <span>${escapeHtml(getExperimentFaultSummary(draft))}</span>
        <span>${draft.safety.durationMinutes}m max</span>
        <span>${draft.target.environments.length} env</span>
      </div>
    </button>
  `;
}

function renderChipGroup(options, selectedValues, config) {
  return `
    <div class="toggle-group">
      ${options
        .map((option) => {
          const value = config.getValue(option);
          const label = config.getLabel(option);
          const activeClass = selectedValues.includes(value) ? ' active' : '';

          return `
            <label class="toggle-chip${activeClass}">
              <input
                class="sr-only"
                type="checkbox"
                data-array-field="${config.arrayField}"
                value="${value}"
                ${selectedValues.includes(value) ? 'checked' : ''}
              />
              <span>${escapeHtml(label)}</span>
            </label>
          `;
        })
        .join('')}
    </div>
  `;
}

function renderRadioGroup(field, activeValue, options) {
  return `
    <div class="toggle-group">
      ${options
        .map((option) => {
          const activeClass = option.value === activeValue ? ' active' : '';

          return `
            <label class="toggle-chip${activeClass}">
              <input
                class="sr-only"
                type="radio"
                name="${field}"
                data-draft-field="${field}"
                value="${option.value}"
                ${option.value === activeValue ? 'checked' : ''}
              />
              <span>${option.label}</span>
            </label>
          `;
        })
        .join('')}
    </div>
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

function getDashboardMetrics(snapshot) {
  return [
    {
      label: 'Active runs',
      value: padMetric(snapshot.activeRuns.length),
      note: `${snapshot.activeRuns.length} live drill${snapshot.activeRuns.length === 1 ? '' : 's'} in scope.`,
      href: '/live-runs/',
    },
    {
      label: 'Recent outcomes',
      value: padMetric(snapshot.summary.recentRuns),
      note: snapshot.summary.recentNote,
      href: '/results/',
    },
    {
      label: 'Failed runs',
      value: padMetric(snapshot.summary.failedRuns),
      note: snapshot.summary.failedNote,
      href: snapshot.failedRuns[0] ? `/results/?result=${snapshot.failedRuns[0].id}` : '/results/',
    },
    {
      label: 'Agent health',
      value: `${snapshot.summary.healthyAgents}/${snapshot.summary.totalAgents}`,
      note: snapshot.summary.agentNote,
      href: snapshot.agents[0] ? `/agents/?agent=${snapshot.agents[0].id}` : '/agents/',
    },
  ];
}

function getDashboardSnapshot(environmentId) {
  const snapshots = dashboardSnapshots[environmentId] || dashboardSnapshots[environments[0].id];
  const index = dashboardState.refreshCount % snapshots.length;
  return snapshots[index];
}

function getLinkedDetail(currentRouteKey) {
  const detailConfig = linkedDetails[currentRouteKey];

  if (!detailConfig) {
    return null;
  }

  const detailId = new URLSearchParams(window.location.search).get(detailConfig.param);

  if (!detailId) {
    return null;
  }

  return detailConfig.records[detailId] || null;
}

function triggerLiveRunRefresh(backgroundRefresh = false) {
  if (liveRunsState.refreshing) {
    return;
  }

  liveRunsState.refreshing = true;
  liveRunsState.stopError = '';

  if (!backgroundRefresh) {
    renderContent();
  }

  window.setTimeout(() => {
    liveRunsState.refreshing = false;
    liveRunsState.refreshCount += 1;
    liveRunsState.lastUpdatedAt = new Date();
    renderContent();
  }, 350);
}

function requestLiveRunStop(runId) {
  if (liveRunsState.stopPendingId) {
    return;
  }

  const operator = liveRunsState.operator.trim();
  const reason = liveRunsState.reason.trim();

  if (!operator || !reason) {
    liveRunsState.stopError = 'Operator and reason are required before issuing a stop request.';
    liveRunsState.stopFeedback = '';
    renderContent();
    return;
  }

  liveRunsState.stopPendingId = runId;
  liveRunsState.stopFeedback = '';
  liveRunsState.stopError = '';
  renderContent();

  window.setTimeout(() => {
    liveRunsState.runOverrides[runId] = {
      statusLabel: 'Stop Requested',
      lifecycle: 'stop-requested',
      sourceLabel: 'Stubbed operator action',
      summary:
        'The stop confirmation state is now visible in the same frame as agent progress so the operator workflow can be validated before backend contracts are wired.',
      runtimeLabel: 'Stop staged just now',
      phase: 'Rollback coordination in progress',
      stopLabel: 'Issued',
      stopNote: `Stubbed stop confirmation captured for ${operator}.`,
      bullets: [
        'The stop path stays in the same frame as the live run detail to avoid a context switch during rollback.',
        'Per-agent state changes immediately so layout and hierarchy can be reviewed before real event data exists.',
      ],
      stopCommandIssuedAt: new Date().toISOString(),
      stopCommandIssuedBy: operator,
      stopCommandReason: reason,
    };
    liveRunsState.lastUpdatedAt = new Date();
    liveRunsState.stopFeedback = `Stubbed stop confirmation captured for ${operator}.`;
    liveRunsState.stopPendingId = null;
    renderContent();
  }, 450);
}

function triggerDashboardRefresh() {
  if (dashboardState.refreshing) {
    return;
  }

  dashboardState.refreshing = true;
  renderContent();

  window.setTimeout(() => {
    dashboardState.refreshCount += 1;
    dashboardState.lastRefreshAt = new Date();
    dashboardState.refreshing = false;
    renderContent();
  }, 350);
}

function renderStatusChip(label) {
  return `<span class="status-chip ${toToken(label)}">${label}</span>`;
}

function getInitialExperimentBuilderState() {
  const stored = loadSavedExperimentState();
  const drafts = stored?.drafts?.length ? stored.drafts : cloneExperimentDrafts(experimentBuilderTemplates);
  const activeDraftId = resolveActiveExperimentDraftId(drafts, stored?.activeDraftId);

  return {
    drafts,
    activeDraftId,
    lastSavedAt: stored?.lastSavedAt ? new Date(stored.lastSavedAt) : null,
    saveState: stored?.lastSavedAt ? 'saved' : 'idle',
    nextDraftNumber: drafts.length + 1,
  };
}

function loadSavedExperimentState() {
  try {
    const rawValue = localStorage.getItem(EXPERIMENT_STORAGE_KEY);

    if (!rawValue) {
      return null;
    }

    const parsed = JSON.parse(rawValue);

    if (!Array.isArray(parsed.drafts) || parsed.drafts.length === 0) {
      return null;
    }

    return parsed;
  } catch (error) {
    return null;
  }
}

function cloneExperimentDrafts(drafts) {
  return JSON.parse(JSON.stringify(drafts));
}

function resolveActiveExperimentDraftId(drafts, storedDraftId) {
  const requestedDraftId = new URLSearchParams(window.location.search).get('experiment');

  if (requestedDraftId && drafts.some((draft) => draft.id === requestedDraftId)) {
    return requestedDraftId;
  }

  if (storedDraftId && drafts.some((draft) => draft.id === storedDraftId)) {
    return storedDraftId;
  }

  return drafts[0].id;
}

function getActiveExperimentDraft() {
  return builderState.drafts.find((draft) => draft.id === builderState.activeDraftId) || builderState.drafts[0];
}

function handleExperimentBuilderFieldChange(target) {
  if (routeKey !== 'experiments') {
    return false;
  }

  if (target instanceof HTMLInputElement && target.dataset.arrayField) {
    updateActiveExperimentDraftArray(target.dataset.arrayField, target.value, target.checked);
    return true;
  }

  if (!target.dataset.draftField) {
    return false;
  }

  if (target instanceof HTMLInputElement && target.type === 'radio' && !target.checked) {
    return true;
  }

  updateActiveExperimentDraftField(target.dataset.draftField, getDraftFieldValue(target));
  return true;
}

function getDraftFieldValue(target) {
  if (target instanceof HTMLInputElement) {
    if (target.type === 'checkbox') {
      return target.checked;
    }

    if (target.type === 'number') {
      return Number(target.value);
    }
  }

  return target.value;
}

function updateActiveExperimentDraftField(path, value) {
  const activeDraft = getActiveExperimentDraft();
  const nextDraft = cloneExperimentDrafts([activeDraft])[0];
  const keys = path.split('.');
  let cursor = nextDraft;

  keys.slice(0, -1).forEach((key) => {
    cursor = cursor[key];
  });

  cursor[keys[keys.length - 1]] = value;
  replaceExperimentDraft(nextDraft);
}

function updateActiveExperimentDraftArray(path, value, checked) {
  const activeDraft = getActiveExperimentDraft();
  const nextDraft = cloneExperimentDrafts([activeDraft])[0];
  const keys = path.split('.');
  let cursor = nextDraft;

  keys.slice(0, -1).forEach((key) => {
    cursor = cursor[key];
  });

  const arrayKey = keys[keys.length - 1];
  const nextValues = new Set(cursor[arrayKey]);

  if (checked) {
    nextValues.add(value);
  } else {
    nextValues.delete(value);
  }

  cursor[arrayKey] = Array.from(nextValues);
  replaceExperimentDraft(nextDraft);
}

function replaceExperimentDraft(nextDraft) {
  builderState.drafts = builderState.drafts.map((draft) => (draft.id === nextDraft.id ? nextDraft : draft));
  builderState.saveState = 'dirty';
}

function selectExperimentDraft(draftId) {
  if (!draftId || !builderState.drafts.some((draft) => draft.id === draftId)) {
    return;
  }

  builderState.activeDraftId = draftId;
  syncExperimentLocation(draftId);
}

function createExperimentDraft() {
  const activeEnvironment = getActiveEnvironment();
  const draft = {
    id: `draft-${builderState.nextDraftNumber}`,
    title: `New experiment draft ${builderState.nextDraftNumber}`,
    status: 'Draft',
    description: 'Start from a blank service selector and tune the fault before saving.',
    target: {
      service: '',
      namespace: experimentNamespaceOptions[0].value,
      tags: [],
      environments: [activeEnvironment.id],
    },
    fault: {
      type: 'latency',
      latencyMs: 150,
      statusCode: '500',
      injectionRate: 10,
    },
    safety: {
      durationMinutes: 5,
      allowlist: [activeEnvironment.id],
      approvalRequired: activeEnvironment.id === 'prod-shadow',
      guardrail: 'Add a stop condition before saving this experiment.',
      rollout: experimentRolloutOptions[1].value,
    },
  };

  builderState.nextDraftNumber += 1;
  builderState.drafts = [draft, ...builderState.drafts];
  builderState.activeDraftId = draft.id;
  builderState.saveState = 'dirty';
  syncExperimentLocation(draft.id);
}

function duplicateExperimentDraft() {
  const source = getActiveExperimentDraft();
  const duplicate = cloneExperimentDrafts([source])[0];

  duplicate.id = `draft-${builderState.nextDraftNumber}`;
  duplicate.title = `${source.title} copy`;
  duplicate.status = 'Draft';

  builderState.nextDraftNumber += 1;
  builderState.drafts = [duplicate, ...builderState.drafts];
  builderState.activeDraftId = duplicate.id;
  builderState.saveState = 'dirty';
  syncExperimentLocation(duplicate.id);
}

function saveExperimentDraft() {
  if (getExperimentWarnings(getActiveExperimentDraft()).length > 0) {
    return;
  }

  builderState.lastSavedAt = new Date();
  builderState.saveState = 'saved';

  localStorage.setItem(
    EXPERIMENT_STORAGE_KEY,
    JSON.stringify({
      drafts: builderState.drafts,
      activeDraftId: builderState.activeDraftId,
      lastSavedAt: builderState.lastSavedAt.toISOString(),
    }),
  );
}

function syncExperimentLocation(draftId) {
  const url = new URL(window.location.href);

  url.searchParams.set('experiment', draftId);
  window.history.replaceState({}, '', `${url.pathname}${url.search}`);
}

function getExperimentWarnings(draft) {
  const warnings = [];
  const serviceName = draft.target.service.trim();
  const durationMinutes = Number(draft.safety.durationMinutes);
  const injectionRate = Number(draft.fault.injectionRate);

  if (!draft.title.trim()) {
    warnings.push('Add an experiment name before saving.');
  }

  if (!serviceName) {
    warnings.push('Choose a service name for the target selector.');
  }

  if (!draft.target.namespace) {
    warnings.push('Choose a namespace for the target selector.');
  }

  if (draft.target.tags.length === 0) {
    warnings.push('Select at least one service tag.');
  }

  if (draft.target.environments.length === 0) {
    warnings.push('Select at least one target environment.');
  }

  if (!Number.isFinite(durationMinutes) || durationMinutes < 1 || durationMinutes > 30) {
    warnings.push('Duration limit must stay between 1 and 30 minutes.');
  }

  if (!Number.isFinite(injectionRate) || injectionRate < 1 || injectionRate > 100) {
    warnings.push('Traffic share must stay between 1% and 100%.');
  }

  if (draft.fault.type === 'latency' && (!Number.isFinite(draft.fault.latencyMs) || draft.fault.latencyMs < 25)) {
    warnings.push('Latency injection must be at least 25ms.');
  }

  if (draft.fault.type === 'http-error' && !['500', '503'].includes(String(draft.fault.statusCode))) {
    warnings.push('HTTP fault selection is limited to 500 or 503.');
  }

  if (draft.safety.allowlist.length === 0) {
    warnings.push('Allowlist at least one environment before save.');
  }

  const missingAllowlistEntries = draft.target.environments.filter(
    (environmentId) => !draft.safety.allowlist.includes(environmentId),
  );

  if (missingAllowlistEntries.length > 0) {
    warnings.push(`Add ${formatEnvironmentNames(missingAllowlistEntries)} to the safety allowlist.`);
  }

  if (!draft.safety.guardrail.trim()) {
    warnings.push('Document a guardrail or stop condition before save.');
  }

  return warnings;
}

function getExperimentBuilderMetrics(draft, warnings) {
  return [
    {
      label: 'Workspace drafts',
      value: padMetric(builderState.drafts.length),
      note: 'Saved templates and unsaved branches stay in the same builder workspace.',
    },
    {
      label: 'Selectors armed',
      value: padMetric(draft.target.tags.length + draft.target.environments.length + (draft.target.service ? 1 : 0)),
      note: 'Service name, tags, namespace, and environment targeting stay editable together.',
    },
    {
      label: 'Save posture',
      value: warnings.length === 0 ? 'Ready' : `-${warnings.length}`,
      note:
        warnings.length === 0
          ? 'Allowlist coverage and guardrails align for the current draft.'
          : warnings[0],
    },
  ];
}

function getExperimentSaveStateLabel() {
  if (builderState.saveState === 'saved' && builderState.lastSavedAt) {
    return `Saved locally at ${formatTime(builderState.lastSavedAt)}`;
  }

  if (builderState.saveState === 'dirty') {
    return 'Unsaved changes in the current workspace';
  }

  return 'No saved draft snapshot yet';
}

function getExperimentSaveCopy(warnings) {
  if (warnings.length === 0) {
    return 'Selectors, fault controls, and safety constraints are aligned for a local save.';
  }

  return `${warnings.length} checklist item${warnings.length === 1 ? '' : 's'} still need review before save.`;
}

function getExperimentNarrative(draft) {
  const target = getExperimentTargetSummary(draft);
  const fault = getExperimentFaultSummary(draft);
  return `${fault} targeting ${target}.`;
}

function getExperimentTargetSummary(draft) {
  return `${draft.target.service || 'unassigned service'} in ${draft.target.namespace} across ${formatEnvironmentNames(draft.target.environments)}`;
}

function getExperimentFaultSummary(draft) {
  if (draft.fault.type === 'latency') {
    return `${draft.fault.latencyMs}ms fixed latency at ${draft.fault.injectionRate}% traffic`;
  }

  return `HTTP ${draft.fault.statusCode} at ${draft.fault.injectionRate}% traffic`;
}

function formatEnvironmentNames(environmentIds) {
  return environmentIds
    .map((environmentId) => environments.find((environment) => environment.id === environmentId)?.name || environmentId)
    .join(', ');
}

function getExperimentPayloadPreview(draft) {
  return {
    name: draft.title,
    description: draft.description,
    target: {
      service: draft.target.service,
      namespace: draft.target.namespace,
      tags: draft.target.tags,
      environments: draft.target.environments,
    },
    fault:
      draft.fault.type === 'latency'
        ? {
            type: 'latency',
            latencyMs: draft.fault.latencyMs,
            trafficSharePercent: draft.fault.injectionRate,
          }
        : {
            type: 'http-error',
            statusCode: Number(draft.fault.statusCode),
            trafficSharePercent: draft.fault.injectionRate,
          },
    safety: {
      durationMinutes: draft.safety.durationMinutes,
      environmentAllowlist: draft.safety.allowlist,
      approvalRequired: draft.safety.approvalRequired,
      guardrail: draft.safety.guardrail,
      rollout: draft.safety.rollout,
    },
  };
}

function getActiveEnvironment() {
  const savedEnvironmentId = localStorage.getItem(STORAGE_KEY) || environments[1].id;

  return environments.find((environment) => environment.id === savedEnvironmentId) || environments[0];
}

function formatTime(date) {
  return new Intl.DateTimeFormat(undefined, {
    hour: 'numeric',
    minute: '2-digit',
    second: '2-digit',
  }).format(date);
}

function padMetric(value) {
  return String(value).padStart(2, '0');
}

function toToken(value) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, '-');
}

function formatFaultName(faultType) {
  const token = String(faultType || 'chaos');

  return token
    .split(/[^a-z0-9]+/i)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

function inferFaultType(title, summary) {
  const text = `${title} ${summary}`.toLowerCase();

  if (text.includes('latency')) {
    return 'latency';
  }

  if (text.includes('cpu')) {
    return 'cpu';
  }

  if (text.includes('packet')) {
    return 'packet loss';
  }

  if (text.includes('queue')) {
    return 'queue pause';
  }

  return 'chaos drill';
}

function inferDurationSeconds(text) {
  const match = String(text).match(/(\d+)\s*minute/);

  if (!match) {
    return null;
  }

  return Number(match[1]) * 60;
}

function formatDuration(seconds) {
  if (!seconds) {
    return 'Unknown duration';
  }

  const minutes = Math.round(seconds / 60);

  if (minutes < 1) {
    return `${seconds}s`;
  }

  if (minutes < 60) {
    return `${minutes}m`;
  }

  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;

  return remainder ? `${hours}h ${remainder}m` : `${hours}h`;
}

function formatDateTime(value) {
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(value));
}

function getStopLabel(statusLabel) {
  switch (statusLabel) {
    case 'Running':
      return 'Armed';
    case 'Holding':
      return 'Ready';
    case 'Recovering':
      return 'Watch';
    case 'Stop Requested':
      return 'Issued';
    default:
      return 'Review';
  }
}

function getStopNote(statusLabel) {
  switch (statusLabel) {
    case 'Running':
      return 'Stop control remains adjacent to the active run timeline.';
    case 'Holding':
      return 'Rollback action should remain visible even when the run is paused.';
    case 'Recovering':
      return 'Operators still need the stop context while recovery settles.';
    case 'Stop Requested':
      return 'The stop confirmation path is already visible in this stubbed flow.';
    default:
      return 'This run remains useful for reviewing the operator frame.';
  }
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}
