# Example Walkthroughs

These walkthroughs are organized by what the current branch can prove today.

- API-backed today: staging latency drills, approval-gated production latency drills, process-kill drills, timed service pauses, run analysis, audit reads, and agent lifecycle checks.
- Demo-only today: the HTTP error experiment builder route and payload preview.
- Not wired yet: a live RabbitMQ-backed execution bus and a backend-verified HTTP error dispatch lifecycle.

Run the setup steps in [Setup guide](local-development.md) first.

## Walkthrough 1: staging latency drill

This is the most complete end-to-end API flow on the current branch.

### 1. Create an experiment template

```bash
curl -s -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: operator-demo' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  http://localhost:8080/api/experiments \
  -d '{
    "name":"Checkout latency envelope",
    "description":"Inject 350ms latency into a guarded checkout canary.",
    "targetSelector":{
      "service":"checkout-api",
      "namespace":"payments",
      "labels":{"lane":"canary"},
      "tags":["checkout","payments"]
    },
    "faultConfig":{
      "type":"latency",
      "durationSeconds":480,
      "parameters":{"latencyMs":350,"percentage":30}
    },
    "safetyRules":{
      "abortConditions":["Abort if 5xx exceeds 2.5% for 90 seconds"],
      "maxAffectedTargets":2,
      "approvalRequired":false,
      "rollbackMode":"automatic"
    },
    "environmentMetadata":{
      "environment":"staging",
      "region":"us-phoenix-1",
      "team":"payments"
    }
  }'
```

Expected response shape:

- `id`: new experiment UUID
- `faultConfig.type`: `latency`
- `faultConfig.parameters.latencyMs`: `350`
- `environmentMetadata.environment`: `staging`

### 2. Validate the run before dispatch

```bash
curl -s -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: operator-demo' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  http://localhost:8080/safety/dispatches/validate \
  -d '{
    "targetEnvironment":"staging",
    "targetSelector":"checkout-service",
    "faultType":"latency",
    "requestedDurationSeconds":120,
    "latencyMilliseconds":350,
    "trafficPercentage":30,
    "requestedBy":"operator-demo"
  }'
```

Expected response shape:

- `decision`: `ALLOWED`
- `allowed`: `true`
- `maxDurationSeconds`: `900`
- `maxLatencyMilliseconds`: `5000`
- `violations`: empty array

### 3. Start the run

```bash
curl -s -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: operator-demo' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  http://localhost:8080/safety/dispatches \
  -d '{
    "targetEnvironment":"staging",
    "targetSelector":"checkout-service",
    "faultType":"latency",
    "requestedDurationSeconds":120,
    "latencyMilliseconds":350,
    "trafficPercentage":30,
    "requestedBy":"operator-demo"
  }'
```

Copy the returned `dispatchId`. You will use it as the run id in the next steps.

Expected response shape:

- `status`: `AUTHORIZED`
- `dispatchId`: run UUID
- `targetEnvironment`: `staging`
- `latencyMilliseconds`: `350`
- `trafficPercentage`: `30`

### 4. Inspect the live run and telemetry

```bash
curl -s \
  -H 'X-Chaos-Dev-User: viewer-demo' \
  -H 'X-Chaos-Dev-Roles: VIEWER' \
  http://localhost:8080/safety/runs/<dispatch-id>
```

```bash
curl -s \
  -H 'X-Chaos-Dev-User: viewer-demo' \
  -H 'X-Chaos-Dev-Roles: VIEWER' \
  http://localhost:8080/safety/runs/<dispatch-id>/telemetry
```

Expected run fields:

- `status`: `ACTIVE` before stop or timeout
- `rollbackScheduledAt`: timestamp 120 seconds after authorization

Expected first telemetry entry:

- `phase`: `INJECTION`
- `latencyMilliseconds`: `350`
- `trafficPercentage`: `30`
- `rollbackVerified`: `false`

### 5. Stop the run and analyze the rollback

