# Step 2 – Plugin API Parallel Layer (legacy-safe)

## Objective

Define the **second migration step** after Step 1 (core boundary hardening), while keeping the current legacy deployment fully operational.

This step introduces a **parallel plugin API layer** in the Core, without requiring immediate migration of existing business modules.

## Scope

Step 2 focuses on three deliverables:

1. **Plugin API contracts** (interfaces + manifest model)
2. **Domain feature flags** (plugin vs legacy routing)
3. **Core proxy runtime resilience** (timeouts, circuit breaking, and fallback behavior)

No legacy module is removed in this phase.

## Non-goals

- Full migration of patient management logic to plugin (Step 3)
- Removal of legacy endpoints
- Runtime plugin unloading

## Legacy deployability rules

The following rules are mandatory for this phase:

- Every migrated route must preserve a **legacy fallback**.
- Feature flags are evaluated per domain (for example `patient`, `visit`, `billing`).
- If a plugin is unavailable, Core must either:
  - route to legacy implementation (if fallback is enabled), or
  - return deterministic 5xx/503 with trace information.

## Proposed architecture for Step 2

### 1) Plugin manifest as source of truth

Introduce a manifest contract per plugin:

- `name`
- `host`
- `port`
- `basePath`
- `permissions`
- `healthEndpoint`
- `timeoutMs`

The manifest is loaded by Core at startup and validated before route registration.

### 2) Central plugin registry

Core builds an in-memory registry from manifests and exposes read-only route metadata for diagnostics.

### 3) Parallel request resolution path

Incoming request resolution order:

1. Domain detection (`/api/patient/**`, `/api/visit/**`, ...)
2. Feature flag check (`domain.plugin.enabled`)
3. If enabled: proxy to plugin using manifest mapping
4. If proxy fails and `domain.plugin.fallback-legacy=true`: execute legacy path
5. Else: return controlled error response

### 4) Policy enforcement at Core

Authentication and authorization checks are executed once in Core before proxying.

### 5) Observability and operations

For each proxied request capture:

- `traceId`
- target plugin
- plugin latency
- outcome (`plugin_success`, `plugin_timeout`, `fallback_legacy`, `plugin_unavailable`)

## Suggested configuration model

```yaml
plugins:
  definitions:
    - name: patient
      host: localhost
      port: 4001
      basePath: /api/plugin/patient
      healthEndpoint: /actuator/health
      timeoutMs: 2500

features:
  domains:
    patient:
      plugin:
        enabled: false
        fallbackLegacy: true
    visit:
      plugin:
        enabled: false
        fallbackLegacy: true

resilience:
  proxy:
    connectTimeoutMs: 1000
    readTimeoutMs: 2500
    circuitBreaker:
      slidingWindowSize: 20
      failureRateThreshold: 50
      waitDurationInOpenStateMs: 10000
```

## Rollout plan

1. Merge contracts + configuration model behind default-off flags.
2. Enable plugin path in non-production environment for one domain.
3. Run shadow/canary validation and compare legacy vs plugin responses.
4. Keep production flags off until SLO and parity checks pass.

## Acceptance criteria for Step 2

- Core starts with zero plugins configured.
- Core starts with invalid plugin manifests rejected at startup.
- Domain plugin flags default to disabled.
- Proxy failures are observable and produce deterministic responses.
- Legacy mode remains the default production behavior.

## Risks and mitigations

- **Risk:** hidden route incompatibility between plugin and legacy.
  - **Mitigation:** contract tests + shadow traffic comparison.
- **Risk:** plugin outages cause user-visible disruptions.
  - **Mitigation:** fallback-to-legacy and circuit breaker defaults.
- **Risk:** authorization drift between legacy and plugin.
  - **Mitigation:** policy enforcement centralized in Core.

## Exit criteria toward Step 3

Step 3 (first real domain extraction, e.g., patient module) can start when:

- manifest and registry are stable,
- per-domain flags are operational,
- resilience defaults are validated,
- observability dashboard includes plugin routing outcomes.
