CREATE TABLE IF NOT EXISTS agents (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    hostname VARCHAR(255) NOT NULL,
    environment VARCHAR(255) NOT NULL,
    region VARCHAR(255) NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL,
    last_heartbeat_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_capabilities (
    agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    capability VARCHAR(255) NOT NULL,
    PRIMARY KEY (agent_id, capability)
);

CREATE INDEX IF NOT EXISTS idx_agents_environment ON agents(environment);
CREATE INDEX IF NOT EXISTS idx_agents_region ON agents(region);
