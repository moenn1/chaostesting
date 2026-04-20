CREATE TABLE IF NOT EXISTS agents (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    hostname VARCHAR(255) NOT NULL,
    environment VARCHAR(255) NOT NULL,
    region VARCHAR(255) NOT NULL,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_heartbeat_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_capabilities (
    agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    capability VARCHAR(255) NOT NULL,
    PRIMARY KEY (agent_id, capability)
);

CREATE INDEX IF NOT EXISTS idx_agents_environment_last_heartbeat ON agents(environment, last_heartbeat_at);
CREATE INDEX IF NOT EXISTS idx_agents_region_last_heartbeat ON agents(region, last_heartbeat_at);

CREATE TABLE IF NOT EXISTS agent_commands (
    id UUID PRIMARY KEY,
    agent_id UUID NOT NULL REFERENCES agents(id),
    fault_type VARCHAR(255) NOT NULL,
    duration_seconds BIGINT NOT NULL,
    target_scope VARCHAR(255) NOT NULL,
    status VARCHAR(64) NOT NULL,
    delivery_count INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    last_delivered_at TIMESTAMP WITH TIME ZONE,
    latest_message VARCHAR(2048)
);

CREATE TABLE IF NOT EXISTS agent_command_parameters (
    command_id UUID NOT NULL REFERENCES agent_commands(id) ON DELETE CASCADE,
    parameter_name VARCHAR(255) NOT NULL,
    parameter_value VARCHAR(255) NOT NULL,
    PRIMARY KEY (command_id, parameter_name)
);

CREATE INDEX IF NOT EXISTS idx_agent_commands_agent_status_created_at
    ON agent_commands(agent_id, status, created_at);

CREATE TABLE IF NOT EXISTS dispatch_approvals (
    id UUID PRIMARY KEY,
    target_environment VARCHAR(255) NOT NULL,
    approved_by VARCHAR(255) NOT NULL,
    reason VARCHAR(512) NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dispatch_approvals_environment_expires_at
    ON dispatch_approvals(target_environment, expires_at);

CREATE TABLE IF NOT EXISTS chaos_runs (
    id UUID PRIMARY KEY,
    experiment_id UUID,
    target_environment VARCHAR(255) NOT NULL,
    target_selector VARCHAR(255) NOT NULL,
    fault_type VARCHAR(255) NOT NULL,
    requested_duration_seconds BIGINT NOT NULL,
    latency_milliseconds INTEGER,
    traffic_percentage INTEGER,
    approval_id UUID REFERENCES dispatch_approvals(id),
    status VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    rollback_scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    rollback_verified_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    target_snapshot_json TEXT,
    stop_command_issued_at TIMESTAMP WITH TIME ZONE,
    stop_command_issued_by VARCHAR(255),
    stop_command_reason VARCHAR(512)
);

CREATE INDEX IF NOT EXISTS idx_chaos_runs_status_created_at ON chaos_runs(status, created_at);
CREATE INDEX IF NOT EXISTS idx_chaos_runs_environment_created_at ON chaos_runs(target_environment, created_at);
CREATE INDEX IF NOT EXISTS idx_chaos_runs_experiment_status_started_at
    ON chaos_runs(experiment_id, status, started_at);

CREATE TABLE IF NOT EXISTS run_assignments (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES chaos_runs(id) ON DELETE CASCADE,
    agent_id UUID NOT NULL REFERENCES agents(id),
    agent_name VARCHAR(255) NOT NULL,
    hostname VARCHAR(255) NOT NULL,
    environment VARCHAR(255) NOT NULL,
    region VARCHAR(255) NOT NULL,
    status VARCHAR(64) NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    stop_requested_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_run_assignments_run_status
    ON run_assignments(run_id, status, assigned_at);
CREATE INDEX IF NOT EXISTS idx_run_assignments_agent_status
    ON run_assignments(agent_id, status, assigned_at);

CREATE TABLE IF NOT EXISTS kill_switch_state (
    id BIGINT PRIMARY KEY,
    enabled BOOLEAN NOT NULL,
    last_enabled_by VARCHAR(255),
    last_enable_reason VARCHAR(512),
    last_enabled_at TIMESTAMP WITH TIME ZONE,
    last_disabled_by VARCHAR(255),
    last_disable_reason VARCHAR(512),
    last_disabled_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS safety_audit_records (
    id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    run_id UUID,
    operator VARCHAR(255) NOT NULL,
    reason VARCHAR(512) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resource_type VARCHAR(64),
    resource_id VARCHAR(255),
    metadata_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_safety_audit_records_recorded_at
    ON safety_audit_records(recorded_at);
CREATE INDEX IF NOT EXISTS idx_safety_audit_records_resource_recorded_at
    ON safety_audit_records(resource_type, resource_id, recorded_at);
CREATE INDEX IF NOT EXISTS idx_safety_audit_records_run_recorded_at
    ON safety_audit_records(run_id, recorded_at);

CREATE TABLE IF NOT EXISTS experiment_definitions (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    target_selector_json TEXT NOT NULL,
    fault_config_json TEXT NOT NULL,
    safety_rules_json TEXT NOT NULL,
    environment_metadata_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_experiment_definitions_updated_at
    ON experiment_definitions(updated_at, id);

CREATE TABLE IF NOT EXISTS latency_telemetry_snapshots (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES chaos_runs(id) ON DELETE CASCADE,
    phase VARCHAR(64) NOT NULL,
    latency_milliseconds INTEGER NOT NULL,
    traffic_percentage INTEGER NOT NULL,
    rollback_verified BOOLEAN NOT NULL,
    message VARCHAR(512) NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_latency_telemetry_snapshots_run_captured_at
    ON latency_telemetry_snapshots(run_id, captured_at);

CREATE TABLE IF NOT EXISTS run_lifecycle_events (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES chaos_runs(id) ON DELETE CASCADE,
    event_type VARCHAR(64) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    summary VARCHAR(512) NOT NULL,
    details_json TEXT NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_run_lifecycle_events_run_recorded_at
    ON run_lifecycle_events(run_id, recorded_at);

CREATE TABLE IF NOT EXISTS experiments (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(160) NOT NULL,
    hypothesis TEXT,
    target_environment VARCHAR(128) NOT NULL,
    target_selector VARCHAR(255) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_experiments_slug UNIQUE (slug)
);

CREATE INDEX IF NOT EXISTS idx_experiments_environment_updated_at
    ON experiments(target_environment, updated_at);
CREATE INDEX IF NOT EXISTS idx_experiments_archived_updated_at
    ON experiments(archived_at, updated_at);

CREATE TABLE IF NOT EXISTS experiment_runs (
    id UUID PRIMARY KEY,
    experiment_id UUID NOT NULL REFERENCES experiments(id),
    lead_agent_id UUID REFERENCES agents(id),
    approval_id UUID REFERENCES dispatch_approvals(id),
    status VARCHAR(64) NOT NULL,
    initiated_by VARCHAR(255) NOT NULL,
    environment VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    summary VARCHAR(512)
);

CREATE INDEX IF NOT EXISTS idx_experiment_runs_experiment_created_at
    ON experiment_runs(experiment_id, created_at);
CREATE INDEX IF NOT EXISTS idx_experiment_runs_status_created_at
    ON experiment_runs(status, created_at);
CREATE INDEX IF NOT EXISTS idx_experiment_runs_lead_agent_created_at
    ON experiment_runs(lead_agent_id, created_at);

CREATE TABLE IF NOT EXISTS fault_injection_actions (
    id UUID PRIMARY KEY,
    experiment_run_id UUID NOT NULL REFERENCES experiment_runs(id),
    agent_id UUID REFERENCES agents(id),
    fault_type VARCHAR(128) NOT NULL,
    target_scope VARCHAR(255) NOT NULL,
    phase VARCHAR(64) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_message VARCHAR(1024)
);

CREATE INDEX IF NOT EXISTS idx_fault_injection_actions_run_requested_at
    ON fault_injection_actions(experiment_run_id, requested_at);
CREATE INDEX IF NOT EXISTS idx_fault_injection_actions_agent_requested_at
    ON fault_injection_actions(agent_id, requested_at);

CREATE TABLE IF NOT EXISTS telemetry_snapshots (
    id UUID PRIMARY KEY,
    experiment_run_id UUID NOT NULL REFERENCES experiment_runs(id),
    agent_id UUID REFERENCES agents(id),
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    healthy BOOLEAN NOT NULL,
    latency_ms DOUBLE PRECISION,
    error_rate DOUBLE PRECISION,
    cpu_percent DOUBLE PRECISION,
    memory_percent DOUBLE PRECISION,
    summary_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_telemetry_snapshots_run_captured_at
    ON telemetry_snapshots(experiment_run_id, captured_at);
CREATE INDEX IF NOT EXISTS idx_telemetry_snapshots_agent_captured_at
    ON telemetry_snapshots(agent_id, captured_at);

CREATE TABLE IF NOT EXISTS audit_events (
    id UUID PRIMARY KEY,
    experiment_id UUID REFERENCES experiments(id),
    experiment_run_id UUID REFERENCES experiment_runs(id),
    agent_id UUID REFERENCES agents(id),
    fault_injection_action_id UUID REFERENCES fault_injection_actions(id),
    event_type VARCHAR(128) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    message VARCHAR(1024) NOT NULL,
    metadata_json TEXT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_events_experiment_occurred_at
    ON audit_events(experiment_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_events_run_occurred_at
    ON audit_events(experiment_run_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_events_agent_occurred_at
    ON audit_events(agent_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_events_action_occurred_at
    ON audit_events(fault_injection_action_id, occurred_at);