```bash
curl -s -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: ops-oncall' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  http://localhost:8080/safety/runs/<dispatch-id>/stop \
  -d '{
    "reason":"customer-impact containment"
  }'
```

```bash
curl -s \
  -H 'X-Chaos-Dev-User: viewer-demo' \
  -H 'X-Chaos-Dev-Roles: VIEWER' \
  "http://localhost:8080/safety/audit-records?resourceId=<dispatch-id>"
```

After the stop completes, expect:

- run `status`: `ROLLED_BACK`
- `stopCommandIssuedBy`: `ops-oncall`
- `stopCommandReason`: `customer-impact containment`
- latest telemetry `phase`: `ROLLBACK`
- latest telemetry `rollbackVerified`: `true`
- audit actions in descending order: `RUN_ROLLBACK_VERIFIED`, `RUN_STOP_REQUESTED`, `RUN_STARTED`

## Walkthrough 2: approval-gated production latency drill

Use this when you want to show the production guardrail path.

### 1. Create an approval

```bash
curl -s -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: platform-admin' \
  -H 'X-Chaos-Dev-Roles: APPROVER' \
  http://localhost:8080/safety/approvals \
  -d '{
    "targetEnvironment":"prod",
    "reason":"approved canary chaos window"
  }'
```

Copy the returned approval `id`.

### 2. Dispatch the production run with that approval

```bash
curl -s -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: experiment-operator' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  http://localhost:8080/safety/dispatches \
  -d '{
    "targetEnvironment":"prod",
    "targetSelector":"checkout-service",
    "faultType":"latency",
    "requestedDurationSeconds":300,
    "latencyMilliseconds":350,
    "trafficPercentage":30,
    "approvalId":"<approval-id>",
    "requestedBy":"experiment-operator"
  }'
```

Expected response shape:

- `status`: `AUTHORIZED`
- `approvalId`: the UUID you just created
- `targetEnvironment`: `prod`

### 3. Confirm the approval and run audit trail

```bash
curl -s \
  -H 'X-Chaos-Dev-User: viewer-demo' \
  -H 'X-Chaos-Dev-Roles: VIEWER' \
  "http://localhost:8080/audit/events?action=approval_created&resourceType=approval&resourceId=<approval-id>"
```

```bash
curl -s \
  -H 'X-Chaos-Dev-User: viewer-demo' \
  -H 'X-Chaos-Dev-Roles: VIEWER' \
  "http://localhost:8080/audit/events?action=run_started&resourceType=run&actor=experiment-operator"
```

Expected audit confirmations:

- approval event actor: `platform-admin`
- run-start event actor: `experiment-operator`
- run-start event metadata includes `approvalId` and `targetSelector`

## Walkthrough 3: HTTP error authoring and demo review

The current branch supports `http_error` experiment templates and demo UI payload previews. It does not yet provide a backend-verified end-to-end HTTP error execution lifecycle.

### 1. Create an HTTP error experiment template

```bash
curl -s -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: operator-demo' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  http://localhost:8080/api/experiments \
  -d '{
    "name":"Inventory gateway 503 surge",
    "description":"Return HTTP 503s while consumer lag recovery is observed.",
    "targetSelector":{
      "service":"inventory-gateway",
      "namespace":"fulfillment",
      "tags":["team:orders","service-group:inventory"]
    },
    "faultConfig":{
      "type":"http_error",
      "durationSeconds":600,
      "parameters":{"statusCode":503,"percentage":18}
    },
    "safetyRules":{
      "abortConditions":["Abort if consumer lag remains above 180 seconds after rollback"],
      "maxAffectedTargets":1,
      "approvalRequired":true,
      "rollbackMode":"manual"
    },
    "environmentMetadata":{
      "environment":"staging-west",
      "region":"us-phoenix-1",
      "team":"orders"
    }
  }'
```

Expected response shape:

- `faultConfig.type`: `http_error`
- `faultConfig.parameters.statusCode`: `503`
- `safetyRules.approvalRequired`: `true`

