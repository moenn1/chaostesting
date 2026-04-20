# Security Authentication and RBAC

`MYG-23` secures the Chaos Platform control plane with OIDC-backed login, explicit role-based access control, and a development-only auth mode for local workflows.

## Auth modes

### OIDC mode

`application.yml` defaults to:

```yaml
chaos:
  auth:
    mode: oidc
    oidc:
      role-claim: groups
      principal-claim: preferred_username
```

Spring Security handles the login flow with `oauth2Login()`. The role claim is read case-insensitively and normalized from values like `viewer`, `ROLE_VIEWER`, or `chaos-viewer`.

Supported platform roles:

- `VIEWER`
- `OPERATOR`
- `APPROVER`
- `ADMIN`

### Dev mode

`application-local.yml` enables a local-only auth shim:

```yaml
chaos:
  auth:
    mode: dev
    dev:
      default-username: local-admin
      default-roles:
        - ADMIN
```

In dev mode, every request is authenticated from either:

- `X-Chaos-Dev-User`
- `X-Chaos-Dev-Roles`

Or, if the headers are absent, the configured default local user.

## Route permissions

### Viewer

- `GET /`
- `GET /index.html`
- `GET /app.js`
- `GET /styles.css`
- `GET /experiments/**`
- `GET /live-runs/**`
- `GET /results/**`
- `GET /history/**`
- `GET /agents/**`
- `GET /audit/events`
- `GET /safety/audit-records`
- `GET /safety/runs`
- `GET /safety/kill-switch`
- `GET /auth/me`

### Operator

- `POST /safety/dispatches/validate`
- `POST /safety/dispatches`
- `POST /safety/runs/{runId}/stop`

### Approver

- `POST /safety/approvals`

### Admin

- `POST /safety/kill-switch/enable`
- `POST /safety/kill-switch/disable`
- `GET /h2-console/**` in local/test environments

## Actor integrity

Operator, approver, and admin actions no longer trust request-body actor fields. Audit entries now derive the actor from the authenticated principal so callers cannot spoof `requestedBy`, `approvedBy`, or `operator`.

## Known follow-up

- `POST /agents/register`
- `POST /agents/heartbeat`

These remain open intentionally until the signed agent-registration and command-authentication work in `MYG-44` lands.