### 2. Confirm that the local agent catalog advertises HTTP error capability

```bash
curl -s \
  -H 'X-Chaos-Dev-User: viewer-demo' \
  -H 'X-Chaos-Dev-Roles: VIEWER' \
  "http://localhost:8080/agents?capability=http_error"
```

Expected result:

- at least one agent record with `supportedFaultCapabilities` containing `http_error`

### 3. Review the demo UI flow

Open this route in the browser:

```text
http://localhost:8080/experiments/?experiment=exp-inventory-packet-loss
```

What to expect in the builder:

- title: `Inventory gateway 503 surge`
- fault mode: `HTTP error`
- status code option: `503`
- rollout scope: `single-service-slice`
- approval required: enabled

This is the recommended demo path for HTTP error scenarios on the current branch because the UI already shows the payload shape and guardrail copy clearly.

### Current limitation

Do not present this as a live backend execution proof yet. The dispatch contract and telemetry model are still latency-oriented, so the repo is ready for HTTP error authoring and demo review, but not for a backend-verified HTTP error run lifecycle.

## Walkthrough 4: process kill and timed service pause lifecycle

These actions now use the backend dispatch lifecycle directly. They reuse the existing run API, but the run status and telemetry messages are action-specific so operators can distinguish activation from verified cleanup.

### 1. Validate and dispatch a process kill run

```bash
curl -s -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: operator-demo' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  http://localhost:8080/safety/dispatches \
  -d '{
    "targetEnvironment":"staging",
    "targetSelector":"checkout-worker",
    "faultType":"process_kill",
    "requestedDurationSeconds":120,
    "requestedBy":"operator-demo"
  }'
```

Expected response shape:

- `status`: `AUTHORIZED`
- `faultType`: `process_kill`
- `targetSelector`: `checkout-worker`

Inspect the run immediately after dispatch:

```bash
curl -s \
  -H 'X-Chaos-Dev-User: viewer-demo' \
  -H 'X-Chaos-Dev-Roles: VIEWER' \
  http://localhost:8080/safety/runs/<dispatch-id>
```

Expected run fields:

- `status`: `ACTIVE`
- `statusMessage`: `Process kill action issued for target checkout-worker; awaiting recovery verification.`

### 2. Stop the process kill run after recovery is verified

```bash
curl -s -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: ops-oncall' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  http://localhost:8080/safety/runs/<dispatch-id>/stop \
  -d '{
    "reason":"recovery validated"
  }'
```

Expected results:

- run `status`: `ROLLED_BACK`
- `statusMessage`: `Process recovery verified after recovery validated.`
- latest telemetry `message`: `Process recovery verified after recovery validated.`

### 3. Dispatch a timed service pause run

```bash
curl -s -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-Chaos-Dev-User: operator-demo' \
  -H 'X-Chaos-Dev-Roles: OPERATOR' \
  http://localhost:8080/safety/dispatches \
  -d '{
    "targetEnvironment":"staging",
    "targetSelector":"inventory-daemon",
    "faultType":"service_pause",
    "requestedDurationSeconds":180,
    "requestedBy":"operator-demo"
  }'
```

Expected run fields after dispatch:

- `status`: `ACTIVE`
- `statusMessage`: `Timed service pause active for target inventory-daemon.`

After the scheduled duration or a manual stop, expect the rollback message to shift to:

- `Timed service pause cleanup verified after <reason>.`

### 4. Confirm the local agent catalog exposes the new capabilities

```bash
curl -s \
  -H 'X-Chaos-Dev-User: viewer-demo' \
  -H 'X-Chaos-Dev-Roles: VIEWER' \
  "http://localhost:8080/agents?capability=process_kill"
```

```bash
curl -s \
  -H 'X-Chaos-Dev-User: viewer-demo' \
  -H 'X-Chaos-Dev-Roles: VIEWER' \
  "http://localhost:8080/agents?capability=service_pause"
```

Expected result:

- at least one agent record for each capability in the seeded local catalog
